"""
hcm_campaign_scheduler.py

Scheduler DAG (runs hourly at minute 0 UTC)
Tasks:
  - find_window_matches      : fetch MDMS and find campaigns in the window (now-1h+1m, now]
  - decide_campaign_trigger  : validate, dedupe and prepare payload
  - trigger_processor        : trigger hcm_dynamic_campaigns DAG with matched campaigns in conf

Notes:
- MDMS triggerTime is expected in UTC (e.g. "09:30:00+0000" or "09:30:00" or "09:30").
- Window logic: window_start = now_utc - 1 hour + 1 minute, window_end = now_utc
- Uses ONLY Python built-in modules (datetime, no pendulum required)
- Required env vars:
    MDMS_URL (base URL), MDMS_SEARCH_ENDPOINT (opt, default: /mdms-v2/v2/_search),
    MDMS_MODULE_NAME (opt), MDMS_MASTER_NAME (opt), TENANT_ID (opt), MDMS_LIMIT (opt),
    PROCESSOR_DAG_ID (opt) - default "hcm_dynamic_campaigns".
"""
from __future__ import annotations
import os
import re
import json
import logging
from datetime import datetime, timedelta, timezone

import requests

from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.models import DagBag
from airflow.utils.state import State
from airflow.utils.types import DagRunType
from airflow.operators.trigger_dagrun import TriggerDagRunOperator

logger = logging.getLogger("airflow.task")
logger.setLevel(logging.INFO)

# -----------------------------
# Configuration via environment
# -----------------------------
MDMS_URL = os.getenv("MDMS_URL")
MDMS_SEARCH_ENDPOINT = os.getenv("MDMS_SEARCH_ENDPOINT", "/mdms-v2/v2/_search")
MDMS_MODULE_NAME = os.getenv("MDMS_MODULE_NAME", "airflow-configs")
MDMS_MASTER_NAME = os.getenv("MDMS_MASTER_NAME", "campaign-report-config")
TENANT_ID = os.getenv("TENANT_ID", "dev")
MDMS_LIMIT = int(os.getenv("MDMS_LIMIT", "500"))
PROCESSOR_DAG_ID = os.getenv("PROCESSOR_DAG_ID", "hcm_dynamic_campaigns")

# Window grace minutes: window = (now - 1 hour + WINDOW_GRACE_MINUTES, now]
WINDOW_GRACE_MINUTES = int(os.getenv("WINDOW_GRACE_MINUTES", "1"))

# Timezone: UTC
UTC = timezone.utc

# UUID regex pattern for detecting projectTypeId
UUID_PATTERN = re.compile(
    r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
)

# -----------------------------
# Helpers
# -----------------------------

def get_campaign_identifier(campaign):
    """
    Get campaign identifier and detect its type from MDMS config.

    MDMS provides 'campaignIdentifier' which can be either:
    - campaignNumber (e.g., "CMP-2025-01-15-001")
    - projectTypeId (UUID, e.g., "a1b2c3d4-e5f6-7890-abcd-ef1234567890")

    Detection Logic:
    - If matches UUID pattern (8-4-4-4-12 hex format) → identifierType = "projectTypeId"
    - Otherwise → identifierType = "campaignNumber"

    Args:
        campaign (dict): Campaign object from MDMS with 'campaignIdentifier' field

    Returns:
        tuple: (identifier_value, identifier_type)
            - identifier_value: The actual identifier string
            - identifier_type: "campaignNumber" or "projectTypeId" or "unknown"

    Examples:
        {"campaignIdentifier": "CMP-2025-01-15-001"}
            → ("CMP-2025-01-15-001", "campaignNumber")

        {"campaignIdentifier": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"}
            → ("a1b2c3d4-e5f6-7890-abcd-ef1234567890", "projectTypeId")

        {"campaignIdentifier": ""}
            → ("UNKNOWN", "unknown")
    """
    identifier = campaign.get("campaignIdentifier", "").strip()

    if not identifier:
        logger.warning("Campaign missing campaignIdentifier")
        return ("UNKNOWN", "unknown")

    # Check if it's a UUID (projectTypeId)
    if UUID_PATTERN.match(identifier):
        logger.debug("Identifier '%s' detected as projectTypeId (UUID)", identifier)
        return (identifier, "projectTypeId")

    # Otherwise, treat as campaignNumber
    logger.debug("Identifier '%s' detected as campaignNumber", identifier)
    return (identifier, "campaignNumber")


