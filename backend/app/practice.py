"""Speaking sessions with Fo, start to finish.

1. We send Fo a "session brief": language, level, topic, length, mistakes to revisit,
   and how to end the call (spoken breakdown, then ask whether to save).
2. Fo phones you and runs the conversation.
3. Afterwards Fo sends back the transcript and a JSON breakdown between marker
   lines, which we parse and store.

Fo might stay "working" for the whole call, or answer "calling you now" right away
and run the call afterwards. We handle both: if the first answer has no results,
we wait until the call should be over, then ask Fo for the results.

Statuses the app shows: calling -> in_call -> summarizing -> complete (or failed)
"""

import asyncio
import json
import logging
import re
from datetime import datetime, timedelta, timezone

from . import config, db, fo_client, mocks, recurrence, scenarios

log = logging.getLogger("hiwar.practice")

LEVEL_GUIDE = {
    "beginner": "Use very short, simple sentences and common everyday words. Ask one short question at a time.",
    "intermediate": "Use natural everyday Arabic at a moderate pace, with some new vocabulary in context.",
    "advanced": "Speak naturally, at normal speed, with richer vocabulary and longer questions.",
}

JSON_START, JSON_END = "SESSION_JSON_START", "SESSION_JSON_END"
TX_START, TX_END = "TRANSCRIPT_START", "TRANSCRIPT_END"
_running: set[int] = set()
_tasks: dict[int, asyncio.Task] = {}   # session id -> the task running it, so it can be cancelled

RESULT_FORMAT = f"""send the results in exactly this format:

{TX_START}
(the full transcript, one turn per line, each starting "Fo:" or "Me:")
{TX_END}
{JSON_START}
{{"saved": true or false (what I answered on the call; null if I didn't answer),
 "summary": "3-5 sentences in English: how the session went overall and the main pattern in my errors",
 "fluency": "2-3 sentences on my pace, hesitations, sentence length, and how often I switched to English",
 "mistakes": [{{"said": "exactly what I said", "correction": "the correct version",
               "category": "grammar | vocabulary | pronunciation | word order | other",
               "explanation": "why it's wrong and the rule behind it, 1-2 sentences in English",
               "example": "one more correct example sentence using the same rule"}}],
 "highlights": ["specific things I did well, quoting what I said"],
 "improvements": ["specific, actionable things to work on, each with a short drill or tip"],
 "vocabulary": [{{"word": "useful word or phrase from the session, or one I was missing",
                 "meaning": "English meaning", "note": "when to use it"}}],
 "next_focus": "the single most important thing to practise in my next session"}}
{JSON_END}
List every mistake you noticed, not just a few. Use [] for any list with nothing in it."""


EMAIL_ON = """After the call, also email me a detailed written report with these sections:
Summary; Fluency; Every mistake (what I said, the correction, the rule, and another example);
Highlights; Improvements with a short drill for each; Vocabulary from the session;
Focus for next session; and the full transcript."""
EMAIL_OFF = "Don't email me a report for this session; my practice app shows it from the results below."


