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
REPORT_FILE_NAME = REPORT_NAME.upper()

FILE_STORE_URL = os.getenv("FILE_STORE_URL") 
FILE_STORE_UPLOAD_FILE_ENDPOINT = os.getenv("FILE_STORE_UPLOAD_FILE_ENDPOINT", "filestore/v1/files")
TENANT_ID = os.getenv("TENANT_ID", "dev")
MODULE_NAME = os.getenv("FILE_STORE_MODULE_NAME", "custom-reports")

DAG_RUN_ID = os.getenv("DAG_RUN_ID")
DAG_ID = os.getenv("DAG_ID")

CUSTOM_REPORTS_AUTOMATION_TOPIC = os.getenv("CUSTOM_REPORTS_AUTOMATION_TOPIC", "save-hcm-report-metadata")
KAFKA_BROKER = os.getenv("KAFKA_BROKER")
IS_CENTRAL_INSTANCE_ENABLED = os.getenv("IS_CENTRAL_INSTANCE_ENABLED", "false").lower() == "true"

PRODUCER_CONFIG = {
    "bootstrap.servers": KAFKA_BROKER,
    'client.id': 'custom-report-metadata-producer',
    "debug": "broker,topic,msg",
}


if not REPORT_NAME or not CAMPAIGN_IDENTIFIER:
    print('REPORT_NAME and CAMPAIGN_IDENTIFIER are required environment variables')
    sys.exit(1)

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

def get_data_to_be_pushed(file_store_id, report_duration_seconds=None, status="SUCCESS", error=None):
    data = {
        "dag_run_id" : DAG_RUN_ID,
        "dag_name" : DAG_ID,
        "campaign_identifier" : CAMPAIGN_IDENTIFIER,
        "report_name" : REPORT_NAME,
        "trigger_frequency" : TRIGGER_FREQUENCY,
        "file_store_id" : file_store_id,
        "trigger_time" : normalize_timestamp_to_utc(TRIGGER_TIME),
        "tenant_id" : TENANT_ID,
        "report_dates" : str(START_DATE) + "_" + str(END_DATE),
        "report_generation_time_seconds" : report_duration_seconds,
        "status" : status,
    }
    if error:
        data["error"] = error
    return data

def push_report_status(status, file_store_id="", message=None, exc=None):
    error = None
    if message or exc:
        error = {"message": message or str(exc)}
        if exc:
            error["type"] = type(exc).__name__
    kafka_topic = f"{TENANT_ID}-{CUSTOM_REPORTS_AUTOMATION_TOPIC}" if IS_CENTRAL_INSTANCE_ENABLED and TENANT_ID else CUSTOM_REPORTS_AUTOMATION_TOPIC
    data = get_data_to_be_pushed(file_store_id, report_duration_seconds, status=status, error=error)
    send_to_kafka(producer=producer, topic=kafka_topic, message=json.dumps(data))

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

start_date_str, end_date_str = get_custom_dates_of_reports()

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

def normalize_timestamp_to_utc(ts: str) -> str:
    """
    Normalize a timestamp string to UTC in the format:
        DD:MM:YYYY HH:MM:SS+0000

    Accepted input formats:
    - "DD:MM:YYYY HH:MM:SS+ZZZZ"  (any numeric UTC offset)
    - "HH:MM:SS+ZZZZ"             (date will be filled with today's UTC date)

    The function:
    - Parses the input with its given offset
    - Converts it to UTC
    - Returns as "DD:MM:YYYY HH:MM:SS+0000"
    """
    ts = ts.strip()

    # Try full format: DD:MM:YYYY HH:MM:SS+ZZZZ
    try:
        dt = datetime.datetime.strptime(ts, "%d:%m:%Y %H:%M:%S%z")
    except ValueError:
        # If that fails, try time-only: HH:MM:SS+ZZZZ
        try:
            # Validate and parse time+offset
            time_only_dt = datetime.datetime.strptime(ts, "%H:%M:%S%z")
        except ValueError:
            raise ValueError("Input does not match expected timestamp formats.")

        # Use today's UTC date, combined with the parsed time and original offset
        today_utc = datetime.datetime.now(datetime.timezone.utc).date()
        dt = datetime.datetime(
            year=today_utc.year,
            month=today_utc.month,
            day=today_utc.day,
            hour=time_only_dt.hour,
            minute=time_only_dt.minute,
            second=time_only_dt.second,
            tzinfo=time_only_dt.tzinfo,
        )

    # Convert to UTC
    dt_utc = dt.astimezone(datetime.timezone.utc)

    # Format as DD:MM:YYYY HH:MM:SS+0000
    return dt_utc.strftime("%Y-%m-%d %H:%M:%S")

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

original_dir = os.getcwd()
# input_folder = reports_config["input"]
# scripts = reports_config["scripts"]


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

    report_start_time = datetime.datetime.now(datetime.timezone.utc)
    subprocess.run(cmd, check=True)
    report_end_time = datetime.datetime.now(datetime.timezone.utc)
    report_duration_seconds = round((report_end_time - report_start_time).total_seconds(), 2)

    print(f"Executed {REPORT_NAME} in {report_duration_seconds}s")

except Exception as e:
    print(f"Error: {e}")
    push_report_status("FAILED", exc=e)
    sys.exit(1)
finally:
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
            push_report_status("FAILED", message=f"No output files found for report: {file_name_substring}")
            sys.exit(2)


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
# ===========================
    print(f"[DEBUG] Zip Name: {zip_name}")

    zip_path = create_zip_of_reports(reports_folder, zip_name)
    
    print(f"[DEBUG] Zip Path: {zip_path}")
# ============================
    # --------------------------------
    #  UPLOAD ZIP TO FILESTORE SERVICE
    # --------------------------------
    try:
        upload_response = upload_to_filestore(zip_path)
        print(f"[DEBUG] FileStore response: {upload_response}")

        file_store_id = ""

        if "files" in upload_response:
            res = upload_response.get("files", [])
            if len(res) > 0:
                file_store_id = res[0].get("fileStoreId")
                push_report_status("SUCCESS", file_store_id=file_store_id)
        else:
            push_report_status("FAILED", message=f"FileStore upload failed: {upload_response}")

    except Exception as e:
        print(f"❌ Exception while uploading to FileStore: {e}")
        push_report_status("FAILED", message="FileStore upload exception", exc=e)

    