def call_api(url, method="POST", json_body=None, headers=None, timeout=30):
    """Generic requests wrapper with logging and error return."""
    try:
        r = requests.request(method=method, url=url, json=json_body, headers=headers, timeout=timeout)
        r.raise_for_status()
        try:
            return r.json()
        except ValueError:
            return {"response_text": r.text}
    except Exception as exc:
        logger.exception("API call failed: %s", exc)
        return {"error": str(exc)}

def fetch_campaigns_from_mdms():
    """Fetch campaigns from MDMS. Returns list of campaign dicts."""
    if not MDMS_URL:
        raise ValueError("MDMS_URL environment variable is required")

    # Construct full MDMS API URL
    full_mdms_url = f"{MDMS_URL}{MDMS_SEARCH_ENDPOINT}"
    logger.info("Fetching campaigns from MDMS: %s", full_mdms_url)

    body = {
        "RequestInfo": {"authToken": ""},
        "MdmsCriteria": {
            "tenantId": TENANT_ID,
            "schemaCode": f"{MDMS_MODULE_NAME}.{MDMS_MASTER_NAME}",
            "limit": MDMS_LIMIT,
            "offset": 0,
            "isActive": True,
        }
    }
    res = call_api(full_mdms_url, json_body=body, headers={"Content-Type": "application/json"})
    if "error" in res:
        raise Exception(f"Error fetching MDMS: {res['error']}")
    # Try common keys; raise if unexpected
    campaigns = res.get("mdms") or res.get("data") or res.get("campaigns") or []
    if not isinstance(campaigns, list):
        raise Exception("Unexpected MDMS response format: expected a list of campaign objects")
    logger.info("MDMS returned %d entries", len(campaigns))

    # Normalize MDMS response: extract 'data' field and merge with root-level isActive
    normalized = []
    for entry in campaigns:
        if isinstance(entry, dict) and "data" in entry:
            # Extract campaign data and add isActive from root level
            campaign = entry["data"].copy()
            campaign["isActive"] = entry.get("isActive", False)
            normalized.append(campaign)
        else:
            # If no nested 'data', use entry as-is
            normalized.append(entry)

    return normalized