def build_brief(language: str, level: str, minutes: int, topic: str | None, revisit: list[dict],
                focus: str | None = None, scenario: dict | None = None,
                email_report: bool = False) -> str:
    """The instructions Fo gets before each session. Turn-taking wording is Fo's own suggestion."""
    if scenario:
        topic_line = "\n\n" + scenarios.brief_block(scenario) + (f"\nExtra detail for the scene: {topic}." if topic else "") + "\n"
    else:
        topic_line = f"Topic: {topic}." if topic else "Topic: your choice, something natural and everyday."
    revisit_block = ""
    if revisit:
        items = "\n".join(f"- I said \"{m.get('said')}\"; correct is \"{m.get('correction')}\"" for m in revisit)
        revisit_block = (
            "\nThese are mistakes from my recent sessions. Steer the conversation so I get a chance "
            f"to use these correctly, without telling me in advance:\n{items}\n"
        )
    if focus:
        revisit_block += f"\nMy focus from last session: {focus.rstrip('.')}. Give me chances to practise it.\n"
    return f"""Please call me now for a {minutes}-minute {language} speaking practice session.

I'm a {level} learner.{topic_line if scenario else " " + topic_line}
Run the conversation in {language} only (the feedback at the end is in English, see the hard rules below). {LEVEL_GUIDE.get(level, LEVEL_GUIDE["beginner"])}
If I say I don't understand (e.g. "لا أفهم"), repeat the question more simply with easier words.
If I still don't understand, say the key word in English once, then carry on in {language}.
Keep me talking: ask open questions and follow-ups, and don't correct me mid-conversation
unless I'm completely stuck. {"Stay in the scene unless I clearly ask to stop." if scenario else "If I ask to change the topic, follow my lead."}

Turn-taking (important): silence is normal, not a prompt to speak. When I pause to think,
wait about {config.PAUSE_SECONDS} seconds of silence before responding, and never start or finish my sentences for me.
If you think I'm done, wait one more beat before you talk. Never talk over me.
{revisit_block}
HARD RULES FOR THE CALL. Put these in the call's brief exactly as written; they override any
default call length or habit you have saved:
- Start the call in {language} (a short {language} greeting, not English).
- This call is exactly {minutes} minutes of {language}. The voice doesn't see a clock, so as a guide,
  after about {minutes * 3} of your questions (roughly 3 a minute) stop the conversation, even mid-topic.
- At that point switch COMPLETELY to ENGLISH. Say, in English: "OK, let's stop there. Here's your feedback."
  Everything from here to the end of the call is in English only, even if I answer in {language}.
  {language} is allowed only when quoting what I said and the correct version.
- Feedback, in English: (1) each mistake I made, the correct way to say it, and why;
  (2) my highlights, what I did well; (3) what I could do to improve.
- Then ask, in English: "Do you want to save this session for later reference?"
- As soon as I answer (yes or no), say "Saved." or "OK, not saved.", then "Goodbye!", and HANG UP
  immediately. No more questions or wrap-up talk.
- If at any point I say I want to stop or end the call, go straight to the English feedback, then the
  save question, then hang up.

{EMAIL_ON if email_report else EMAIL_OFF}

After the call ends, {RESULT_FORMAT}"""


def parse_results(text: str) -> tuple[str | None, dict | None]:
    tx = re.search(rf"{TX_START}\s*(.*?)\s*{TX_END}", text, re.S)
    js = re.search(rf"{JSON_START}\s*(.*?)\s*{JSON_END}", text, re.S)
    breakdown = None
    if js:
        raw = js.group(1).strip().removeprefix("```json").removeprefix("```").removesuffix("```")
        try:
            breakdown = json.loads(raw)
        except json.JSONDecodeError:
            log.warning("Fo's breakdown wasn't valid JSON: %s", raw[:200])
    return (tx.group(1).strip() if tx else None), breakdown


def _clean_breakdown(b: dict) -> dict:
    """Keep only the fields the app knows, with safe types, whatever Fo sent."""
    def text(v) -> str:
        return "" if v is None else str(v)

    def dicts(key: str) -> list[dict]:
        return [d for d in b.get(key) or [] if isinstance(d, dict)]

    return {
        "summary": text(b.get("summary")),
        "fluency": text(b.get("fluency")),
        "mistakes": [
            {"said": text(m.get("said")), "correction": text(m.get("correction")),
             "category": text(m.get("category")) or "other",
             "explanation": text(m.get("explanation")), "example": text(m.get("example"))}
            for m in dicts("mistakes")
        ],
        "highlights": [text(h) for h in b.get("highlights") or []],
        "improvements": [text(i) for i in b.get("improvements") or []],
        "vocabulary": [
            {"word": text(v.get("word")), "meaning": text(v.get("meaning")), "note": text(v.get("note"))}
            for v in dicts("vocabulary")
        ],
        "next_focus": text(b.get("next_focus")),
    }


def _saved_value(b: dict) -> bool | None:
    v = b.get("saved")
    if isinstance(v, bool) or v is None:
        return v
    return str(v).strip().lower() in ("true", "yes", "1")


