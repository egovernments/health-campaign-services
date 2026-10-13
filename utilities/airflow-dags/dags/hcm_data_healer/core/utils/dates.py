"""
Shared UTC day-boundary helpers and a no-op callback.

Used by both the reconciliation check and chain recovery so their date
windows always match.
"""

from datetime import datetime, timezone


def epoch_ms(date_str, end_of_day=False):
    """YYYY-MM-DD -> UTC epoch ms (start of day, or 23:59:59.999 if end_of_day)."""
    dt = datetime.strptime(date_str, "%Y-%m-%d").replace(tzinfo=timezone.utc)
    if end_of_day:
        dt = dt.replace(hour=23, minute=59, second=59, microsecond=999000)
    return int(dt.timestamp() * 1000)


def iso(date_str, end_of_day=False):
    """YYYY-MM-DD -> ISO-8601 UTC day boundary string."""
    return f"{date_str}T23:59:59.999Z" if end_of_day else f"{date_str}T00:00:00.000Z"


def noop(*_a, **_k):
    """No-op default for optional log/progress callbacks."""
    return None
