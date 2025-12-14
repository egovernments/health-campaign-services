"""
hcm_dynamic_campaigns.py

DAG 2: Campaign Report Processor (Trigger-only DAG)

This DAG is triggered by hcm_campaign_scheduler with matched campaigns in conf.
It dynamically creates KubernetesPodOperator tasks (one per campaign) that run in PARALLEL.

Flow:
1. build_payload: Reads matched campaigns from conf, calculates date ranges, builds env vars
2. campaign_pods: Dynamically expands to N pods (one per campaign), all run in parallel
3. print_summary: Logs final summary of processed campaigns

Triggered by: hcm_campaign_scheduler
Schedule: None (trigger-only)
Parallelism: All campaigns run simultaneously (max controlled by max_active_tasks)

Required Environment Variables:
- REPORT_IMAGE: Docker image for report generation (set by CI/CD)
- K8S_NAMESPACE: Kubernetes namespace (default: default)

Note: Reports are generated in ephemeral storage (emptyDir) and uploaded to FileStore.
No PVC is required as files don't need to persist after upload.
"""

import os
import json
import logging
from datetime import datetime, timedelta, timezone

from airflow import DAG
from airflow.decorators import task
from airflow.models import Variable

from airflow.providers.cncf.kubernetes.operators.pod import KubernetesPodOperator
from kubernetes.client import models as k8s_models

logger = logging.getLogger("airflow.task")
logger.setLevel(logging.INFO)

# Timezone: All calculations in UTC
UTC = timezone.utc

# -----------------------------------------
# ENVIRONMENT CONFIGURATION
# -----------------------------------------

# Docker image for report generation (configured via Airflow Variables)
# Set via: Admin → Variables → Add Variable (Key: REPORT_IMAGE, Value: <image:tag>)
# Or via CLI: airflow variables set REPORT_IMAGE "egovio/hcm-custom-reports:airflow-analysis-3b0c066"
REPORT_IMAGE = Variable.get("REPORT_IMAGE", default_var=None)
if not REPORT_IMAGE:
    raise ValueError("REPORT_IMAGE Airflow Variable is required. Set it via Admin → Variables in Airflow UI.")

# Temporary storage path for report generation (uses emptyDir, not PVC)
OUTPUT_MOUNT_PATH = "/app/REPORTS_GENERATION/FINAL_REPORTS"

# Kubernetes namespace where pods will be created
K8S_NAMESPACE = os.getenv("K8S_NAMESPACE", "airflow")

#File store env variables
FILE_STORE_URL = os.getenv("FILE_STORE_URL") 
FILE_STORE_UPLOAD_FILE_ENDPOINT = os.getenv("FILE_STORE_UPLOAD_FILE_ENDPOINT", "filestore/v1/files")
TENANT_ID = os.getenv("TENANT_ID", "dev")
MODULE_NAME = os.getenv("FILE_STORE_MODULE_NAME", "custom-reports")

# Container resource constraints
# Requests: Minimum guaranteed resources
# Limits: Maximum allowed resources
CONTAINER_RESOURCES = k8s_models.V1ResourceRequirements(
    requests={
        "memory": "128Mi",   # Start with minimal memory
        "cpu": "100m"        # 0.1 CPU cores
    },
    limits={
        "memory": "2Gi",     # Max 2GB memory
        "cpu": "1000m"       # Max 1 CPU core
    }
)

#Kafka config
KAFKA_BROKER = os.getenv("KAFKA_BROKER")

if not KAFKA_BROKER:
    raise ValueError("Missing required field: KAFKA_BROKER")

# -------------------------------------------------
# HELPER FUNCTIONS
# -------------------------------------------------

def parse_time_string(time_str):
    """
    Parse a time string (HH:MM:SS or HH:MM) into hours, minutes, seconds.

    Args:
        time_str (str): Time string like "14:00:00", "14:00", or "14"

    Returns:
        tuple: (hour, minute, second)
    """
    parts = time_str.strip().split(":")
    hour = int(parts[0]) if len(parts) > 0 else 0
    minute = int(parts[1]) if len(parts) > 1 else 0
    second = int(parts[2]) if len(parts) > 2 else 0
    return (hour, minute, second)


