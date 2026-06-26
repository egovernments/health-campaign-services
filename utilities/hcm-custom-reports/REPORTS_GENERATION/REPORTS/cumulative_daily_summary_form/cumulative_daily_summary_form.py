import os
import sys
import warnings
import requests
import pandas as pd
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
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
ES_HF_REFERRAL_INDEX  = es_index_url("hf-referral-index-v1")
ES_HHM_V1             = es_index_url("household-member-index-v1")
ES_STAFF_INDEX        = es_index_url("project-staff-index-v1")
ES_SCROLL_API         = es_scroll_url()
INDIVIDUAL_HOST       = os.getenv('INDIVIDUAL_HOST', 'http://individual.egov:8080')
AUTH_TOKEN            = os.getenv('AUTH_TOKEN', '')
TENANT_ID             = os.getenv('TENANT_ID', '')
SYSTEM_USER_UUID      = os.getenv('SYSTEM_USER_UUID', '')
THREAD_POOL_SIZE = 5
USER_BUCKET_SIZE = 100

# === DATE RANGE ===
lteTime, gteTime, start_date_str, end_date_str = get_custom_dates_of_reports(START_DATE, END_DATE)

# === CAMPAIGN FILTER ===
if IDENTIFIER_TYPE == "projectTypeId":
    CAMPAIGN_FILTER_FIELD = "Data.projectTypeId.keyword"
    print(f"Using projectTypeId filter: {CAMPAIGN_IDENTIFIER}")
else:
    CAMPAIGN_FILTER_FIELD = "Data.campaignNumber.keyword"
    print(f"Using campaignNumber filter: {CAMPAIGN_IDENTIFIER}")

RELEVANT_STATUSES = ["ADMINISTRATION_SUCCESS", "VISITED", "INELIGIBLE"]


def format_date(raw_date):
    """Convert yyyy-MM-dd to dd-MMM (e.g. 2026-06-18 → 18-Jun)."""
    try:
        return datetime.strptime(raw_date, "%Y-%m-%d").strftime("%d-%b")
    except Exception:
        return raw_date


def epoch_millis_to_date(ts):
    try:
        return datetime.utcfromtimestamp(int(ts) / 1000).strftime('%Y-%m-%d')
    except Exception:
        return ""


def fetch_staff_userid_by_usernames(usernames):
    """Returns {userName: userId} from project-staff-index-v1."""
    if not usernames:
        return {}
    results = {}
    usernames = [u for u in usernames if u]
    for i in range(0, len(usernames), 1000):
        batch = usernames[i:i + 1000]
        query = {
            "size": len(batch) * 3,
            "query": {"terms": {"Data.userName.keyword": batch}},
            "_source": ["Data.userId", "Data.userName"],
        }
        try:
            resp = get_resp(ES_STAFF_INDEX, query, True).json()
            for doc in resp.get("hits", {}).get("hits", []):
                data = doc["_source"].get("Data", {})
                uname = data.get("userName", "")
                uid = data.get("userId", "")
                if uname and uid and uname not in results:
                    results[uname] = uid
        except Exception as e:
            print(f"Staff index username lookup error: {e}")
    return results


