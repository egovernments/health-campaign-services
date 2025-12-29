import os
import sys
import warnings
import requests
import json
import time
from datetime import datetime
from openpyxl import Workbook
import argparse

# === COMMAND LINE ARGUMENTS ===
parser = argparse.ArgumentParser()
parser.add_argument('--campaign_identifier', required=True,
                    help='Campaign identifier (can be campaignNumber or projectTypeId)')
parser.add_argument('--identifier_type', default='campaignNumber',
                    help='Type of identifier: "campaignNumber" or "projectTypeId"')
parser.add_argument('--start_date', default='')
parser.add_argument('--end_date', default='')
parser.add_argument('--file_name', required=True)
args = parser.parse_args()

CAMPAIGN_IDENTIFIER = args.campaign_identifier
IDENTIFIER_TYPE = args.identifier_type
START_DATE = args.start_date
END_DATE = args.end_date
FILE_NAME = args.file_name

# ===========================
# PATH SETUP
# ===========================
file_path = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
sys.path.append(file_path)

from REPORTS_GENERATION.COMMON_UTILS.custom_date_utils import get_custom_dates_of_reports

# ===========================
# CONFIG
# ===========================
ES_STOCK_SEARCH = "http://elasticsearch-master.es-upgrade.svc.cluster.local:9200/stock-index-v1/_search"
ES_SCROLL_API = "http://elasticsearch-master.es-upgrade.svc.cluster.local:9200/_search/scroll"

ELASTIC_AUTH = "Basic ZWxhc3RpYzpaRFJsT0RJME1UQTNNV1ppTVRGbFptRms="

SCROLL_TIME = "2m"
BATCH_SIZE = 1000

max_retries = 1
retry_delay = 1

warnings.filterwarnings("ignore", message="Unverified HTTPS request is being made.*")

# === DATE RANGE ===
lteTime, gteTime, start_date_str, end_date_str = get_custom_dates_of_reports(START_DATE, END_DATE)

# === CAMPAIGN FILTER FIELD ===
if IDENTIFIER_TYPE == "projectTypeId":
    CAMPAIGN_FILTER_FIELD = "Data.projectTypeId.keyword"
    print(f"Using projectTypeId filter: {CAMPAIGN_IDENTIFIER}")
else:
    CAMPAIGN_FILTER_FIELD = "Data.campaignNumber.keyword"
    print(f"Using campaignNumber filter: {CAMPAIGN_IDENTIFIER}")

# ===========================
# COMMON ES CALL FUNCTION
# ===========================
def get_resp(url, data, es=False):
    headers = {"Content-Type": "application/json"}
    if es:
        headers["Authorization"] = ELASTIC_AUTH

    try:
        return requests.post(url, json=data, headers=headers, verify=False)
    except Exception as e:
        print("ES error:", e)
        return None

# ===========================
# QUERY
# ===========================
QUERY = {
    "size": BATCH_SIZE,
    "_source": [
        "Data.userName",
        "Data.createdTime",
        "Data.eventType",
        "Data.reason",
        "Data.transactingFacilityType",
        "Data.transactingFacilityName",
        "Data.facilityType",
        "Data.facilityName",
        "Data.physicalCount",
        "Data.additionalDetails.balesQuantity",
        "Data.additionalDetails.waybill_quantity"
    ],
    "query": {
        "bool": {
            "must": [
                {"term": {CAMPAIGN_FILTER_FIELD: CAMPAIGN_IDENTIFIER}},
                {"range": {"Data.createdTime": {"gte": gteTime, "lte": lteTime}}},
                {"wildcard": {"Data.role.keyword": {"value": "WAREHOUSE_MANAGER"}}}
            ]
        }
    }
}

# ===========================
# GENERATE XLSX REPORT
# ===========================
def generate_stock_xlsx():
    print("Fetching stock data using Scroll API...")

    resp = get_resp(f"{ES_STOCK_SEARCH}?scroll={SCROLL_TIME}", QUERY, es=True)
    if resp is None or resp.status_code != 200:
        print("Initial search failed")
        return

    resp_json = resp.json()
    scroll_id = resp_json.get("_scroll_id")
    hits = resp_json["hits"]["hits"]

    all_hits = []
    all_hits.extend(hits)

    while hits:
        scroll_resp = get_resp(
            ES_SCROLL_API,
            {"scroll": SCROLL_TIME, "scroll_id": scroll_id},
            es=True
        )
        if scroll_resp is None:
            break

        scroll_json = scroll_resp.json()
        hits = scroll_json["hits"]["hits"]
        scroll_id = scroll_json.get("_scroll_id")
        all_hits.extend(hits)

    print(f"Total records fetched: {len(all_hits)}")

    # ===========================
    # XLSX CREATION
    # ===========================
    output_file = f"{FILE_NAME}.xlsx"
    output_path = os.path.join(os.getcwd(), output_file)

    wb = Workbook()
    sheet = wb.active
    sheet.title = "Stock Data"

    header = [
        "userName",
        "createdTime",
        "eventType",
        "Reason",
        "transactingFacilityType",
        "transactingFacilityName",
        "RecevingFacilityType",
        "facilityName",
        "physicalCount",
        "balesQuantity",
        "waybill_quantity"
    ]
    sheet.append(header)

    local_tz = datetime.now().astimezone().tzinfo

    for hit in all_hits:
        data = hit.get("_source", {}).get("Data", {})

        created_time = data.get("createdTime")
        created_time_str = ""
        if created_time:
            created_dt = datetime.fromtimestamp(created_time / 1000, tz=local_tz)
            created_time_str = created_dt.strftime('%Y-%m-%d %H:%M:%S %Z')

        sheet.append([
            data.get("userName", ""),
            created_time_str,
            data.get("eventType", ""),
            data.get("reason", ""),
            data.get("transactingFacilityType", ""),
            data.get("transactingFacilityName", ""),
            data.get("facilityType", ""),
            data.get("facilityName", ""),
            data.get("physicalCount", ""),
            data.get("additionalDetails", {}).get("balesQuantity", ""),
            data.get("additionalDetails", {}).get("waybill_quantity", "")
        ])

    wb.save(output_path)
    print(f"Report saved to: {output_path}")

# ===========================
# RUN
# ===========================
generate_stock_xlsx()
