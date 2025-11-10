import json
from datetime import datetime


def get_custom_dates_of_reports():
    # Customize the path name of the file
    reports_date_config_file = open("reports_date_config.json")
    reports_date_config = json.load(reports_date_config_file)

    start_date_str = reports_date_config['start_date']
    end_date_str = reports_date_config['end_date']
    date_format = '%Y-%m-%d %H:%M:%S%z'

    start_date = datetime.strptime(start_date_str, date_format)
    end_date = datetime.strptime(end_date_str, date_format)
    gte_time = start_date.timestamp() * 1000
    lte_time = end_date.timestamp() * 1000
    print("Reports start date:", start_date)
    print("Reports end date:", end_date)

    return int(lte_time), int(gte_time), start_date_str, end_date_str