def parse_time_with_timezone(time_str):
    """
    Parse a time string with timezone offset and return time components plus UTC offset.

    Supports formats:
        - "10:00:00+0530" → (10, 0, 0, 330) - 10 AM IST, offset +330 minutes
        - "14:00:00-0500" → (14, 0, 0, -300) - 2 PM EST, offset -300 minutes
        - "10:00:00" → (10, 0, 0, 0) - assumes UTC

    Args:
        time_str (str): Time string with optional timezone offset

    Returns:
        tuple: (hour, minute, second, tz_offset_minutes)
            - hour, minute, second: Time components
            - tz_offset_minutes: Timezone offset in minutes (positive = ahead of UTC)
    """
    time_part = time_str.strip()
    tz_offset_minutes = 0

    # Check for timezone offset (+HHMM or -HHMM)
    if '+' in time_part:
        t, tz = time_part.split('+')
        tz_sign = 1
    elif time_part.count('-') > 2:
        # More than 2 hyphens means timezone (not just time separator)
        parts = time_part.rsplit('-', 1)
        t = parts[0]
        tz = parts[1] if len(parts) > 1 else '0000'
        tz_sign = -1
    else:
        t = time_part
        tz = None
        tz_sign = 0

    # Parse timezone offset
    if tz:
        tz = tz.strip().replace(':', '')
        tz_hours = int(tz[:2]) if len(tz) >= 2 else 0
        tz_mins = int(tz[2:4]) if len(tz) >= 4 else 0
        tz_offset_minutes = tz_sign * (tz_hours * 60 + tz_mins)

    # Parse time components
    parts = t.split(":")
    hour = int(parts[0]) if len(parts) > 0 else 0
    minute = int(parts[1]) if len(parts) > 1 else 0
    second = int(parts[2]) if len(parts) > 2 else 0

    return (hour, minute, second, tz_offset_minutes)


def to_utc(date, hour, minute, second, tz_offset_minutes):
    """
    Convert a local datetime to UTC.

    Args:
        date: The date object
        hour, minute, second: Time components in local timezone
        tz_offset_minutes: Timezone offset in minutes (positive = ahead of UTC)

    Returns:
        datetime: UTC datetime

    Example:
        10:00:00+0530 (10 AM IST) → subtract 330 minutes → 04:30:00 UTC
        14:00:00-0500 (2 PM EST) → subtract -300 minutes → 19:00:00 UTC
    """
    local_dt = datetime(date.year, date.month, date.day, hour, minute, second, tzinfo=UTC)
    return local_dt - timedelta(minutes=tz_offset_minutes)


def should_report_today(campaign, now):
    """
    Determine if the report should cover today's data or yesterday's data
    based on reportEndTime vs triggerTime comparison.

    Logic:
        - If reportEndTime < triggerTime: Report covers TODAY (data collection complete)
        - If reportEndTime >= triggerTime: Report covers YESTERDAY (data collection not complete)

    Args:
        campaign (dict): Campaign with reportEndTime and triggerTime
        now (datetime): Current execution time

    Returns:
        bool: True if report should cover today, False for yesterday
    """
    report_end_time_str = campaign.get("reportEndTime", "23:59:59")
    trigger_time_str = campaign.get("triggerTime", "00:00:00")

    # Parse reportEndTime (just the time part, ignore timezone)
    end_h, end_m, end_s = parse_time_string(report_end_time_str.split("+")[0].split("-")[0])
    report_end_minutes = end_h * 60 + end_m

    # Parse triggerTime (just the time part, ignore timezone)
    trig_h, trig_m, trig_s = parse_time_string(trigger_time_str.split("+")[0].split("-")[0])
    trigger_minutes = trig_h * 60 + trig_m

    # If reportEndTime < triggerTime, data collection is complete for today
    return report_end_minutes < trigger_minutes


