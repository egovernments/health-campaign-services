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

# ===== PATH SETUP =====
file_path = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.append(file_path)

warnings.filterwarnings("ignore", message="Unverified HTTPS request is being made.*")

from COMMON_UTILS.common_utils import get_resp
from COMMON_UTILS.custom_date_utils import get_custom_dates_of_reports

# ===== CONSTANTS =====
REFERRAL_INDEX = "http://elasticsearch-master.es-upgrade.svc.cluster.local:9200/referral-index-v1/_search"
INDIVIDUAL_INDEX = "http://elasticsearch-master.es-upgrade.svc.cluster.local:9200/individual-index-v1/_search"
SCROLL_API = "http://elasticsearch-master.es-upgrade.svc.cluster.local:9200/_search/scroll"

SCROLL_TIME = "2m"
PAGE_SIZE = 1000

if IDENTIFIER_TYPE == "projectTypeId":
    CAMPAIGN_FILTER_FIELD = "Data.projectTypeId.keyword"
    print(f"📋 Using projectTypeId filter: {CAMPAIGN_IDENTIFIER}")
else:
    CAMPAIGN_FILTER_FIELD = "Data.campaignNumber.keyword"
    print(f"📋 Using campaignNumber filter: {CAMPAIGN_IDENTIFIER}")


lteTime, gteTime, start_date_str, end_date_str = get_custom_dates_of_reports(START_DATE, END_DATE)

# ===== REFERRAL PAYLOAD =====
referral_payload = {
    "_source": [
        "Data.userName",
        "Data.individualId",
        "Data.boundaryHierarchy",
        "Data.taskDates",
        "Data.additionalDetails.referralReasons"
    ],
    "size": PAGE_SIZE,
    "query": {
        "bool": {
            "must": [
                {
                    "term": {
                        CAMPAIGN_FILTER_FIELD : CAMPAIGN_IDENTIFIER
                    }
                }
            ],
            "filter": [
                {"range": {"Data.@timestamp": {"gte": gteTime, "lte": lteTime}}}
            ]
        }
    }
}

# ===== FETCH REFERRALS (SCROLL) =====
print("Fetching referral data using scroll API...")

response = get_resp(f"{REFERRAL_INDEX}?scroll={SCROLL_TIME}", referral_payload, True).json()
scroll_id = response["_scroll_id"]
hits = response["hits"]["hits"]

all_hits = hits[:]

while hits:
    scroll_resp = get_resp( SCROLL_API, {
        "scroll": SCROLL_TIME,
        "scroll_id": scroll_id
    }, es=True).json()
    hits = scroll_resp["hits"]["hits"]
    all_hits.extend(hits)

print(f"Total referral records fetched: {len(all_hits)}")

# ===== COLLECT REFERRAL REASONS + INDIVIDUAL IDS =====
all_reasons = set()
individual_ids = set()

for h in all_hits:
    data = h["_source"]["Data"]
    individual_ids.add(data.get("individualId"))

    raw = data.get("additionalDetails", {}).get("referralReasons", "")
    for r in raw.split(","):
        if r.strip():
            all_reasons.add(r.strip().upper())

all_reasons = sorted(all_reasons)
print("Detected referral reasons:", all_reasons)

# ===== FETCH INDIVIDUAL DETAILS =====
print("Fetching individual details...")

individual_map = {}

for ind_id in individual_ids:
    if not ind_id:
        continue

    payload = {
        "size": 1,
        "query": {
            "term": {
                "clientReferenceId.keyword": ind_id
            }
        }
    }

    resp = get_resp( INDIVIDUAL_INDEX, payload, True).json()
    hits = resp.get("hits", {}).get("hits", [])

    if not hits:
        continue

    src = hits[0]["_source"]

    # Name
    name_obj = src.get("name", {})
    full_name = " ".join(
        filter(None, [name_obj.get("givenName"), name_obj.get("familyName")])
    )

    # Gender
    gender = src.get("gender")

    # Age in months
    dob_str = src.get("dateOfBirth")
    created_time = src.get("clientAuditDetails", {}).get("createdTime")

    age_months = None
    if dob_str and created_time:
        dob = datetime.strptime(dob_str, "%d/%m/%Y")
        created_dt = datetime.utcfromtimestamp(created_time / 1000)
        age_months = (created_dt.year - dob.year) * 12 + (created_dt.month - dob.month)

    individual_map[ind_id] = {
        "Full Name": full_name,
        "Gender": gender,
        "Age (Months)": age_months
    }

# ===== BUILD FINAL ROWS =====
rows = []

for h in all_hits:
    data = h["_source"]["Data"]
    boundary = data.get("boundaryHierarchy", {})
    raw_reasons = data.get("additionalDetails", {}).get("referralReasons", "")

    reasons_set = {
        r.strip().upper()
        for r in raw_reasons.split(",")
        if r.strip()
    }

    ind_id = data.get("individualId")
    ind_info = individual_map.get(ind_id, {})

    row = {
        "Country": boundary.get("country"),
        "Province": boundary.get("province"),
        "District": boundary.get("district"),
        "Locality": boundary.get("locality"),
        "Administrative Province": boundary.get("administrativeProvince"),
        "Village": boundary.get("village"),
        "Health Facility": boundary.get("healthFacility"),
        "Task Date": data.get("taskDates"),
        "User Name": data.get("userName"),
        "Individual ID": ind_id,
        "Full Name": ind_info.get("Full Name"),
        "Gender": ind_info.get("Gender"),
        "Age (Months)": ind_info.get("Age (Months)")
    }

    for reason in all_reasons:
        row[reason] = "yes" if reason in reasons_set else "no"

    rows.append(row)

# ===== EXPORT =====
df = pd.DataFrame(rows)
file_name = f"{FILE_NAME}.xlsx"
output_path = os.path.join(os.getcwd(), file_name)
df.to_excel(output_path, index=False)

print(f" Final referral report generated: {output_path}")