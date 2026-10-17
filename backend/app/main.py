"""Hiwar backend: the API the Android app talks to.

Run:  uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
Docs: http://localhost:8000/docs  (try every endpoint from the browser)
"""

import asyncio
import logging
import re
from contextlib import asynccontextmanager
from datetime import date, datetime
from typing import Literal
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, RedirectResponse
from pydantic import BaseModel, Field, field_validator, model_validator

from . import calendar_sync, config, db, practice, progress, recurrence, scenarios

logging.basicConfig(level=logging.INFO)


@asynccontextmanager
async def lifespan(_: FastAPI):
    task = asyncio.create_task(practice.scheduler_loop())
    yield
    task.cancel()


app = FastAPI(title="Hiwar API", lifespan=lifespan)


@app.exception_handler(RequestValidationError)
async def readable_validation_error(_: Request, exc: RequestValidationError):
    """Turn FastAPI's list of errors into one sentence the app can show."""
    msgs = [str(e.get("msg", "")).removeprefix("Value error, ") for e in exc.errors()]
    return JSONResponse(status_code=422, content={"detail": "; ".join(msgs) or "Invalid request"})

Level = Literal["beginner", "intermediate", "advanced"]
Frequency = Literal["once", "daily", "weekly", "every_n_days"]


# ---------------------------------------------------------------- models

PLACEHOLDER_TOPICS = {"", "string", "null", "none"}


def _clean_scenario(v: str | None) -> str | None:
    if v is None or v.strip().lower() in PLACEHOLDER_TOPICS:
        return None
    if not scenarios.get(v.strip()):
        valid = ", ".join(sc["id"] for sc in scenarios.SCENARIOS)
        raise ValueError(f"Unknown scenario '{v}'. Choose one of: {valid}")
    return v.strip()


def _clean_topic(v: str | None) -> str | None:
    """/docs pre-fills text fields with "string"; never send that to Fo as a topic."""
    if v is None or v.strip().lower() in PLACEHOLDER_TOPICS:
        return None
    return v.strip()


class SessionRequest(BaseModel):
    topic: str | None = Field(None, max_length=200)
    scenario: str | None = Field(None, description="Role-play scenario id from GET /scenarios")
    language: str = config.TARGET_LANGUAGE
    level: Level = "beginner"
    duration_minutes: int = Field(10, ge=2, le=60)

    _topic = field_validator("topic")(classmethod(lambda cls, v: _clean_topic(v)))
    _scenario = field_validator("scenario")(classmethod(lambda cls, v: _clean_scenario(v)))


class ScheduleRequest(BaseModel):
    title: str = Field("Speaking practice", max_length=80)
    topic: str | None = Field(None, max_length=200)
    scenario: str | None = Field(None, description="Role-play scenario id from GET /scenarios")
    add_to_calendar: bool = Field(False, description="Ask Fo to put this schedule on your Google Calendar")
    language: str = config.TARGET_LANGUAGE
    level: Level = "beginner"
    duration_minutes: int = Field(10, ge=2, le=60)
    frequency: Frequency = "daily"
    weekdays: list[int] = Field(default_factory=list, description="0=Mon ... 6=Sun; required for weekly")
    interval_days: int = Field(1, ge=1, le=60, description="For every_n_days")
    time_of_day: str = Field("19:00", description="24h HH:MM in your timezone")
    timezone: str = config.DEFAULT_TIMEZONE
    start_date: date | None = Field(None, description="Defaults to today; the date for 'once'")

    _topic = field_validator("topic")(classmethod(lambda cls, v: _clean_topic(v)))
    _scenario = field_validator("scenario")(classmethod(lambda cls, v: _clean_scenario(v)))

    @field_validator("time_of_day")
    @classmethod
    def _time(cls, v: str) -> str:
        m = re.fullmatch(r"([01]?\d|2[0-3]):([0-5]\d)", v)
        if not m:
            raise ValueError("time_of_day must be HH:MM, e.g. 19:00")
        return f"{int(m.group(1)):02d}:{m.group(2)}"

    @field_validator("timezone")
    @classmethod
    def _tz(cls, v: str) -> str:
        try:
            ZoneInfo(v)
        except (ZoneInfoNotFoundError, ValueError):
            raise ValueError(f"Unknown timezone: {v}") from None
        return v

    @model_validator(mode="after")
    def _weekdays(self):
        if self.frequency == "weekly":
            if not self.weekdays or any(d not in range(7) for d in self.weekdays):
                raise ValueError("weekly schedules need weekdays, e.g. [0, 2, 4] for Mon/Wed/Fri")
            self.weekdays = sorted(set(self.weekdays))
        return self


class ScheduleUpdate(BaseModel):
    active: bool


class SessionUpdate(BaseModel):
    saved: bool


# ---------------------------------------------------------------- routes

@app.get("/", include_in_schema=False)
def root():
    return RedirectResponse("/docs")


@app.get("/health")
def health():
    return {"ok": True, "mock_fo": config.fo_is_mocked(), "language": config.TARGET_LANGUAGE}


