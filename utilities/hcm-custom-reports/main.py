import os
import subprocess
import sys
from pathlib import Path
import json
import datetime
import shutil
import glob


# Read env vars
REPORT_NAME = os.getenv('REPORT_NAME')
CAMPAIGN_NUMBER = os.getenv('CAMPAIGN_NUMBER')
START_DATE = os.getenv('START_DATE')
END_DATE = os.getenv('END_DATE')
OUTPUT_PVC_NAME = os.getenv('OUTPUT_PVC_NAME', 'hcm-reports-output')
OUTPUT_DIR = os.getenv('OUTPUT_DIR', '/app/FINAL_REPORTS')
TRIGGER_FREQUENCY = os.getenv('TRIGGER_FREQUENCY', 'DAILY')
REPORT_FILE_NAME = REPORT_NAME.upper()


if not REPORT_NAME or not CAMPAIGN_NUMBER:
    print('REPORT_TYPE and CAMPAIGN_NUMBER are required environment variables')
    sys.exit(1)


# # Script path (mounted from report scripts volume)
# script_path = Path('/app/reports') / REPORT_NAME / f"{REPORT_NAME}.py"
# if not script_path.exists():
#     print(f"Report script not found: {script_path}")
#     sys.exit(2)


# # Prepare output dir (mounted PVC expected at /app/REPORTS_GENERATION/FINAL_REPORTS)
# output_base = Path('/app/REPORTS_GENERATION/FINAL_REPORTS')
# campaign_dir = output_base / f"campaign-{CAMPAIGN_NUMBER}"
# campaign_dir.mkdir(parents=True, exist_ok=True)


# # Build command
# cmd = [sys.executable, str(script_path),
#     '--campaign', CAMPAIGN_NUMBER,
#     '--start', START_DATE or '',
#     '--end', END_DATE or '',
#     '--output', str(campaign_dir)]


# print('Running command:', ' '.join(cmd))
# ret = subprocess.run(cmd)
# if ret.returncode != 0:
#     print('Report script failed with code', ret.returncode)
#     sys.exit(ret.returncode)


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

def save_file_to_folder(file):
    _, extension = os.path.splitext(file)

    # Generate timestamped filename
    timestamp = datetime.datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
    new_file_name = f"{REPORT_FILE_NAME}_{timestamp}{extension}"

    # Build folder path: OUTPUT_DIR/CAMPAIGN_NUMBER/REPORT_NAME/TRIGGER_FREQUENCY/
    folder_path = os.path.join(
        OUTPUT_DIR,
        CAMPAIGN_NUMBER,
        REPORT_NAME,
        TRIGGER_FREQUENCY
    )

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
    '--campaign_number', CAMPAIGN_NUMBER,
    '--start_date', START_DATE or '',
    '--end_date', END_DATE or '',
    '--file_name', REPORT_FILE_NAME]

    print('Running command:', ' '.join(cmd))

    subprocess.run(cmd, check=True)

    print(f"Executed {REPORT_NAME}")

except Exception as e:
    print(f"Error: {e}")
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
    # if move_file:
    #     # for file_name in file_names:
    #     if os.path.exists(file_name):
    #         save_file_to_folder(file_name)
    #     else:
    #         print(f"⚠ File not found, skipping: {file_name}")

    