import json
import os
import sys
import warnings
import pandas as pd
import requests
from datetime import datetime
from zoneinfo import ZoneInfo
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
ES_PROJECT_TASK_INDEX = "https://elasticsearch-data.es-cluster-v8:9200/project-task-index-v1/_search"
ES_INDIVIDUAL_INDEX = "https://elasticsearch-data.es-cluster-v8:9200/individual-index-v1/_search"
ES_SCROLL_API = "https://elasticsearch-data.es-cluster-v8:9200/_search/scroll"
ES_HOUSEHOLD_MEMBER_INDEX = "https://elasticsearch-data.es-cluster-v8:9200/household-member-index-v1/_search"
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
        if response and response.status_code == 200:
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
            "Data.taskDates", "Data.additionalDetails.dateOfAdministration",
            "Data.productName", "Data.additionalDetails.cycleIndex"
        ]
    }

    scroll_url = ES_PROJECT_TASK_INDEX + "?scroll=10m"
    scroll_id = None

    while True:
        if scroll_id is None:
            resp = get_resp(scroll_url, query, True)
        else:
            resp = get_resp(ES_SCROLL_API, {"scroll": "10m", "scroll_id": scroll_id}, True)

        if not resp:
            print("⚠ Warning: Project Task ES returned None. Breaking scroll.")
            break

        try:
            resp_json = resp.json()
        except Exception as e:
            print(f"⚠ Error parsing Project Task response: {e}")
            break

        scroll_id = resp_json.get("_scroll_id", "")
        hits = resp_json.get("hits", {}).get("hits", [])
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
                    "Age List": [],
                    "Gender": data.get("gender", ""),
                    "Beneficiary ID (Child)": "",
                    "Household Client Reference ID": "",
                    "Household Head Name": "",
                    "Product Name": data.get("productName", ""),
                    "Cycle Index List": [],
                    "Quantity Administered List": [],
                    "Redose Quantity Administered List": [],
                    "BENEFICIARY_INELIGIBLE": "no",
                    "BENEFICIARY_REFERRED": "no",
                    "BENEFICIARY_REFUSED": "no",
                    "CLOSED_HOUSEHOLD": "no",
                    "Date of Registration": data.get("taskDates", ""),
                    "Date of Administration List": []
                }

            # Append unique cycle index and age
            cycle_index = data.get("additionalDetails", {}).get("cycleIndex")
            age = data.get("age", "")
            date_of_admin = convert_ts_to_date(data.get("additionalDetails", {}).get("dateOfAdministration"))
            quantity = data.get("quantity", 0)
            re_administered = str(data.get("additionalDetails", {}).get("reAdministered", "")).lower()

            if cycle_index is not None:
                cycle_str = str(cycle_index).zfill(2)
                if cycle_str not in ind_id_vs_info[indv_id]["Cycle Index List"]:
                    ind_id_vs_info[indv_id]["Cycle Index List"].append(cycle_str)
                    ind_id_vs_info[indv_id]["Age List"].append(age)
                    ind_id_vs_info[indv_id]["Date of Administration List"].append(date_of_admin)
                    ind_id_vs_info[indv_id]["Quantity Administered List"].append(
                        quantity if data.get("administrationStatus") == "ADMINISTRATION_SUCCESS" else 0
                    )
                    ind_id_vs_info[indv_id]["Redose Quantity Administered List"].append(
                        quantity if re_administered == "true" else 0
                    )

            # Update status flags
            status = data.get("administrationStatus", "")
            if status in ["BENEFICIARY_INELIGIBLE", "BENEFICIARY_REFERRED", "BENEFICIARY_REFUSED", "CLOSED_HOUSEHOLD"]:
                ind_id_vs_info[indv_id][status] = "yes"

# === ENRICH CHILD DETAILS ===
def enrich_child_info(individual_ids, batch_size=1000):
    enriched_docs = []
    for i in range(0, len(individual_ids), batch_size):
        batch = individual_ids[i:i+batch_size]
        query = {
            "size": len(batch),
            "query": {"terms": {"clientReferenceId.keyword": batch}},
            "_source": ["clientReferenceId", "name", "identifiers"]
        }
        resp = get_resp(ES_INDIVIDUAL_INDEX, query, True)
        if not resp:
            print(f"⚠ Warning: ES returned None for individual batch {i}-{i+len(batch)}")
            continue
        try:
            enriched_docs.extend(resp.json().get("hits", {}).get("hits", []))
        except Exception as e:
            print(f"⚠ Error parsing child info batch {i}-{i+len(batch)}: {e}")
            continue
    return enriched_docs

# === FETCH HOUSEHOLD LINKS ===
def fetch_household_links(child_client_refs, batch_size=1000):
    child_to_hh = {}
    for i in range(0, len(child_client_refs), batch_size):
        batch = child_client_refs[i:i+batch_size]
        query = {
            "size": len(batch),
            "query": {"terms": {"Data.householdMember.individualClientReferenceId.keyword": batch}},
            "_source": ["Data.householdMember.householdClientReferenceId", "Data.householdMember.individualClientReferenceId"]
        }
        resp = get_resp(ES_HOUSEHOLD_MEMBER_INDEX, query, True)
        if not resp:
            print(f"⚠ Warning: ES returned None for household batch {i}-{i+len(batch)}")
            continue
        try:
            hits = resp.json().get("hits", {}).get("hits", [])
            for doc in hits:
                src = doc["_source"]["Data"]["householdMember"]
                child_to_hh[src["individualClientReferenceId"]] = src["householdClientReferenceId"]
        except Exception as e:
            print(f"⚠ Error parsing household batch {i}-{i+len(batch)}: {e}")
            continue
    return child_to_hh