@app.get("/scenarios")
def list_scenarios():
    """Role-play scenarios for the picker. Send the `id` as `scenario`."""
    return scenarios.public_list()


@app.get("/progress")
def get_progress(include_mock: bool = False):
    """Streaks, minutes and mistakes over time. ?include_mock=true to see mock data while building the UI."""
    return progress.progress(include_mock)


# --- sessions ---

@app.post("/sessions")
async def start_now(req: SessionRequest):
    """Start a speaking session right now: Fo calls you."""
    if db.active_session():
        raise HTTPException(409, "A session is already in progress.")
    session_id = practice.start_session(None, req.topic, req.language, req.level, req.duration_minutes,
                                        req.scenario)
    return db.get_session(session_id)


@app.get("/sessions")
def list_sessions(saved: bool = False):
    """All sessions, newest first. ?saved=true for your saved library."""
    return db.list_sessions(saved_only=saved)


@app.get("/sessions/active")
def active_session():
    """{"session": <the session happening now, or null>}. Check this when the app opens."""
    return {"session": db.active_session()}


@app.get("/sessions/{session_id}")
def get_session(session_id: int):
    """Poll this every few seconds while a session is in progress."""
    s = db.get_session(session_id)
    if not s:
        raise HTTPException(404, "Session not found")
    return s


@app.get("/sessions/{session_id}/share")
def share_session(session_id: int):
    """Data for a shareable card: headline, stats, highlight, and ready-to-post text."""
    s = db.get_session(session_id)
    if not s:
        raise HTTPException(404, "Session not found")
    if s["status"] != "complete":
        raise HTTPException(400, "You can share a session once it's complete.")
    return progress.share_card(s)


@app.patch("/sessions/{session_id}")
def update_session(session_id: int, req: SessionUpdate):
    """Save or unsave a session later, from the app."""
    if not db.get_session(session_id):
        raise HTTPException(404, "Session not found")
    db.update_session(session_id, saved=req.saved)
    return db.get_session(session_id)


# --- schedules ---

def _first_run(req: ScheduleRequest, start: date) -> str | None:
    nxt = recurrence.next_run(req.frequency, req.time_of_day, req.timezone, start.isoformat(),
                              req.weekdays, req.interval_days)
    return nxt.isoformat() if nxt else None


@app.post("/schedules")
async def create_schedule(req: ScheduleRequest):
    start = req.start_date or datetime.now(ZoneInfo(req.timezone)).date()
    next_run_at = _first_run(req, start)
    if next_run_at is None:
        raise HTTPException(400, "That time has already passed. Pick a future date or time.")
    data = req.model_dump(exclude={"add_to_calendar"})
    data.update(start_date=start.isoformat(), next_run_at=next_run_at)
    schedule_id = db.create_schedule(data)
    if req.add_to_calendar:
        db.update_schedule(schedule_id, calendar_status="adding", calendar_note="Asking Fo to add it to your calendar")
        asyncio.create_task(calendar_sync.add_to_calendar(schedule_id))
    return db.get_schedule(schedule_id)


@app.get("/schedules")
def list_schedules():
    return db.list_schedules()


@app.get("/schedules/{schedule_id}")
def get_schedule(schedule_id: int):
    s = db.get_schedule(schedule_id)
    if not s:
        raise HTTPException(404, "Schedule not found")
    return s


@app.patch("/schedules/{schedule_id}")
def pause_or_resume(schedule_id: int, req: ScheduleUpdate):
    s = db.get_schedule(schedule_id)
    if not s:
        raise HTTPException(404, "Schedule not found")
    next_run_at = None
    if req.active:
        nxt = recurrence.next_run(s["frequency"], s["time_of_day"], s["timezone"], s["start_date"],
                                  s["weekdays"], s["interval_days"])
        if nxt is None:
            raise HTTPException(400, "This schedule has no future runs left.")
        next_run_at = nxt.isoformat()
    db.update_schedule(schedule_id, active=req.active, next_run_at=next_run_at)
    return db.get_schedule(schedule_id)


@app.post("/schedules/{schedule_id}/calendar")
async def add_schedule_to_calendar(schedule_id: int):
    """Add (or retry adding) an existing schedule to your Google Calendar via Fo."""
    s = db.get_schedule(schedule_id)
    if not s:
        raise HTTPException(404, "Schedule not found")
    if s.get("calendar_status") in ("adding", "added"):
        return s
    db.update_schedule(schedule_id, calendar_status="adding", calendar_note="Asking Fo to add it to your calendar")
    asyncio.create_task(calendar_sync.add_to_calendar(schedule_id))
    return db.get_schedule(schedule_id)


@app.delete("/schedules/{schedule_id}")
async def delete_schedule(schedule_id: int):
    """Deletes the schedule. If it was on your calendar, Fo is asked to remove the event too."""
    s = db.get_schedule(schedule_id)
    if not s:
        raise HTTPException(404, "Schedule not found")
    db.delete_schedule(schedule_id)
    asyncio.create_task(calendar_sync.remove_from_calendar(s))
    return {"deleted": schedule_id}
