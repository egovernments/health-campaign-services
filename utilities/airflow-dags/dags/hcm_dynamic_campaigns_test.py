"""
hcm_dynamic_campaigns_test.py

TEST DAG: Same flow as hcm_dynamic_campaigns but instead of spinning up
KubernetesPodOperator pods with REPORT_IMAGE, it runs a PythonOperator that:
1. Generates a sample CSV file with dummy data
2. Zips it
3. Uploads to FileStore
4. Publishes metadata to Kafka

Trigger manually with conf (same format as hcm_dynamic_campaigns):
{
  "matched_campaigns": [
    {
      "campaignIdentifier": "dbd45c31-de9e-4e62-a9b6-abb818928fd1",
      "identifierType": "projectTypeId",
      "reportName": "anomaly_report",
      "triggerFrequency": "DAILY",
      "triggerTime": "00:45:00+0530",
      "startDate": "02-01-2026 00:00:00+0530",
      "endDate": "30-01-2027 00:00:00+0530",
      "reportStartTime": "00:00:00+0530",
      "reportEndTime": "23:59:59+0530"
    }
  ]
}
"""

import os
import json
import logging
import tempfile
import zipfile
from datetime import datetime, timedelta, timezone

import requests

from airflow import DAG
from airflow.decorators import task

logger = logging.getLogger("airflow.task")
logger.setLevel(logging.INFO)

UTC = timezone.utc

# FileStore config from environment
FILE_STORE_URL = os.getenv("FILE_STORE_URL")
FILE_STORE_UPLOAD_FILE_ENDPOINT = os.getenv("FILE_STORE_UPLOAD_FILE_ENDPOINT", "filestore/v1/files")
TENANT_ID = os.getenv("TENANT_ID", "dev")
MODULE_NAME = os.getenv("FILE_STORE_MODULE_NAME", "custom-reports")

# Kafka config
KAFKA_BROKER = os.getenv("KAFKA_BROKER")
CUSTOM_REPORTS_AUTOMATION_TOPIC = os.getenv("CUSTOM_REPORTS_AUTOMATION_TOPIC", "save-hcm-report-metadata")


# -------------------------------------------------------
# Reuse date parsing helpers from hcm_dynamic_campaigns
# -------------------------------------------------------

def parse_time_with_timezone(time_str):
    time_part = time_str.strip()
    tz_offset_minutes = 0

    if '+' in time_part:
        t, tz = time_part.split('+')
        tz_sign = 1
    elif time_part.count('-') > 2:
        parts = time_part.rsplit('-', 1)
        t = parts[0]
        tz = parts[1] if len(parts) > 1 else '0000'
        tz_sign = -1
    else:
        t = time_part
        tz = None
        tz_sign = 0

    if tz:
        tz = tz.strip().replace(':', '')
        tz_hours = int(tz[:2]) if len(tz) >= 2 else 0
        tz_mins = int(tz[2:4]) if len(tz) >= 4 else 0
        tz_offset_minutes = tz_sign * (tz_hours * 60 + tz_mins)

    parts = t.split(":")
    hour = int(parts[0]) if len(parts) > 0 else 0
    minute = int(parts[1]) if len(parts) > 1 else 0
    second = int(parts[2]) if len(parts) > 2 else 0

    return (hour, minute, second, tz_offset_minutes)


def to_utc(date, hour, minute, second, tz_offset_minutes):
    local_dt = datetime(date.year, date.month, date.day, hour, minute, second, tzinfo=UTC)
    return local_dt - timedelta(minutes=tz_offset_minutes)


def parse_time_string(time_str):
    parts = time_str.strip().split(":")
    hour = int(parts[0]) if len(parts) > 0 else 0
    minute = int(parts[1]) if len(parts) > 1 else 0
    second = int(parts[2]) if len(parts) > 2 else 0
    return (hour, minute, second)


