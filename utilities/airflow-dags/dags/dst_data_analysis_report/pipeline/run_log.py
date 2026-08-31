"""The "Run Log" tab of the config Google Sheet — the run history in sheet mode.

One row per report attempt, written by the Airflow finalize task (and by
run.py's cumulative path in the identical format), and read back by the
Airflow scheduler's retime guard. There is no database anywhere: this tab is the
system of record in sheet mode (mdms mode publishes Kafka events instead).
All writes are non-fatal — a Sheets hiccup must never mask a run's outcome.
"""
import logging
import os
from datetime import datetime, timezone

log = logging.getLogger(__name__)

# Mirrors dst_report_metadata (platform/dst_report_metadata.sql) field for
# field, so switching DST_MDMS_ENABLED does not change what you can audit.
# Read back by NAME, never by position, so a tab written before a column was
# added still parses.
_HEADER = ["Timestamp UTC", "Tenant", "State", "Campaign", "Cycle", "Day",
           "Slot Date", "Slot Time", "Mode", "Status", "Step Failed", "Error",
           "Drive Folder", "DAG Run Id"]


def _open_runlog_worksheet():
    """Open (creating if absent) the Run Log tab. Returns None when the sheet
    is not configured or unreachable — callers degrade, never crash."""
    try:
        import gspread
        from google.oauth2.service_account import Credentials

        from dst_data_analysis_report.pipeline.config import _resolve_creds_path

        sheet_id = os.getenv("GOOGLE_SHEET_ID")
        if not sheet_id:
            log.warning("[run-log] GOOGLE_SHEET_ID not set — Run Log unavailable")
            return None
        creds = Credentials.from_service_account_file(
            _resolve_creds_path(),
            scopes=["https://www.googleapis.com/auth/spreadsheets",
                    "https://www.googleapis.com/auth/drive"])
        spreadsheet = gspread.Client(auth=creds).open_by_key(sheet_id)
        tab_name = os.getenv("GOOGLE_RUNLOG_TAB", "Run Log")
        try:
            ws = spreadsheet.worksheet(tab_name)
            # An existing tab predates any column added since it was created.
            # Appending a row WIDER than the grid silently writes nothing, which
            # is how a run logged "appended" while the tab stayed empty. Widen
            # and rewrite the header so tabs survive an upgrade.
            if ws.col_count < len(_HEADER):
                ws.resize(rows=max(ws.row_count, 1000), cols=len(_HEADER))
                log.info(f"[run-log] widened '{tab_name}' to {len(_HEADER)} columns")
            if ws.row_values(1)[:len(_HEADER)] != _HEADER:
                ws.update([_HEADER], "A1")
                log.info(f"[run-log] header of '{tab_name}' upgraded")
        except gspread.WorksheetNotFound:
            # width derived from _HEADER: a hardcoded value silently truncated
            # the append once columns were added, and the failure was swallowed
            ws = spreadsheet.add_worksheet(tab_name, rows=1000,
                                           cols=max(len(_HEADER), 12))
            ws.append_row(_HEADER)
        return ws
    except Exception as e:
        log.warning(f"[run-log] worksheet unavailable: {e}")
        return None


def append_run_log(state_name, campaign_name, day, status, step_failed="",
                   error="", drive_link="", mode="", tenant="", cycle_index="",
                   slot_date="", slot_time="", dag_run_id=""):
    """Append one outcome row. Returns True on success, False otherwise.

    Timestamp is UTC: slots are UTC, and a naive local timestamp made the
    retime guard compare times across a timezone offset. Slot Date/Slot Time
    are the SCHEDULED slot, not the moment of writing — a 17:00 slot that
    finishes at 17:04 must still read as the 17:00 slot, or the guard cannot
    tell whether that slot already produced a report.
    """
    ws = _open_runlog_worksheet()
    if ws is None:
        return False
    try:
        now = datetime.now(timezone.utc)
        ws.append_row(
            [now.strftime("%Y-%m-%d %H:%M"), tenant, state_name, campaign_name,
             str(cycle_index or ""), str(day), slot_date, slot_time, mode,
             status, step_failed, str(error)[:300] if error else "",
             drive_link or "", dag_run_id],
            value_input_option="USER_ENTERED")
        log.info(f"[run-log] appended: {state_name} Day {day} -> {status}")
        return True
    except Exception as e:
        log.warning(f"[run-log] append failed (non-fatal): {e}")
        return False


def fetch_today_runs():
    """Today's Run Log rows as dicts: {tenant, state, status, slot_time, mode}.

    Columns are resolved by HEADER NAME, so rows written before Tenant/Slot
    Time existed still parse — those fall back to the old positions and to
    mode "both", the safe direction for the retime guard (it covers every
    slot mode). Returns [] when the sheet is unavailable; callers treat that
    as "history unknown" and prefer a rare duplicate over never firing.
    """
    ws = _open_runlog_worksheet()
    if ws is None:
        return []
    try:
        values = ws.get_all_values()
        if not values:
            return []
        header = [h.strip() for h in values[0]]
        idx = {name: i for i, name in enumerate(header)}

        def cell(row, name, default=""):
            i = idx.get(name)
            return row[i].strip() if i is not None and i < len(row) else default

        today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
        runs = []
        for row in values[1:]:
            if not row:
                continue
            stamp = (cell(row, "Timestamp UTC") or cell(row, "Timestamp")
                     or (row[0] if row else ""))
            if not str(stamp).startswith(today):
                continue
            runs.append({
                # legacy rows (and run.py's) have no Tenant column; the guard
                # falls back to State so they are not silently unmatchable
                "tenant": cell(row, "Tenant").lower(),
                "state": cell(row, "State"),
                "status": cell(row, "Status").upper(),
                # the scheduled slot; legacy rows only recorded the write time
                "slot_time": cell(row, "Slot Time") or str(stamp)[11:16],
                "mode": cell(row, "Mode") or "both",
                # append_run_log has always WRITTEN these two, but nothing read
                # them back - so the retime guard matched on tenant alone and a
                # Bauchi SMC success suppressed the Bauchi ITN slot. Legacy rows
                # have neither, and an empty value must match anything so those
                # rows keep working.
                "campaign": cell(row, "Campaign"),
                "cycle": cell(row, "Cycle"),
            })
        return runs
    except Exception as e:
        log.warning(f"[run-log] fetch failed: {e}")
        return []
