import os
import sys
import warnings
import json
import requests
import pandas as pd
import argparse
from datetime import datetime

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
ES_PROJECT_TASK_INDEX = es_index_url("project-task-index-v1")
ES_INDIVIDUAL_INDEX   = es_index_url("individual-index-v1")
ES_HHM_V1             = es_index_url("household-member-index-v1")
ES_STAFF_INDEX        = es_index_url("project-staff-index-v1")
ES_SCROLL_API         = es_scroll_url()
DECRYPT_URL           = os.getenv('DECRYPT_URL', 'http://egov-enc-service.egov:8080/egov-enc-service/crypto/v1/_decrypt')
INDIVIDUAL_HOST       = os.getenv('INDIVIDUAL_HOST', 'http://individual.egov:8080')
AUTH_TOKEN            = os.getenv('AUTH_TOKEN', '')
TENANT_ID             = os.getenv('TENANT_ID', '')
SYSTEM_USER_UUID      = os.getenv('SYSTEM_USER_UUID', '')

SCROLL_TIME      = "10m"
BATCH_SIZE       = 5000
USER_BUCKET_SIZE = 1000

RELEVANT_STATUSES = [
    "ADMINISTRATION_SUCCESS", "INELIGIBLE",
    "BENEFICIARY_REFERRED", "BENEFICIARY_REFUSED",
    "BENEFICIARY_DIED", "BENEFICIARY_ABSENT", "VISITED", "BENEFICIARY_MIGRATED",
]

# === DATE RANGE ===
lteTime, gteTime, start_date_str, end_date_str = get_custom_dates_of_reports(START_DATE, END_DATE)

# === CAMPAIGN FILTER ===
if IDENTIFIER_TYPE == "projectTypeId":
    CAMPAIGN_FILTER_FIELD = "Data.projectTypeId.keyword"
    print(f"Using projectTypeId filter: {CAMPAIGN_IDENTIFIER}")
else:
    CAMPAIGN_FILTER_FIELD = "Data.campaignNumber.keyword"
    print(f"Using campaignNumber filter: {CAMPAIGN_IDENTIFIER}")

# === OUTPUT HOLDER ===
ind_id_vs_info = {}


# === HELPERS ===
def epoch_millis_to_date(ts):
    try:
        return datetime.utcfromtimestamp(int(ts) / 1000).strftime('%Y-%m-%d')
    except Exception:
        return ""


def convert_ts_to_datetime(ts):
    try:
        return datetime.utcfromtimestamp(int(ts) / 1000).strftime('%d-%m-%Y %H:%M:%S')
    except Exception:
        return ""


def decrypt_identifier(encrypted_id):
    try:
        response = requests.post(
            DECRYPT_URL,
            headers={"Content-Type": "application/json"},
            data=json.dumps(encrypted_id),
            verify=False,
            timeout=30,
        )
        if response.status_code == 200:
            return response.text.strip().replace('"', '')
    except Exception as e:
        print(f"Decryption error: {e}")
    return ""


def scroll_all(index_url, query):
    scroll_url = f"{index_url}?scroll={SCROLL_TIME}"
    scroll_id = None
    while True:
        if scroll_id is None:
            resp = get_resp(scroll_url, query, True).json()
        else:
            resp = get_resp(ES_SCROLL_API, {"scroll": SCROLL_TIME, "scroll_id": scroll_id}, True).json()
        scroll_id = resp.get("_scroll_id", "")
        hits = resp.get("hits", {}).get("hits", [])
        if not hits:
            break
        for doc in hits:
            yield doc["_source"]["Data"]


