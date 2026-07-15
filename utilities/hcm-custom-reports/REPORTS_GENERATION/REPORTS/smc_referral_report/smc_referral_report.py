import os
import sys
import warnings
import pandas as pd
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

# === PATH SETUP ===
file_path = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.append(file_path)

from COMMON_UTILS.custom_date_utils import get_custom_dates_of_reports
from COMMON_UTILS.common_utils import get_resp, es_index_url, es_scroll_url

warnings.filterwarnings("ignore", message="Unverified HTTPS request is being made.*")

# === CONSTANTS ===
ES_HF_REFERRAL_INDEX = es_index_url("hf-referral-index-v1")
ES_SCROLL_API = es_scroll_url()

# === DATE RANGE ===
lteTime, gteTime, start_date_str, end_date_str = get_custom_dates_of_reports(START_DATE, END_DATE)

# === CAMPAIGN FILTER ===
if IDENTIFIER_TYPE == "projectTypeId":
    CAMPAIGN_FILTER_FIELD = "Data.projectTypeId.keyword"
    print(f"Using projectTypeId filter: {CAMPAIGN_IDENTIFIER}")
else:
    CAMPAIGN_FILTER_FIELD = "Data.campaignNumber.keyword"
    print(f"Using campaignNumber filter: {CAMPAIGN_IDENTIFIER}")


def fetch_referral_records():
    """Scroll all hf-referral-index-v1 records for the campaign and return as a list of rows."""
    query = {
        "size": 5000,
        "query": {
            "bool": {
                "must": [
                    {"term": {CAMPAIGN_FILTER_FIELD: CAMPAIGN_IDENTIFIER}},
                    {"range": {"Data.hfReferral.auditDetails.lastModifiedTime": {"gte": gteTime, "lte": lteTime}}}
                ]
            }
        },
        "_source": [
            "Data.boundaryHierarchy",
            "Data.additionalDetails.cycleIndex",
            "Data.hfReferral.additionalFields.fields",
            "Data.hfReferral.clientAuditDetails"
        ],
        "sort": [
            {"Data.boundaryHierarchy.country.keyword": {"order": "asc"}},
            {"Data.boundaryHierarchy.state.keyword": {"order": "asc"}},
            {"Data.boundaryHierarchy.lga.keyword": {"order": "asc"}},
            {"Data.boundaryHierarchy.ward.keyword": {"order": "asc"}},
            {"Data.boundaryHierarchy.healthFacility.keyword": {"order": "asc"}}
        ]
    }

    rows = []
    scroll_url = ES_HF_REFERRAL_INDEX + "?scroll=10m"
    scroll_id = None

    while True:
        if scroll_id is None:
            resp = get_resp(scroll_url, query, True).json()
        else:
            resp = get_resp(ES_SCROLL_API, {"scroll": "10m", "scroll_id": scroll_id}, True).json()

        scroll_id = resp.get("_scroll_id", "")
        hits = resp.get("hits", {}).get("hits", [])
        if not hits:
            break

        for doc in hits:
            data = doc["_source"]["Data"]
            boundary = data.get("boundaryHierarchy", {})
            additional_details = data.get("additionalDetails", {})
            hf_referral = data.get("hfReferral", {})

            # Extract nameOfReferral from additionalFields key-value array
            member_id = hf_referral.get("beneficiaryId", "")
            for field in hf_referral.get("additionalFields", {}).get("fields", []):
                if field.get("key") == "nameOfReferral":
                    member_id = field.get("value", "")
                    break

            # Determine seenAtHF: YES if record was modified by a different user (i.e. HF staff updated it)
            client_audit = hf_referral.get("clientAuditDetails", {})
            created_by = client_audit.get("createdBy", "")
            last_modified_by = client_audit.get("lastModifiedBy", "")
            seen_at_hf = "YES" if (created_by and last_modified_by and created_by != last_modified_by) else "NO"

            rows.append({
                "Country": boundary.get("country", ""),
                "State": boundary.get("state", ""),
                "LGA": boundary.get("lga", ""),
                "Ward": boundary.get("ward", ""),
                "HF": boundary.get("healthFacility", ""),
                "Settlement": boundary.get("community", ""),
                "memberId": member_id,
                "Cycle": additional_details.get("cycleIndex", ""),
                "isReferred": "YES",
                "seenAtHF": seen_at_hf,
            })

    return rows


# === MAIN EXECUTION ===
print("Fetching referral records...")
rows = fetch_referral_records()
print(f"Fetched {len(rows)} records")

df = pd.DataFrame(rows, columns=[
    "Country", "State", "LGA", "Ward", "HF", "Settlement",
    "memberId", "Cycle", "isReferred", "seenAtHF"
])

# Sort by boundary hierarchy columns
df.sort_values(
    by=["Country", "State", "LGA", "Ward", "HF", "Settlement"],
    ascending=True,
    inplace=True,
    ignore_index=True
)

output_file = f"{FILE_NAME}.xlsx"
output_path = os.path.join(os.getcwd(), output_file)
df.to_excel(output_path, index=False)

print(f"Report saved to: {output_path} ({len(rows)} rows)")