# === FETCH HOUSEHOLD HEAD REFERENCES ===
def fetch_household_head_reference_ids(household_ids, batch_size=1000):
    hh_to_head = {}
    for i in range(0, len(household_ids), batch_size):
        batch = household_ids[i:i+batch_size]
        query = {
            "size": len(batch),
            "query": {
                "bool": {
                    "must": [
                        {"terms": {"Data.householdMember.householdClientReferenceId.keyword": batch}},
                        {"term": {"Data.householdMember.isHeadOfHousehold": True}}
                    ]
                }
            },
            "_source": ["Data.householdMember.householdClientReferenceId", "Data.householdMember.individualClientReferenceId"]
        }
        resp = get_resp(ES_HOUSEHOLD_MEMBER_INDEX, query, True)
        if not resp:
            print(f"⚠ Warning: ES returned None for household head batch {i}-{i+len(batch)}")
            continue
        try:
            hits = resp.json().get("hits", {}).get("hits", [])
            for doc in hits:
                src = doc["_source"]["Data"]["householdMember"]
                hh_to_head[src["householdClientReferenceId"]] = src["individualClientReferenceId"]
        except Exception as e:
            print(f"⚠ Error parsing household head batch {i}-{i+len(batch)}: {e}")
            continue
    return hh_to_head

# === FETCH HOUSEHOLD HEAD NAMES ===
def fetch_head_names_from_individuals(head_refs, batch_size=1000):
    head_id_to_name = {}
    for i in range(0, len(head_refs), batch_size):
        batch = head_refs[i:i+batch_size]
        query = {
            "size": len(batch),
            "query": {"terms": {"clientReferenceId.keyword": batch}},
            "_source": ["clientReferenceId", "name"]
        }
        resp = get_resp(ES_INDIVIDUAL_INDEX, query, True)
        if not resp:
            print(f"⚠ Warning: ES returned None for head batch {i}-{i+len(batch)}")
            continue
        try:
            hits = resp.json().get("hits", {}).get("hits", [])
            for doc in hits:
                src = doc["_source"]
                name = src.get("name", {})
                full_name = f"{name.get('givenName','')} {name.get('familyName','')}".replace("None","").strip()
                head_id_to_name[src["clientReferenceId"]] = full_name
        except Exception as e:
            print(f"⚠ Error parsing head names batch {i}-{i+len(batch)}: {e}")
            continue
    return head_id_to_name

# === EXECUTE STEPS ===
print("Fetching project-task data...")
fetch_project_tasks()

all_individuals = list(ind_id_vs_info.keys())

# Enrich child info
print("Enriching child details...")
enriched = enrich_child_info(all_individuals)
for doc in enriched:
    src = doc["_source"]
    indv_id = src["clientReferenceId"]
    name_obj = src.get("name", {})
    full_name = f"{name_obj.get('givenName','')} {name_obj.get('familyName','')}".replace("None","").strip()
    ind_id_vs_info[indv_id]["Child Name"] = full_name

    # Decrypt Beneficiary ID
    for ident in src.get("identifiers", []):
        if ident.get("identifierType") == "UNIQUE_BENEFICIARY_ID":
            ind_id_vs_info[indv_id]["Beneficiary ID (Child)"] = decrypt_identifier(ident.get("identifierId"))
            break

# Household info
print("Fetching household links...")
child_to_household = fetch_household_links(all_individuals)
household_ids = list(child_to_household.values())
hh_to_head_ref = fetch_household_head_reference_ids(household_ids)
head_names = fetch_head_names_from_individuals(list(hh_to_head_ref.values()))

for child_id in all_individuals:
    hh_id = child_to_household.get(child_id, "")
    ind_id_vs_info[child_id]["Household Client Reference ID"] = hh_id
    head_ref = hh_to_head_ref.get(hh_id, "")
    if head_ref:
        ind_id_vs_info[child_id]["Household Head Name"] = head_names.get(head_ref, "")

# === EXPLODE CHILD CYCLES UP TO 4 ===
all_rows = []
for indv_id, info in ind_id_vs_info.items():
    cycle_indices = info.get("Cycle Index List", [])
    age_list = info.get("Age List", [])
    quantity_list = info.get("Quantity Administered List", [])
    redose_list = info.get("Redose Quantity Administered List", [])
    date_admin_list = info.get("Date of Administration List", [])

    for i in range(4):  # 4 cycles
        row = info.copy()
        row["Cycle Index"] = i+1
        row["Cycle Administered"] = "yes" if i < len(cycle_indices) else "no"
        row["Age (Months)"] = age_list[i] if i < len(age_list) else ""
        row["Quantity Administered"] = quantity_list[i] if i < len(quantity_list) else 0
        row["Redose Quantity Administered"] = redose_list[i] if i < len(redose_list) else 0
        row["Date of Administration"] = date_admin_list[i] if i < len(date_admin_list) else ""
        all_rows.append(row)

df_exploded = pd.DataFrame(all_rows)
df_exploded = df_exploded.sort_values(by=["Child Name", "Cycle Index"]).reset_index(drop=True)

# === EXPORT TO EXCEL ===
output_file = f"{FILE_NAME}.xlsx"
output_path = os.path.join(os.getcwd(), output_file)

with pd.ExcelWriter(output_path, engine="openpyxl") as writer:
    df = pd.DataFrame(list(ind_id_vs_info.values()))
    df.to_excel(writer, index=False, sheet_name="Detailed Report")
    df_exploded.to_excel(writer, index=False, sheet_name="Child_Cycle_Mapping")

print(f"Report saved to: {output_path}")
