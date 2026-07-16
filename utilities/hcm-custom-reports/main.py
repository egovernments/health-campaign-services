import os
import subprocess
import sys
from pathlib import Path
import json
import datetime
import shutil
import glob
import requests
import zipfile
import uuid
import openpyxl
from confluent_kafka import Producer, KafkaException


# Read env vars
REPORT_NAME = os.getenv('REPORT_NAME')
# Campaign identifier: can be either campaignNumber or projectTypeId (UUID)
CAMPAIGN_IDENTIFIER = os.getenv('CAMPAIGN_IDENTIFIER')
# Identifier type: "campaignNumber" or "projectTypeId" - determines which ES field to query
IDENTIFIER_TYPE = os.getenv('IDENTIFIER_TYPE', 'campaignNumber')
START_DATE = os.getenv('START_DATE')
END_DATE = os.getenv('END_DATE')
# Use /tmp for temporary file storage - files are uploaded to FileStore and don't need to persist
OUTPUT_DIR = os.getenv('OUTPUT_DIR', '/tmp/reports')
TRIGGER_FREQUENCY = os.getenv('TRIGGER_FREQUENCY', 'DAILY')
TRIGGER_TIME = os.getenv("TRIGGER_TIME")

FILE_STORE_URL = os.getenv("FILE_STORE_URL")
FILE_STORE_UPLOAD_FILE_ENDPOINT = os.getenv("FILE_STORE_UPLOAD_FILE_ENDPOINT", "filestore/v1/files")
TENANT_ID = os.getenv("TENANT_ID", "dev")
MODULE_NAME = os.getenv("FILE_STORE_MODULE_NAME", "custom-reports")

DAG_RUN_ID = os.getenv("DAG_RUN_ID")
DAG_ID = os.getenv("DAG_ID")

# Actual moment this run was triggered (distinct from TRIGGER_TIME, which is just the
# configured time-of-day) - stamped once upstream (scheduler, or build_payload's own
# dag_run.start_date for direct/custom triggers) and passed through unchanged so it reads
# identically across every status row for this run.
REPORT_TRIGGERED_TIME_MS = os.getenv("REPORT_TRIGGERED_TIME_MS")
REPORT_TRIGGERED_TIME = os.getenv("REPORT_TRIGGERED_TIME")
try:
    REPORT_TRIGGERED_TIME_MS = int(REPORT_TRIGGERED_TIME_MS) if REPORT_TRIGGERED_TIME_MS else None
except ValueError:
    REPORT_TRIGGERED_TIME_MS = None

# Estimates computed once by airflow-trigger-service's trigger_dag (UI-originated CUSTOM
# runs only) - threaded through env vars unchanged, same pattern as REPORT_TRIGGERED_TIME_MS.
EXPECTED_ROWS = os.getenv("EXPECTED_ROWS")
try:
    EXPECTED_ROWS = int(EXPECTED_ROWS) if EXPECTED_ROWS else None
except ValueError:
    EXPECTED_ROWS = None

EXPECTED_GENERATION_TIME_SECONDS = os.getenv("EXPECTED_GENERATION_TIME_SECONDS")
try:
    EXPECTED_GENERATION_TIME_SECONDS = float(EXPECTED_GENERATION_TIME_SECONDS) if EXPECTED_GENERATION_TIME_SECONDS else None
except ValueError:
    EXPECTED_GENERATION_TIME_SECONDS = None

CUSTOM_REPORTS_AUTOMATION_TOPIC = os.getenv("CUSTOM_REPORTS_AUTOMATION_TOPIC", "save-hcm-report-metadata")
KAFKA_BROKER = os.getenv("KAFKA_BROKER")
IS_CENTRAL_INSTANCE_ENABLED = os.getenv("IS_CENTRAL_INSTANCE_ENABLED", "false").lower() == "true"

PRODUCER_CONFIG = {
    "bootstrap.servers": KAFKA_BROKER,
    'client.id': 'custom-report-metadata-producer',
    "debug": "broker,topic,msg",
}