async def run_session(session_id: int) -> None:
    if session_id in _running:
        return
    _running.add(session_id)
    try:
        s = db.get_session(session_id)

        def progress(text: str, status: str | None = None) -> None:
            fields = {"progress": text[:300]}
            if status:
                fields["status"] = status
            db.update_session(session_id, **fields)

        if config.fo_is_mocked():
            transcript, breakdown, saved = await mocks.mock_session(s, progress)
            db.update_session(session_id, mock=True)
            _finish(session_id, transcript, breakdown, saved)
            return

        brief = build_brief(s["language"], s["level"], s["duration_minutes"], s["topic"],
                            db.recent_mistakes(s["language"]), db.last_focus(s["language"]),
                            scenarios.get(s.get("scenario")), bool(s.get("email_report")))
        started = datetime.now(timezone.utc)
        call_deadline_min = s["duration_minutes"] + 10

        def on_progress(text: str) -> None:
            progress(text, "in_call")

        reply, conv = await fo_client.run_task(brief, on_progress, max_minutes=call_deadline_min)
        db.update_session(session_id, conversation_id=conv)

        if reply.status == "waiting_for_user":
            progress(f"Fo needs your answer: {reply.text}", "failed")
            return
        if reply.status == "failed":
            progress(reply.text or "Fo couldn't start the call", "failed")
            return

        transcript, breakdown = parse_results(reply.text)

        # Fo answered before the call finished: wait for the call to end, then ask for results.
        give_up_at = started + timedelta(minutes=s["duration_minutes"] + 30)
        call_should_end = started + timedelta(minutes=s["duration_minutes"] + 3)
        while breakdown is None:
            now = datetime.now(timezone.utc)
            if now >= give_up_at:
                progress("Fo didn't send the session results in time", "failed")
                return
            if now < call_should_end:
                progress(f"On the call with Fo ({s['duration_minutes']} min session)", "in_call")
                await asyncio.sleep(min(60, (call_should_end - now).total_seconds()))
                continue
            progress("Getting Fo's breakdown", "summarizing")
            again = await fo_client.follow_up(
                "Has my practice call ended? If it has, " + RESULT_FORMAT
                + "\nIf it hasn't ended yet, just reply NOT_DONE.",
                conv,
            )
            transcript, breakdown = parse_results(again.text)
            if breakdown is None:
                await asyncio.sleep(90)

        _finish(session_id, transcript, breakdown, _saved_value(breakdown))
    except Exception as exc:  # keep the scheduler alive no matter what
        log.exception("Session %s failed", session_id)
        db.update_session(session_id, status="failed", progress=_explain(exc)[:300])
    finally:
        _running.discard(session_id)


def _finish(session_id: int, transcript: str | None, breakdown: dict, saved: bool | None) -> None:
    db.update_session(
        session_id,
        status="complete",
        progress="Done",
        ended_at=db.now_iso(),
        transcript=transcript,
        breakdown=_clean_breakdown(breakdown),
        saved=saved,
    )


def _explain(exc: BaseException) -> str:
    """Turn errors into a message the app can show."""
    while isinstance(exc, BaseExceptionGroup) and exc.exceptions:
        exc = exc.exceptions[0]
    if isinstance(exc, (fo_client.FoError, RuntimeError)):
        return str(exc)
    return f"Error: {exc or type(exc).__name__}"


# ---------------------------------------------------------------- scheduler

def start_session(schedule_id: int | None, topic: str | None, language: str, level: str, minutes: int,
                  scenario: str | None = None, email_report: bool = False) -> int:
    session_id = db.create_session(schedule_id, topic, language, level, minutes, scenario, email_report)
    task = asyncio.create_task(run_session(session_id))
    _tasks[session_id] = task
    task.add_done_callback(lambda _t: _tasks.pop(session_id, None))
    return session_id


def cancel_session(session_id: int) -> None:
    """Stop following a session and free the app to start another.
    Note: if Fo has already placed the call, hang up on your phone; this only stops Hiwar waiting."""
    task = _tasks.pop(session_id, None)
    if task and not task.done():
        task.cancel()
    _running.discard(session_id)
    db.update_session(session_id, status="failed", progress="Cancelled", ended_at=db.now_iso())


async def scheduler_loop() -> None:
    """Every 20 seconds: start any schedule whose time has come, then book its next run."""
    while True:
        try:
            for sch in db.due_schedules():
                nxt = recurrence.next_run(
                    sch["frequency"], sch["time_of_day"], sch["timezone"], sch["start_date"],
                    sch["weekdays"], sch["interval_days"],
                )
                # Book the next run first, so a slow start can't fire the same slot twice.
                db.update_schedule(sch["id"], next_run_at=nxt.isoformat() if nxt else None,
                                   active=nxt is not None)
                if db.active_session():
                    sid = db.create_session(sch["id"], sch["topic"], sch["language"], sch["level"],
                                            sch["duration_minutes"], sch.get("scenario"))
                    db.update_session(sid, status="failed", progress="Skipped: another session was in progress")
                    continue
                start_session(sch["id"], sch["topic"], sch["language"], sch["level"], sch["duration_minutes"],
                              sch.get("scenario"), bool(sch.get("email_report")))
        except Exception:
            log.exception("Scheduler tick failed")
        await asyncio.sleep(20)
