import os
import subprocess
import json
import datetime
import shutil
import time
from openpyxl import load_workbook

def get_custom_dates_of_reports():
    with open("reports_date_config.json") as f:
        reports_date_config = json.load(f)

    start_date_str = reports_date_config['start_date']
    end_date_str = reports_date_config['end_date']
    date_format = '%Y-%m-%d %H:%M:%S%z'

    start_date = datetime.datetime.strptime(start_date_str, date_format)
    end_date = datetime.datetime.strptime(end_date_str, date_format)

    printable_start = start_date.strftime("%Y-%m-%d")
    printable_end = end_date.strftime("%Y-%m-%d")

    print("Reports start date:", start_date)
    print("Reports end date:", end_date)

    return printable_start, printable_end

start_date_str, end_date_str = get_custom_dates_of_reports()

def remove_empty_sheets(file_path):
    workbook = load_workbook(file_path)
    if "Sheet" in workbook.sheetnames:
        del workbook["Sheet"]
        workbook.save(file_path)

def save_file_to_folder(file):
    file_name, extension = os.path.splitext(file)
    new_file_name = f"{file_name}_{start_date_str}_TO_{end_date_str}{extension}"
    folder_path = os.path.join("FINAL_REPORTS", f"{start_date_str}_TO_{end_date_str}")
    os.makedirs(folder_path, exist_ok=True)
    shutil.move(file, os.path.join(folder_path, os.path.basename(new_file_name)))

def today_date():
    current_datetime = datetime.datetime.now()
    return current_datetime.strftime("%d_%b").upper()

config_file = open("reports_config.json")
config = json.load(config_file)
start_time = time.time()

for report, reports_config in config.items():
    original_dir = os.getcwd()
    input_folder = reports_config["input"]
    scripts = reports_config["scripts"]

    try:
        print("\n")
        print(f"===== Generating report : {report}")
        for script in scripts:
            script_path = f"{input_folder}{script}"
            venv_python = os.path.join(original_dir, 'venv', 'bin', 'python3')
            subprocess.run([venv_python, original_dir + script_path], check=True)
            print(f"Executed {script}")
    except Exception as e:
        print(f"Error: {e}")
    finally:
        file_names = reports_config.get("filename", [])
        move_file = reports_config.get("moveReport", True)
        if move_file:
            for file_name in file_names:
                if os.path.exists(file_name):
                    if reports_config.get("removeSheet", True):
                        remove_empty_sheets(file_name)
                    save_file_to_folder(file_name)
                else:
                    print(f"⚠ File not found, skipping: {file_name}")