def should_report_today(campaign, now):
    report_end_time_str = campaign.get("reportEndTime", "23:59:59")
    trigger_time_str = campaign.get("triggerTime", "00:00:00")
    end_h, end_m, _ = parse_time_string(report_end_time_str.split("+")[0].split("-")[0])
    trig_h, trig_m, _ = parse_time_string(trigger_time_str.split("+")[0].split("-")[0])
    return (end_h * 60 + end_m) < (trig_h * 60 + trig_m)


def compute_daily_range(campaign, now):
    report_start_time_str = campaign.get("reportStartTime", "00:00:00")
    report_end_time_str = campaign.get("reportEndTime", "23:59:59")
    start_h, start_m, start_s, start_tz = parse_time_with_timezone(report_start_time_str)
    end_h, end_m, end_s, end_tz = parse_time_with_timezone(report_end_time_str)

    use_today = should_report_today(campaign, now)
    base_date = now.date() if use_today else (now - timedelta(days=1)).date()

    start = to_utc(base_date, start_h, start_m, start_s, start_tz)
    end = to_utc(base_date, end_h, end_m, end_s, end_tz)
    return (start, end)


def normalize_timestamp_to_utc(ts):
    import datetime as dt_mod
    ts = ts.strip()
    try:
        d = dt_mod.datetime.strptime(ts, "%d:%m:%Y %H:%M:%S%z")
    except ValueError:
        try:
            time_only = dt_mod.datetime.strptime(ts, "%H:%M:%S%z")
        except ValueError:
            raise ValueError("Input does not match expected timestamp formats.")
        today_utc = dt_mod.datetime.now(dt_mod.timezone.utc).date()
        d = dt_mod.datetime(
            year=today_utc.year, month=today_utc.month, day=today_utc.day,
            hour=time_only.hour, minute=time_only.minute, second=time_only.second,
            tzinfo=time_only.tzinfo,
        )
    return d.astimezone(dt_mod.timezone.utc).strftime("%Y-%m-%d %H:%M:%S")


# -------------------------------------------------------
# DAG
# -------------------------------------------------------

default_args = {
    "owner": "hcm-reports-team",
    "depends_on_past": False,
    "retries": 0,
    "retry_delay": timedelta(minutes=1),
}

