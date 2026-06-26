import json
import os
import warnings
import pandas as pd
import requests
from datetime import datetime


warnings.filterwarnings(
    "ignore",
    message="Unverified HTTPS request is being made.*"
)



AUTH_HEADER = {
    "Content-Type": "application/json",
    "Authorization": "Basic bm1lcC1raWJhbmEtcmVhZDpubWVwQDIwMjY=",
}
# ================= INDEX ENDPOINTS =================
ES_PROJECT_TASK_INDEX = "https://elasticsearch-data.es-cluster-v8:9200/tarabauat-project-task-index-v1/_search"
ES_INDIVIDUAL_INDEX = "https://elasticsearch-data.es-cluster-v8:9200/tarabauat-individual-index-v1/_search"
ES_SCROLL_API = "https://elasticsearch-data.es-cluster-v8:9200/_search/scroll"
ES_HOUSEHOLD_MEMBER_INDEX = "https://elasticsearch-data.es-cluster-v8:9200/tarabauat-household-member-index-v1/_search"
DECRYPT_URL = "http://egov-enc-service.egov:8080/egov-enc-service/crypto/v1/_decrypt"

# ================= DATE RANGE =================
# Used directly in the @timestamp range filter below.
gteTime = "2026-04-01T00:00:00.000Z"
lteTime = "2026-06-18T23:59:59.077Z"

# ================= OUTPUT LOCATION =================
# __file__ doesn't exist in a notebook; use the current working dir.
file_path = os.getcwd()

# ================= OUTPUT HOLDER =================
ind_id_vs_info = {}


# ================= REQUEST HELPER =================
# Uses the auth header sourced above. verify=False keeps the original
# behaviour of skipping cert validation on the internal cluster.
def get_resp(url, payload, _verify_flag=True):
    return requests.post(
        url,
        headers=AUTH_HEADER,
        data=json.dumps(payload),
        verify=False,
        timeout=120,
    )


# ================= DECRYPT FUNCTION =================
def decrypt_identifier(encrypted_id):
    try:
        payload = json.dumps(encrypted_id)
        response = requests.post(
            DECRYPT_URL,
            headers={"Content-Type": "application/json"},
            data=payload,
            verify=False,
            timeout=30,
        )
        if response.status_code == 200:
            return response.text.strip().replace('"', '')
    except Exception as e:
        print(f"Decryption error: {e}")
    return ""


# ================= TIMESTAMP CONVERTERS =================
def convert_ts_to_date(ts):
    try:
        return datetime.utcfromtimestamp(int(ts) / 1000).strftime('%Y-%m-%d')
    except Exception:
        return ""


def convert_ts_to_datetime(ts):
    # epoch millis -> 'dd-mm-yyyy HH:MM:SS' in UTC
    try:
        return datetime.utcfromtimestamp(
            int(ts) / 1000
        ).strftime('%d-%m-%Y %H:%M:%S')
    except Exception:
        return ""