def is_first_day(campaign, now):
    """
    Check if today is the first day of the campaign.

    Args:
        campaign (dict): Campaign object with startDate field
        now (datetime): Current execution time

    Returns:
        bool: True if today is the campaign's start date
    """
    start_date_str = campaign.get("startDate", "")
    if not start_date_str:
        return False

    # Parse start date (format: DD-MM-YYYY HH:MM:SS+ZZZZ)
    try:
        start_dt = datetime.strptime(start_date_str, "%d-%m-%Y %H:%M:%S%z")
        return now.date() == start_dt.date()
    except ValueError:
        logger.warning("Failed to parse startDate: %s", start_date_str)
        return False


def frequency_due(campaign, now):
    """
    Check if the campaign is due to run based on its trigger frequency.

    Uses 1-indexed days (campaign start date = Day 1, not Day 0):
      - DAILY: Always due (day_number >= 1)
      - WEEKLY: Due on Day 7, 14, 21, ... (day_number >= 7 and day_number % 7 == 0)
      - MONTHLY: Due on Day 30, 60, 90, ... (day_number >= 30 and day_number % 30 == 0)

    Example for WEEKLY:
      - Day 1 (start date): Not due
      - Day 7: Due (first weekly report covers Days 1-7)
      - Day 14: Due (second weekly report covers Days 8-14)

    Args:
        campaign (dict): Campaign object with triggerFrequency and startDate
        now (datetime): Current execution time

    Returns:
        bool: True if the campaign is due to run based on frequency
    """
    freq = (campaign.get("triggerFrequency") or "DAILY").strip().lower()
    start_date_str = campaign.get("startDate", "")

    if not start_date_str:
        return True  # Default to running if no start date

    try:
        start_dt = datetime.strptime(start_date_str, "%d-%m-%Y %H:%M:%S%z")
        # Use 1-indexed days: start date = Day 1
        day_number = (now.date() - start_dt.date()).days + 1

        if freq == "daily":
            return day_number >= 1
        if freq == "weekly":
            return day_number >= 7 and (day_number % 7 == 0)
        if freq == "monthly":
            return day_number >= 30 and (day_number % 30 == 0)
        return True  # Default to running for unknown frequencies
    except ValueError:
        logger.warning("Failed to parse startDate for frequency check: %s", start_date_str)
        return True  # Default to running if parsing fails


def is_final_report_due(campaign, ref_dt):
    """
    Check if campaign is ending today and needs a final partial report.

    Uses 1-indexed days (campaign start date = Day 1).
    This handles the scenario where a campaign ends mid-cycle (e.g., on Day 8
    of a weekly report cycle). In such cases, we need to generate a final
    report covering the remaining days after the last scheduled report.

    Args:
        campaign (dict): Campaign object with endDate, startDate, and triggerFrequency
        ref_dt (datetime): Reference datetime (current execution time)

    Returns:
        tuple: (is_due: bool, remaining_days: int)
            - is_due: True if a final partial report should be generated
            - remaining_days: Number of days to include in the final report

    Example:
        Campaign starts: Day 1 (05-12-2025)
        Weekly reports on: Day 7 (11-12-2025) covers Days 1-7
        Campaign ends: Day 8 (12-12-2025)

        On Day 8, this function returns (True, 1) because:
        - Today is the campaign end date
        - 1 day has passed since the last weekly report (Day 7)
        - A final report covering Day 8 should be generated
    """
    # Get campaign end date
    end_date_str = campaign.get("endDate", "")
    if not end_date_str:
        return (False, 0)

    try:
        end_dt = datetime.strptime(end_date_str, "%d-%m-%Y %H:%M:%S%z")
        end_date = end_dt.date()
    except ValueError:
        logger.warning("Failed to parse endDate for final report check: %s", end_date_str)
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

    # Get campaign start date to calculate day number
    start_date_str = campaign.get("startDate", "")
    if not start_date_str:
        return (False, 0)

    try:
        start_dt = datetime.strptime(start_date_str, "%d-%m-%Y %H:%M:%S%z")
        start_date = start_dt.date()
    except ValueError:
        logger.warning("Failed to parse startDate for final report check: %s", start_date_str)
        return (False, 0)

    # Use 1-indexed days: start date = Day 1
    day_number = (today - start_date).days + 1

    # Calculate days since last scheduled report
    if freq == "weekly":
        days_since_last_report = day_number % 7
    elif freq == "monthly":
        days_since_last_report = day_number % 30
    else:
        return (False, 0)

    # If there are remaining days (not on a regular report boundary), trigger final report
    # Also check that day_number >= 1 to ensure there's at least one day of data
    if days_since_last_report > 0 and day_number >= 1:
        logger.info("Campaign %s: Final report due with %d remaining days",
                   campaign.get("campaignIdentifier", "UNKNOWN"), days_since_last_report)
        return (True, days_since_last_report)

    return (False, 0)


