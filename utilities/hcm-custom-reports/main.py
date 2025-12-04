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

PRODUCER_CONFIG = {
    "bootstrap.servers": KAFKA_BROKER,
    'client.id': 'custom-report-metadata-producer',
}


if not REPORT_NAME or not CAMPAIGN_IDENTIFIER:
    print('REPORT_NAME and CAMPAIGN_IDENTIFIER are required environment variables')
    sys.exit(1)

producer = Producer(PRODUCER_CONFIG)

def send_to_kafka(producer, topic, message):
    try:
        print(f"Trying to push data to Kafka topic: {topic}")
        producer.produce(topic, message)
        print(f"Pushed data to Kafka topic: {topic}")
    except KafkaException as e:
        print(f"Failed to produce message: {e} in topic {topic}")
    except Exception as e:
        print(f"An error occurred: {e} while trying to push data to topic {topic}")

def get_data_to_be_pushed(file_store_id):
    data = {
        "dag_run_id" : DAG_RUN_ID,
        "dag_name" : DAG_ID,
        "campaign_identifier" : CAMPAIGN_IDENTIFIER,
        "report_name" : REPORT_NAME,
        "trigger_frequency" : TRIGGER_FREQUENCY,
        "file_store_id" : file_store_id,
        "trigger_time" : normalize_timestamp_to_utc(TRIGGER_TIME),
        "tenant_id" : TENANT_ID
    }

    return data

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

    zip_path = os.path.join(folder_path, zip_name)

    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zipf:
        for root, _, files in os.walk(folder_path):
            for f in files:
                file_path = os.path.join(root, f)
                arcname = os.path.relpath(file_path, folder_path)
                zipf.write(file_path, arcname)

    print(f"📦 Created ZIP: {zip_path}")
    return zip_path


def upload_to_filestore(file_path):
    """
    Uploads the given file to FileStore service using multipart/form-data.
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

    print(f"📤 Uploading ZIP to FileStore: {url}")
    print(f"[DEBUG] tenantId={TENANT_ID}, module={MODULE_NAME}")

    # Use context manager to ensure file handle closes
    with open(file_path, "rb") as f:
        files = {
            "file": (os.path.basename(file_path), f, "application/zip")
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
    return dt_utc.strftime("%d:%m:%Y %H:%M:%S%z")

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

    subprocess.run(cmd, check=True)

    print(f"Executed {REPORT_NAME}")

except Exception as e:
    print(f"Error: {e}")
    sys.exit(1)
finally:
    file_name_substring = REPORT_FILE_NAME
    move_file = True
    if move_file:
        matching_files = glob.glob(f"*{file_name_substring}*")  # Case-sensitive
        
        if matching_files:
            for file_name in matching_files:
                if os.path.exists(file_name):
                    save_file_to_folder(file_name)
                    print(f"Moved file: {file_name}")
                else:
                    print(f"⚠ File not found, skipping: {file_name}")
        else:
            print(f"⚠ No files found containing substring: {file_name_substring}")
            sys.exit(2)


    reports_folder = os.path.join(
        OUTPUT_DIR,
        CAMPAIGN_IDENTIFIER,
        REPORT_NAME,
        TRIGGER_FREQUENCY
    )

    timestamp = datetime.datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
    zip_name = f"{REPORT_FILE_NAME}_{CAMPAIGN_IDENTIFIER}_{timestamp}.zip"

    zip_path = create_zip_of_reports(reports_folder, zip_name)

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
                data = get_data_to_be_pushed(file_store_id)

                data_object = json.dumps(data)
                send_to_kafka(producer=producer, topic=CUSTOM_REPORTS_AUTOMATION_TOPIC, message=data_object)
        else:
            print(f"Error response : {upload_response}")



    except Exception as e:
        print(f"❌ Exception while uploading to FileStore: {e}")

    