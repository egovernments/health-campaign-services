"""Unit tests for the pure/DB-free helpers in main.py.

Run from this directory (deps: fastapi, pydantic, psycopg2, httpx, pytest):
    cd utilities/airflow-trigger-service/api && python -m pytest test_main.py -q

Importing main.py does NOT open a DB connection (that's lazy in _get_db_conn), so these
tests need no database.
"""
import os
import sys
from datetime import datetime, timedelta, timezone

import pytest
from fastapi import HTTPException

# Make `import main` resolve regardless of the directory pytest is invoked from.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import main


IST = timezone(timedelta(minutes=330))  # +05:30, main's default REPORT_QUERY_TZ_OFFSET_MINUTES


# --------------- _triggered_day_bounds_ms ---------------

def test_triggered_day_bounds_spans_full_day(monkeypatch):
    monkeypatch.setattr(main, "REPORT_QUERY_TZ_OFFSET_MINUTES", 330)  # pin tz, don't depend on env
    start_ms, end_ms = main._triggered_day_bounds_ms("30-07-2026")
    assert end_ms - start_ms == 24 * 3600 * 1000 - 1  # 23:59:59.999 inclusive
    # midnight of the requested day in the configured tenant tz (+05:30)
    assert datetime.fromtimestamp(start_ms / 1000, IST).strftime("%d-%m-%Y %H:%M:%S") == "30-07-2026 00:00:00"


def test_triggered_day_bounds_bad_format_raises_valueerror():
    with pytest.raises(ValueError):
        main._triggered_day_bounds_ms("2026-07-30")  # yyyy-MM-dd, not dd-MM-yyyy


# --------------- _parse_custom_range / _compute_custom_report_range ---------------

def test_parse_custom_range_preserves_offset():
    start, end = main._parse_custom_range("13-07-2026 00:00:00+0530", "28-07-2026 00:00:00+0530")
    assert start.utcoffset() == timedelta(minutes=330)  # tz preserved, not converted to UTC
    assert end > start


def test_compute_custom_report_range_string():
    assert main._compute_custom_report_range(
        "13-07-2026 00:00:00+0530", "28-07-2026 00:00:00+0530"
    ) == "2026-07-13 00:00:00+0530_2026-07-28 00:00:00+0530"


def test_parse_custom_range_bad_format_raises_400():
    with pytest.raises(HTTPException) as ei:
        main._parse_custom_range("2026-07-13", "2026-07-28")
    assert ei.value.status_code == 400


# --------------- _get_retry_cooldown_seconds ---------------

def test_cooldown_default_for_unlisted_report():
    assert main._get_retry_cooldown_seconds("some_other_report") == main.CUSTOM_REPORT_RETRY_COOLDOWN_SECONDS


def test_cooldown_never_returns_none(monkeypatch):
    # pin the override so the test doesn't depend on the deployed default
    monkeypatch.setitem(main.CUSTOM_REPORT_RETRY_COOLDOWN_OVERRIDES, "beneficiary_visit_list", "NEVER")
    assert main._get_retry_cooldown_seconds("beneficiary_visit_list") is None


def test_cooldown_int_override(monkeypatch):
    monkeypatch.setitem(main.CUSTOM_REPORT_RETRY_COOLDOWN_OVERRIDES, "x_report", 600)
    assert main._get_retry_cooldown_seconds("x_report") == 600


# --------------- _enrich_status_row ---------------

def test_enrich_completed_is_terminal_full_bar():
    row = main._enrich_status_row(
        {"status": "REPORT_COMPLETED", "reporttriggeredtimems": 1000, "eventtimestampms": 6000,
         "reportgenerationtimeseconds": 1.0, "expectedrows": None,
         "expectedgenerationtimeseconds": None, "secondssincetriggered": 5.0},
        now_ms=10000,
    )
    assert row["isTerminal"] is True and row["isFailed"] is False
    assert row["progressPercent"] == 100
    assert row["elapsedSeconds"] is None            # terminal -> no live elapsed
    assert row["processingTimeSeconds"] == 5.0      # (6000-1000)/1000


def test_enrich_failed_is_failed():
    row = main._enrich_status_row(
        {"status": "ZIP_FAILED", "reporttriggeredtimems": 0, "eventtimestampms": 1000,
         "reportgenerationtimeseconds": None, "expectedrows": None,
         "expectedgenerationtimeseconds": None, "secondssincetriggered": None},
        now_ms=2000,
    )
    assert row["isTerminal"] is True and row["isFailed"] is True
    assert row["progressPercent"] == 75             # stops at the stage it failed at


def test_enrich_live_falls_back_to_stage_percent():
    row = main._enrich_status_row(
        {"status": "TRIGGERED_ON_UI", "reporttriggeredtimems": 1000, "eventtimestampms": 2000,
         "reportgenerationtimeseconds": None, "expectedrows": None,
         "expectedgenerationtimeseconds": None, "secondssincetriggered": 0.0},
        now_ms=5000,
    )
    assert row["isTerminal"] is False
    assert row["progressPercent"] == 12             # STAGE_PROGRESS_PERCENT fallback
    assert row["elapsedSeconds"] == 4.0             # (5000-1000)/1000


def test_enrich_live_uses_time_estimate_when_available():
    # 50s elapsed of a 100s estimate -> 50% (continuous mode, capped at 95)
    row = main._enrich_status_row(
        {"status": "REPORT_GENERATION_STARTED", "reporttriggeredtimems": 0, "eventtimestampms": 1000,
         "reportgenerationtimeseconds": None, "expectedrows": 100,
         "expectedgenerationtimeseconds": 100.0, "secondssincetriggered": 10.0},
        now_ms=50000,
    )
    assert row["progressPercent"] == 50


def test_enrich_time_estimate_capped_at_95():
    # elapsed already exceeds estimate -> capped at 95, never 100 until REPORT_COMPLETED
    row = main._enrich_status_row(
        {"status": "REPORT_GENERATION_STARTED", "reporttriggeredtimems": 0, "eventtimestampms": 1000,
         "reportgenerationtimeseconds": None, "expectedrows": 100,
         "expectedgenerationtimeseconds": 10.0, "secondssincetriggered": 10.0},
        now_ms=999999,
    )
    assert row["progressPercent"] == 95