# Pipeline position per status - lets a status consumer compute "current state" correctly
# even if events are consumed out of order (a later/more-terminal status always wins).
STATUS_ORDER = {
    "SCHEDULED": 10,
    "TRIGGERED": 20,
    "SKIPPED": 20,
    "POD_STARTED": 30,
    "REPORT_GENERATION_STARTED": 31,
    "ZIP_STARTED": 32,
    "FILESTORE_UPLOAD_STARTED": 33,
    "POD_INFRA_FAILED": 40,
    "ENV_VALIDATION_FAILED": 40,
    "REPORT_GENERATION_FAILED": 40,
    "OUTPUT_NOT_FOUND_FAILED": 40,
    "ZIP_FAILED": 40,
    "FILESTORE_UPLOAD_FAILED": 40,
    "REPORT_COMPLETED": 40,
}

# Producer is built unconditionally (needs only KAFKA_BROKER) so that even a missing
# required-env-var failure below can be reported via push_report_status.
producer = Producer(PRODUCER_CONFIG)
report_duration_seconds = None

def send_to_kafka(producer, topic, message, flush_timeout=10):
    print(f"[KAFKA] Broker config: {PRODUCER_CONFIG}")
    print(f"[KAFKA] Using broker: {KAFKA_BROKER!r}")
    try:
        print(f"[KAFKA] Trying to push data to Kafka topic: {topic}")
        print(f"[KAFKA] Message preview: {message[:200]}")  # avoid huge logs

        # Make sure we're sending bytes
        if isinstance(message, str):
            value = message.encode("utf-8")
        else:
            value = message

        print("[KAFKA] Calling producer.produce()")
        producer.produce(topic=topic, value=value)

        # Serve delivery callbacks (even if we don't use them yet)
        producer.poll(0)
        print("[KAFKA] Called poll(0), now flushing...")

        remaining = producer.flush(flush_timeout)
        print(f"[KAFKA] flush() returned, remaining messages in queue: {remaining}")

        if remaining == 0:
            print(f"[KAFKA] ✅ Successfully delivered message to Kafka topic: {topic}")
        else:
            print(
                f"[KAFKA] ⚠ Warning: {remaining} message(s) still undelivered "
                f"after flush() for topic {topic}"
            )

    except KafkaException as e:
        print(f"[KAFKA] ❌ KafkaException while producing to topic {topic}: {e}")
    except BufferError as e:
        print(f"[KAFKA] ❌ Local producer queue is full for topic {topic}: {e}")
    except Exception as e:
        print(f"[KAFKA] ❌ Unexpected error while pushing to topic {topic}: {e}")

def _topic_for(base_topic):
    """Apply the same tenant-prefixing convention used for every Kafka topic here."""
    return f"{TENANT_ID}-{base_topic}" if IS_CENTRAL_INSTANCE_ENABLED and TENANT_ID else base_topic

def count_xlsx_rows(file_path):
    """
    Best-effort data-row count (excludes the header row) of an xlsx file's active
    worksheet. Report scripts vary (different report types, occasionally multiple
    sheets) so this is a rough figure, not an exact cross-report guarantee.
    Never raises - returns None on any failure so it can never block status reporting.
    """
    try:
        wb = openpyxl.load_workbook(file_path, read_only=True)
        ws = wb.active
        count = max(0, (ws.max_row or 1) - 1)
        wb.close()
        return count
    except Exception as e:
        print(f"[WARN] Failed to count rows in {file_path}: {e}")
        return None

