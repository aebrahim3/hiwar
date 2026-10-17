"""Signing in to Fo with OAuth, the same way Claude connected to it.

Analogy: a hotel key card. You show ID at the front desk once (sign in to
Wajo in your browser). You get a key card (access token) that expires, plus
a voucher (refresh token) to get a new card without showing ID again.

Steps, all from Wajo's published OAuth metadata:
1. register:  POST /oauth/register -> a client_id for this app (no secret needed)
2. authorize: open /oauth/authorize in the browser, you sign in, Wajo redirects
              back to localhost with a one-time code
3. token:     trade the code (plus the PKCE verifier) for access + refresh tokens
4. refresh:   when the access token expires, trade the refresh token for a new one
"""

import base64
import hashlib
import json
import secrets
import time
from urllib.parse import urlencode, urlparse

import httpx

from . import config

TOKENS_FILE = config.FO_TOKENS_FILE
SCOPE = "mcp"


def _auth_server() -> str:
    # https://mcp.api.wajo.ai/fo -> https://mcp.api.wajo.ai
    u = urlparse(config.FO_MCP_URL)
    return f"{u.scheme}://{u.netloc}"


def _load() -> dict | None:
    if TOKENS_FILE.exists():
        return json.loads(TOKENS_FILE.read_text())
    return None


def _save(data: dict) -> None:
    TOKENS_FILE.write_text(json.dumps(data, indent=2))


# ---------------------------------------------------------------- one-time login

def register(redirect_uri: str) -> str:
    resp = httpx.post(
        f"{_auth_server()}/oauth/register",
        json={
            "client_name": "Hiwar",
            "redirect_uris": [redirect_uri],
            "grant_types": ["authorization_code", "refresh_token"],
            "response_types": ["code"],
            "token_endpoint_auth_method": "none",
            "scope": SCOPE,
        },
        timeout=30,
    )
    resp.raise_for_status()
    return resp.json()["client_id"]


def make_pkce() -> tuple[str, str]:
    """PKCE: a secret (verifier) we keep, and its hash (challenge) we send first.
    Proves the app trading the code is the same one that started the login."""
    verifier = secrets.token_urlsafe(64)
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
    return verifier, challenge


def authorize_url(client_id: str, redirect_uri: str, challenge: str, state: str) -> str:
    params = {
        "response_type": "code",
        "client_id": client_id,
        "redirect_uri": redirect_uri,
        "code_challenge": challenge,
        "code_challenge_method": "S256",
        "scope": SCOPE,
        "state": state,
        "resource": config.FO_MCP_URL,
    }
    return f"{_auth_server()}/oauth/authorize?{urlencode(params)}"


def exchange_code(client_id: str, code: str, verifier: str, redirect_uri: str) -> None:
    resp = httpx.post(
        f"{_auth_server()}/oauth/token",
        data={
            "grant_type": "authorization_code",
            "code": code,
            "redirect_uri": redirect_uri,
            "client_id": client_id,
            "code_verifier": verifier,
            "resource": config.FO_MCP_URL,
        },
        timeout=30,
    )
    resp.raise_for_status()
    _store_tokens(client_id, resp.json(), old_refresh=None)


def _store_tokens(client_id: str, tok: dict, old_refresh: str | None) -> dict:
    data = {
        "client_id": client_id,
        "access_token": tok["access_token"],
        "refresh_token": tok.get("refresh_token") or old_refresh,
        "expires_at": time.time() + int(tok.get("expires_in", 3600)) - 60,
    }
    _save(data)
    return data


# ---------------------------------------------------------------- every request

def refresh() -> str:
    data = _load()
    if not data or not data.get("refresh_token"):
        raise RuntimeError("Not signed in to Fo. Run: python fo_login.py")
    resp = httpx.post(
        f"{_auth_server()}/oauth/token",
        data={
            "grant_type": "refresh_token",
            "refresh_token": data["refresh_token"],
            "client_id": data["client_id"],
            "resource": config.FO_MCP_URL,
        },
        timeout=30,
    )
    if resp.status_code >= 400:
        raise RuntimeError("Fo sign-in expired. Run: python fo_login.py")
    return _store_tokens(data["client_id"], resp.json(), data["refresh_token"])["access_token"]


def access_token() -> str:
    """A valid token: FO_TOKEN if you set one, else the saved login (refreshed if expired)."""
    if config.FO_TOKEN:
        return config.FO_TOKEN
    data = _load()
    if not data:
        raise RuntimeError("Not signed in to Fo. Run: python fo_login.py")
    if time.time() >= data.get("expires_at", 0):
        return refresh()
    return data["access_token"]
