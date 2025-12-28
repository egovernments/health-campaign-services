import json
import os
import sys
import warnings
import pandas as pd
from datetime import datetime
import argparse


parser = argparse.ArgumentParser()
parser.add_argument('--campaign_identifier', required=True,
                    help='Campaign identifier (can be campaignNumber or projectTypeId)')
parser.add_argument('--identifier_type', default='campaignNumber',
                    help='Type of identifier: "campaignNumber" or "projectTypeId"')
parser.add_argument('--start_date', default='')
parser.add_argument('--end_date', default='')
parser.add_argument('--file_name', required=True)
args = parser.parse_args()

CAMPAIGN_IDENTIFIER = args.campaign_identifier  # Can be campaignNumber or projectTypeId (UUID)
IDENTIFIER_TYPE = args.identifier_type  # "campaignNumber" or "projectTypeId"
START_DATE = args.start_date
END_DATE = args.end_date
FILE_NAME = args.file_name

# === PATH SETUP ===
file_path = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.append(file_path)

from COMMON_UTILS.common_utils import get_resp
from COMMON_UTILS.custom_date_utils import get_custom_dates_of_reports

warnings.filterwarnings("ignore", message="Unverified HTTPS request is being made.*")

if IDENTIFIER_TYPE == "projectTypeId":
    CAMPAIGN_FILTER_FIELD = "Data.projectTypeId.keyword"
    print(f"📋 Using projectTypeId filter: {CAMPAIGN_IDENTIFIER}")
else:
    CAMPAIGN_FILTER_FIELD = "Data.campaignNumber.keyword"
    print(f"📋 Using campaignNumber filter: {CAMPAIGN_IDENTIFIER}")

# === CONSTANTS ===
ES_HOUSEHOLD_INDEX = "http://elasticsearch-master.es-upgrade.svc.cluster.local:9200/household-index-v1/_search"
ES_BENEFICIARY_INDEX = "http://elasticsearch-master.es-upgrade.svc.cluster.local:9200/project-beneficiary-index-v1/_search"
ES_SCROLL_API = "http://elasticsearch-master.es-upgrade.svc.cluster.local:9200/_search/scroll"

# === DATE RANGE ===
lteTime, gteTime, start_date_str, end_date_str = get_custom_dates_of_reports(START_DATE, END_DATE)
start_date = datetime.strptime(start_date_str, "%Y-%m-%d %H:%M:%S%z")
end_date = datetime.strptime(end_date_str, "%Y-%m-%d %H:%M:%S%z")
date_folder = f"{start_date.strftime('%Y-%m-%d')}_to_{end_date.strftime('%Y-%m-%d')}"

# === FETCH HOUSEHOLD DATA ===
def fetch_household_data():
    query = {
        "size": 5000,
        "_source": [
            "Data.boundaryHierarchy",
            "Data.household.id",
            "Data.household.clientReferenceId",
            "Data.additionalDetails.status",
            "Data.additionalDetails.isClosedHouseholdEdit"
        ],
        "query": {
            "bool": {
                "filter": [
                    {"range": {"Data.@timestamp": {"gte": gteTime, "lte": lteTime}}}
                ],
                "must" : [
                    {
                        "term" : {
                            CAMPAIGN_FILTER_FIELD : CAMPAIGN_IDENTIFIER
                        }
                    }
                ]
            }
        }
    }

    scroll_id = None
    initial_scroll_url = ES_HOUSEHOLD_INDEX + "?scroll=5m"
    all_data = []

    while True:
        if scroll_id is None:
            resp = get_resp(initial_scroll_url, query, True).json()
            scroll_id = resp.get("_scroll_id")
        else:
            resp = get_resp(ES_SCROLL_API, {"scroll": "5m", "scroll_id": scroll_id}, True).json()
            scroll_id = resp.get("_scroll_id")

        hits = resp.get("hits", {}).get("hits", [])
        if not hits:
            break

        for hit in hits:
            data = hit["_source"].get("Data", {})
            boundary = data.get("boundaryHierarchy", {})
            household = data.get("household", {})
            additional = data.get("additionalDetails", {})

            row = {
                "Country": boundary.get("country", ""),
                "Province": boundary.get("province", ""),
                "District": boundary.get("district", ""),
                "Administrative Province": boundary.get("administrativeProvince", ""),
                "Locality": boundary.get("locality", ""),
                "Village": boundary.get("village", ""),
                "Household ID": household.get("id", ""),
                "Household Client Reference ID": household.get("clientReferenceId", ""),
                "Status": additional.get("status", ""),
                "Is Closed Household Edit": additional.get("isClosedHouseholdEdit", "")
            }
            all_data.append(row)

    return all_data