def parse_trigger_time_today(trigger_time_str, ref_dt):
    """
    Convert triggerTime string (e.g. '09:30:00+0000', '19:26:00+0530', '14:00:00+0200')
    into a datetime for the reference date (ref_dt) in UTC.

    Supports timezone offsets in formats: +HHMM, -HHMM, +HH:MM, -HH:MM
    Examples:
        - '09:30:00+0000' -> 09:30 UTC
        - '19:26:00+0530' -> 13:56 UTC (IST to UTC)
        - '14:00:00+0200' -> 12:00 UTC (SAST to UTC)
        - '09:30:00-0500' -> 14:30 UTC (EST to UTC)
        - '09:30:00' or '09:30' -> 09:30 UTC (assumes UTC if no offset)

    Args:
        trigger_time_str (str): Time string from MDMS with optional timezone offset
        ref_dt (datetime): Reference datetime (current execution time)

    Returns:
        datetime: Datetime object for today at the trigger time converted to UTC
    """
    # Parse timezone offset
    tz_offset_minutes = 0
    time_part = trigger_time_str.strip()

    # Check for timezone offset (+HHMM or -HHMM)
    if '+' in time_part or time_part.count('-') > 2:  # More than 2 hyphens means timezone (not just time separator)
        # Split by + or - (but preserve the sign)
        if '+' in time_part:
            t, tz = time_part.split('+')
            tz_sign = 1
        else:
            # Find the last occurrence of - (timezone separator, not time separator)
            parts = time_part.rsplit('-', 1)
            t = parts[0]
            tz = parts[1] if len(parts) > 1 else '0000'
            tz_sign = -1

        # Parse timezone offset (HHMM or HH:MM format)
        tz = tz.strip().replace(':', '')
        if tz:
            tz_hours = int(tz[:2]) if len(tz) >= 2 else 0
            tz_mins = int(tz[2:4]) if len(tz) >= 4 else 0
            tz_offset_minutes = tz_sign * (tz_hours * 60 + tz_mins)
    else:
        t = time_part

    # Parse time components (HH:MM:SS or HH:MM or HH)
    parts = t.split(":")
    hh = int(parts[0])
    mm = int(parts[1]) if len(parts) > 1 else 0
    ss = int(parts[2]) if len(parts) > 2 else 0

    # Create datetime with today's date and trigger time in the source timezone
    dt_local = datetime(
        ref_dt.year,
        ref_dt.month,
        ref_dt.day,
        hh,
        mm,
        ss,
        tzinfo=UTC  # Temporarily set as UTC
    )

    # Convert to UTC by subtracting the timezone offset
    # If time is 19:26 +0530 (IST), we subtract 5h30m to get UTC time
    dt_utc = dt_local - timedelta(minutes=tz_offset_minutes)

    return dt_utc

def parse_date_string(date_str):
    """
    Parse date string from MDMS (format: "DD-MM-YYYY HH:mm:ss+0000")
    Returns datetime object in UTC.

    Args:
        date_str (str): Date string from MDMS

    Returns:
        datetime: Parsed datetime in UTC
    """
    # Remove timezone suffix
    date_part = date_str.split('+')[0].strip()

    # Parse datetime (format: "23-09-2025 00:00:00")
    dt = datetime.strptime(date_part, "%d-%m-%Y %H:%M:%S")

    # Add UTC timezone
    return dt.replace(tzinfo=UTC)


def is_campaign_active(campaign, ref_dt):
    """
    Check if today falls within the campaign's active date range.

    Reports should only be generated when:
        campaignStartDate <= today <= campaignEndDate

    This ensures:
    - No reports are generated before the campaign starts
    - No reports are generated after the campaign ends
    - The last daily report is generated ON the campaignEndDate

    Args:
        campaign (dict): Campaign object with campaignStartDate and campaignEndDate
        ref_dt (datetime): Reference datetime (current execution time)

    Returns:
        tuple: (is_active: bool, reason: str)
            - is_active: True if campaign is within active date range
            - reason: Description of why campaign is active/inactive
    """
    today = ref_dt.date()

    # Get campaign start date
    start_date_str = campaign.get("campaignStartDate") or campaign.get("startDate")
    if not start_date_str:
        return (True, "No start date defined - assuming active")

    try:
        start_date = parse_date_string(start_date_str).date()
    except Exception:
        logger.exception("Failed to parse start date for campaign %s", campaign.get("campaignNumber"))
        return (True, "Failed to parse start date - assuming active")

    # Check if campaign hasn't started yet
    if today < start_date:
        return (False, f"Campaign hasn't started yet (starts: {start_date})")

    # Get campaign end date
    end_date_str = campaign.get("campaignEndDate") or campaign.get("endDate")
    if not end_date_str:
        return (True, "No end date defined - campaign is active")

    try:
        end_date = parse_date_string(end_date_str).date()
    except Exception:
        logger.exception("Failed to parse end date for campaign %s", campaign.get("campaignNumber"))
        return (True, "Failed to parse end date - assuming active")

    # Check if campaign has ended (today > campaignEndDate)
    if today > end_date:
        return (False, f"Campaign has ended (ended: {end_date})")

    # Campaign is active: startDate <= today <= endDate
    return (True, f"Campaign is active ({start_date} to {end_date})")