def fetch_individual_team_mappings(user_uuids):
    """
    Call Individual API for given userUuids (recorder/CDD UUIDs).
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
                print(f"Individual API HTTP {resp.status_code} for batch {i}")
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
            print(f"Staff index userId lookup error: {e}")
    return results


def fill_dispenser_usernames(all_rows):
    """
    Resolve 'Dispenser Username' for every row in-place.

    Steps:
      1. Collect unique recorder usernames and look up their userId via staff index.
      2. Call Individual API with those userIds to get team_mapping entries.
      3. Match team_mapping date to each row's _raw_date.
      4. Look up matched dispenser UUIDs in staff index for their userName.
      5. Write comma-separated usernames into 'Dispenser Username'.
    """
    recorder_usernames = list({r["Recorder Username"] for r in all_rows if r.get("Recorder Username")})
    if not recorder_usernames:
        return

    print(f"Resolving dispensers for {len(recorder_usernames)} recorder username(s)...")

    username_to_userid = fetch_staff_userid_by_usernames(recorder_usernames)
    creator_uuids = list(set(username_to_userid.values()))
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

    for row in all_rows:
        recorder = row.get("Recorder Username", "")
        raw_date = row.get("_raw_date", "")
        creator_uuid = username_to_userid.get(recorder, "")
        dispenser_uuids = creator_date_to_dispenser_uuids.get((creator_uuid, raw_date), set())
        usernames = sorted(filter(None, (uuid_to_username.get(u, "") for u in dispenser_uuids)))
        row["Dispenser Username"] = ", ".join(usernames)


def discover_hf_combinations():
    """Discover all unique (state, lga, ward, healthFacility, hf_code) via composite aggregation."""
    results = []
    after_key = None

    while True:
        sources = [
            {"state": {"terms": {"field": "Data.boundaryHierarchy.state.keyword"}}},
            {"lga": {"terms": {"field": "Data.boundaryHierarchy.lga.keyword"}}},
            {"ward": {"terms": {"field": "Data.boundaryHierarchy.ward.keyword"}}},
            {"healthFacility": {"terms": {"field": "Data.boundaryHierarchy.healthFacility.keyword"}}},
            {"hf_code": {"terms": {"field": "Data.boundaryHierarchyCode.healthFacility.keyword"}}}
        ]

        composite = {"size": 1000, "sources": sources}
        if after_key:
            composite["after"] = after_key

        query = {
            "size": 0,
            "query": {
                "bool": {
                    "must": [
                        {"term": {CAMPAIGN_FILTER_FIELD: CAMPAIGN_IDENTIFIER}},
                        {"range": {"Data.lastModifiedTime": {"gte": gteTime, "lte": lteTime}}},
                        {"terms": {"Data.administrationStatus.keyword": RELEVANT_STATUSES}}
                    ]
                }
            },
            "aggs": {"composite_agg": {"composite": composite}}
        }

        resp = get_resp(ES_PROJECT_TASK_INDEX, query, True).json()
        agg = resp.get("aggregations", {}).get("composite_agg", {})
        buckets = agg.get("buckets", [])

        if not buckets:
            break

        for bucket in buckets:
            results.append(bucket["key"])

        after_key = agg.get("after_key")
        if not after_key:
            break

    print(f"Found {len(results)} HF combinations")
    return results


def fetch_spaq_metrics_for_hf(hf_info):
    """
    Fetch per-(userName, date) SPAQ and redose metrics for a single HF via aggregations.
    Returns dict keyed by (userName, raw_date yyyy-MM-dd).
    """
    hf_code = hf_info["hf_code"]

    query = {
        "size": 0,
        "query": {
            "bool": {
                "must": [
                    {"term": {CAMPAIGN_FILTER_FIELD: CAMPAIGN_IDENTIFIER}},
                    {"range": {"Data.lastModifiedTime": {"gte": gteTime, "lte": lteTime}}},
                    {"term": {"Data.boundaryHierarchyCode.healthFacility.keyword": hf_code}},
                    {"terms": {"Data.administrationStatus.keyword": RELEVANT_STATUSES}}
                ]
            }
        },
        "aggs": {
            "by_user": {
                "terms": {"field": "Data.userName.keyword", "size": USER_BUCKET_SIZE},
                "aggs": {
                    "by_date": {
                        "date_histogram": {
                            "field": "Data.taskDates",
                            "calendar_interval": "day",
                            "format": "yyyy-MM-dd",
                            "min_doc_count": 1
                        },
                        "aggs": {
                            "spaq_total": {
                                "filter": {"term": {"Data.administrationStatus.keyword": "ADMINISTRATION_SUCCESS"}}
                            },
                            "spaq1_total": {
                                "filter": {"bool": {"must": [
                                    {"term": {"Data.administrationStatus.keyword": "ADMINISTRATION_SUCCESS"}},
                                    {"term": {"Data.productName.keyword": "SPAQ 1"}}
                                ]}}
                            },
                            "spaq1_male": {
                                "filter": {"bool": {"must": [
                                    {"term": {"Data.administrationStatus.keyword": "ADMINISTRATION_SUCCESS"}},
                                    {"term": {"Data.productName.keyword": "SPAQ 1"}},
                                    {"term": {"Data.additionalDetails.gender.keyword": "MALE"}}
                                ]}}
                            },
                            "spaq1_female": {
                                "filter": {"bool": {"must": [
                                    {"term": {"Data.administrationStatus.keyword": "ADMINISTRATION_SUCCESS"}},
                                    {"term": {"Data.productName.keyword": "SPAQ 1"}},
                                    {"term": {"Data.additionalDetails.gender.keyword": "FEMALE"}}
                                ]}}
                            },
                            "spaq2_total": {
                                "filter": {"bool": {"must": [
                                    {"term": {"Data.administrationStatus.keyword": "ADMINISTRATION_SUCCESS"}},
                                    {"term": {"Data.productName.keyword": "SPAQ 2"}}
                                ]}}
                            },
                            "spaq2_male": {
                                "filter": {"bool": {"must": [
                                    {"term": {"Data.administrationStatus.keyword": "ADMINISTRATION_SUCCESS"}},
                                    {"term": {"Data.productName.keyword": "SPAQ 2"}},
                                    {"term": {"Data.additionalDetails.gender.keyword": "MALE"}}
                                ]}}
                            },
                            "spaq2_female": {
                                "filter": {"bool": {"must": [
                                    {"term": {"Data.administrationStatus.keyword": "ADMINISTRATION_SUCCESS"}},
                                    {"term": {"Data.productName.keyword": "SPAQ 2"}},
                                    {"term": {"Data.additionalDetails.gender.keyword": "FEMALE"}}
                                ]}}
                            },
                            "redose_total": {
                                "filter": {"term": {"Data.administrationStatus.keyword": "VISITED"}}
                            },
                            "redose_spaq1": {
                                "filter": {"bool": {"must": [
                                    {"term": {"Data.administrationStatus.keyword": "VISITED"}},
                                    {"term": {"Data.productName.keyword": "SPAQ 1"}}
                                ]}}
                            },
                            "redose_spaq2": {
                                "filter": {"bool": {"must": [
                                    {"term": {"Data.administrationStatus.keyword": "VISITED"}},
                                    {"term": {"Data.productName.keyword": "SPAQ 2"}}
                                ]}}
                            },
                            "ineligible_total": {
                                "filter": {"term": {"Data.administrationStatus.keyword": "INELIGIBLE"}}
                            }
                        }
                    }
                }
            }
        }
    }

    resp = get_resp(ES_PROJECT_TASK_INDEX, query, True).json()
    result = {}

    for user_bucket in resp.get("aggregations", {}).get("by_user", {}).get("buckets", []):
        recorder_username = user_bucket["key"]
        for date_bucket in user_bucket.get("by_date", {}).get("buckets", []):
            raw_date = date_bucket["key_as_string"]
            result[(recorder_username, raw_date)] = {
                "spaq_total":       date_bucket["spaq_total"]["doc_count"],
                "spaq1_total":      date_bucket["spaq1_total"]["doc_count"],
                "spaq1_male":       date_bucket["spaq1_male"]["doc_count"],
                "spaq1_female":     date_bucket["spaq1_female"]["doc_count"],
                "spaq2_total":      date_bucket["spaq2_total"]["doc_count"],
                "spaq2_male":       date_bucket["spaq2_male"]["doc_count"],
                "spaq2_female":     date_bucket["spaq2_female"]["doc_count"],
                "redose_total":     date_bucket["redose_total"]["doc_count"],
                "redose_spaq1":     date_bucket["redose_spaq1"]["doc_count"],
                "redose_spaq2":     date_bucket["redose_spaq2"]["doc_count"],
                "ineligible_total": date_bucket["ineligible_total"]["doc_count"],
            }

    return result


def fetch_referral_metrics_for_hf(hf_info):
    """
    Fetch referral metrics for a single HF from hf-referral-index-v1.

    Counts (ref_total, age splits, ec values) are computed via aggregations using
    Data.additionalDetails.ageInMonths (numeric) and ec*Value fields directly.
    'Went to HF' still requires a scroll (clientAuditDetails.createdBy != lastModifiedBy).

    Returns dict keyed by (userName, raw_date yyyy-MM-dd).
    """
    hf_code = hf_info["hf_code"]

    base_must = [
        {"term": {CAMPAIGN_FILTER_FIELD: CAMPAIGN_IDENTIFIER}},
        {"range": {"Data.hfReferral.auditDetails.lastModifiedTime": {"gte": gteTime, "lte": lteTime}}},
        {"term": {"Data.boundaryHierarchyCode.healthFacility.keyword": hf_code}},
    ]

    # Aggregation for all count metrics
    agg_query = {
        "size": 0,
        "query": {"bool": {"must": base_must}},
        "aggs": {
            "by_user": {
                "terms": {"field": "Data.userName.keyword", "size": USER_BUCKET_SIZE},
                "aggs": {
                    "by_date": {
                        "date_histogram": {
                            "field": "Data.taskDates",
                            "calendar_interval": "day",
                            "format": "yyyy-MM-dd",
                            "min_doc_count": 1
                        },
                        "aggs": {
                            "ref_3_11": {
                                "filter": {"range": {"Data.additionalDetails.ageInMonths": {"gte": 3, "lte": 11}}}
                            },
                            "ref_12_59": {
                                "filter": {"range": {"Data.additionalDetails.ageInMonths": {"gte": 12, "lte": 59}}}
                            },
                            "spaq_taken_4weeks": {
                                "filter": {"term": {"Data.additionalDetails.ec5Value.keyword": "YES"}}
                            },
                            "with_fever": {
                                "filter": {"term": {"Data.additionalDetails.ec2Value.keyword": "YES"}}
                            },
                            "very_sick": {
                                "filter": {"term": {"Data.additionalDetails.ec1Value.keyword": "YES"}}
                            },
                            "signs_allergies": {
                                "filter": {"term": {"Data.additionalDetails.ec4Value.keyword": "YES"}}
                            }
                        }
                    }
                }
            }
        }
    }

    result = {}
    resp = get_resp(ES_HF_REFERRAL_INDEX, agg_query, True).json()
    for user_bucket in resp.get("aggregations", {}).get("by_user", {}).get("buckets", []):
        recorder_username = user_bucket["key"]
        for date_bucket in user_bucket.get("by_date", {}).get("buckets", []):
            raw_date = date_bucket["key_as_string"]
            result[(recorder_username, raw_date)] = {
                "ref_total":         date_bucket["doc_count"],
                "ref_3_11":          date_bucket["ref_3_11"]["doc_count"],
                "ref_12_59":         date_bucket["ref_12_59"]["doc_count"],
                "ref_went_hf":       0,
                "spaq_taken_4weeks": date_bucket["spaq_taken_4weeks"]["doc_count"],
                "with_fever":        date_bucket["with_fever"]["doc_count"],
                "very_sick":         date_bucket["very_sick"]["doc_count"],
                "signs_allergies":   date_bucket["signs_allergies"]["doc_count"],
            }

    # Scroll only for "went to HF": clientAuditDetails.createdBy != lastModifiedBy
    scroll_query = {
        "size": 5000,
        "query": {"bool": {"must": base_must}},
        "_source": ["Data.userName", "Data.taskDates", "Data.hfReferral.clientAuditDetails"]
    }
    scroll_url = ES_HF_REFERRAL_INDEX + "?scroll=5m"
    scroll_id = None
    while True:
        if scroll_id is None:
            resp = get_resp(scroll_url, scroll_query, True).json()
        else:
            resp = get_resp(ES_SCROLL_API, {"scroll": "5m", "scroll_id": scroll_id}, True).json()
        scroll_id = resp.get("_scroll_id", "")
        hits = resp.get("hits", {}).get("hits", [])
        if not hits:
            break
        for doc in hits:
            data = doc["_source"]["Data"]
            key = (data.get("userName", ""), data.get("taskDates", ""))
            client_audit = data.get("hfReferral", {}).get("clientAuditDetails", {})
            created_by = client_audit.get("createdBy", "")
            last_modified_by = client_audit.get("lastModifiedBy", "")
            if created_by and last_modified_by and created_by != last_modified_by:
                if key in result:
                    result[key]["ref_went_hf"] += 1

    return result


def fetch_eligible_for_hf(hf_info):
    """
    Fetch per-(userName, date) eligible children counts from household-member-index-v1.
    Eligible = isHeadOfHousehold false, age 3-59 months.
    Returns dict keyed by (userName, raw_date yyyy-MM-dd).
    """
    hf_code = hf_info["hf_code"]

    query = {
        "size": 0,
        "query": {
            "bool": {
                "must": [
                    {"term": {CAMPAIGN_FILTER_FIELD: CAMPAIGN_IDENTIFIER}},
                    {"range": {"Data.householdMember.auditDetails.lastModifiedTime": {"gte": gteTime, "lte": lteTime}}},
                    {"term": {"Data.boundaryHierarchyCode.healthFacility.keyword": hf_code}},
                    {"term": {"Data.householdMember.isHeadOfHousehold": False}},
                    {"range": {"Data.age": {"gte": 3, "lte": 59}}},
                ]
            }
        },
        "aggs": {
            "by_user": {
                "terms": {"field": "Data.userName.keyword", "size": USER_BUCKET_SIZE},
                "aggs": {
                    "by_date": {
                        "date_histogram": {
                            "field": "Data.taskDates",
                            "calendar_interval": "day",
                            "format": "yyyy-MM-dd",
                            "min_doc_count": 1
                        },
                        "aggs": {
                            "eligible_3_11": {
                                "filter": {"range": {"Data.age": {"gte": 3, "lte": 11}}}
                            },
                            "eligible_12_59": {
                                "filter": {"range": {"Data.age": {"gte": 12, "lte": 59}}}
                            }
                        }
                    }
                }
            }
        }
    }

    resp = get_resp(ES_HHM_V1, query, True).json()
    result = {}

    for user_bucket in resp.get("aggregations", {}).get("by_user", {}).get("buckets", []):
        recorder_username = user_bucket["key"]
        for date_bucket in user_bucket.get("by_date", {}).get("buckets", []):
            raw_date = date_bucket["key_as_string"]
            result[(recorder_username, raw_date)] = {
                "eligible_total": date_bucket["doc_count"],
                "eligible_3_11":  date_bucket["eligible_3_11"]["doc_count"],
                "eligible_12_59": date_bucket["eligible_12_59"]["doc_count"],
            }

    return result


def process_hf(hf_info):
    """Combine SPAQ and referral metrics for a single HF into report rows."""
    state = hf_info["state"]
    lga = hf_info["lga"]
    ward = hf_info["ward"]
    health_facility = hf_info["healthFacility"]

    spaq_metrics = fetch_spaq_metrics_for_hf(hf_info)
    referral_metrics = fetch_referral_metrics_for_hf(hf_info)
    eligible_metrics = fetch_eligible_for_hf(hf_info)

    all_keys = set(spaq_metrics.keys()) | set(referral_metrics.keys()) | set(eligible_metrics.keys())
    rows = []

    empty_spaq = {
        "spaq_total": 0, "spaq1_total": 0, "spaq1_male": 0, "spaq1_female": 0,
        "spaq2_total": 0, "spaq2_male": 0, "spaq2_female": 0,
        "redose_total": 0, "redose_spaq1": 0, "redose_spaq2": 0,
        "ineligible_total": 0,
    }
    empty_ref = {
        "ref_total": 0, "ref_3_11": 0, "ref_12_59": 0, "ref_went_hf": 0,
        "spaq_taken_4weeks": 0, "with_fever": 0, "very_sick": 0, "signs_allergies": 0,
    }
    empty_eligible = {"eligible_total": 0, "eligible_3_11": 0, "eligible_12_59": 0}

    for (recorder_username, raw_date) in all_keys:
        spaq = spaq_metrics.get((recorder_username, raw_date), empty_spaq)
        ref = referral_metrics.get((recorder_username, raw_date), empty_ref)
        eligible = eligible_metrics.get((recorder_username, raw_date), empty_eligible)

        rows.append({
            "State": state,
            "LGA": lga,
            "Ward": ward,
            "Health Facility": health_facility,
            "Recorder Username": recorder_username,
            "Dispenser Username": "",
            "_raw_date": raw_date,
            "Admin Day-Month": format_date(raw_date),
            "Number of Children who received SPAQ": spaq["spaq_total"],
            "Number of Children who received SPAQ1": spaq["spaq1_total"],
            "Number of Male Children who received SPAQ1": spaq["spaq1_male"],
            "Number of Female Children who received SPAQ1": spaq["spaq1_female"],
            "Number of Children who received SPAQ2": spaq["spaq2_total"],
            "Number of Male Children who received SPAQ2": spaq["spaq2_male"],
            "Number of Female Children who received SPAQ2": spaq["spaq2_female"],
            "Number of Children who were RE-DOSED with SPAQ": spaq["redose_total"],
            "Number of Children who were RE-DOSED with SPAQ 3 to 11 month": spaq["redose_spaq1"],
            "Number of Children who were RE-DOSED with SPAQ 12 to 59 month": spaq["redose_spaq2"],
            "Number of Children ineligible": spaq["ineligible_total"],
            "Number of Children eligible": eligible["eligible_total"],
            "Number of Children eligible 3 to 11 month": eligible["eligible_3_11"],
            "Number of Children eligible 12 to 59 month": eligible["eligible_12_59"],
            "Number of Children REFERRED to HF": ref["ref_total"],
            "Number of Children REFERRED to HF 3 to 11 month": ref["ref_3_11"],
            "Number of Children REFERRED to HF 12 to 59 month": ref["ref_12_59"],
            "Number of Children REFERRED and Went to Health Facility": ref["ref_went_hf"],
            "Number of Children SPAQ Taken in Last 4 Weeks": ref["spaq_taken_4weeks"],
            "Number of Children with Fever": ref["with_fever"],
            "Number of Children Very Sick": ref["very_sick"],
            "Number of Children Show Signs Allergies (ADR)": ref["signs_allergies"],
        })

    return rows


# === MAIN EXECUTION ===
print("Discovering HF combinations...")
hf_combinations = discover_hf_combinations()

all_rows = []

print(f"Processing {len(hf_combinations)} HF combinations with {THREAD_POOL_SIZE} threads...")
with ThreadPoolExecutor(max_workers=THREAD_POOL_SIZE) as executor:
    futures = {executor.submit(process_hf, hf): hf for hf in hf_combinations}
    for future in as_completed(futures):
        hf = futures[future]
        try:
            rows = future.result()
            all_rows.extend(rows)
            print(f"Processed HF: {hf.get('healthFacility', '')} — {len(rows)} row(s)")
        except Exception as e:
            print(f"Error processing HF {hf.get('healthFacility', hf)}: {e}")

# === DISPENSER RESOLUTION ===
fill_dispenser_usernames(all_rows)

# === EXPORT TO EXCEL ===
df = pd.DataFrame(all_rows, columns=[
    "State", "LGA", "Ward", "Health Facility",
    "Recorder Username", "Dispenser Username", "Admin Day-Month",
    "Number of Children who received SPAQ",
    "Number of Children who received SPAQ1",
    "Number of Male Children who received SPAQ1",
    "Number of Female Children who received SPAQ1",
    "Number of Children who received SPAQ2",
    "Number of Male Children who received SPAQ2",
    "Number of Female Children who received SPAQ2",
    "Number of Children who were RE-DOSED with SPAQ",
    "Number of Children who were RE-DOSED with SPAQ 3 to 11 month",
    "Number of Children who were RE-DOSED with SPAQ 12 to 59 month",
    "Number of Children ineligible",
    "Number of Children eligible",
    "Number of Children eligible 3 to 11 month",
    "Number of Children eligible 12 to 59 month",
    "Number of Children REFERRED to HF",
    "Number of Children REFERRED to HF 3 to 11 month",
    "Number of Children REFERRED to HF 12 to 59 month",
    "Number of Children REFERRED and Went to Health Facility",
    "Number of Children SPAQ Taken in Last 4 Weeks",
    "Number of Children with Fever",
    "Number of Children Very Sick",
    "Number of Children Show Signs Allergies (ADR)",
])

output_file = f"{FILE_NAME}.xlsx"
output_path = os.path.join(os.getcwd(), output_file)
df.to_excel(output_path, index=False)

print(f"Report saved to: {output_path} ({len(all_rows)} rows)")