def build_status_event(status, file_store_id=None, file_size_bytes=None, row_count=None, error=None):
    """The one payload shape, sent to CUSTOM_REPORTS_AUTOMATION_TOPIC for every status -
    REPORTS_METADATA is now append-only (one row per status event, not just terminal ones)."""
    now_dt = datetime.datetime.now(datetime.timezone.utc)
    timestamp_ms = int(now_dt.timestamp() * 1000)
    # How long after trigger this specific event happened - gives a per-stage timeline
    # (e.g. POD_STARTED at +52s, REPORT_COMPLETED at +230s) for every row, not just
    # completed runs. Mirrors kafka_status.py's push_status_event exactly.
    seconds_since_triggered = (
        round((timestamp_ms - REPORT_TRIGGERED_TIME_MS) / 1000, 2)
        if REPORT_TRIGGERED_TIME_MS is not None else None
    )
    return {
        "event_id": str(uuid.uuid4()),
        "tenant_id": TENANT_ID,
        "campaign_identifier": CAMPAIGN_IDENTIFIER,
        "identifier_type": IDENTIFIER_TYPE,
        "report_name": REPORT_NAME,
        "trigger_frequency": TRIGGER_FREQUENCY,
        # Raw passthrough - matches kafka_status.py's DAG-side events, so this field reads
        # identically across every status row for the same report run regardless of which
        # producer sent it.
        "trigger_time": TRIGGER_TIME,
        "report_triggered_time_ms": REPORT_TRIGGERED_TIME_MS,
        "report_triggered_time": REPORT_TRIGGERED_TIME,
        "expected_rows": EXPECTED_ROWS,
        "expected_generation_time_seconds": EXPECTED_GENERATION_TIME_SECONDS,
        "seconds_since_triggered": seconds_since_triggered,
        "dag_run_id": DAG_RUN_ID,
        "dag_name": DAG_ID,
        "status": status,
        "status_order": STATUS_ORDER.get(status, 0),
        "error_message": (error or {}).get("message"),
        "error_type": (error or {}).get("type"),
        "file_store_id": file_store_id,
        "file_size_bytes": file_size_bytes,
        "row_count": row_count,
        "report_dates": f"{START_DATE}_{END_DATE}",
        "report_generation_time_seconds": report_duration_seconds,
        # Epoch millis is the value actually bound into the DB (plain number -> BIGINT,
        # no JDBC string-to-timestamp cast risk); the ISO string is kept alongside it
        # purely for human-readable display/debugging.
        "timestamp_ms": timestamp_ms,
        "timestamp": now_dt.isoformat(),
    }

def push_report_status(status, file_store_id="", message=None, exc=None, file_size_bytes=None, row_count=None):
    error = None
    if message or exc:
        error = {"message": message or str(exc)}
        if exc:
            error["type"] = type(exc).__name__

    status_event = build_status_event(
        status, file_store_id=file_store_id or None, file_size_bytes=file_size_bytes, row_count=row_count, error=error
    )
    send_to_kafka(producer=producer, topic=_topic_for(CUSTOM_REPORTS_AUTOMATION_TOPIC), message=json.dumps(status_event))

def get_custom_dates_of_reports():

    start_date_str = START_DATE
    end_date_str = END_DATE
    date_format = '%Y-%m-%d %H:%M:%S%z'

    start_date = datetime.datetime.strptime(start_date_str, date_format)
    end_date = datetime.datetime.strptime(end_date_str, date_format)

    printable_start = start_date.strftime("%Y-%m-%d")
    printable_end = end_date.strftime("%Y-%m-%d")

    print("Reports start date:", start_date)
    print("Reports end date:", end_date)

    return printable_start, printable_end

def create_zip_of_reports(folder_path, zip_name):
    """
    Creates a ZIP file containing all files inside folder_path.
    Returns full path of created zip.
    """
    print(f"[DEBUG] Starting Zip: Folder Path - {folder_path}, Zip Name - {zip_name}")

    zip_path = os.path.join(folder_path, zip_name)
    print(f"[DEBUG] Zip Path: {zip_path}")
    try:
        with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_STORED) as zipf:
            for root, _, files in os.walk(folder_path):
                for f in files:
                    if f.endswith(".zip"):
                        continue
                    file_path = os.path.join(root, f)
                    arcname = os.path.relpath(file_path, folder_path)
                    zipf.write(file_path, arcname)
    except Exception as e:
        print(f"❌ Error creating ZIP file: {e}")
        push_report_status("ZIP_FAILED", exc=e)
        sys.exit(3)
    print(f"📦 Created ZIP: {zip_path}")
    return zip_path


