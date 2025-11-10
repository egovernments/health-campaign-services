import time
import requests
import json
import datetime
from pytz import timezone
import pandas as pd
import shutil
import os
from decouple import config

PATH_TO_QUERIES_JSON = config('PATH_TO_QUERIES_JSON')
PATH_TO_FINAL_REPORTS = config('PATH_TO_FINAL_REPORTS')
max_retries = 30
retry_delay = 5


def get_resp(url, data, es=False):
    # ZWxhc3RpYzpHZDE5OWZubzkyV29pUW52RkVOVzNLUWo=
    failed = False
    for attempt in range(max_retries):
        if failed:
            print(f"Retry {attempt}/{max_retries}")
        try:
            print(url, data)
            if es:
                response = requests.post(
                    url,
                    data=json.dumps(data),
                    headers={"Content-Type": "application/json", "Authorization": "Basic ZWxhc3RpYzpaRFJsT0RJME1UQTNNV1ppTVRGbFptRms="},
                    verify=False,
                    timeout=30
                )
            else:
                response = requests.post(
                    url,
                    data=json.dumps(data),
                    headers={"Content-Type": "application/json"},
                    timeout=30
                )

            print(response)
            if response.status_code == 200:
                print(response)
                return response
            else:
                print(f"HTTP {response.status_code} received. Retrying in {retry_delay} seconds...")
                failed = True
        except (requests.exceptions.ConnectionError,
                requests.exceptions.Timeout,
                requests.exceptions.ReadTimeout,
                requests.exceptions.RequestException) as e:
            print(f"Network error ({type(e).__name__}): {str(e)}")
            print(f"Retrying in {retry_delay} seconds...")
            print(f"Retrying connecting to {url}")
            failed = True

        if failed:
            time.sleep(retry_delay)

    # If all retries failed, raise an exception
    raise Exception(f"Failed to connect to {url} after {max_retries} attempts")


def epoch_millis_to_cat(milli_seconds):
    seconds = milli_seconds / 1000.0
    utc_dt = datetime.datetime.fromtimestamp(seconds, tz=datetime.timezone.utc)
    cat_tz = timezone('Africa/Harare')
    cat_dt = utc_dt.astimezone(cat_tz)
    formatted_time = cat_dt.strftime('%d-%m-%y %H:%M:%S')
    return formatted_time


def current_date_time_in_cat():
    cat_tz = timezone('Africa/Harare')
    cat_time = datetime.datetime.now(cat_tz)
    return cat_time.strftime('%Y-%m-%d %H:%M:%S %Z')


def load_query(query_name):
    with open(PATH_TO_QUERIES_JSON, "r") as queries:
        all_queries = json.load(queries)
    return all_queries.get(query_name)


def simple_excel(data_array, file_name):
    print(f"------WRITING DATA TO EXCEL FILE {file_name} -------")
    writer = pd.ExcelWriter(file_name, engine="xlsxwriter")
    df = pd.DataFrame(data_array)
    df.to_excel(writer, index=False)
    writer._save()
    print(f"------{file_name} EXCEL FILE SAVED -------")


def simple_excel_group_by_column(data_array, file_name, column):
    print(f"------WRITING DATA TO EXCEL FILE {file_name} -------")

    writer = pd.ExcelWriter(file_name, engine="xlsxwriter")

    df = pd.DataFrame(data_array)

    unique_groups = df[column].unique()

    for group in unique_groups:
        group_df = df[df[column] == group]
        sheet_name = f"{group}"
        group_df.to_excel(writer, sheet_name=sheet_name, index=False)

    writer._save()
    print(f"------{file_name} EXCEL FILE SAVED -------")


def simple_excel_with_key_as_sheets(data_obj, file_name):
    writer = pd.ExcelWriter(file_name, engine="xlsxwriter")
    for sheet, data_set in data_obj.items():
        df = pd.DataFrame(data_set)
        df.to_excel(writer, sheet_name=sheet, index=False)
    writer._save()


def replace_place_holder(query, placeholders, replace_values):
    data_str = json.dumps(query)
    for idx, item in enumerate(placeholders):
        if type(replace_values[idx]) is int:
            item_with_quotes = f'"{item}"'
            data_str = data_str.replace(item_with_quotes, str(replace_values[idx]))
            continue
        data_str = data_str.replace(item, replace_values[idx])
    replaced_query = json.loads(data_str)
    return replaced_query


def load_replace_query(query_name, placeholders, replace_values):
    with open(PATH_TO_QUERIES_JSON, "r") as queries:
        all_queries = json.load(queries)
    query = all_queries.get(query_name)
    data_str = json.dumps(query)
    for idx, item in enumerate(placeholders):
        if type(replace_values[idx]) is int:
            item_with_quotes = f'"{item}"'
            data_str = data_str.replace(item_with_quotes, str(replace_values[idx]))
            continue
        data_str = data_str.replace(item, replace_values[idx])
    replaced_query = json.loads(data_str)
    return replaced_query


def load_multi_queries(queries):
    print(queries)
    with open(PATH_TO_QUERIES_JSON, "r") as queries_all:
        all_queries = json.load(queries_all)
    return [all_queries.get(queries[i]) for i in range(len(queries))]


def save_file_to_folder(file):

    time_str = yesterdays_date()
    new_file_name = f"{time_str}_{file}"
    folder_path = PATH_TO_FINAL_REPORTS + time_str + "/"
    if not os.path.exists(folder_path):
        os.makedirs(folder_path)
    shutil.move(file, folder_path + new_file_name)


def today_date():
    current_datetime = datetime.datetime.now()
    time_str = current_datetime.strftime("%d_%b").upper()
    return time_str


def yesterdays_date():
    current_datetime = datetime.datetime.now()
    yesterday_datetime = current_datetime - datetime.timedelta(days=1)
    date_str = yesterday_datetime.strftime("%d_%b").upper()
    return date_str
# without thread lock
# def pd_different_sheets():
