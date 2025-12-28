import json
import os
import sys
import warnings
import requests
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

# ===== Setup project path =====
file_path = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.append(file_path)

warnings.filterwarnings("ignore", message="Unverified HTTPS request is being made.*")

from COMMON_UTILS.custom_date_utils import get_custom_dates_of_reports
from COMMON_UTILS.common_utils import get_resp

# ===== Elasticsearch Endpoints =====
ES_PROJECT_TASK_SEARCH = "https://elasticsearch-data.es-cluster-v8:9200/project-task-index-v1/_search"
ES_HOUSEHOLD_MEMBER_SEARCH = "https://elasticsearch-data.es-cluster-v8:9200/household-member-index-v1/_search"
ES_INDIVIDUAL_SEARCH = "https://elasticsearch-data.es-cluster-v8:9200/individual-index-v1/_search"
EGOV_DECRYPT_URL = "http://egov-enc-service.egov:8080/egov-enc-service/crypto/v1/_decrypt"

# ===== Get date range for report and folder naming =====
gte_time, lte_time, gte_iso, lte_iso = get_custom_dates_of_reports(START_DATE, END_DATE)

# === CAMPAIGN FILTER FIELD ===
if IDENTIFIER_TYPE == "projectTypeId":
    CAMPAIGN_FILTER_FIELD = "Data.projectTypeId.keyword"
    print(f"Using projectTypeId filter: {CAMPAIGN_IDENTIFIER}")
else:
    CAMPAIGN_FILTER_FIELD = "Data.campaignNumber.keyword"
    print(f"Using campaignNumber filter: {CAMPAIGN_IDENTIFIER}")


# ==========================================================
#                 SAFE DECRYPT FUNCTION
# ==========================================================
def decrypt_value(encrypted_value):
    """Safely decrypt a value using the eGov encryption service."""
    if not encrypted_value or encrypted_value in ["Unknown", "null"]:
        return encrypted_value
    try:
        # Handle all possible types (dict, int, str)
        if isinstance(encrypted_value, dict):
            value_to_decrypt = encrypted_value.get("encryptedValue") or encrypted_value.get("value")
        elif isinstance(encrypted_value, (int, float)):
            # Already numeric — not encrypted
            return str(encrypted_value)
        elif isinstance(encrypted_value, str):
            value_to_decrypt = encrypted_value.strip()
        else:
            print(f"[WARN] Unexpected type for encrypted_value: {type(encrypted_value)}")
            return encrypted_value

        if not value_to_decrypt:
            return encrypted_value

        headers = {"Content-Type": "application/json"}
        payload = json.dumps({"value": value_to_decrypt})
        response = requests.post(EGOV_DECRYPT_URL, headers=headers, data=payload, verify=False)

        if response.status_code == 200:
            try:
                data = response.json()
                # eGov responses can return either 'decryptedValue' or 'value'
                return data.get("decryptedValue") or data.get("value") or encrypted_value
            except json.JSONDecodeError:
                # Some versions return plain text
                return response.text.strip().strip('"')
        else:
            print(f"[WARN] Decryption failed (HTTP {response.status_code}): {encrypted_value}")
            return encrypted_value

    except Exception as e:
        print(f"[ERROR] Exception during decryption: {e}")
        return encrypted_value