def upload_to_filestore(file_path, mime_type="application/zip"):
    """
    Uploads the given file to FileStore service using multipart/form-data.
    mime_type defaults to application/zip; pass the xlsx MIME type for direct xlsx upload.
    """
    if not FILE_STORE_URL:
        raise RuntimeError("FILE_STORE_URL is not set")

    url = FILE_STORE_URL + FILE_STORE_UPLOAD_FILE_ENDPOINT

    headers = {
        "accept": "application/json, text/plain, */*"
    }

    data = {
        "tenantId": TENANT_ID,
        "module": MODULE_NAME,
    }

    print(f"📤 Uploading to FileStore: {url} [{mime_type}]")
    print(f"[DEBUG] tenantId={TENANT_ID}, module={MODULE_NAME}")

    with open(file_path, "rb") as f:
        files = {
            "file": (os.path.basename(file_path), f, mime_type)
        }

        try:
            response = requests.post(url, headers=headers, files=files, data=data, timeout=60)
        except requests.exceptions.RequestException as e:
            print(f"❌ Upload failed (network error): {e}")
            return {"error": str(e)}

    print(f"[DEBUG] Upload status code: {response.status_code}")
    try:
        resp_json = response.json()
    except ValueError:
        resp_json = {"raw": response.text}

    if response.status_code in [200, 201, 202]:
        print("✅ Upload successful:", resp_json)
    else:
        print(f"❌ Upload failed with status code: {response.status_code}, and response {resp_json}")

    return resp_json

def save_file_to_folder(file):
    _, extension = os.path.splitext(file)

    # Generate timestamped filename
    timestamp = datetime.datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
    new_file_name = f"{REPORT_FILE_NAME}_{timestamp}{extension}"

    # Build folder path: OUTPUT_DIR/CAMPAIGN_IDENTIFIER/REPORT_NAME/TRIGGER_FREQUENCY/
    folder_path = os.path.join(
        OUTPUT_DIR,
        CAMPAIGN_IDENTIFIER,
        REPORT_NAME,
        TRIGGER_FREQUENCY
    )

    print(f"[DEBUG] Folder Path: {folder_path}")
    print(f"[DEBUG] Parent Exists: {os.path.exists(os.path.dirname(folder_path))}")
    print(f"[DEBUG] Is OUTPUT_DIR Writable: {os.access(OUTPUT_DIR, os.W_OK)}")
    print(f"[DEBUG] Is Parent Writable: {os.access(os.path.dirname(folder_path), os.W_OK)}")


    os.makedirs(folder_path, exist_ok=True)
    destination = os.path.join(folder_path, new_file_name)
    shutil.move(file, destination)
    print(f"✅ File saved to: {destination}")
    return destination

# Required-env-var validation happens here (after push_report_status is defined) so a
# failure can be reported via Kafka instead of crashing unreported.
if not REPORT_NAME or not CAMPAIGN_IDENTIFIER:
    print('REPORT_NAME and CAMPAIGN_IDENTIFIER are required environment variables')
    push_report_status("ENV_VALIDATION_FAILED", message="REPORT_NAME and CAMPAIGN_IDENTIFIER are required environment variables")
    sys.exit(1)

REPORT_FILE_NAME = REPORT_NAME.upper()
push_report_status("POD_STARTED")

try:
    start_date_str, end_date_str = get_custom_dates_of_reports()
except Exception as e:
    print(f"Error parsing START_DATE/END_DATE: {e}")
    push_report_status("ENV_VALIDATION_FAILED", exc=e)
    sys.exit(1)

original_dir = os.getcwd()
# input_folder = reports_config["input"]
# scripts = reports_config["scripts"]