# === FETCH BENEFICIARY DATA (tag + clientReferenceId) ===
def fetch_beneficiary_details(client_ref_ids):
    id_to_details = {}
    chunk_size = 500  # To stay within Elasticsearch 'terms' filter limit
    id_list = [i for i in client_ref_ids if i]

    for i in range(0, len(id_list), chunk_size):
        chunk = id_list[i:i + chunk_size]

        query = {
            "size": 10000,
            "_source": ["beneficiaryClientReferenceId", "tag", "clientReferenceId"],
            "query": {
                "bool": {
                    "filter": [
                        {"terms": {"beneficiaryClientReferenceId.keyword": chunk}}
                    ]
                }
            }
        }

        resp = get_resp(ES_BENEFICIARY_INDEX, query, True).json()
        hits = resp.get("hits", {}).get("hits", [])

        for hit in hits:
            src = hit.get("_source", {})
            ref_id = src.get("beneficiaryClientReferenceId", "")
            tag = src.get("tag", "")
            beneficiary_client_ref = src.get("clientReferenceId", "")
            if ref_id:
                id_to_details[ref_id] = {
                    "Tag": tag,
                    "Beneficiary Client Reference ID": beneficiary_client_ref
                }

    return id_to_details

# === FETCH PROJECT TASK DATA (Data.administrationStatus) ===
def fetch_task_status(beneficiary_client_ref_ids):
    id_to_status = {}
    chunk_size = 500  # Elasticsearch 'terms' limit
    id_list = [i for i in beneficiary_client_ref_ids if i]

    for i in range(0, len(id_list), chunk_size):
        chunk = id_list[i:i + chunk_size]

        query = {
            "size": 10000,
            "_source": ["Data.projectBeneficiaryClientReferenceId", "Data.administrationStatus"],
            "query": {
                "bool": {
                    "filter": [
                        {"terms": {"Data.projectBeneficiaryClientReferenceId.keyword": chunk}}
                    ]
                }
            }
        }

        ES_TASK_INDEX = "http://elasticsearch-master.es-upgrade.svc.cluster.local:9200/project-task-index-v1/_search"
        resp = get_resp(ES_TASK_INDEX, query, True).json()
        hits = resp.get("hits", {}).get("hits", [])

        for hit in hits:
            src = hit.get("_source", {}).get("Data", {})
            ref_id = src.get("projectBeneficiaryClientReferenceId", "")
            admin_status = src.get("administrationStatus", "")
            if ref_id:
                id_to_status[ref_id] = admin_status

    return id_to_status


# === MAIN ===
# === MAIN ===
print("🔄 Fetching household data...")
household_data = fetch_household_data()
df = pd.DataFrame(household_data)

if df.empty:
    print("⚠️ No household records found.")
else:
    print(f"✅ Retrieved {len(df)} household records.")

    # === Fetch beneficiary details (tag + clientReferenceId) ===
    print("🔄 Fetching beneficiary details...")
    beneficiary_map = fetch_beneficiary_details(df["Household Client Reference ID"].unique())

    # Map beneficiary info
    df["Tag"] = df["Household Client Reference ID"].map(
        lambda x: beneficiary_map.get(x, {}).get("Tag", "")
    )
    df["Beneficiary Client Reference ID"] = df["Household Client Reference ID"].map(
        lambda x: beneficiary_map.get(x, {}).get("Beneficiary Client Reference ID", "")
    )

    # === Fetch task status using Beneficiary Client Reference IDs ===
    print("🔄 Fetching task administration status...")
    task_map = fetch_task_status(df["Beneficiary Client Reference ID"].unique())

    # Map administration status (leave blank if missing)
    df["Administration Status"] = df["Beneficiary Client Reference ID"].map(
        lambda x: task_map.get(x, "")
    )

    # === Sort & Save ===
    sort_cols = [col for col in ["Province", "District", "Administrative Province", "Locality", "Village"] if col in df.columns]
    if sort_cols:
        df.sort_values(by=sort_cols, inplace=True)

    # === SAVE REPORT ===
    # output_dir = os.path.join(file_path, "FINAL_REPORTS", date_folder)
    # os.makedirs(output_dir, exist_ok=True)

    file_name = f"{FILE_NAME}.xlsx"
    output_path = os.path.join(os.getcwd(), file_name)

    df.to_excel(output_path, index=False)
    print(f"✅ Household report generated with tag, clientReferenceId, and administrationStatus: {output_path}")