def compute_range(campaign, now, is_final=False, remaining_days=0):
    """
    Calculate report date range based on trigger frequency and report time bounds.

    Args:
        campaign (dict): Campaign object with:
            - triggerFrequency: Daily, Weekly, Monthly
            - reportStartTime: Start time for data collection (e.g., "00:00:00")
            - reportEndTime: End time for data collection (e.g., "14:00:00") - used for DAILY only
            - triggerTime: When the report is triggered (e.g., "12:00:00") - used as end time for WEEKLY/MONTHLY
        now (datetime): Current execution time (UTC)
        is_final (bool): Whether this is a final partial report for campaign end date
        remaining_days (int): Number of days to include in final partial report

    Returns:
        tuple: (start_datetime, end_datetime) in UTC

    Logic for Daily Reports:
        - Uses reportStartTime and reportEndTime
        - If reportEndTime < triggerTime: Report covers TODAY's reportStartTime to reportEndTime
        - If reportEndTime >= triggerTime: Report covers YESTERDAY's reportStartTime to reportEndTime

    Logic for Weekly/Monthly Reports:
        - Uses reportStartTime for start time and triggerTime as end time
        - This ensures all data up to the trigger time is captured, avoiding missing last day data
        - End date is always TODAY (when report is triggered)
        - Start date is 7 days back (weekly) or 30 days back (monthly)

    Examples:
        Daily - Scenario 1: reportStartTime=00:00, reportEndTime=14:00, triggerTime=16:00
        - reportEndTime (14:00) < triggerTime (16:00)
        - Report covers: TODAY 00:00 to TODAY 14:00

        Daily - Scenario 2: reportStartTime=00:00, reportEndTime=17:00, triggerTime=16:00
        - reportEndTime (17:00) >= triggerTime (16:00)
        - Report covers: YESTERDAY 00:00 to YESTERDAY 17:00

        Weekly: reportStartTime=00:00, triggerTime=12:00, freq=weekly
        - campaignStartDate=06-12-2025, report runs on 12-12-2025
        - Report covers: 06-12-2025 00:00 to 12-12-2025 12:00
    """
    freq = campaign.get("triggerFrequency", "Daily").lower()

    # Parse report time bounds with timezone support
    # Example: "10:00:00+0530" → 10 AM IST → 04:30 UTC
    report_start_time_str = campaign.get("reportStartTime", "00:00:00")
    report_end_time_str = campaign.get("reportEndTime", "23:59:59")
    trigger_time_str = campaign.get("triggerTime", "00:00:00")

    start_h, start_m, start_s, start_tz_offset = parse_time_with_timezone(report_start_time_str)
    end_h, end_m, end_s, end_tz_offset = parse_time_with_timezone(report_end_time_str)
    trigger_h, trigger_m, trigger_s, trigger_tz_offset = parse_time_with_timezone(trigger_time_str)

    # For WEEKLY and MONTHLY: use triggerTime as end time instead of reportEndTime
    # This ensures we capture all data up to the moment the report is generated
    if freq in ("weekly", "monthly"):
        # End date is always TODAY (when the report is triggered)
        end_date = now.date()

        if freq == "weekly":
            # Report covers 7 days: from (today - 6 days) to today
            start_date = end_date - timedelta(days=6)
        else:  # monthly
            # Report covers 30 days: from (today - 29 days) to today
            start_date = end_date - timedelta(days=29)

        # Convert to UTC:
        # - Start: first day at reportStartTime
        # - End: last day (today) at triggerTime
        start = to_utc(start_date, start_h, start_m, start_s, start_tz_offset)
        end = to_utc(end_date, trigger_h, trigger_m, trigger_s, trigger_tz_offset)

        logger.info("%s report range: %s %02d:%02d:%02d to %s %02d:%02d:%02d (using triggerTime as end)",
                   freq.upper(),
                   start_date.strftime("%Y-%m-%d"), start_h, start_m, start_s,
                   end_date.strftime("%Y-%m-%d"), trigger_h, trigger_m, trigger_s)
        return (start, end)

    # DAILY reports: use reportStartTime and reportEndTime
    # Determine if we should report for today or yesterday
    use_today = should_report_today(campaign, now)

    if use_today:
        # Data collection is complete for today
        base_date = now.date()
        logger.info("Report will cover TODAY's data (reportEndTime < triggerTime)")
    else:
        # Data collection not complete for today, use yesterday
        base_date = (now - timedelta(days=1)).date()
        logger.info("Report will cover YESTERDAY's data (reportEndTime >= triggerTime)")

    # Handle final partial report for campaign end date
    if is_final and remaining_days > 0:
        # FINAL PARTIAL REPORT: Cover remaining days since last scheduled report
        # For final reports, use triggerTime as end time to capture all data

        # Calculate start date for partial period
        if use_today:
            # Start from (remaining_days - 1) days ago (since today is included)
            start_date = now.date() - timedelta(days=remaining_days - 1)
            end_date = now.date()
        else:
            # Start from remaining_days days ago, end yesterday
            start_date = now.date() - timedelta(days=remaining_days)
            end_date = (now - timedelta(days=1)).date()

        # Convert to UTC - use triggerTime as end time for final reports
        start = to_utc(start_date, start_h, start_m, start_s, start_tz_offset)
        end = to_utc(end_date, trigger_h, trigger_m, trigger_s, trigger_tz_offset)

        logger.info("Final partial report: %d days (%s to %s)",
                   remaining_days,
                   start.strftime("%Y-%m-%d %H:%M:%S"),
                   end.strftime("%Y-%m-%d %H:%M:%S"))
        return (start, end)

    # Default: Daily report
    # Report covers single day (today or yesterday based on time comparison)
    # Convert to UTC considering timezone offsets
    start = to_utc(base_date, start_h, start_m, start_s, start_tz_offset)
    end = to_utc(base_date, end_h, end_m, end_s, end_tz_offset)
    return (start, end)