with DAG(
    dag_id="hcm_dynamic_campaigns_test",
    default_args=default_args,
    description="TEST: generates sample CSV, uploads to FileStore, publishes to Kafka",
    schedule=None,
    start_date=datetime(2025, 1, 1, tzinfo=UTC),
    catchup=False,
    tags=["hcm", "reports", "test"],
) as dag:

    @task(task_id="build_payload")
    def build_payload(**context):
        """Read matched campaigns from conf and build payload (same as production DAG)."""
        matches = context["dag_run"].conf.get("matched_campaigns", [])
        if not matches:
            logger.warning("No matched campaigns in DAG run conf")
            return []

        now = datetime.now(UTC)
        dag_run_id = context["dag_run"].run_id
        dag_id = context["dag"].dag_id
        payload_list = []

        for idx, c in enumerate(matches, 1):
            campaign_id = c.get("campaignIdentifier")
            identifier_type = c.get("identifierType", "campaignNumber")
            report_name = c.get("reportName")
            frequency = c.get("triggerFrequency", "Daily")

            start_dt, end_dt = compute_daily_range(c, now)

            timestamp = now.strftime("%Y-%m-%d_%H-%M-%S")

            payload = {
                "CAMPAIGN_IDENTIFIER": campaign_id,
                "IDENTIFIER_TYPE": identifier_type,
                "REPORT_NAME": report_name,
                "TRIGGER_FREQUENCY": frequency,
                "TRIGGER_TIME": c.get("triggerTime", ""),
                "START_DATE": start_dt.strftime("%Y-%m-%d %H:%M:%S%z"),
                "END_DATE": end_dt.strftime("%Y-%m-%d %H:%M:%S%z"),
                "DAG_RUN_ID": dag_run_id,
                "DAG_ID": dag_id,
            }

            logger.info("Campaign %d: %s [%s] - %s (%s)", idx, campaign_id, identifier_type, report_name, frequency)
            logger.info("  Date range: %s to %s", payload["START_DATE"], payload["END_DATE"])
            payload_list.append(payload)

        logger.info("Payload built for %d campaigns", len(payload_list))
        return payload_list

    @task(task_id="process_campaign")
    def process_campaign(env_dict):
        """
        Replace KubernetesPodOperator: generate sample CSV, zip, upload to FileStore, publish to Kafka.
        Uses only standard library (no openpyxl needed).
        """
        import csv

        campaign_id = env_dict["CAMPAIGN_IDENTIFIER"]
        report_name = env_dict["REPORT_NAME"]
        frequency = env_dict["TRIGGER_FREQUENCY"]
        trigger_time = env_dict.get("TRIGGER_TIME", "")

        logger.info("=" * 60)
        logger.info("PROCESSING: %s - %s (%s)", campaign_id, report_name, frequency)
        logger.info("=" * 60)

        # ----- Step 1: Generate sample CSV report -----
        with tempfile.TemporaryDirectory() as tmpdir:
            report_dir = os.path.join(tmpdir, campaign_id, report_name, frequency)
            os.makedirs(report_dir, exist_ok=True)

            timestamp = datetime.now(UTC).strftime("%Y-%m-%d_%H-%M-%S")
            csv_filename = f"{report_name.upper()}_{timestamp}.csv"
            csv_path = os.path.join(report_dir, csv_filename)

            # Generate sample CSV
            headers = ["Province", "District", "UserName", "Registrations", "Deliveries", "Anomaly_Score"]
            sample_data = [
                ["Province A", "District 1", "user_001", "150", "120", "0.85"],
                ["Province A", "District 2", "user_002", "200", "180", "0.92"],
                ["Province B", "District 3", "user_003", "80", "75", "0.45"],
                ["Province B", "District 4", "user_004", "300", "50", "3.20"],
                ["Province C", "District 5", "user_005", "100", "95", "0.70"],
            ]
            with open(csv_path, "w", newline="") as f:
                writer = csv.writer(f)
                writer.writerow(headers)
                writer.writerows(sample_data)

            # Write metadata file
            meta_path = os.path.join(report_dir, "metadata.txt")
            with open(meta_path, "w") as f:
                f.write(f"Campaign: {campaign_id}\n")
                f.write(f"Report: {report_name}\n")
                f.write(f"Frequency: {frequency}\n")
                f.write(f"Start Date: {env_dict['START_DATE']}\n")
                f.write(f"End Date: {env_dict['END_DATE']}\n")
                f.write(f"Generated At: {datetime.now(UTC).isoformat()}\n")
                f.write(f"Mode: TEST (sample data)\n")

            logger.info("Generated sample CSV: %s", csv_path)

            # ----- Step 2: Create ZIP -----
            zip_filename = f"{report_name}_{campaign_id}_{timestamp}.zip"
            zip_path = os.path.join(tmpdir, zip_filename)

            with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
                for root, _, files in os.walk(report_dir):
                    for f in files:
                        fp = os.path.join(root, f)
                        arcname = os.path.relpath(fp, report_dir)
                        zf.write(fp, arcname)

            logger.info("Created ZIP: %s (%.1f KB)", zip_path, os.path.getsize(zip_path) / 1024)

            # ----- Step 3: Upload to FileStore -----
            if not FILE_STORE_URL:
                logger.error("FILE_STORE_URL not set - skipping upload")
                return {"status": "error", "reason": "FILE_STORE_URL not set"}

            upload_url = FILE_STORE_URL + FILE_STORE_UPLOAD_FILE_ENDPOINT
            logger.info("Uploading to FileStore: %s", upload_url)

            with open(zip_path, "rb") as f:
                files = {"file": (zip_filename, f, "application/zip")}
                data = {"tenantId": TENANT_ID, "module": MODULE_NAME}
                try:
                    resp = requests.post(upload_url, files=files, data=data,
                                         headers={"accept": "application/json, text/plain, */*"},
                                         timeout=60)
                except requests.exceptions.RequestException as e:
                    logger.error("FileStore upload failed: %s", e)
                    return {"status": "error", "reason": str(e)}

            if resp.status_code not in (200, 201, 202):
                logger.error("FileStore returned %d: %s", resp.status_code, resp.text)
                return {"status": "error", "reason": f"HTTP {resp.status_code}"}

            resp_json = resp.json()
            logger.info("FileStore response: %s", json.dumps(resp_json))

            # Extract fileStoreId
            file_store_id = None
            files_list = resp_json.get("files", [])
            if files_list:
                file_store_id = files_list[0].get("fileStoreId")

            if not file_store_id:
                logger.error("No fileStoreId in response: %s", resp_json)
                return {"status": "error", "reason": "No fileStoreId in response"}

            logger.info("FileStore ID: %s", file_store_id)

            # ----- Step 4: Publish metadata to Kafka -----
            if not KAFKA_BROKER:
                logger.warning("KAFKA_BROKER not set - skipping Kafka publish")
                return {
                    "status": "uploaded_no_kafka",
                    "fileStoreId": file_store_id,
                    "campaign": campaign_id,
                }

            metadata = {
                "dag_run_id": env_dict.get("DAG_RUN_ID", ""),
                "dag_name": env_dict.get("DAG_ID", ""),
                "campaign_identifier": campaign_id,
                "report_name": report_name,
                "trigger_frequency": frequency,
                "file_store_id": file_store_id,
                "trigger_time": normalize_timestamp_to_utc(trigger_time) if trigger_time else "",
                "tenant_id": TENANT_ID,
            }

            message = json.dumps(metadata)
            logger.info("Publishing to Kafka topic '%s': %s", CUSTOM_REPORTS_AUTOMATION_TOPIC, message)

            try:
                from confluent_kafka import Producer

                producer = Producer({
                    "bootstrap.servers": KAFKA_BROKER,
                    "client.id": "test-report-metadata-producer",
                })
                producer.produce(
                    topic=CUSTOM_REPORTS_AUTOMATION_TOPIC,
                    value=message.encode("utf-8"),
                )
                producer.poll(0)
                remaining = producer.flush(10)
                if remaining == 0:
                    logger.info("Kafka publish SUCCESS")
                else:
                    logger.warning("Kafka flush: %d messages still pending", remaining)
            except Exception as e:
                logger.error("Kafka publish failed: %s", e)
                return {
                    "status": "uploaded_kafka_failed",
                    "fileStoreId": file_store_id,
                    "error": str(e),
                }

            logger.info("=" * 60)
            logger.info("DONE: %s - fileStoreId=%s", campaign_id, file_store_id)
            logger.info("=" * 60)

            return {
                "status": "success",
                "campaign": campaign_id,
                "report": report_name,
                "fileStoreId": file_store_id,
            }

    @task(task_id="print_summary")
    def print_summary(results):
        """Log summary of all processed campaigns."""
        if not results:
            logger.info("No campaigns processed")
            return

        logger.info("=" * 60)
        logger.info("TEST SUMMARY: %d campaign(s)", len(results))
        logger.info("=" * 60)
        for r in results:
            logger.info("  %s - %s (fileStoreId: %s)",
                         r.get("campaign", "?"),
                         r.get("status", "?"),
                         r.get("fileStoreId", "N/A"))

    # DAG flow: build_payload → process_campaign (dynamic) → print_summary
    envs = build_payload()
    results = process_campaign.expand(env_dict=envs)
    print_summary(results)
