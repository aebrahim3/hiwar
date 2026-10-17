"""SQLite storage. Two tables:

- schedules: "call me every weekday at 7pm for 15 minutes about travel"
- sessions:  one actual conversation with Fo, its transcript, and Fo's breakdown

A schedule produces sessions. You can also start a one-off session with no schedule.
"""

import json
import sqlite3
import threading
from datetime import datetime, timezone

from . import config

_lock = threading.Lock()
_conn = sqlite3.connect(config.DB_PATH, check_same_thread=False)
_conn.row_factory = sqlite3.Row

_conn.executescript(
    """
    CREATE TABLE IF NOT EXISTS schedules (
        id               INTEGER PRIMARY KEY AUTOINCREMENT,
        title            TEXT NOT NULL,
        topic            TEXT,
        language         TEXT NOT NULL,
        level            TEXT NOT NULL,          -- beginner | intermediate | advanced
        duration_minutes INTEGER NOT NULL,
        frequency        TEXT NOT NULL,          -- once | daily | weekly | every_n_days
        weekdays         TEXT NOT NULL,          -- JSON list, 0=Mon..6=Sun (weekly only)
        interval_days    INTEGER NOT NULL,       -- every_n_days only
        time_of_day      TEXT NOT NULL,          -- "19:00"
        timezone         TEXT NOT NULL,          -- "America/Vancouver"
        start_date       TEXT NOT NULL,          -- "2026-10-17"
        active           INTEGER NOT NULL DEFAULT 1,
        next_run_at      TEXT,                   -- UTC ISO, null when finished/paused
        created_at       TEXT NOT NULL
    );
    CREATE TABLE IF NOT EXISTS sessions (
        id               INTEGER PRIMARY KEY AUTOINCREMENT,
        schedule_id      INTEGER,
        status           TEXT NOT NULL,          -- calling | in_call | summarizing | complete | failed
        progress         TEXT,
        topic            TEXT,
        language         TEXT NOT NULL,
        level            TEXT NOT NULL,
        duration_minutes INTEGER NOT NULL,
        started_at       TEXT NOT NULL,
        ended_at         TEXT,
        conversation_id  TEXT,
        transcript       TEXT,
        breakdown        TEXT,                   -- JSON: summary, mistakes, highlights, improvements
        saved            INTEGER                 -- 1 yes, 0 no, NULL not answered
    );
    """
)
_conn.commit()

# ---------------------------------------------------------------- migrations
# Add columns that newer versions need to databases created by older versions.

def _columns(table: str) -> list[str]:
    return [r[1] for r in _conn.execute(f"PRAGMA table_info({table})")]


if "mock" not in _columns("sessions"):
    _conn.execute("ALTER TABLE sessions ADD COLUMN mock INTEGER NOT NULL DEFAULT 0")
    from .mocks import MOCK_TRANSCRIPT  # mark fake sessions made before this change
    _conn.execute("UPDATE sessions SET mock = 1 WHERE transcript = ?", (MOCK_TRANSCRIPT,))
for _table, _col, _decl in [
    ("sessions", "scenario", "TEXT"),            # role-play scenario id, or NULL
    ("schedules", "scenario", "TEXT"),
    ("schedules", "calendar_status", "TEXT"),    # NULL | adding | added | failed | removing
    ("schedules", "calendar_note", "TEXT"),      # Fo's reply or the error, for the app to show
]:
    if _col not in _columns(_table):
        _conn.execute(f"ALTER TABLE {_table} ADD COLUMN {_col} {_decl}")
_conn.commit()


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def _exec(sql: str, params: tuple = ()) -> sqlite3.Cursor:
    with _lock:
        cur = _conn.execute(sql, params)
        _conn.commit()
        return cur


def _one(sql: str, params: tuple = ()) -> sqlite3.Row | None:
    with _lock:
        return _conn.execute(sql, params).fetchone()


def _all(sql: str, params: tuple = ()) -> list[sqlite3.Row]:
    with _lock:
        return _conn.execute(sql, params).fetchall()


# ---------------------------------------------------------------- schedules

def _schedule(row: sqlite3.Row) -> dict:
    d = dict(row)
    d["weekdays"] = json.loads(d["weekdays"])
    d["active"] = bool(d["active"])
    return d


def create_schedule(s: dict) -> int:
    cur = _exec(
        "INSERT INTO schedules (title, topic, scenario, language, level, duration_minutes, frequency, weekdays, "
        "interval_days, time_of_day, timezone, start_date, active, next_run_at, created_at) "
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)",
        (s["title"], s["topic"], s.get("scenario"), s["language"], s["level"], s["duration_minutes"],
         s["frequency"], json.dumps(s["weekdays"]), s["interval_days"], s["time_of_day"], s["timezone"],
         s["start_date"], s["next_run_at"], now_iso()),
    )
    return cur.lastrowid