# ==========================================================
#                 FETCH PROJECT TASKS
# ==========================================================
def fetch_project_tasks():
    print("[INFO] Fetching project-task data using Scroll API")
    collected_data = {}

    # Fix: use ISO UTC format
    gte_es = gte_iso.replace(" ", "T").replace("+01:00", "Z").replace("+0100", "Z")
    lte_es = lte_iso.replace(" ", "T").replace("+01:00", "Z").replace("+0100", "Z")
    print(f"[DEBUG] Using ISO date range: {gte_es} → {lte_es}")

    query = {
        "size": 10000,
        "query": {
            "bool": {
                "must": [
                    {"term": {CAMPAIGN_FILTER_FIELD: CAMPAIGN_IDENTIFIER}},
                    {"term": {"Data.administrationStatus.keyword": "ADMINISTRATION_SUCCESS"}},
                    {"range": {"Data.@timestamp": {"gte": gte_es, "lte": lte_es}}}
                ]
            }
        },
        "_source": [
            "Data.boundaryHierarchy", "Data.householdId", "Data.userName", "Data.nameOfUser",
            "Data.createdTime", "Data.taskDates", "Data.memberCount", "Data.additionalDetails.children",
            "Data.additionalDetails.pregnantWomen", "Data.latitude", "Data.longitude",
            "Data.quantity", "Data.@timestamp"
        ]
    }

    url = ES_PROJECT_TASK_SEARCH + "?scroll=2m"
    resp = get_resp(url, query, is_post=True, es=True)
    if not resp:
        return {}

    scroll_id = resp.json().get("_scroll_id")
    hits = resp.json().get("hits", {}).get("hits", [])

    while hits:
        for hit in hits:
            data = hit["_source"]["Data"]
            household_id = data.get("householdId")
            if not household_id:
                continue
            collected_data[household_id] = {
                "householdId": household_id,
                "userName": data.get("userName", "null"),
                "nameOfUser": data.get("nameOfUser", "null"),
                "taskDates": data.get("taskDates", "null"),
                "memberCount": data.get("memberCount", 0),
                "children": data.get("additionalDetails", {}).get("children", 0),
                "pregnantWomen": data.get("additionalDetails", {}).get("pregnantWomen", 0),
                "latitude": data.get("latitude", "null"),
                "longitude": data.get("longitude", "null"),
                "quantity": data.get("quantity", 0),
                "boundary": data.get("boundaryHierarchy", {}),
            }

        scroll_resp = get_resp(
            "https://elasticsearch-data.es-cluster-v8:9200/_search/scroll",
            {"scroll": "2m", "scroll_id": scroll_id},
            is_post=True,
            es=True
        )
        if not scroll_resp:
            break

        scroll_id = scroll_resp.json().get("_scroll_id")
        hits = scroll_resp.json().get("hits", {}).get("hits", [])

    print(f"[INFO] Retrieved {len(collected_data)} project-task records")
    return collected_data


