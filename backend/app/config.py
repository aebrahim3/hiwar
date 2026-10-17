"""Settings, read from environment variables (or a .env file next to the backend)."""

import os
from pathlib import Path

ENV_FILE = Path(__file__).resolve().parent.parent / ".env"


def _load_env_file() -> None:
    """Tiny .env loader so we don't need python-dotenv."""
    if not ENV_FILE.exists():
        return
    for line in ENV_FILE.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip().strip('"').strip("'"))


_load_env_file()

# --- Fo ---
FO_MCP_URL = os.getenv("FO_MCP_URL", "https://mcp.api.wajo.ai/fo")
FO_TOKEN = os.getenv("FO_TOKEN", "")  # only if Wajo hands you one; otherwise run fo_login.py

# --- App defaults (each schedule/session can override language and level) ---
TARGET_LANGUAGE = os.getenv("TARGET_LANGUAGE", "Arabic")
NATIVE_LANGUAGE = os.getenv("NATIVE_LANGUAGE", "English")
DEFAULT_TIMEZONE = os.getenv("DEFAULT_TIMEZONE", "America/Vancouver")
# How many seconds of silence Fo waits before replying on a call (you pause to think mid-sentence).
PAUSE_SECONDS = int(os.getenv("PAUSE_SECONDS", "3"))
_BACKEND_DIR = Path(__file__).resolve().parent.parent
# The app used to be called Jidhr; keep using an existing jidhr.db so no sessions are lost.
_DEFAULT_DB = _BACKEND_DIR / "hiwar.db"
if not _DEFAULT_DB.exists() and (_BACKEND_DIR / "jidhr.db").exists():
    _DEFAULT_DB = _BACKEND_DIR / "jidhr.db"
DB_PATH = os.getenv("DB_PATH", str(_DEFAULT_DB))

FO_TOKENS_FILE = Path(DB_PATH).with_name("fo_tokens.json")  # written by fo_login.py

# Mock mode: on automatically until you sign in to Fo, so the app can be built first.
# In mock mode a "session" takes MOCK_SECONDS and returns a fake transcript + breakdown.
MOCK_FO = os.getenv("MOCK_FO", "auto")
MOCK_SECONDS = int(os.getenv("MOCK_SECONDS", "12"))


def fo_is_mocked() -> bool:
    if MOCK_FO == "auto":
        return not (FO_TOKEN or FO_TOKENS_FILE.exists())
    return MOCK_FO.lower() in ("1", "true", "yes")