def update_schedule(schedule_id: int, **fields) -> None:
    if "weekdays" in fields:
        fields["weekdays"] = json.dumps(fields["weekdays"])
    if "active" in fields:
        fields["active"] = int(fields["active"])
    cols = ", ".join(f"{k} = ?" for k in fields)
    _exec(f"UPDATE schedules SET {cols} WHERE id = ?", (*fields.values(), schedule_id))


def get_schedule(schedule_id: int) -> dict | None:
    row = _one("SELECT * FROM schedules WHERE id = ?", (schedule_id,))
    return _schedule(row) if row else None


def list_schedules() -> list[dict]:
    return [_schedule(r) for r in _all("SELECT * FROM schedules ORDER BY created_at DESC")]


def delete_schedule(schedule_id: int) -> None:
    _exec("DELETE FROM schedules WHERE id = ?", (schedule_id,))


def due_schedules() -> list[dict]:
    rows = _all(
        "SELECT * FROM schedules WHERE active = 1 AND next_run_at IS NOT NULL AND next_run_at <= ?",
        (now_iso(),),
    )
    return [_schedule(r) for r in rows]


# ---------------------------------------------------------------- sessions

def _session(row: sqlite3.Row) -> dict:
    d = dict(row)
    d["breakdown"] = json.loads(d["breakdown"]) if d["breakdown"] else None
    d["saved"] = None if d["saved"] is None else bool(d["saved"])
    d["mock"] = bool(d.get("mock"))
    d.pop("conversation_id", None)  # internal handle, never sent to the app
    return d


def create_session(schedule_id: int | None, topic: str | None, language: str, level: str, duration: int,
                   scenario: str | None = None) -> int:
    cur = _exec(
        "INSERT INTO sessions (schedule_id, status, progress, topic, scenario, language, level, "
        "duration_minutes, started_at) VALUES (?, 'calling', 'Asking Fo to call you', ?, ?, ?, ?, ?, ?)",
        (schedule_id, topic, scenario, language, level, duration, now_iso()),
    )
    return cur.lastrowid


def update_session(session_id: int, **fields) -> None:
    if "breakdown" in fields and fields["breakdown"] is not None:
        fields["breakdown"] = json.dumps(fields["breakdown"], ensure_ascii=False)
    for key in ("saved", "mock"):
        if key in fields and fields[key] is not None:
            fields[key] = int(fields[key])
    cols = ", ".join(f"{k} = ?" for k in fields)
    _exec(f"UPDATE sessions SET {cols} WHERE id = ?", (*fields.values(), session_id))


def get_session(session_id: int) -> dict | None:
    row = _one("SELECT * FROM sessions WHERE id = ?", (session_id,))
    return _session(row) if row else None


def completed_sessions(include_mock: bool = False) -> list[dict]:
    """Finished sessions, oldest first, for progress stats."""
    sql = "SELECT * FROM sessions WHERE status = 'complete'" + ("" if include_mock else " AND mock = 0")
    return [_session(r) for r in _all(sql + " ORDER BY started_at ASC")]


def list_sessions(saved_only: bool = False) -> list[dict]:
    sql = "SELECT * FROM sessions" + (" WHERE saved = 1" if saved_only else "") + " ORDER BY started_at DESC"
    return [_session(r) for r in _all(sql)]


def active_session() -> dict | None:
    row = _one("SELECT * FROM sessions WHERE status IN ('calling', 'in_call', 'summarizing') LIMIT 1")
    return _session(row) if row else None


def recent_mistakes(language: str, limit_sessions: int = 3) -> list[dict]:
    """Mistakes from your last few saved real sessions (never mock ones), so Fo can revisit them."""
    rows = _all(
        "SELECT breakdown FROM sessions WHERE saved = 1 AND mock = 0 AND language = ? "
        "AND breakdown IS NOT NULL ORDER BY started_at DESC LIMIT ?",
        (language, limit_sessions),
    )
    mistakes = []
    for r in rows:
        mistakes += json.loads(r["breakdown"]).get("mistakes", [])
    return mistakes[:8]


def last_focus(language: str) -> str | None:
    """'Focus for next session' from your most recent saved session."""
    row = _one(
        "SELECT breakdown FROM sessions WHERE saved = 1 AND mock = 0 AND language = ? "
        "AND breakdown IS NOT NULL ORDER BY started_at DESC LIMIT 1",
        (language,),
    )
    return (json.loads(row["breakdown"]).get("next_focus") or None) if row else None
