# Hiwar (حوار) — speak a language with Fo

**Hiwar** turns [Fo](https://wajo.ai), Wajo's personal AI agent, into your speaking partner. Schedule a session
(or start one now) and Fo **phones you** for a real conversation in the language you're learning. At the end,
Fo tells you out loud what you got wrong, what you did well, and what to work on, then asks whether to save
the session. Your app gets the full transcript and a detailed breakdown, and every session builds on the last.

Built for Wajo's **GSD (Get Sh\*t Done) Hackathon**, October 2026.

> *Hiwar* (حوار) means "dialogue" in Arabic. You don't learn to speak a language by reading about it;
> you learn it in conversation.

## What it does

- **Fo calls you.** Daily, weekly, every N days, or once, at the time and length you choose.
- **Role-play scenes.** Fo plays a waiter, a taxi driver in Amman, a souq shopkeeper, a doctor, a hotel
  receptionist, a new neighbour, or a job interviewer, and stays in character.
- **Spoken feedback, then a written breakdown.** Every mistake with the correction, the rule and another
  example; highlights; improvements with drills; vocabulary; and one focus for next time.
- **It remembers.** Mistakes and the "next focus" from your saved sessions are fed into the next call, so
  Fo steers you back to exactly what you struggled with.
- **Progress.** Streaks, minutes spoken, mistakes by category, and whether your mistakes per session are dropping.
- **Share card.** One tap turns a session into an image and post for X.
- **Google Calendar.** Fo puts your practice schedule on your calendar (and removes it when you delete it).
- **Learner-friendly calls.** Fo waits for you to finish thinking, simplifies when you say "لا أفهم",
  and gives feedback in English.

## Why Fo

Most language apps make you talk to a screen. Fo can actually **pick up the phone**, hold a conversation,
**email** you a report, and manage your **calendar**: the parts of practice that happen in the real world.
Hiwar uses all of that through Fo's MCP server.

## How it works

```
Android app (Kotlin, Jetpack Compose, Retrofit, Hilt)
        │  REST
        ▼
FastAPI backend (Python)
  ├─ scheduler: starts sessions when they're due
  ├─ session brief: language, level, scenario, past mistakes, turn-taking rules, result format
  ├─ progress + share-card stats (SQLite)
  └─ Fo MCP client (OAuth 2.1 + PKCE) ──► Fo (mcp.api.wajo.ai/fo)
                                            ├─ phones you and runs the session
                                            ├─ emails your detailed report
                                            └─ creates calendar events
```

1. The backend sends Fo a **session brief** with `ask_fo`.
2. Fo calls your phone. The backend follows along with `get_fo_reply`, handling both ways Fo can respond
   (staying busy through the call, or replying "calling you now" and finishing later).
3. Fo returns the transcript and a JSON breakdown between marker lines; the backend parses, cleans and stores it.
4. The app shows the live status, then the breakdown, progress and share card.

## Repo layout

| Folder | What's inside |
|---|---|
| [`backend/`](backend/) | FastAPI server, Fo integration, scheduler, progress. See [backend/README.md](backend/README.md) for setup and the full API. |
| [`android/`](android/) | Android app: networking layer, view models, share card. See [android/SETUP.md](android/SETUP.md). |

## Run it

```powershell
cd backend
python -m venv .venv
.venv\Scripts\activate          # macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt
copy .env.example .env          # macOS/Linux: cp .env.example .env
python fo_login.py              # one-time Fo sign-in in your browser
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Then open http://localhost:8000/docs. Before you sign in to Fo, everything runs in **mock mode** with a
sample Arabic session, so the app can be built and demoed without a live call.

Link your phone number in your Wajo account first: that's the number Fo calls.

## Tech

Python 3.12+, FastAPI, the official MCP Python SDK, SQLite, `zoneinfo`/`tzdata` ·
Kotlin, Jetpack Compose, Retrofit + kotlinx.serialization, Hilt · Fo MCP (OAuth with PKCE and dynamic client registration).
