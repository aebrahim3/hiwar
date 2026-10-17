"""Put scheduled sessions on your Google Calendar by asking Fo to do it.

Fo has access to your calendar through your Wajo account, so we don't need any
Google API keys: we describe the event in plain English and Fo creates it.
The schedule's calendar_status shows progress: adding -> added | failed.
Deleting a schedule asks Fo to remove the event (best effort).
"""

import asyncio
import logging

from . import config, db, fo_client, scenarios

log = logging.getLogger("hiwar.calendar")

DAY_NAMES = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
OK, FAIL = "CALENDAR_OK", "CALENDAR_FAILED"


def describe_repeat(s: dict) -> str:
    if s["frequency"] == "once":
        return f"once, on {s['start_date']}"
    if s["frequency"] == "daily":
        return f"every day, starting {s['start_date']}"
    if s["frequency"] == "weekly":
        days = ", ".join(DAY_NAMES[d] for d in s["weekdays"])
        return f"every week on {days}, starting {s['start_date']}"
    return f"every {s['interval_days']} days, starting {s['start_date']}"


def event_title(s: dict) -> str:
    scenario = scenarios.get(s.get("scenario"))
    what = scenario["title"] if scenario else (s.get("topic") or s["title"])
    return f"{s['language']} practice with Fo: {what}"


def add_request(s: dict) -> str:
    return (
        "Please add an event to my Google Calendar. Do NOT call me now; only create the calendar event.\n"
        f"Title: {event_title(s)}\n"
        f"When: {describe_repeat(s)}, at {s['time_of_day']} ({s['timezone']} time)\n"
        f"Length: {s['duration_minutes']} minutes\n"
        f"Description: Fo will phone you for a {s['duration_minutes']}-minute {s['language']} speaking session. "
        f"Created by the Hiwar app (schedule #{s['id']}).\n"
        "Set a 10-minute reminder.\n"
        f"When the event is created, end your reply with {OK}. If you can't create it, explain why and end with {FAIL}."
    )


def remove_request(s: dict) -> str:
    return (
        "Please delete this event (including all repeats) from my Google Calendar. Do NOT call me.\n"
        f"Title: {event_title(s)}\n"
        f"It repeats {describe_repeat(s)}, at {s['time_of_day']} ({s['timezone']} time). "
        f"Its description mentions Hiwar schedule #{s['id']}.\n"
        f"End your reply with {OK} when it's deleted, or {FAIL} if you couldn't find it."
    )


def _outcome(text: str) -> bool:
    if FAIL in text:
        return False
    return OK in text


async def add_to_calendar(schedule_id: int) -> None:
    s = db.get_schedule(schedule_id)
    if not s:
        return
    db.update_schedule(schedule_id, calendar_status="adding", calendar_note="Asking Fo to add it to your calendar")
    try:
        if config.fo_is_mocked():
            await asyncio.sleep(2)
            db.update_schedule(schedule_id, calendar_status="added", calendar_note="(mock) Added to your calendar")
            return
        reply, _ = await fo_client.run_task(add_request(s), max_minutes=5)
        ok = reply.status == "complete" and _outcome(reply.text)
        note = reply.text.replace(OK, "").replace(FAIL, "").strip()[:300]
        db.update_schedule(schedule_id, calendar_status="added" if ok else "failed",
                           calendar_note=note or ("Added to your calendar" if ok else "Fo couldn't add the event"))
    except Exception as exc:
        log.exception("Calendar add failed for schedule %s", schedule_id)
        db.update_schedule(schedule_id, calendar_status="failed", calendar_note=f"Error: {exc}"[:300])


async def remove_from_calendar(schedule: dict) -> None:
    """Called after the schedule row is deleted, so it works from a copy."""
    if schedule.get("calendar_status") != "added" or config.fo_is_mocked():
        return
    try:
        reply, _ = await fo_client.run_task(remove_request(schedule), max_minutes=5)
        log.info("Calendar remove for schedule %s: %s", schedule["id"], reply.text[:200])
    except Exception:
        log.exception("Calendar remove failed for schedule %s", schedule["id"])