# === FETCH PROJECT TASK DATA ===
def fetch_project_tasks():
    query = {
        "size": BATCH_SIZE,
        "query": {
            "bool": {
                "must": [
                    {"term": {CAMPAIGN_FILTER_FIELD: CAMPAIGN_IDENTIFIER}},
                    {"range": {"Data.lastModifiedTime": {"gte": gteTime, "lte": lteTime}}},
                    {"terms": {"Data.administrationStatus.keyword": RELEVANT_STATUSES}},
                ]
            }
        },
        "_source": [
            "Data.boundaryHierarchy",
            "Data.age", "Data.gender", "Data.individualId",
            "Data.userName", "Data.quantity", "Data.uniqueBeneficiaryID",
            "Data.administrationStatus", "Data.additionalDetails.reAdministered",
            "Data.taskDates", "Data.additionalDetails.dateOfAdministration",
            "Data.latitude", "Data.longitude", "Data.locationAccuracy",
            "Data.productName", "Data.memberCount", "Data.createdBy",
            "Data.additionalDetails.gender", "Data.additionalDetails.ageInMonths",
            "Data.additionalDetails.cycleIndex", "Data.additionalDetails.memberCount",
            "Data.additionalDetails.taskType", "Data.deliveryComments",
        ]
    }

    for data in scroll_all(ES_PROJECT_TASK_INDEX, query):
        indv_id = data.get("individualId")
        if not indv_id:
            continue

        additional = data.get("additionalDetails", {}) or {}
        task_dates = data.get("taskDates", "")
        # taskDates may be a list or a single string
        if isinstance(task_dates, list):
            task_date = task_dates[0] if task_dates else ""
        else:
            task_date = task_dates

        if indv_id not in ind_id_vs_info:
            boundary = data.get("boundaryHierarchy", {}) or {}
            ind_id_vs_info[indv_id] = {
                "Country":                       boundary.get("country", ""),
                "State":                         boundary.get("state", ""),
                "LGA":                           boundary.get("lga", ""),
                "Health Facility":               boundary.get("healthFacility", ""),
                "Community":                     boundary.get("community", ""),
                "Username":                      data.get("userName", ""),
                "Child Name":                    "",
                "Age":                           data.get("age", ""),
                "Gender":                        additional.get("gender", ""),
                "Age In Months":                 additional.get("ageInMonths", ""),
                "Latitude":                      data.get("latitude", ""),
                "Longitude":                     data.get("longitude", ""),
                "Location Accuracy":             data.get("locationAccuracy", ""),
                "Product Name":                  data.get("productName", ""),
                "Cycle Index":                   additional.get("cycleIndex", ""),
                "Member Count":                  data.get("memberCount", additional.get("memberCount", "")),
                "Beneficiary ID (Child)":        "",
                "Household Client Reference ID": "",
                "Household Head Name":           "",
                "Quantity Administered":         0,
                "Redose Quantity Administered":  0,
                "INELIGIBLE":                    "no",
                "BENEFICIARY_REFERRED":          "no",
                "BENEFICIARY_REFUSED":           "no",
                "BENEFICIARY_ABSENT":            "no",
                "BENEFICIARY_DIED":              "no",
                "Date of Administration":        task_date,
                "_creator_uuid":                 data.get("createdBy", ""),
                "Dispenser":                     "",
            }

        status = data.get("administrationStatus", "")
        task_type = str(additional.get("taskType", "")).upper()
        delivery_comments = str(data.get("deliveryComments", "")).upper()
        is_redose = (task_type == "REDOSE" or delivery_comments == "REDOSE")

        if is_redose:
            ind_id_vs_info[indv_id]["Redose Quantity Administered"] += data.get("quantity", 0)
        elif status == "ADMINISTRATION_SUCCESS":
            ind_id_vs_info[indv_id]["Quantity Administered"] += data.get("quantity", 0)

        if status in ["INELIGIBLE", "BENEFICIARY_REFERRED",
                      "BENEFICIARY_REFUSED", "BENEFICIARY_ABSENT", "BENEFICIARY_DIED"]:
            ind_id_vs_info[indv_id][status] = "yes"

    print(f"Fetched {len(ind_id_vs_info)} unique beneficiaries from project-task-index")


# === ENRICH CHILD DETAILS ===
def enrich_child_info(individual_ids):
    query = {
        "size": USER_BUCKET_SIZE,
        "query": {"terms": {"clientReferenceId.keyword": individual_ids}},
        "_source": ["clientReferenceId", "name", "identifiers"],
    }
    resp = get_resp(ES_INDIVIDUAL_INDEX, query, True).json()
    return resp.get("hits", {}).get("hits", [])


# === FETCH HOUSEHOLD LINKS ===
def fetch_household_links(child_client_refs):
    query = {
        "size": USER_BUCKET_SIZE,
        "query": {
            "terms": {
                "Data.householdMember.individualClientReferenceId.keyword": child_client_refs
            }
        },
        "_source": [
            "Data.householdMember.householdClientReferenceId",
            "Data.householdMember.individualClientReferenceId",
        ],
    }
    resp = get_resp(ES_HHM_V1, query, True).json()
    child_to_household = {}
    for doc in resp.get("hits", {}).get("hits", []):
        hh_member = doc["_source"]["Data"]["householdMember"]
        child_to_household[hh_member["individualClientReferenceId"]] = \
            hh_member["householdClientReferenceId"]
    return child_to_household


