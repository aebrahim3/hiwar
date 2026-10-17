"""Works out when a schedule should run next.

Times are stored in UTC but computed in your own timezone, so "7pm daily"
stays 7pm across daylight-saving changes.
"""

from datetime import date, datetime, time, timedelta, timezone
from zoneinfo import ZoneInfo


def next_run(
    frequency: str,
    time_of_day: str,
    tz: str,
    start_date: str,
    weekdays: list[int],
    interval_days: int,
    after: datetime | None = None,
) -> datetime | None:
    """Next run strictly after `after` (default: now), as a UTC datetime. None = no more runs."""
    zone = ZoneInfo(tz)
    after = (after or datetime.now(timezone.utc)).astimezone(zone)
    hh, mm = (int(x) for x in time_of_day.split(":"))
    start = date.fromisoformat(start_date)

    def at(d: date) -> datetime:
        return datetime.combine(d, time(hh, mm), zone)

    day = max(start, after.date())
    for _ in range(400):  # a bit over a year of days is plenty
        candidate = at(day)
        if candidate > after and _matches(frequency, day, start, weekdays, interval_days):
            return candidate.astimezone(timezone.utc)
        if frequency == "once" and day >= start:
            return None  # the single date has passed
        day += timedelta(days=1)
    return None


def _matches(frequency: str, day: date, start: date, weekdays: list[int], interval_days: int) -> bool:
    if frequency == "once":
        return day == start
    if frequency == "daily":
        return True
    if frequency == "weekly":
        return day.weekday() in weekdays
    if frequency == "every_n_days":
        return (day - start).days % interval_days == 0
    raise ValueError(f"Unknown frequency: {frequency}")