def frequency_due(campaign, ref_dt):
    """
    Frequency rules:
      - DAILY: days_since_start >= 1
      - WEEKLY: days_since_start >= 7 and days_since_start % 7 == 0
      - MONTHLY: days_since_start >= 30 and days_since_start % 30 == 0

    Args:
        campaign (dict): Campaign object with triggerFrequency and campaignStartDate/startDate
        ref_dt (datetime): Reference datetime (current execution time)

    Returns:
        bool: True if the campaign is due to run based on frequency
    """
    freq = (campaign.get("triggerFrequency") or "DAILY").strip().lower()
    # Support both campaignStartDate (MDMS) and startDate (legacy)
    start = campaign.get("campaignStartDate") or campaign.get("startDate")
    if not start:
        return False
    try:
        sdt = parse_date_string(start).date()
    except Exception:
        logger.exception("Failed to parse start date for frequency check: %s", campaign.get("campaignNumber"))
        return False

    days = (ref_dt.date() - sdt).days

    if freq == "daily":
        return days >= 1
    if freq == "weekly":
        return days >= 7 and (days % 7 == 0)
    if freq == "monthly":
        return days >= 30 and (days % 30 == 0)
    return False


def is_final_report_due(campaign, ref_dt):
    """
    Check if campaign is ending today and needs a final partial report.

    This handles the scenario where a campaign ends mid-cycle (e.g., on day 5
    of a weekly report cycle). In such cases, we need to generate a final
    report covering the remaining days.

    Args:
        campaign (dict): Campaign object with campaignEndDate and triggerFrequency
        ref_dt (datetime): Reference datetime (current execution time)

    Returns:
        tuple: (is_due: bool, remaining_days: int)
            - is_due: True if a final partial report should be generated
            - remaining_days: Number of days to include in the final report

    Example:
        Campaign starts: Day 1
        Weekly reports on: Day 7, Day 14
        Campaign ends: Day 19

        On Day 19, this function returns (True, 5) because:
        - Today is the campaign end date
        - 5 days have passed since the last weekly report (Day 14)
        - A final report covering days 15-19 should be generated
    """
    # Get campaign end date
    end_date_str = campaign.get("campaignEndDate") or campaign.get("endDate")
    if not end_date_str:
        return (False, 0)

    try:
        end_date = parse_date_string(end_date_str).date()
    except Exception:
        logger.exception("Failed to parse end date for final report check: %s", campaign.get("campaignNumber"))
        return (False, 0)

    today = ref_dt.date()

    # Check if today is campaign end date
    if today != end_date:
        return (False, 0)

    # Only applies to WEEKLY and MONTHLY frequencies
    # Daily reports don't need special handling as they run every day
    freq = (campaign.get("triggerFrequency") or "DAILY").strip().lower()
    if freq == "daily":
        return (False, 0)

    # Get campaign start date to calculate days since start
    start_date_str = campaign.get("campaignStartDate") or campaign.get("startDate")
    if not start_date_str:
        return (False, 0)

    try:
        start_date = parse_date_string(start_date_str).date()
    except Exception:
        logger.exception("Failed to parse start date for final report check: %s", campaign.get("campaignNumber"))
        return (False, 0)

    days_since_start = (today - start_date).days

    # Calculate days since last scheduled report
    if freq == "weekly":
        days_since_last_report = days_since_start % 7
    elif freq == "monthly":
        days_since_last_report = days_since_start % 30
    else:
        return (False, 0)

    # If there are remaining days (not on a regular report boundary), trigger final report
    # Also check that days_since_start >= 1 to ensure there's at least one day of data
    if days_since_last_report > 0 and days_since_start >= 1:
        logger.info("Campaign %s: Final report due with %d remaining days",
                   campaign.get("campaignNumber"), days_since_last_report)
        return (True, days_since_last_report)

    return (False, 0)

