# Hiwar backend (حوار, "dialogue")

Speaking practice with Fo. Schedule a session (daily, weekly, every N days, or once) or start one now.
Fo phones you and holds a conversation in your target language for as long as you chose. At the end, Fo
tells you out loud what you got wrong, what you did well, and what to work on, then asks whether to save
the session. The app gets the transcript and the breakdown, and your saved sessions build a library.
Mistakes and the "next focus" from your recent saved sessions are passed to Fo so it steers the next
conversation back to them. Fo also emails you a detailed written report after each session.

Extras: **role-play scenarios** (Fo plays a waiter, taxi driver, shopkeeper...), a **progress** view
(streaks, minutes, mistakes by category over time), a **shareable session card**, and **Google Calendar**
events for your schedules, created by Fo.

The brief tells Fo to wait `PAUSE_SECONDS` (default 3) of silence before speaking and never to talk
over you. Change it in `.env`; restart the server after editing `.env`.
Fo's call voice is fixed by Wajo's calling system and can't be changed from the app.

## Before your first real session
1. **Link your phone number in your Wajo account settings.** Fo calls that number.
2. Sign in to Fo from the backend: `python fo_login.py` (below).
3. Do one short test session (2-3 minutes) before relying on long ones. Fo hasn't confirmed 20-minute calls yet.

## Run it (Windows PowerShell)

```powershell
cd C:\Users\User\AndroidStudioProjects\Hiwar\backend
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
python fo_login.py          # one-time: sign in to Wajo in your browser
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Open http://localhost:8000/docs to try every endpoint in the browser.
`GET /health` shows `"mock_fo": true` until you've signed in.

**Mock mode:** before you sign in, a session takes about 12 seconds (`MOCK_SECONDS` in `.env`) and returns
a sample Arabic transcript and breakdown, so you can build the whole app first.

**Fo sign-in:** Fo uses OAuth, not an API key. `fo_login.py` registers this app with Wajo, opens your
browser to sign in, and saves tokens to `fo_tokens.json` (git-ignored). The backend refreshes them
automatically. If you see "Run: python fo_login.py", run it again.

**From the Android emulator**, the backend is at `http://10.0.2.2:8000`. On a real phone, use your
computer's LAN IP, or a tunnel such as ngrok for the demo.

**Keep the backend running** for schedules to fire: it checks for due sessions every 20 seconds.

## How the files fit together

| File | Job |
|---|---|
| `app/main.py` | API routes the Android app calls |
| `app/db.py` | SQLite: schedules and sessions |
| `app/recurrence.py` | Works out a schedule's next run in your timezone |
| `app/practice.py` | The session pipeline, Fo's session brief, and the scheduler |
| `app/fo_client.py` | Fo MCP client: `ask_fo`, then poll `get_fo_reply` until done |
| `app/fo_auth.py` + `fo_login.py` | OAuth sign-in and token refresh |
| `app/scenarios.py` | Role-play scenarios (add your own here) |
| `app/progress.py` | Progress stats and share-card data |
| `app/calendar_sync.py` | Asks Fo to add/remove schedule events on your Google Calendar |
| `app/mocks.py` | Fake session so everything runs before you sign in |

**How a session runs:** we send Fo a brief (language, level, topic, length, mistakes to revisit, how to
end the call). Fo might stay "working" for the whole call, or reply "calling you now" and run the call
afterwards. The backend handles both: if the first reply has no results, it waits until the call should
be over, then asks Fo for the transcript and breakdown.
Statuses: `calling → in_call → summarizing → complete` (or `failed`, with the reason in `progress`).

## API for the Android app

Errors always come back as `{"detail": "readable message"}`.

### Scenarios

**`GET /scenarios`**: list for the picker.
```json
[{"id": "restaurant", "title": "Ordering at a restaurant", "icon": "restaurant",
  "description": "Order a meal, ask about dishes, and pay the bill."}, ...]
```
Ids: `restaurant, taxi, souq, doctor, hotel, directions, neighbour, interview`. Send the id as `scenario`
when starting a session or creating a schedule. `topic` can still be sent with a scenario as extra detail.

### Sessions

**`POST /sessions`**: start now. All fields optional.
```json
{"scenario": "restaurant", "topic": null, "language": "Arabic", "level": "beginner", "duration_minutes": 10}
```
`level`: `beginner | intermediate | advanced`. `duration_minutes`: 2-60. Returns 409 if a session is already running.