# ================= FETCH PROJECT TASK DATA =================
def fetch_project_tasks():
    query = {
        "size": 6000,
        "query": {
            "bool": {
                "must": [
                    {"range": {"Data.@timestamp": {"gte": gteTime, "lte": lteTime}}},
                    {"terms": {"Data.administrationStatus.keyword": [
                        "ADMINISTRATION_SUCCESS", "INELIGIBLE",
                        "BENEFICIARY_REFERRED", "BENEFICIARY_REFUSED","BENEFICIARY_DIED",
                        "BENEFICIARY_ABSENT","VISITED","BENEFICIARY_MIGRATED"
                    ]}}
                ]
            }
        },
        "_source": [
            "Data.boundaryHierarchy", "Data.age", "Data.gender", "Data.individualId",
            "Data.userName", "Data.quantity", "Data.uniqueBeneficiaryID",
            "Data.administrationStatus", "Data.additionalDetails.reAdministered",
            "Data.taskDates", "Data.additionalDetails.dateOfAdministration",
            # ---- top-level fields ----
            "Data.latitude", "Data.longitude", "Data.locationAccuracy",
            "Data.productName", "Data.memberCount", "Data.createdBy",
            # ---- additionalDetails fields ----
            "Data.additionalDetails.gender", "Data.additionalDetails.ageInMonths",
            "Data.additionalDetails.cycleIndex", "Data.additionalDetails.memberCount",
            "Data.additionalDetails.taskType","Data.deliveryComments"
        ]
    }

    scroll_url = ES_PROJECT_TASK_INDEX + "?scroll=10m"
    scroll_id = None

    while True:
        if scroll_id is None:
            resp = get_resp(scroll_url, query, True).json()
        else:
            resp = get_resp(
                ES_SCROLL_API,
                {"scroll": "10m", "scroll_id": scroll_id},
                True
            ).json()

        scroll_id = resp.get("_scroll_id", "")
        hits = resp.get("hits", {}).get("hits", [])
        if not hits:
            break

        for doc in hits:
            data = doc["_source"]["Data"]
            indv_id = data.get("individualId")
            if not indv_id:
                continue

            additional = data.get("additionalDetails", {}) or {}

            if indv_id not in ind_id_vs_info:
                ind_id_vs_info[indv_id] = {
                    "country": data["boundaryHierarchy"].get("country", ""),
                    "state": data["boundaryHierarchy"].get("state", ""),
                    "lga": data["boundaryHierarchy"].get("lga", ""),
                    "Health Facility": data["boundaryHierarchy"].get("healthFacility", ""),
                    "community": data["boundaryHierarchy"].get("community", ""),
                    "Username": data.get("userName", ""),
                    "Child Name": "",
                    "Age": data.get("age", ""),
                    # ---- gender / ageInMonths from additionalDetails ----
                    "Gender": additional.get("gender", ""),
                    "Age In Months": additional.get("ageInMonths", ""),
                    # ---- location fields (top-level) ----
                    "Latitude": data.get("latitude", ""),
                    "Longitude": data.get("longitude", ""),
                    "Location Accuracy": data.get("locationAccuracy", ""),
                    # ---- product / cycle / member ----
                    "Product Name": data.get("productName", ""),
                    "Cycle Index": additional.get("cycleIndex", ""),
                    "Member Count": data.get(
                        "memberCount",
                        additional.get("memberCount", "")
                    ),
                    "Beneficiary ID (Child)": "",
                    "Household Client Reference ID": "",
                    "Household Head Name": "",
                    "Quantity Administered": 0,
                    "Redose Quantity Administered": 0,
                    "INELIGIBLE": "no",
                    "BENEFICIARY_REFERRED": "no",
                    "BENEFICIARY_REFUSED": "no",
                    "BENEFICIARY_ABSENT": "no",
                    "BENEFICIARY_DIED":"no",
                    "Date of Administration": data.get("taskDates", ""),
                    "UserUUid of User": data.get("createdBy", ""),
                    # populated later in the dispenser resolution step
                    "Dispenser": "",
                }

            status = data.get("administrationStatus", "")
            task_type = str(additional.get("taskType", "")).upper()
            delivery_comments = str(data.get("deliveryComments", "")).upper()

            is_redose = (task_type == "REDOSE"or delivery_comments == "REDOSE")

            if is_redose:
                ind_id_vs_info[indv_id]["Redose Quantity Administered"] += data.get("quantity", 0)
            elif status == "ADMINISTRATION_SUCCESS":
                ind_id_vs_info[indv_id]["Quantity Administered"] += data.get("quantity", 0)

            if status in ["INELIGIBLE", "BENEFICIARY_REFERRED",
                          "BENEFICIARY_REFUSED", "BENEFICIARY_ABSENT","BENEFICIARY_DIED"]:
                ind_id_vs_info[indv_id][status] = "yes"


# ================= ENRICH CHILD DETAILS =================
def enrich_child_info(individual_ids):
    query = {
        "size": 10000,
        "query": {"terms": {"clientReferenceId.keyword": individual_ids}},
        "_source": ["clientReferenceId", "name", "identifiers"]
    }
    resp = get_resp(ES_INDIVIDUAL_INDEX, query, True).json()
    return resp["hits"]["hits"]


# ================= FETCH HOUSEHOLD INFO =================
def fetch_household_links(child_client_refs):
    # Step 1: Match child to householdClientReferenceId
    query = {
        "size": 10000,
        "query": {
            "terms": {
                "Data.householdMember.individualClientReferenceId.keyword": child_client_refs
            }
        },
        "_source": [
            "Data.householdMember.householdClientReferenceId",
            "Data.householdMember.individualClientReferenceId"
        ]
    }
    resp = get_resp(ES_HOUSEHOLD_MEMBER_INDEX, query, True).json()
    child_to_household = {}
    for doc in resp["hits"]["hits"]:
        hh_member = doc["_source"]["Data"]["householdMember"]
        child_to_household[hh_member["individualClientReferenceId"]] = \
            hh_member["householdClientReferenceId"]
    return child_to_household


# ================= FETCH HEAD REFERENCE IDS =================
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
        "_source": [
            "Data.householdMember.householdClientReferenceId",
            "Data.householdMember.individualClientReferenceId"
        ]
    }
    resp = get_resp(ES_HOUSEHOLD_MEMBER_INDEX, query, True).json()
    hh_id_to_head_ref = {}
    for doc in resp["hits"]["hits"]:
        hh = doc["_source"]["Data"]["householdMember"]
        hh_id_to_head_ref[hh["householdClientReferenceId"]] = \
            hh["individualClientReferenceId"]
    return hh_id_to_head_ref


