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

import os
import json
import logging
from datetime import datetime, timedelta, timezone

import requests

from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.models import DagBag
from airflow.utils.state import State
from airflow.utils.types import DagRunType

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

# -----------------------------
# Helpers
# -----------------------------
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
    return campaigns

def parse_trigger_time_today(trigger_time_str, ref_dt):
    """
    Convert triggerTime string (e.g. '09:30:00+0000' or '09:30:00' or '09:30')
    into a datetime for the reference date (ref_dt) in UTC.

    Args:
        trigger_time_str (str): Time string from MDMS
        ref_dt (datetime): Reference datetime (current execution time)

    Returns:
        datetime: Datetime object for today at the trigger time in UTC
    """
    # Remove timezone suffix (e.g., "+0000")
    t = trigger_time_str.split("+")[0].strip()

    # Split into components
    parts = t.split(":")
    hh = int(parts[0])
    mm = int(parts[1]) if len(parts) > 1 else 0
    ss = int(parts[2]) if len(parts) > 2 else 0

    # Create datetime with today's date and trigger time in UTC
    return datetime(
        ref_dt.year,
        ref_dt.month,
        ref_dt.day,
        hh,
        mm,
        ss,
        tzinfo=UTC
    )

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

def is_first_day(campaign, ref_dt):
    """
    Return True if campaign.startDate equals the reference date (no previous data to report).

    Args:
        campaign (dict): Campaign object with startDate
        ref_dt (datetime): Reference datetime (current execution time)

    Returns:
        bool: True if today is the campaign's start date
    """
    s = campaign.get("startDate")
    if not s:
        return False
    try:
        parsed = parse_date_string(s)
        return parsed.date() == ref_dt.date()
    except Exception:
        logger.exception("Failed to parse startDate for campaign %s", campaign.get("campaignNumber"))
        return False

def frequency_due(campaign, ref_dt):
    """
    Frequency rules:
      - Daily: days_since_start >= 1
      - Weekly: days_since_start >= 7 and days_since_start % 7 == 0
      - Monthly: days_since_start >= 30 and days_since_start % 30 == 0

    Args:
        campaign (dict): Campaign object with triggerFrequency and startDate
        ref_dt (datetime): Reference datetime (current execution time)

    Returns:
        bool: True if the campaign is due to run based on frequency
    """
    freq = (campaign.get("triggerFrequency") or "Daily").strip().lower()
    start = campaign.get("startDate")
    if not start:
        return False
    try:
        sdt = parse_date_string(start).date()
    except Exception:
        logger.exception("Failed to parse startDate for frequency check: %s", campaign.get("campaignNumber"))
        return False

    days = (ref_dt.date() - sdt).days

    if freq == "daily":
        return days >= 1
    if freq == "weekly":
        return days >= 7 and (days % 7 == 0)
    if freq == "monthly":
        return days >= 30 and (days % 30 == 0)
    return False

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
                campaign_number = c.get("campaignNumber", "UNKNOWN")

                # Check if active
                if not c.get("active", False):
                    logger.debug("Campaign %s: Inactive - SKIP", campaign_number)
                    continue

                # Check if first day (no previous data to report)
                if is_first_day(c, now):
                    logger.info("Campaign %s: First day - SKIP", campaign_number)
                    continue

                # Get trigger time
                trig = c.get("triggerTime")
                if not trig:
                    logger.warning("Campaign %s: Missing triggerTime - SKIP", campaign_number)
                    continue

                # Parse trigger time for today
                trig_dt = parse_trigger_time_today(trig, now)

                # Check if in window
                in_window = window_start <= trig_dt <= window_end

                # Check if frequency is due
                freq_due = frequency_due(c, now)

                logger.info("Campaign %s:", campaign_number)
                logger.info("  Trigger time: %s → %s", trig, trig_dt.strftime("%H:%M:%S"))
                logger.info("  In window: %s", in_window)
                logger.info("  Frequency due: %s", freq_due)

                if in_window and freq_due:
                    logger.info("  ✓ MATCH - Adding to processing list")
                    matched.append({
                        "campaignNumber": c.get("campaignNumber"),
                        "reportName": c.get("reportName"),
                        "triggerFrequency": c.get("triggerFrequency"),
                        "triggerTime": c.get("triggerTime"),
                        "startDate": c.get("startDate"),
                        "endDate": c.get("endDate"),
                        "outputPvcName": c.get("outputPvcName")
                    })
                else:
                    logger.info("  ✗ SKIP")

            except Exception:
                logger.exception("Error while evaluating campaign %s", c.get("campaignNumber"))
                continue

        logger.info("=" * 80)
        logger.info("RESULT: %d campaign(s) matched", len(matched))
        if matched:
            for m in matched:
                logger.info("  - %s (%s)", m["campaignNumber"], m["reportName"])
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
            cid = c.get("campaignNumber")
            if not cid:
                continue

            # Dedupe by (campaignNumber, reportName)
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
        Trigger the processor DAG programmatically and pass final_payload in conf.
        """
        final = context["ti"].xcom_pull(key="final_payload", task_ids="decide_campaign_trigger") or []
        if not final:
            logger.info("No campaigns to trigger this hour.")
            return "no_triggers"

        conf = {"matched_campaigns": final}
        try:
            db = DagBag()
            proc_dag = db.get_dag(PROCESSOR_DAG_ID)
            if not proc_dag:
                raise Exception(f"Processor DAG '{PROCESSOR_DAG_ID}' not found")

            # Get current time for run_id
            now = datetime.now(UTC)
            run_id = f"auto_trigger__{now.strftime('%Y%m%d_%H%M%S')}"

            proc_dag.create_dagrun(
                run_id=run_id,
                state=State.QUEUED,
                execution_date=now,
                conf=conf,
                run_type=DagRunType.MANUAL,
                external_trigger=True,
            )
            logger.info("✓ Triggered processor DAG %s with %d campaigns", PROCESSOR_DAG_ID, len(final))
            return f"triggered_{len(final)}"
        except Exception:
            logger.exception("Failed to trigger processor DAG")
            raise

    t1 = PythonOperator(task_id="find_window_matches", python_callable=find_window_matches)
    t2 = PythonOperator(task_id="decide_campaign_trigger", python_callable=decide_campaign_trigger)
    t3 = PythonOperator(task_id="trigger_processor", python_callable=trigger_processor)

    t1 >> t2 >> t3