# === FETCH HEAD REFERENCE IDs ===
def fetch_household_head_reference_ids(household_ids):
    query = {
        "size": USER_BUCKET_SIZE,
        "query": {
            "bool": {
                "must": [
                    {"terms": {"Data.householdMember.householdClientReferenceId.keyword": household_ids}},
                    {"term": {"Data.householdMember.isHeadOfHousehold": True}},
                ]
            }
        },
        "_source": [
            "Data.householdMember.householdClientReferenceId",
            "Data.householdMember.individualClientReferenceId",
        ],
    }
    resp = get_resp(ES_HHM_V1, query, True).json()
    hh_id_to_head_ref = {}
    for doc in resp.get("hits", {}).get("hits", []):
        hh = doc["_source"]["Data"]["householdMember"]
        hh_id_to_head_ref[hh["householdClientReferenceId"]] = \
            hh["individualClientReferenceId"]
    return hh_id_to_head_ref


# === FETCH HEAD NAMES FROM INDIVIDUAL INDEX ===
def fetch_head_names_from_individuals(head_refs):
    query = {
        "size": USER_BUCKET_SIZE,
        "query": {"terms": {"clientReferenceId.keyword": head_refs}},
        "_source": ["clientReferenceId", "name"],
    }
    resp = get_resp(ES_INDIVIDUAL_INDEX, query, True).json()
    head_id_to_name = {}
    for doc in resp.get("hits", {}).get("hits", []):
        src = doc["_source"]
        name = src.get("name", {}) or {}
        full_name = f"{name.get('givenName', '')} {name.get('familyName', '')}".replace("None", "").strip()
        head_id_to_name[src["clientReferenceId"]] = full_name
    return head_id_to_name


# === DISPENSER RESOLUTION ===
def fetch_individual_team_mappings(user_uuids):
    """
    Call Individual API for given userUuids (CDD/recorder UUIDs).
    Returns {userUuid: [(dispenser_uuid, date_str_yyyy_MM_dd), ...]}
    """
    if not user_uuids:
        return {}
    result = {}
    user_uuids = [u for u in user_uuids if u]
    for i in range(0, len(user_uuids), 200):
        batch = user_uuids[i:i + 200]
        url = (f"{INDIVIDUAL_HOST}/individual/v1/_search"
               f"?limit=1000&offset=0&tenantId={TENANT_ID}&includeDeleted=false")
        payload = {
            "RequestInfo": {
                "authToken": AUTH_TOKEN,
                "userInfo": {"uuid": SYSTEM_USER_UUID},
            },
            "Individual": {"userUuid": batch},
        }
        try:
            resp = requests.post(
                url,
                json=payload,
                headers={"Content-Type": "application/json"},
                verify=False,
                timeout=30,
            )
            if resp.status_code != 200:
                print(f"Individual API HTTP {resp.status_code} for batch starting at {i}")
                continue
            for individual in resp.json().get("Individual", []):
                uuid = individual.get("userUuid", "")
                if not uuid:
                    continue
                pairs = []
                fields = (individual.get("additionalFields") or {}).get("fields", []) or []
                for f in fields:
                    if not str(f.get("key", "")).startswith("team_mapping"):
                        continue
                    value = str(f.get("value", ""))
                    if "," not in value:
                        continue
                    dispenser_uuid, _, ts = value.partition(",")
                    date_str = epoch_millis_to_date(ts.strip())
                    if dispenser_uuid.strip() and date_str:
                        pairs.append((dispenser_uuid.strip(), date_str))
                result[uuid] = pairs
        except Exception as e:
            print(f"Individual API error (batch {i}): {e}")
    return result


def fetch_staff_username_by_userids(user_ids):
    """Returns {userId: userName} from project-staff-index-v1."""
    if not user_ids:
        return {}
    results = {}
    user_ids = [u for u in user_ids if u]
    for i in range(0, len(user_ids), 1000):
        batch = user_ids[i:i + 1000]
        query = {
            "size": len(batch) * 3,
            "query": {"terms": {"Data.userId.keyword": batch}},
            "_source": ["Data.userId", "Data.userName"],
        }
        try:
            resp = get_resp(ES_STAFF_INDEX, query, True).json()
            for doc in resp.get("hits", {}).get("hits", []):
                data = doc["_source"].get("Data", {})
                uid = data.get("userId", "")
                uname = data.get("userName", "")
                if uid and uname and uid not in results:
                    results[uid] = uname
        except Exception as e:
            print(f"Staff index lookup error: {e}")
    return results