report_generation_failed = False
try:
    print("\n")
    print(f"===== Generating report : {REPORT_NAME}")

    script_path = os.path.join(
        original_dir,
        "REPORTS_GENERATION",
        "REPORTS",
        REPORT_NAME,
        f"{REPORT_NAME}.py"
    )
    # Use venv python if available (local), otherwise use system python (Docker)
    venv_python = os.path.join(original_dir, 'venv', 'bin', 'python3')
    python_executable = venv_python if os.path.exists(venv_python) else 'python3'

    cmd = [python_executable, script_path,
    '--campaign_identifier', CAMPAIGN_IDENTIFIER,
    '--identifier_type', IDENTIFIER_TYPE,
    '--start_date', START_DATE or '',
    '--end_date', END_DATE or '',
    '--file_name', REPORT_FILE_NAME]

    print('Running command:', ' '.join(cmd))

    # Change working directory to PVC mount (OUTPUT_DIR)
    print(f"[DEBUG] Changing working directory to OUTPUT_DIR: {OUTPUT_DIR}")
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    os.chdir(OUTPUT_DIR)

    push_report_status("REPORT_GENERATION_STARTED")
    report_start_time = datetime.datetime.now(datetime.timezone.utc)
    subprocess.run(cmd, check=True)
    report_end_time = datetime.datetime.now(datetime.timezone.utc)
    report_duration_seconds = round((report_end_time - report_start_time).total_seconds(), 2)

    print(f"Executed {REPORT_NAME} in {report_duration_seconds}s")

except Exception as e:
    print(f"Error: {e}")
    push_report_status("REPORT_GENERATION_FAILED", exc=e)
    report_generation_failed = True
finally:
    # Only attempt to move/zip/upload output if the report script actually ran -
    # otherwise there's nothing valid to package, and doing so risked masking the
    # real REPORT_GENERATION_FAILED with a later OUTPUT_NOT_FOUND_FAILED/REPORT_COMPLETED.
    if not report_generation_failed:
        file_name_substring = REPORT_FILE_NAME
        move_file = True
        saved_files = []
        if move_file:
            matching_files = glob.glob(f"*{file_name_substring}*")  # Case-sensitive

            if matching_files:
                for file_name in matching_files:
                    if os.path.exists(file_name):
                        saved_path = save_file_to_folder(file_name)
                        saved_files.append(saved_path)
                        print(f"Moved file: {file_name}")
                    else:
                        print(f"⚠ File not found, skipping: {file_name}")
            else:
                print(f"⚠ No files found containing substring: {file_name_substring}")
                push_report_status("OUTPUT_NOT_FOUND_FAILED", message=f"No output files found for report: {file_name_substring}")
                report_generation_failed = True

        if not report_generation_failed:
            reports_folder = os.path.join(
                OUTPUT_DIR,
                CAMPAIGN_IDENTIFIER,
                REPORT_NAME,
                TRIGGER_FREQUENCY
            )

            print(f"[DEBUG] Preparing to zip folder: {reports_folder}")
            print(f"[DEBUG] Folder exists: {os.path.exists(reports_folder)}")

            timestamp = datetime.datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
            zip_name = f"{REPORT_FILE_NAME}_{CAMPAIGN_IDENTIFIER}_{timestamp}.zip"
            print(f"[DEBUG] Zip Name: {zip_name}")

            push_report_status("ZIP_STARTED")
            zip_path = create_zip_of_reports(reports_folder, zip_name)

            print(f"[DEBUG] Zip Path: {zip_path}")
            # --------------------------------
            #  UPLOAD ZIP TO FILESTORE SERVICE
            # --------------------------------
            try:
                push_report_status("FILESTORE_UPLOAD_STARTED")
                upload_response = upload_to_filestore(zip_path)
                print(f"[DEBUG] FileStore response: {upload_response}")

                if "files" in upload_response and upload_response.get("files"):
                    file_store_id = upload_response["files"][0].get("fileStoreId")
                    file_size_bytes = os.path.getsize(zip_path)
                    xlsx_files = [f for f in saved_files if f.lower().endswith(".xlsx")]
                    row_count = sum((count_xlsx_rows(f) or 0) for f in xlsx_files) if xlsx_files else None
                    push_report_status(
                        "REPORT_COMPLETED", file_store_id=file_store_id,
                        file_size_bytes=file_size_bytes, row_count=row_count
                    )
                else:
                    push_report_status("FILESTORE_UPLOAD_FAILED", message=f"FileStore upload failed: {upload_response}")

            except Exception as e:
                print(f"❌ Exception while uploading to FileStore: {e}")
                push_report_status("FILESTORE_UPLOAD_FAILED", message="FileStore upload exception", exc=e)

if report_generation_failed:
    sys.exit(1)

