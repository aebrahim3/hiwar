"""Talks to Fo's MCP server.

Analogy: ordering at a busy counter. `ask_fo` hands Fo the order and Fo often
says "working on it" instead of answering right away. We then keep asking
`get_fo_reply` ("is it ready?"), each time waiting up to 40 seconds, until Fo
says complete or failed. `run_task` does that whole loop for you.
"""

import asyncio
import json
import logging
from dataclasses import dataclass

from . import config, fo_auth

log = logging.getLogger("hiwar.fo")


@dataclass
class FoReply:
    status: str                 # complete | working | failed | waiting_for_user
    text: str                   # Fo's reply, or its progress line while working
    conversation_id: str | None


def _parse(result) -> FoReply:
    """Fo's tools may return structured JSON or plain text; handle both."""
    data = result.structured_content
    text = "\n".join(c.text for c in result.content if getattr(c, "type", "") == "text")

    # Some servers wrap a plain-string result as {"result": "..."}; unwrap it.
    if isinstance(data, dict) and "status" not in data and isinstance(data.get("result"), str):
        text, data = data["result"], None

    if data is None and text.strip().startswith("{"):
        try:
            data = json.loads(text)
        except json.JSONDecodeError:
            data = None

    if not isinstance(data, dict):
        status = "failed" if result.is_error else "complete"
        return FoReply(status=status, text=text, conversation_id=None)

    status = str(data.get("status", "complete")).lower()
    body = data.get("response") or data.get("reply") or data.get("message") or data.get("text") or data.get("progress") or text
    if "waiting" in status or data.get("waitingForUser") or data.get("waiting_for_user"):
        status = "waiting_for_user"
    conv = data.get("conversationId") or data.get("conversation_id")
    return FoReply(status=status, text=str(body), conversation_id=conv)


async def _call(tool: str, args: dict, _retried: bool = False) -> FoReply:
    # Imported here so mock mode works even if the MCP SDK isn't installed.
    from mcp import Client
    from mcp.client.streamable_http import streamable_http_client
    from mcp.shared._httpx_utils import create_mcp_http_client

    token = await asyncio.to_thread(fo_auth.access_token)
    http = create_mcp_http_client(headers={"Authorization": f"Bearer {token}"})
    seen_status: list[int] = []

    async def remember_status(response):
        seen_status.append(response.status_code)

    http.event_hooks["response"].append(remember_status)
    try:
        async with http:
            async with Client(streamable_http_client(config.FO_MCP_URL, http_client=http)) as client:
                result = await client.call_tool(tool, args, read_timeout_seconds=90)
    except BaseException as exc:
        # The MCP SDK hides HTTP status codes; surface the useful ones.
        if 401 in seen_status and not _retried and not config.FO_TOKEN:
            await asyncio.to_thread(fo_auth.refresh)  # token went stale early: refresh once, retry
            return await _call(tool, args, _retried=True)
        if any(s in (401, 403) for s in seen_status):
            raise FoError("Fo rejected the sign-in. Run: python fo_login.py") from exc
        if 404 in seen_status:
            raise FoError(f"Fo MCP URL not found: {config.FO_MCP_URL}") from exc
        raise
    return _parse(result)


class FoError(Exception):
    pass


async def run_task(message: str, on_progress=None, max_minutes: int = 20) -> tuple[FoReply, str | None]:
    """Send a request to Fo in a fresh conversation and wait until it settles.

    Returns (final reply, conversation id). `on_progress(text)` is called with
    each progress line so the app can show what Fo is doing.
    """
    reply = await _call("ask_fo", {"message": message, "newConversation": True})
    conv = reply.conversation_id
    loops = max_minutes * 60 // 40

    for _ in range(loops):
        if reply.status != "working":
            return reply, conv
        if on_progress:
            on_progress(reply.text)
        args = {"waitSeconds": 40}
        if conv:
            args["conversationId"] = conv
        reply = await _call("get_fo_reply", args)
        conv = reply.conversation_id or conv

    return FoReply("failed", "Fo took too long to finish.", conv), conv


async def follow_up(message: str, conversation_id: str | None, max_minutes: int = 10) -> FoReply:
    """Continue the same Fo conversation (e.g. 'send me the session summary')."""
    args = {"message": message}
    if conversation_id:
        args["conversationId"] = conversation_id
    reply = await _call("ask_fo", args)
    for _ in range(max_minutes * 60 // 40):
        if reply.status != "working":
            return reply
        args = {"waitSeconds": 40}
        if conversation_id:
            args["conversationId"] = conversation_id
        reply = await _call("get_fo_reply", args)
    return FoReply("failed", "Fo took too long to answer.", conversation_id)
