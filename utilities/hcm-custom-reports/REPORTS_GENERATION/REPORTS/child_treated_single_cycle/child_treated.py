import json
import os
import sys
import warnings
import pandas as pd
import requests
from datetime import datetime
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
from COMMON_UTILS.common_utils import get_resp

warnings.filterwarnings("ignore", message="Unverified HTTPS request is being made.*")

# === CONSTANTS ===
ES_PROJECT_TASK_INDEX = "http://elasticsearch-master.es-upgrade.svc.cluster.local:9200/project-task-index-v1/_search"
ES_INDIVIDUAL_INDEX = "http://elasticsearch-master.es-upgrade.svc.cluster.local:9200/individual-index-v1/_search"
ES_SCROLL_API = "http://elasticsearch-master.es-upgrade.svc.cluster.local:9200/_search/scroll"
ES_HOUSEHOLD_MEMBER_INDEX = "http://elasticsearch-master.es-upgrade.svc.cluster.local:9200/household-member-index-v1/_search"
DECRYPT_URL = "http://egov-enc-service.egov:8080/egov-enc-service/crypto/v1/_decrypt"

# === DATE RANGE ===
lteTime, gteTime, start_date_str, end_date_str = get_custom_dates_of_reports(START_DATE, END_DATE)

# === CAMPAIGN FILTER FIELD ===
if IDENTIFIER_TYPE == "projectTypeId":
    CAMPAIGN_FILTER_FIELD = "Data.projectTypeId.keyword"
    print(f"Using projectTypeId filter: {CAMPAIGN_IDENTIFIER}")
else:
    CAMPAIGN_FILTER_FIELD = "Data.campaignNumber.keyword"
    print(f"Using campaignNumber filter: {CAMPAIGN_IDENTIFIER}")

# === OUTPUT HOLDER ===
ind_id_vs_info = {}

# === DECRYPT FUNCTION ===
def decrypt_identifier(encrypted_id):
    try:
        payload = json.dumps(encrypted_id)
        response = requests.post(DECRYPT_URL, headers={"Content-Type": "application/json"}, data=payload)
        if response.status_code == 200:
            return response.text.strip().replace('"', '')
    except Exception as e:
        print(f"Decryption error: {e}")
    return ""

# === TIMESTAMP CONVERTER ===
def convert_ts_to_date(ts):
    try:
        return datetime.utcfromtimestamp(int(ts) / 1000).strftime('%Y-%m-%d')
    except Exception:
        return ""

# === FETCH PROJECT TASK DATA ===
def fetch_project_tasks():
    query = {
        "size": 6000,
        "query": {
            "bool": {
                "must": [
                    {"term": {CAMPAIGN_FILTER_FIELD: CAMPAIGN_IDENTIFIER}},
                    {"range": {"Data.@timestamp": {"gte": gteTime, "lte": lteTime}}},
                    {"terms": {"Data.administrationStatus.keyword": [
                        "ADMINISTRATION_SUCCESS", "BENEFICIARY_INELIGIBLE", "BENEFICIARY_REFERRED",
                        "BENEFICIARY_REFUSED", "CLOSED_HOUSEHOLD"
                    ]}}
                ]
            }
        },
        "_source": [
            "Data.boundaryHierarchy", "Data.age", "Data.gender", "Data.individualId",
            "Data.userName", "Data.quantity", "Data.uniqueBeneficiaryID",
            "Data.administrationStatus", "Data.additionalDetails.reAdministered",
            "Data.taskDates", "Data.additionalDetails.dateOfAdministration"
        ]
    }

    scroll_url = ES_PROJECT_TASK_INDEX + "?scroll=10m"
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
            indv_id = data.get("individualId")
            if not indv_id:
                continue

            if indv_id not in ind_id_vs_info:
              boundary = data.get("boundaryHierarchy", {})
              ind_id_vs_info[indv_id] = {
        "Province": boundary.get("province", ""),
        "District": boundary.get("district", ""),
        "Administrative Province": boundary.get("administrativeProvince", ""),
        "Locality": boundary.get("locality", ""),
        "Village": boundary.get("village", ""),
        "Username": data.get("userName", ""),
        "Child Name": "",
        "Age": data.get("age", ""),
        "Gender": data.get("gender", ""),
        "Beneficiary ID (Child)": "",
        "Household Client Reference ID": "",
        "Household Head Name": "",
        "Quantity Administered": 0,
        "Redose Quantity Administered": 0,
        "BENEFICIARY_INELIGIBLE": "no",
        "BENEFICIARY_REFERRED": "no",
        "BENEFICIARY_REFUSED": "no",
        "CLOSED_HOUSEHOLD": "no",
        "Date of Registration": data.get("taskDates", ""),
        "Date of Administration": convert_ts_to_date(data.get("additionalDetails", {}).get("dateOfAdministration"))
    }


            status = data.get("administrationStatus", "")
            re_administered = str(data.get("additionalDetails", {}).get("reAdministered", "")).lower()

            if status == "ADMINISTRATION_SUCCESS":
                ind_id_vs_info[indv_id]["Quantity Administered"] += data.get("quantity", 0)

            if re_administered == "true":
                ind_id_vs_info[indv_id]["Redose Quantity Administered"] += data.get("quantity", 0)

            if status in ["BENEFICIARY_INELIGIBLE", "BENEFICIARY_REFERRED", "BENEFICIARY_REFUSED", "CLOSED_HOUSEHOLD"]:
                ind_id_vs_info[indv_id][status] = "yes"