# -----------------------------
# DAG definition
# -----------------------------
default_args = {
    "owner": "data-team",
    "depends_on_past": False,
    "retries": 1,
    "retry_delay": timedelta(minutes=5),
}

with DAG(
    dag_id="hcm_campaign_scheduler",
    default_args=default_args,
    schedule="0 * * * *",  # run hourly at minute 0 UTC
    start_date=datetime(2025, 1, 1, tzinfo=UTC),
    catchup=False,
    tags=["hcm", "scheduler"],
) as dag:

    def find_window_matches(**context):
        """
        Find campaigns whose triggerTime (today) falls in:
            window_start = now - 1 hour + WINDOW_GRACE_MINUTES
            window_end = now
        Push matched list to XCom 'matched_campaigns'
        """
        # Get current time in UTC
        now = datetime.now(UTC)

        # Calculate window (backward-looking 1 hour)
        window_start = now - timedelta(hours=1) + timedelta(minutes=WINDOW_GRACE_MINUTES)
        window_end = now

        logger.info("=" * 80)
        logger.info("SCHEDULER CHECK - %s", now.strftime("%Y-%m-%d %H:%M:%S UTC"))
        logger.info("=" * 80)
        logger.info("Window: %s to %s",
                   window_start.strftime("%Y-%m-%d %H:%M:%S"),
                   window_end.strftime("%Y-%m-%d %H:%M:%S"))

        campaigns = fetch_campaigns_from_mdms()
        matched = []

        for c in campaigns:
            try:
                # Get campaign identifier (can be campaignNumber or projectTypeId)
                identifier, identifier_type = get_campaign_identifier(c)

                # Check if active (support both isActive and active)
                is_active = c.get("isActive", c.get("active", False))
                if not is_active:
                    logger.debug("Campaign %s: Inactive - SKIP", identifier)
                    continue

                # Check if campaign is within active date range (startDate <= today <= endDate)
                campaign_active, active_reason = is_campaign_active(c, now)
                if not campaign_active:
                    logger.info("Campaign %s: %s - SKIP", identifier, active_reason)
                    continue

                # Get trigger time
                trig = c.get("triggerTime")
                if not trig:
                    logger.warning("Campaign %s: Missing triggerTime - SKIP", identifier)
                    continue

                # Parse trigger time for today
                trig_dt = parse_trigger_time_today(trig, now)

                # Check if in window
                in_window = window_start <= trig_dt <= window_end

                # Check if frequency is due
                freq_due = frequency_due(c, now)

                # Check if final report is due (campaign ending today with remaining days)
                is_final, remaining_days = is_final_report_due(c, now)

                logger.info("Campaign %s (%s):", identifier, identifier_type)
                logger.info("  Trigger time: %s → %s", trig, trig_dt.strftime("%H:%M:%S"))
                logger.info("  In window: %s", in_window)
                logger.info("  Frequency due: %s", freq_due)
                logger.info("  Final report due: %s (remaining days: %d)", is_final, remaining_days)

                if in_window and (freq_due or is_final):
                    if is_final:
                        logger.info("  ✓ FINAL REPORT MATCH - Campaign ending with %d remaining days", remaining_days)
                    else:
                        logger.info("  ✓ MATCH - Adding to processing list")
                    matched.append({
                        # New unified identifier fields
                        "campaignIdentifier": identifier,
                        "identifierType": identifier_type,
                        # Keep campaignNumber for backward compatibility
                        "campaignNumber": identifier if identifier_type == "campaignNumber" else None,
                        "projectTypeId": identifier if identifier_type == "projectTypeId" else None,
                        "reportName": c.get("reportName"),
                        "triggerFrequency": c.get("triggerFrequency"),
                        "triggerTime": c.get("triggerTime"),
                        "startDate": c.get("campaignStartDate"),
                        "endDate": c.get("campaignEndDate"),
                        "outputPvcName": c.get("outputPvcName"),
                        "isFinalReport": is_final,
                        "remainingDays": remaining_days if is_final else 0,
                        # Report time bounds for data collection window
                        "reportStartTime": c.get("reportStartTime", "00:00:00"),
                        "reportEndTime": c.get("reportEndTime", "23:59:59")
                    })
                else:
                    logger.info("  ✗ SKIP")

            except Exception:
                logger.exception("Error while evaluating campaign %s", c.get("campaignIdentifier", "UNKNOWN"))
                continue

        logger.info("=" * 80)
        logger.info("RESULT: %d campaign(s) matched", len(matched))
        if matched:
            for m in matched:
                logger.info("  - %s [%s] (%s)", m["campaignIdentifier"], m["identifierType"], m["reportName"])
        logger.info("=" * 80)

        context["ti"].xcom_push(key="matched_campaigns", value=matched)
        return matched

    def decide_campaign_trigger(**context):
        """
        Validate matched campaigns, dedupe and prepare final payload.
        Push to XCom 'final_payload'.
        """
        matched = context["ti"].xcom_pull(key="matched_campaigns", task_ids="find_window_matches") or []
        logger.info("Deciding on %d matched campaigns", len(matched))
        final = []
        seen = set()

        for c in matched:
            # Use campaignIdentifier for identification
            cid = c.get("campaignIdentifier")
            if not cid:
                logger.warning("Campaign missing campaignIdentifier; skipping")
                continue

            # Dedupe by (campaignIdentifier, reportName)
            key = (cid, c.get("reportName"))
            if key in seen:
                logger.warning("Duplicate: %s - %s (skipping)", cid, c.get("reportName"))
                continue

            # Basic validation
            if not c.get("reportName"):
                logger.warning("Campaign %s missing reportName; skipping", cid)
                continue

            # Validate dates format
            try:
                if c.get("startDate"):
                    parse_date_string(c.get("startDate"))
                if c.get("endDate"):
                    parse_date_string(c.get("endDate"))
            except Exception:
                logger.warning("Campaign %s has invalid date format; skipping", cid)
                continue

            final.append(c)
            seen.add(key)

        logger.info("Final payload size: %d", len(final))
        context["ti"].xcom_push(key="final_payload", value=final)
        return final

    def trigger_processor(**context):
        """
        Instead of calling REST API,
        internally trigger the dynamic campaigns DAG using TriggerDagRunOperator.
        """

        final = context["ti"].xcom_pull(
        key="final_payload",
        task_ids="decide_campaign_trigger"
        ) or []


        if not final:
            logger.info("No campaigns to trigger this hour.")
            return "no_triggers"


        run_id = f"auto_trigger__{datetime.now(UTC).strftime('%Y%m%d_%H%M%S')}"
        conf = {"matched_campaigns": final}


        logger.info(
        "Running TriggerDagRunOperator internally for '%s' with run_id=%s",
        PROCESSOR_DAG_ID,
        run_id,
        )


        # TriggerDagRunOperator dynamically created and executed inside Python callable
        trigger_op = TriggerDagRunOperator(
        task_id=f"trigger_internal_{run_id}",
        trigger_dag_id=PROCESSOR_DAG_ID,
        conf=conf,
        wait_for_completion=False,
        reset_dag_run=True,
        poke_interval=10,
        )


        # Manually execute operator
        trigger_op.execute(context=context)
        return f"triggered_{len(final)}"

    t1 = PythonOperator(task_id="find_window_matches", python_callable=find_window_matches)
    t2 = PythonOperator(task_id="decide_campaign_trigger", python_callable=decide_campaign_trigger)
    t3 = PythonOperator(task_id="trigger_processor", python_callable=trigger_processor)

    t1 >> t2 >> t3