# ==========================================================
#        ENRICH WITH HOUSEHOLD HEAD DETAILS
# ==========================================================
def enrich_with_household_head_name(task_data):
    household_ids = list(task_data.keys())
    household_head_map = {}
    population_type_map = {}
    household_head_age_map = {}

    for i in range(0, len(household_ids), 10000):
        chunk = household_ids[i:i + 10000]
        query = {
            "size": 10000,
            "query": {
                "bool": {
                    "must": [
                        {"terms": {"Data.householdMember.householdClientReferenceId.keyword": chunk}},
                        {"term": {"Data.householdMember.isHeadOfHousehold": True}}
                    ]
                }
            },
            "_source": [
                "Data.householdMember.householdClientReferenceId",
                "Data.householdMember.individualClientReferenceId",
                "Data.additionalDetails.populationType",
                "Data.age"
            ]
        }
        resp = get_resp(ES_HOUSEHOLD_MEMBER_SEARCH, query, is_post=True, es=True)
        if not resp:
            continue

        for hit in resp.json()["hits"]["hits"]:
            d = hit["_source"]["Data"]
            household_id = d["householdMember"]["householdClientReferenceId"]
            individual_id = d["householdMember"]["individualClientReferenceId"]
            population_type = d.get("additionalDetails", {}).get("populationType", "Unknown")
            age = d.get("age", "Unknown")

            household_head_map[household_id] = individual_id
            population_type_map[household_id] = population_type
            household_head_age_map[household_id] = age

    print(f"[INFO] Found {len(household_head_map)} household heads")

    individual_ids = list(household_head_map.values())
    individual_info_map = {}

    for i in range(0, len(individual_ids), 10000):
        chunk = individual_ids[i:i + 10000]
        query = {
            "size": 10000,
            "query": {"terms": {"clientReferenceId.keyword": chunk}},
            "_source": [
                "name.givenName", "name.familyName",
                "clientReferenceId", "gender", "mobileNumber",
                "additionalFields.fields", "identifiers"
            ]
        }
        resp = get_resp(ES_INDIVIDUAL_SEARCH, query, is_post=True, es=True)
        if not resp:
            continue

        for hit in resp.json()["hits"]["hits"]:
            d = hit["_source"]
            name = d.get("name", {})
            full_name = f"{(name.get('givenName') or '').strip()} {(name.get('familyName') or '').strip()}".strip()
            gender = d.get("gender", "Unknown")
            raw_mobile = d.get("mobileNumber", "Unknown")
            decrypted_mobile = decrypt_value(raw_mobile)

            nationality_type = "Unknown"
            for field in d.get("additionalFields", {}).get("fields", []):
                if field.get("key") == "nationalityType":
                    nationality_type = field.get("value", "Unknown")
                    break

            individual_info_map[d["clientReferenceId"]] = {
                "full_name": full_name or "Unknown",
                "gender": gender,
                "nationalityType": nationality_type,
                "mobileNumber": decrypted_mobile,
            }

    for household_id in task_data:
        individual_id = household_head_map.get(household_id)
        info = individual_info_map.get(individual_id, {})
        task_data[household_id]["householdHeadName"] = info.get("full_name", "Unknown")
        task_data[household_id]["populationType"] = population_type_map.get(household_id, "Unknown")
        task_data[household_id]["gender"] = info.get("gender", "Unknown")
        task_data[household_id]["nationalityType"] = info.get("nationalityType", "Unknown")
        task_data[household_id]["mobileNumber"] = info.get("mobileNumber", "Unknown")
        task_data[household_id]["headOfHouseholdAge"] = household_head_age_map.get(household_id, "Unknown")

    return task_data


# ==========================================================
#                  GENERATE REPORT
# ==========================================================
def generate_report():
    print("[INFO] Generating report...")
    task_data = fetch_project_tasks()
    task_data = enrich_with_household_head_name(task_data)

    wb = Workbook()
    ws = wb.active
    ws.append([
        "Province", "District", "Administrative Province", "Locality", "Village",
        "Latitude", "Longitude",
        "Username", "Name of User", "taskDates", "Household Head Name", "Household ID",
        "Head of Household Age", "Gender", "Mobile Number", "Nationality Type", "Population Type",
        "Member Count", "PregnantWomen", "Children", "Quantity"
    ])

    for data in task_data.values():
        bh = data.get("boundary", {})
        row = [
            bh.get("province", ""),
            bh.get("district", ""),
            bh.get("administrativeProvince", ""),
            bh.get("locality", ""),
            bh.get("village", ""),
            data.get("latitude", "null"),
            data.get("longitude", "null"),
            data.get("userName", "null"),
            data.get("nameOfUser", "null"),
            data.get("taskDates", "null"),
            data.get("householdHeadName", "Unknown"),
            data.get("householdId", "null"),
            data.get("headOfHouseholdAge", "Unknown"),
            data.get("gender", "Unknown"),
            data.get("mobileNumber", "Unknown"),
            data.get("nationalityType", "Unknown"),
            data.get("populationType", "Unknown"),
            data.get("memberCount", 0),
            data.get("pregnantWomen", 0),
            data.get("children", 0),
            data.get("quantity", 0)
        ]
        ws.append(row)

    # Use FILE_NAME from command line args
    output_file = f"{FILE_NAME}.xlsx"
    output_path = os.path.join(os.getcwd(), output_file)
    wb.save(output_path)
    print(f"[INFO] Report saved at: {output_path}")


# ==========================================================
#                    MAIN EXECUTION
# ==========================================================
if __name__ == "__main__":
    try:
        print(f"Reports start date: {gte_iso}")
        print(f"Reports end date: {lte_iso}")
        generate_report()
        print("[SUCCESS] Report generation completed.")
    except Exception as e:
        print("[ERROR] Report generation failed:", e)