**`GET /sessions/{id}`**: poll every few seconds while `status` isn't `complete`/`failed`.
```json
{
  "id": 1, "schedule_id": null, "status": "complete", "progress": "Done",
  "topic": "weekend plans", "language": "Arabic", "level": "beginner", "duration_minutes": 10,
  "started_at": "...", "ended_at": "...",
  "transcript": "Fo: ...\nMe: ...",
  "breakdown": {
    "summary": "...",
    "fluency": "pace, hesitations, switching to English",
    "mistakes": [{"said": "...", "correction": "...", "category": "grammar",
                  "explanation": "the rule", "example": "another correct sentence"}],
    "highlights": ["..."],
    "improvements": ["... with a short drill"],
    "vocabulary": [{"word": "...", "meaning": "...", "note": "..."}],
    "next_focus": "the one thing to practise next time"
  },
  "saved": true
}
```
`saved` is what you answered on the call (`null` if you didn't answer).

**`GET /sessions/active`**: `{"session": {...}}` or `{"session": null}`. Check when the app opens.

**`GET /sessions`**: history, newest first. **`GET /sessions?saved=true`**: saved library.

**`PATCH /sessions/{id}`** `{"saved": true}`: save or unsave later from the app.

**`GET /sessions/{id}/share`**: data for the share card (completed sessions only).
```json
{"session_id": 3, "headline": "10 min of Arabic with Fo", "label": "Ordering at a restaurant",
 "date": "2026-10-17",
 "stats": [{"label": "Minutes", "value": "10"}, {"label": "Corrections", "value": "3"},
           {"label": "Streak", "value": "2 days"}],
 "highlight": "...", "next_focus": "...",
 "share_text": "🗣️ 10 min of Arabic with Fo: Ordering at a restaurant\n✍️ 3 mistakes caught and corrected\n..."}
```

### Progress

**`GET /progress`** (`?include_mock=true` to see mock sessions while building the screen)
```json
{
  "total_sessions": 5, "total_minutes": 42,
  "current_streak_days": 2, "longest_streak_days": 3,
  "sessions_this_week": 3, "minutes_this_week": 25,
  "mistakes_by_category": {"grammar": 9, "vocabulary": 3, "pronunciation": 1, "word order": 0, "other": 2},
  "last_7_days": [{"date": "2026-10-11", "minutes": 0}, ...],
  "timeline": [{"session_id": 2, "date": "2026-10-17", "minutes": 8, "mistakes": 3, "highlights": 4,
                "by_category": {"grammar": 2, ...}, "label": "Free conversation"}],
  "trend": {"early_avg_mistakes": 4.5, "recent_avg_mistakes": 2.0},
  "next_focus": "..."
}
```
Only completed real sessions count (mock ones are excluded unless you ask). `trend` is `null` until
you have 4 sessions. Minutes are the real call length, capped at twice the planned length.

### Schedules

**`POST /schedules`**
```json
{
  "title": "Evening Arabic", "topic": null, "scenario": "taxi", "add_to_calendar": true,
  "language": "Arabic", "level": "beginner",
  "duration_minutes": 10,
  "frequency": "weekly", "weekdays": [0, 2, 4],
  "interval_days": 1,
  "time_of_day": "19:00", "timezone": "America/Vancouver",
  "start_date": "2026-10-20"
}
```
- `frequency`: `once | daily | weekly | every_n_days`
- `weekdays` (weekly only): 0=Mon ... 6=Sun
- `interval_days` (every_n_days only): e.g. 3 = every 3 days from `start_date`
- `start_date`: defaults to today; for `once` it's the date of the session

Returns the schedule with `next_run_at` (UTC). A `once` schedule switches itself off after it runs.

**Google Calendar:** with `"add_to_calendar": true`, Fo is asked to create the (repeating) event on your
Google Calendar. Watch `calendar_status` on the schedule: `adding → added` or `failed`, with Fo's reply
or the reason in `calendar_note`. Your Google account must be connected in Wajo for this to work.
**`POST /schedules/{id}/calendar`** adds an existing schedule, or retries after a failure.
Deleting a schedule asks Fo to remove its calendar event too.

**`GET /schedules`**, **`GET /schedules/{id}`**

**`PATCH /schedules/{id}`** `{"active": false}` to pause, `true` to resume.

**`DELETE /schedules/{id}`** (also removes the calendar event if one was added)

If a schedule comes due while another session is running, it's skipped and recorded as a failed session
with `progress` "Skipped: another session was in progress".

## Known gaps
- Tested against a fake Fo server built from Fo's real reply format, not Wajo's live server.
  The first real session is the real test; if it fails, the reason is in the session's `progress`
  and the server log.
- Single user, no accounts: fine for the demo.