def resolve_dispensers():
    """
    For each beneficiary row, populate 'Dispenser' with comma-separated
    usernames of dispensers active on that record's Date of Administration.

    Steps:
      1. Collect unique creator UUIDs (_creator_uuid) from all rows.
      2. Hit Individual API to get team_mapping entries per creator.
      3. Match team_mapping date to the row's Date of Administration.
      4. Look up matched dispenser UUIDs in staff index for their userName.
      5. Write comma-separated usernames into the row's 'Dispenser' field.
    """
    creator_uuids = list({
        row["_creator_uuid"]
        for row in ind_id_vs_info.values()
        if row.get("_creator_uuid")
    })
    if not creator_uuids:
        return

    print(f"Resolving dispensers for {len(creator_uuids)} creator UUID(s)...")
    team_mappings = fetch_individual_team_mappings(creator_uuids)

    # Build (creator_uuid, date) → set of dispenser UUIDs
    creator_date_to_dispenser_uuids = {}
    all_dispenser_uuids = set()
    for creator_uuid, pairs in team_mappings.items():
        for dispenser_uuid, date_str in pairs:
            key = (creator_uuid, date_str)
            creator_date_to_dispenser_uuids.setdefault(key, set()).add(dispenser_uuid)
            all_dispenser_uuids.add(dispenser_uuid)

    uuid_to_username = fetch_staff_username_by_userids(list(all_dispenser_uuids))

    for row in ind_id_vs_info.values():
        creator_uuid = row.get("_creator_uuid", "")
        record_date = row.get("Date of Administration", "")
        dispenser_uuids = creator_date_to_dispenser_uuids.get((creator_uuid, record_date), set())
        usernames = sorted(filter(None, (uuid_to_username.get(u, "") for u in dispenser_uuids)))
        row["Dispenser"] = ", ".join(usernames)


# === MAIN EXECUTION ===
print("Fetching project task data...")
fetch_project_tasks()

chunks = [
    list(ind_id_vs_info.keys())[i:i + 1000]
    for i in range(0, len(ind_id_vs_info), 1000)
]

print(f"Enriching {len(ind_id_vs_info)} beneficiaries in {len(chunks)} chunk(s)...")
for chunk in chunks:
    enriched = enrich_child_info(chunk)

    for doc in enriched:
        src = doc["_source"]
        indv_id = src["clientReferenceId"]
        name_obj = src.get("name", {}) or {}
        full_name = f"{name_obj.get('givenName', '')} {name_obj.get('familyName', '')}".replace("None", "").strip()
        ind_id_vs_info[indv_id]["Child Name"] = full_name

        for ident in src.get("identifiers", []):
            if ident.get("identifierType") == "UNIQUE_BENEFICIARY_ID":
                ind_id_vs_info[indv_id]["Beneficiary ID (Child)"] = decrypt_identifier(ident.get("identifierId"))
                break

    child_to_household = fetch_household_links(chunk)
    household_ids = list(child_to_household.values())
    hh_to_head_ref = fetch_household_head_reference_ids(household_ids)
    head_names = fetch_head_names_from_individuals(list(hh_to_head_ref.values()))

    for child_id in chunk:
        hh_id = child_to_household.get(child_id, "")
        ind_id_vs_info[child_id]["Household Client Reference ID"] = hh_id
        head_ref = hh_to_head_ref.get(hh_id, "")
        if head_ref:
            ind_id_vs_info[child_id]["Household Head Name"] = head_names.get(head_ref, "")

print("Resolving dispensers...")
resolve_dispensers()

# === EXPORT TO EXCEL ===
df = pd.DataFrame(list(ind_id_vs_info.values()), columns=[
    "Country", "State", "LGA", "Health Facility", "Community",
    "Username", "Child Name", "Age", "Gender", "Age In Months",
    "Latitude", "Longitude", "Location Accuracy",
    "Product Name", "Cycle Index", "Member Count",
    "Beneficiary ID (Child)", "Household Client Reference ID", "Household Head Name",
    "Quantity Administered", "Redose Quantity Administered",
    "INELIGIBLE", "BENEFICIARY_REFERRED", "BENEFICIARY_REFUSED",
    "BENEFICIARY_ABSENT", "BENEFICIARY_DIED",
    "Date of Administration", "Dispenser",
])

output_file = f"{FILE_NAME}.xlsx"
output_path = os.path.join(os.getcwd(), output_file)
df.to_excel(output_path, index=False)

print(f"Report saved to: {output_path} ({len(df)} rows)")