# ============================
#       DAG DEFINITION
# ============================

default_args = {
    "owner": "hcm-reports-team",
    "depends_on_past": False,
    "retries": 1,
    "retry_delay": timedelta(minutes=2),
}

with DAG(
    dag_id="hcm_dynamic_campaigns",
    default_args=default_args,
    description="Campaign report processor - dynamically creates pods for matched campaigns",
    schedule=None,   # ✅ No schedule - triggered by hcm_campaign_scheduler only
    start_date=datetime(2025, 1, 1, tzinfo=UTC),
    catchup=False,
    tags=["hcm", "reports", "processor"],
    max_active_tasks=10,      # Max 10 pods running in parallel (adjust based on cluster capacity)
) as dag:

    # ================================================================
    # TASK 1: Build Payload
    # ================================================================
    @task(task_id="build_payload")
    def build_payload(**context):
        """
        Read matched campaigns from DAG run conf and build environment variables
        for each campaign's pod.

        Input:
            context["dag_run"].conf = {
                "matched_campaigns": [
                    {
                        "campaignIdentifier": "CMP-2025-01-15-001" or "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                        "identifierType": "campaignNumber" or "projectTypeId",
                        "campaignNumber": "CMP-2025-01-15-001" or null,
                        "projectTypeId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890" or null,
                        "reportName": "hhr_registered_report",
                        "triggerFrequency": "Daily",
                        "triggerTime": "09:00:00+0000",
                        "startDate": "15-01-2025 00:00:00+0000",
                        "endDate": "31-01-2025 00:00:00+0000"
                    },
                    ...
                ]
            }

        Output:
            List of environment variable dictionaries, one per campaign:
            [
                {
                    "CAMPAIGN_IDENTIFIER": "CMP-2025-01-15-001",
                    "IDENTIFIER_TYPE": "campaignNumber",
                    "REPORT_NAME": "hhr_registered_report",
                    "TRIGGER_FREQUENCY": "Daily",
                    "START_DATE": "2025-01-17 00:00:00+0000",
                    "END_DATE": "2025-01-17 23:59:59+0000",
                    "OUTPUT_DIR": "/app/REPORTS_GENERATION/FINAL_REPORTS/CMP-001/hhr_registered_report/Daily",
                    "OUTPUT_FILE": "/app/REPORTS_GENERATION/FINAL_REPORTS/CMP-001/hhr_registered_report/Daily/hhr_registered_report_2025-01-18_09-00-00.xlsx",
                    "REPORT_SCRIPT": "/app/REPORTS_GENERATION/REPORTS/hhr_registered_report/hhr_registered_report.py"
                },
                ...
            ]

        Pod Execution Flow:
        1. Dockerfile ENTRYPOINT runs: python /app/main.py
        2. main.py reads environment variables (REPORT_NAME, CAMPAIGN_IDENTIFIER, IDENTIFIER_TYPE, START_DATE, END_DATE)
        3. main.py calls report script with command-line arguments
        4. Report script queries Elasticsearch filtered by campaign and date range
        5. Generate Excel file, create ZIP, and upload to FileStore
        """
        # Read matched campaigns from DAG run configuration
        matches = context["dag_run"].conf.get("matched_campaigns", [])

        if not matches:
            logger.warning("No matched campaigns in DAG run conf")
            return []

        logger.info("=" * 80)
        logger.info("BUILDING PAYLOAD FOR %d CAMPAIGNS", len(matches))
        logger.info("=" * 80)

        dag_run_id = context["dag_run"].run_id
        dag_id = context["dag"].dag_id

        env_list = []
        now = datetime.now(UTC)

        for idx, c in enumerate(matches, 1):
            # Required fields - already validated by scheduler DAG
            campaign_identifier = c.get("campaignIdentifier")
            identifier_type = c.get("identifierType")
            report_name = c.get("reportName")
            frequency = c.get("triggerFrequency", "Daily")
            report_start_time = c.get("reportStartTime", "00:00:00")
            report_end_time = c.get("reportEndTime", "23:59:59")
            trigger_time = c.get("triggerTime", "00:00:00")

            # Check if this is a final report (campaign ending today with remaining days)
            # This logic was moved from scheduler DAG to keep all report logic here
            is_final_report, remaining_days = is_final_report_due(c, now)

            logger.info("Campaign %d/%d: %s [%s]", idx, len(matches), campaign_identifier, identifier_type)
            logger.info("  Report: %s", report_name)
            logger.info("  Frequency: %s", frequency)
            logger.info("  Report time window: %s to %s", report_start_time, report_end_time)
            logger.info("  Trigger time: %s", trigger_time)
            if is_final_report:
                logger.info("  ⚡ FINAL REPORT: Covering %d remaining days", remaining_days)

            # Check if frequency is due (DAILY/WEEKLY/MONTHLY)
            # Final reports bypass frequency check (campaign ending mid-cycle)
            if not is_final_report and not frequency_due(c, now):
                logger.info("  ⏭️ SKIP: Frequency not due (%s)", frequency)
                continue

            # Check if first day AND report would cover yesterday's data
            # On first day, only allow if reportEndTime < triggerTime (report covers today)
            # Skip if reportEndTime >= triggerTime (would try to report yesterday before campaign existed)
            if is_first_day(c, now) and not should_report_today(c, now):
                logger.info("  ⏭️ SKIP: First day and report would cover yesterday (no data exists)")
                continue

            # Calculate report date range based on frequency
            # Uses reportStartTime, reportEndTime, and triggerTime to determine today vs yesterday
            # Pass is_final_report and remaining_days computed above
            start_dt, end_dt = compute_range(c, now, is_final_report, remaining_days)
            logger.info("  Date range: %s to %s",
                    start_dt.strftime("%Y-%m-%d %H:%M:%S"),
                    end_dt.strftime("%Y-%m-%d %H:%M:%S"))

            # Build folder structure for temporary storage
            # Format: /app/REPORTS_GENERATION/FINAL_REPORTS/<campaign_identifier>/<report>/<frequency>/
            # Example: /app/REPORTS_GENERATION/FINAL_REPORTS/CMP-001/hhr_registered_report/Daily/
            # Example: /app/REPORTS_GENERATION/FINAL_REPORTS/a1b2c3d4-e5f6-7890-abcd-ef1234567890/hhr_registered_report/Daily/
            output_dir = f"{OUTPUT_MOUNT_PATH}/{campaign_identifier}/{report_name}/{frequency}"

            # Generate timestamped filename
            # Format: <report_name>_YYYY-MM-DD_HH-MM-SS.xlsx
            # Example: hhr_registered_report_2025-01-18_09-00-00.xlsx
            timestamp = now.strftime("%Y-%m-%d_%H-%M-%S")
            filename = f"{report_name}_{timestamp}.xlsx"
            output_file = f"{output_dir}/{filename}"

            logger.info("  Output: %s", output_file)

            # Build environment variables for this campaign's pod
            # These match the env vars expected by main.py
            env_dict = {
                # Campaign identification
                "CAMPAIGN_IDENTIFIER": campaign_identifier,
                "IDENTIFIER_TYPE": identifier_type,

                "REPORT_NAME": report_name,
                "TRIGGER_FREQUENCY": frequency,
                "TRIGGER_TIME" : c.get("triggerTime", 0),

                # Date range for report (format: YYYY-MM-DD HH:MM:SS+ZZZZ)
                # main.py expects START_DATE and END_DATE (not REPORT_START/REPORT_END)
                "START_DATE": start_dt.strftime("%Y-%m-%d %H:%M:%S%z"),
                "END_DATE": end_dt.strftime("%Y-%m-%d %H:%M:%S%z"),

                # Final report flag (for campaign end date with partial period)
                "IS_FINAL_REPORT": str(is_final_report).lower(),
                "REMAINING_DAYS": str(remaining_days),

                # Output configuration
                "OUTPUT_DIR": OUTPUT_MOUNT_PATH,  # Base path - main.py will add campaign/report/frequency
                "OUTPUT_FILE": output_file,

                # Script location (main.py will execute this)
                # Report scripts are stored in /app/REPORTS_GENERATION/REPORTS/<report_name>/<report_name>.py
                "REPORT_SCRIPT": f"/app/REPORTS_GENERATION/REPORTS/{report_name}/{report_name}.py",

                #File store configurations
                "FILE_STORE_URL" : FILE_STORE_URL,
                "FILE_STORE_UPLOAD_FILE_ENDPOINT" : FILE_STORE_UPLOAD_FILE_ENDPOINT,
                "TENANT_ID" : TENANT_ID,
                "FILE_STORE_MODULE_NAME" : MODULE_NAME,

                #Dag information
                "DAG_RUN_ID" : dag_run_id,
                "DAG_ID" : dag_id,

                #Kafka configurations
                "CUSTOM_REPORTS_AUTOMATION_TOPIC" : os.getenv("CUSTOM_REPORTS_AUTOMATION_TOPIC"),
                "KAFKA_BROKER" : KAFKA_BROKER
            }

            env_list.append(env_dict)

        logger.info("=" * 80)
        logger.info("PAYLOAD BUILT: %d environment configurations", len(env_list))
        logger.info("=" * 80)

        # Return list of env dicts (will be passed to .expand())
        return env_list


    # ================================================================
    # TASK 2: Campaign Pods (Dynamic Expansion)
    # ================================================================

    # Build environment variables for all campaigns
    envs = build_payload()

    # Dynamically create KubernetesPodOperator tasks (one per campaign)
    # All pods run in PARALLEL (up to max_active_tasks limit)
    #
    # .partial() sets common parameters for all pods
    # .expand(env_vars=envs) creates one pod task per env dict in the list
    #
    # If envs = [{...}, {...}, {...}], this creates 3 pod tasks:
    #   - campaign_pods[0] with env_vars = {...}
    #   - campaign_pods[1] with env_vars = {...}
    #   - campaign_pods[2] with env_vars = {...}
    campaign_pods = KubernetesPodOperator.partial(
        task_id="campaign_pods",

        # Kubernetes configuration
        namespace=K8S_NAMESPACE,
        image=REPORT_IMAGE,

        # No cmds/arguments needed - Dockerfile ENTRYPOINT handles execution
        # Dockerfile should contain: ENTRYPOINT ["python3", "/app/main.py"]

        # Resource constraints (requests and limits)
        container_resources=CONTAINER_RESOURCES,

        # Security context for pod execution
        security_context=k8s_models.V1PodSecurityContext(
            run_as_user=1000,
            run_as_group=1000,      
            fs_group=1000         # File system group
        ),

        # Pod behavior
        get_logs=True,                    # Stream pod logs to Airflow task logs
        is_delete_operator_pod=True,      # Delete pod after completion (cleanup)
        in_cluster=True,                  # Running inside Kubernetes cluster
        startup_timeout_seconds=600,      # Wait up to 10 minutes for pod to start

        # Volume configuration: Use emptyDir for temporary report storage
        # Reports are uploaded to FileStore and don't need persistent storage
        volumes=[
            k8s_models.V1Volume(
                name="reports-output",
                empty_dir=k8s_models.V1EmptyDirVolumeSource()
            )
        ],
        volume_mounts=[
            k8s_models.V1VolumeMount(
                mount_path=OUTPUT_MOUNT_PATH,
                name="reports-output"
            )
        ],

        # Labels for pod identification in Kubernetes
        labels={
            "app": "hcm-reports",
            "managed-by": "airflow"
        }
    ).expand(
        # ✅ CRITICAL: .expand() creates dynamic tasks
        # Each env dict becomes a separate pod with those environment variables
        env_vars=envs
    )


    # ================================================================
    # TASK 3: Print Summary
    # ================================================================
    @task(task_id="print_summary")
    def print_summary(env_list):
        """
        Log final summary of processed campaigns.

        This task runs after all pods complete (successfully or failed).
        It provides a consolidated view of what was processed.

        Args:
            env_list: List of environment dicts from build_payload
        """
        if not env_list:
            logger.info("=" * 80)
            logger.info("NO CAMPAIGNS PROCESSED")
            logger.info("=" * 80)
            return {"status": "no_campaigns", "count": 0}

        logger.info("=" * 80)
        logger.info("CAMPAIGN PROCESSING SUMMARY")
        logger.info("=" * 80)
        logger.info("Total campaigns processed: %d", len(env_list))
        logger.info("")

        summary = []
        for idx, env in enumerate(env_list, 1):
            campaign_info = {
                "index": idx,
                "campaignIdentifier": env.get("CAMPAIGN_IDENTIFIER"),
                "identifierType": env.get("IDENTIFIER_TYPE"),
                "reportName": env.get("REPORT_NAME"),
                "frequency": env.get("TRIGGER_FREQUENCY"),
                "dateRange": f"{env.get('START_DATE')} to {env.get('END_DATE')}",
                "outputFile": env.get("OUTPUT_FILE")
            }
            summary.append(campaign_info)

            logger.info("Campaign %d: %s [%s]", idx, env.get("CAMPAIGN_IDENTIFIER"), env.get("IDENTIFIER_TYPE"))
            logger.info("  Report: %s", env.get("REPORT_NAME"))
            logger.info("  Frequency: %s", env.get("TRIGGER_FREQUENCY"))
            logger.info("  Date range: %s to %s",
                       env.get("START_DATE"),
                       env.get("END_DATE"))
            logger.info("  Output file: %s", env.get("OUTPUT_FILE"))
            logger.info("")

        logger.info("=" * 80)
        logger.info("All campaign reports have been generated and uploaded to FileStore")
        logger.info("=" * 80)

        return {
            "status": "completed",
            "count": len(env_list),
            "campaigns": summary
        }


    # ================================================================
    # DAG FLOW
    # ================================================================
    #
    # build_payload → campaign_pods (N pods in parallel) → print_summary
    #      ↓                ↓ ↓ ↓                              ↓
    #   [env1,         pod1 pod2 pod3                    log summary
    #    env2,          ↓    ↓    ↓
    #    env3]       report report report
    #
    # The campaign_pods task automatically waits for build_payload to complete
    # because it depends on the output (envs)
    #
    # print_summary receives the env_list and waits for all pods to complete
    envs >> campaign_pods >> print_summary(envs)
