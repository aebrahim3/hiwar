"""Progress stats and share cards, computed from finished sessions.

Nothing new is stored: everything comes from the sessions table, so stats are
always consistent with your history. Days are counted in your timezone.
"""

from collections import Counter
from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo

from . import config, db, scenarios

CATEGORIES = ["grammar", "vocabulary", "pronunciation", "word order", "other"]


def _local_day(iso: str, tz: ZoneInfo) -> date:
    return datetime.fromisoformat(iso).astimezone(tz).date()


def _minutes(s: dict) -> int:
    """Real call length when we know it (capped at 2x the plan), else the planned length."""
    if s.get("ended_at"):
        mins = (datetime.fromisoformat(s["ended_at"]) - datetime.fromisoformat(s["started_at"])).total_seconds() / 60
        if s.get("mock"):
            return s["duration_minutes"]  # mock sessions only take seconds
        return max(1, min(round(mins), s["duration_minutes"] * 2))
    return s["duration_minutes"]


def _category(raw: str) -> str:
    raw = (raw or "").strip().lower()
    return raw if raw in CATEGORIES else "other"


def _streaks(days: set[date], today: date) -> tuple[int, int]:
    """(current, longest). Current counts back from today, or from yesterday if you haven't practised yet today."""
    longest = run = 0
    prev = None
    for d in sorted(days):
        run = run + 1 if prev and (d - prev).days == 1 else 1
        longest = max(longest, run)
        prev = d
    start = today if today in days else today - timedelta(days=1)
    current = 0
    while start in days:
        current += 1
        start -= timedelta(days=1)
    return current, longest


def progress(include_mock: bool = False) -> dict:
    tz = ZoneInfo(config.DEFAULT_TIMEZONE)
    today = datetime.now(tz).date()
    week_start = today - timedelta(days=today.weekday())  # Monday
    sessions = db.completed_sessions(include_mock)

    overall = Counter()
    timeline = []
    for s in sessions:
        mistakes = (s.get("breakdown") or {}).get("mistakes", [])
        cats = Counter(_category(m.get("category")) for m in mistakes)
        overall.update(cats)
        scenario = scenarios.get(s.get("scenario"))
        timeline.append({
            "session_id": s["id"],
            "date": _local_day(s["started_at"], tz).isoformat(),
            "minutes": _minutes(s),
            "mistakes": len(mistakes),
            "highlights": len((s.get("breakdown") or {}).get("highlights", [])),
            "by_category": {c: cats.get(c, 0) for c in CATEGORIES},
            "label": scenario["title"] if scenario else (s.get("topic") or "Free conversation"),
        })

    days = {date.fromisoformat(t["date"]) for t in timeline}
    current, longest = _streaks(days, today)
    this_week = [t for t in timeline if date.fromisoformat(t["date"]) >= week_start]

    # Last 7 days, one bar per day (0 on days you didn't practise) for a simple chart.
    last7 = []
    for i in range(6, -1, -1):
        d = today - timedelta(days=i)
        last7.append({"date": d.isoformat(), "minutes": sum(t["minutes"] for t in timeline if t["date"] == d.isoformat())})

    # Mistakes per session, first half vs second half of your history: are you improving?
    trend = None
    if len(timeline) >= 4:
        half = len(timeline) // 2
        early = sum(t["mistakes"] for t in timeline[:half]) / half
        recent = sum(t["mistakes"] for t in timeline[half:]) / (len(timeline) - half)
        trend = {"early_avg_mistakes": round(early, 1), "recent_avg_mistakes": round(recent, 1)}

    return {
        "total_sessions": len(timeline),
        "total_minutes": sum(t["minutes"] for t in timeline),
        "current_streak_days": current,
        "longest_streak_days": longest,
        "sessions_this_week": len(this_week),
        "minutes_this_week": sum(t["minutes"] for t in this_week),
        "mistakes_by_category": {c: overall.get(c, 0) for c in CATEGORIES},
        "last_7_days": last7,
        "timeline": timeline,
        "trend": trend,
        "next_focus": db.last_focus(config.TARGET_LANGUAGE),
    }


def share_card(session: dict) -> dict:
    """Everything the app needs to draw a shareable card for one session."""
    b = session.get("breakdown") or {}
    scenario = scenarios.get(session.get("scenario"))
    stats = progress(include_mock=bool(session.get("mock")))
    minutes = _minutes(session)
    mistakes = b.get("mistakes", [])
    language = session["language"]

    label = scenario["title"] if scenario else (session.get("topic") or "Free conversation")
    headline = f"{minutes} min of {language} with Fo"
    highlight = (b.get("highlights") or [""])[0]
    streak = stats["current_streak_days"]

    lines = [f"🗣️ {headline}: {label}"]
    if mistakes:
        lines.append(f"✍️ {len(mistakes)} mistake{'s' if len(mistakes) != 1 else ''} caught and corrected")
    if streak >= 2:
        lines.append(f"🔥 {streak}-day streak")
    if b.get("next_focus"):
        lines.append(f"🎯 Next focus: {b['next_focus']}")
    lines.append("Practising with Hiwar + @hey__fo")

    return {
        "session_id": session["id"],
        "headline": headline,
        "label": label,
        "date": _local_day(session["started_at"], ZoneInfo(config.DEFAULT_TIMEZONE)).isoformat(),
        "stats": [
            {"label": "Minutes", "value": str(minutes)},
            {"label": "Corrections", "value": str(len(mistakes))},
            {"label": "Streak", "value": f"{streak} day{'s' if streak != 1 else ''}"},
        ],
        "highlight": highlight,
        "next_focus": b.get("next_focus") or "",
        "share_text": "\n".join(lines),
    }