# === ENRICH CHILD DETAILS ===
def enrich_child_info(individual_ids):
    query = {
        "size": 10000,
        "query": {
            "terms": {
                "clientReferenceId.keyword": individual_ids
            }
        },
        "_source": ["clientReferenceId", "name", "identifiers"]
    }
    resp = get_resp(ES_INDIVIDUAL_INDEX, query, True).json()
    return resp["hits"]["hits"]

# === FETCH HOUSEHOLD INFO ===
def fetch_household_links(child_client_refs):
    # Step 1: Match child to householdClientReferenceId
    query = {
        "size": 10000,
        "query": {
            "terms": {
                "Data.householdMember.individualClientReferenceId.keyword": child_client_refs
            }
        },
        "_source": ["Data.householdMember.householdClientReferenceId", "Data.householdMember.individualClientReferenceId"]
    }
    resp = get_resp(ES_HOUSEHOLD_MEMBER_INDEX, query, True).json()
    child_to_household = {}
    for doc in resp["hits"]["hits"]:
        hh_member = doc["_source"]["Data"]["householdMember"]
        child_to_household[hh_member["individualClientReferenceId"]] = hh_member["householdClientReferenceId"]
    return child_to_household

# === FETCH HEAD REFERENCE IDS ===
def fetch_household_head_reference_ids(household_ids):
    query = {
        "size": 10000,
        "query": {
            "bool": {
                "must": [
                    {"terms": {"Data.householdMember.householdClientReferenceId.keyword": household_ids}},
                    {"term": {"Data.householdMember.isHeadOfHousehold": True}}
                ]
            }
        },
        "_source": ["Data.householdMember.householdClientReferenceId", "Data.householdMember.individualClientReferenceId"]
    }
    resp = get_resp(ES_HOUSEHOLD_MEMBER_INDEX, query, True).json()
    hh_id_to_head_ref = {}
    for doc in resp["hits"]["hits"]:
        hh = doc["_source"]["Data"]["householdMember"]
        hh_id_to_head_ref[hh["householdClientReferenceId"]] = hh["individualClientReferenceId"]
    return hh_id_to_head_ref

# === FETCH HEAD NAMES FROM INDIVIDUAL INDEX ===
def fetch_head_names_from_individuals(head_refs):
    query = {
        "size": 10000,
        "query": {
            "terms": {
                "clientReferenceId.keyword": head_refs
            }
        },
        "_source": ["clientReferenceId", "name"]
    }
    resp = get_resp(ES_INDIVIDUAL_INDEX, query, True).json()
    head_id_to_name = {}
    for doc in resp["hits"]["hits"]:
        src = doc["_source"]
        name = src.get("name", {})
        full_name = f"{name.get('givenName', '')} {name.get('familyName', '')}".replace("None", "").strip()
        head_id_to_name[src["clientReferenceId"]] = full_name
    return head_id_to_name

# === EXECUTE STEPS ===
fetch_project_tasks()

chunks = [list(ind_id_vs_info.keys())[i:i + 1000] for i in range(0, len(ind_id_vs_info), 1000)]
for chunk in chunks:
    enriched = enrich_child_info(chunk)
    child_to_beneficiary_id = {}

    for doc in enriched:
        src = doc["_source"]
        indv_id = src["clientReferenceId"]
        name_obj = src.get("name", {})
        full_name = f"{name_obj.get('givenName', '')} {name_obj.get('familyName', '')}".replace("None", "").strip()
        ind_id_vs_info[indv_id]["Child Name"] = full_name

        for ident in src.get("identifiers", []):
            if ident.get("identifierType") == "UNIQUE_BENEFICIARY_ID":
                decrypted_id = decrypt_identifier(ident.get("identifierId"))
                ind_id_vs_info[indv_id]["Beneficiary ID (Child)"] = decrypted_id
                child_to_beneficiary_id[indv_id] = decrypted_id
                break

    # Step 1: child → householdClientReferenceId
    child_to_household = fetch_household_links(chunk)

    # Step 2: householdClientReferenceId → headClientReferenceId
    household_ids = list(child_to_household.values())
    hh_to_head_ref = fetch_household_head_reference_ids(household_ids)

    # Step 3: headClientReferenceId → head name
    head_names = fetch_head_names_from_individuals(list(hh_to_head_ref.values()))

    # Assign final values into report
    for child_id in chunk:
        hh_id = child_to_household.get(child_id, "")
        ind_id_vs_info[child_id]["Household Client Reference ID"] = hh_id

        head_ref = hh_to_head_ref.get(hh_id, "")
        if head_ref:
            ind_id_vs_info[child_id]["Household Head Name"] = head_names.get(head_ref, "")

# === EXPORT TO EXCEL ===
df = pd.DataFrame(list(ind_id_vs_info.values()))

# Use FILE_NAME from command line args
output_file = f"{FILE_NAME}.xlsx"
output_path = os.path.join(os.getcwd(), output_file)
df.to_excel(output_path, index=False)

print(f"Report saved to: {output_path}")