# ================= FETCH HEAD NAMES FROM INDIVIDUAL INDEX =================
def fetch_head_names_from_individuals(head_refs):
    query = {
        "size": 10000,
        "query": {"terms": {"clientReferenceId.keyword": head_refs}},
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


# ================= DISPENSER RESOLUTION (via userUuid) =================
# Two-step lookup, both keyed on userUuid.keyword:
#   1) createdBy UUID -> that individual's additionalFields team_mapping_*
#      Each team_mapping value is "<userUuid>,<epoch_millis>".
#   2) Each team_mapping userUuid -> givenName + familyName.
# Result per createdBy UUID: ["<name> dd-mm-yyyy HH:MM:SS", ...]

def fetch_individuals_by_user_uuid(user_uuids):
    """
    Look up individual records by userUuid.keyword (batched).
    Returns {userUuid: _source}.
    """
    out = {}
    user_uuids = [u for u in user_uuids if u]
    for i in range(0, len(user_uuids), 1000):
        batch = user_uuids[i:i + 1000]
        query = {
            "size": 10000,
            "query": {"terms": {"userUuid.keyword": batch}},
            "_source": ["userUuid", "name", "additionalFields"]
        }
        resp = get_resp(ES_INDIVIDUAL_INDEX, query, True).json()
        for doc in resp.get("hits", {}).get("hits", []):
            src = doc.get("_source", {})
            uuid = src.get("userUuid", "")
            if uuid:
                out[uuid] = src
    return out


def parse_team_mappings(additional_fields):
    """
    From an individual's additionalFields, pull every team_mapping_* value
    and split into (member_user_uuid, epoch_millis). Returns list of tuples.
    """
    pairs = []
    fields = (additional_fields or {}).get("fields", []) or []
    for f in fields:
        key = str(f.get("key", ""))
        if not key.startswith("team_mapping"):
            continue
        raw = str(f.get("value", ""))
        if "," not in raw:
            continue
        member_uuid, _, ts = raw.partition(",")
        pairs.append((member_uuid.strip(), ts.strip()))
    return pairs


def resolve_dispensers():
    """
    Populate the 'Dispenser' field for every child row using its
    'UserUUid of User' (the task createdBy).
    """
    # 1) collect all createdBy UUIDs across the report
    creator_uuids = {
        row.get("UserUUid of User", "")
        for row in ind_id_vs_info.values()
        if row.get("UserUUid of User", "")
    }
    if not creator_uuids:
        return

    # 2) look up the creator individuals -> their team mappings
    creators = fetch_individuals_by_user_uuid(list(creator_uuids))

    # creator_uuid -> list of (member_uuid, epoch)
    creator_to_pairs = {}
    member_uuids = set()
    for cu, src in creators.items():
        pairs = parse_team_mappings(src.get("additionalFields", {}))
        creator_to_pairs[cu] = pairs
        for member_uuid, _ in pairs:
            if member_uuid:
                member_uuids.add(member_uuid)

    # 3) resolve every team-mapping member UUID -> name
    members = fetch_individuals_by_user_uuid(list(member_uuids))
    member_uuid_to_name = {}
    for mu, src in members.items():
        name = src.get("name", {}) or {}
        full_name = (
            f"{name.get('givenName', '')} {name.get('familyName', '')}"
        ).replace("None", "").strip()
        member_uuid_to_name[mu] = full_name

    # 4) build the dispenser list per creator UUID
    creator_to_dispenser = {}
    for cu, pairs in creator_to_pairs.items():
        entries = []
        for member_uuid, ts in pairs:
            name = member_uuid_to_name.get(member_uuid, member_uuid)
            when = convert_ts_to_datetime(ts)
            entries.append(f"{name} {when}".strip())
        creator_to_dispenser[cu] = entries

    # 5) assign onto each child row
    for row in ind_id_vs_info.values():
        cu = row.get("UserUUid of User", "")
        row["Dispenser"] = creator_to_dispenser.get(cu, [])


# ================= EXECUTE STEPS =================
fetch_project_tasks()

chunks = [
    list(ind_id_vs_info.keys())[i:i + 1000]
    for i in range(0, len(ind_id_vs_info), 1000)
]

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

    # Step 1: child -> householdClientReferenceId
    child_to_household = fetch_household_links(chunk)

    # Step 2: householdClientReferenceId -> headClientReferenceId
    household_ids = list(child_to_household.values())
    hh_to_head_ref = fetch_household_head_reference_ids(household_ids)

    # Step 3: headClientReferenceId -> head name
    head_names = fetch_head_names_from_individuals(list(hh_to_head_ref.values()))

    # Assign final values into report
    for child_id in chunk:
        hh_id = child_to_household.get(child_id, "")
        ind_id_vs_info[child_id]["Household Client Reference ID"] = hh_id

        head_ref = hh_to_head_ref.get(hh_id, "")
        if head_ref:
            ind_id_vs_info[child_id]["Household Head Name"] = head_names.get(head_ref, "")

# ---- resolve dispensers from task createdBy via userUuid team mappings ----
resolve_dispensers()


# ================= EXPORT TO EXCEL =================
df = pd.DataFrame(list(ind_id_vs_info.values()))

# Dispenser is a list; render it as a readable bracketed string for Excel,
# e.g. [distributor 18-06-2026 04:02:01, bhanu 18-06-2026 06:02:01]
df["Dispenser"] = df["Dispenser"].apply(
    lambda v: "[" + ", ".join(v) + "]" if isinstance(v, list) else (v or "")
)

output_dir = os.path.join(file_path, "FINAL_REPORTS")
os.makedirs(output_dir, exist_ok=True)
output_file = os.path.join(output_dir, "CHILDREN_TREATED.xlsx")
df.to_excel(output_file, index=False)

print(f"Report saved to: {output_file}")
