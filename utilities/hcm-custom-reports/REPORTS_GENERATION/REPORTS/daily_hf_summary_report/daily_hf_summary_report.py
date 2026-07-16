import os
import sys
import warnings
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
ES_HF_REFERRAL_INDEX = es_index_url("hf-referral-index-v1")
ES_HHM_V1 = es_index_url("household-member-index-v1")
ES_SCROLL_API = es_scroll_url()
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
    try:
        return datetime.strptime(raw_date, "%Y-%m-%d").strftime("%d-%b")
    except Exception:
        return raw_date


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
    Fetch per-(userName, date) SPAQ/redose/ineligible metrics from project-task-index-v1.
    Also returns name_map (userName -> nameOfUser) via a sub-aggregation.
    Returns (result_dict, name_map).
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
                    "by_name": {
                        "terms": {"field": "Data.nameOfUser.keyword", "size": 1}
                    },
                    "by_date": {
                        "date_histogram": {
                            "field": "Data.taskDates",
                            "calendar_interval": "day",
                            "format": "yyyy-MM-dd",
                            "min_doc_count": 1
                        },
                        "aggs": {
                            "spaq1_total": {
                                "filter": {"bool": {"must": [
                                    {"term": {"Data.administrationStatus.keyword": "ADMINISTRATION_SUCCESS"}},
                                    {"term": {"Data.productName.keyword": "SPAQ 1"}}
                                ]}}
                            },
                            "spaq2_total": {
                                "filter": {"bool": {"must": [
                                    {"term": {"Data.administrationStatus.keyword": "ADMINISTRATION_SUCCESS"}},
                                    {"term": {"Data.productName.keyword": "SPAQ 2"}}
                                ]}}
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
    name_map = {}

    for user_bucket in resp.get("aggregations", {}).get("by_user", {}).get("buckets", []):
        recorder_username = user_bucket["key"]
        name_buckets = user_bucket.get("by_name", {}).get("buckets", [])
        name_map[recorder_username] = name_buckets[0]["key"] if name_buckets else ""
        for date_bucket in user_bucket.get("by_date", {}).get("buckets", []):
            raw_date = date_bucket["key_as_string"]
            result[(recorder_username, raw_date)] = {
                "spaq1_total":      date_bucket["spaq1_total"]["doc_count"],
                "spaq2_total":      date_bucket["spaq2_total"]["doc_count"],
                "redose_spaq1":     date_bucket["redose_spaq1"]["doc_count"],
                "redose_spaq2":     date_bucket["redose_spaq2"]["doc_count"],
                "ineligible_total": date_bucket["ineligible_total"]["doc_count"],
            }

    return result, name_map


def fetch_referral_metrics_for_hf(hf_info):
    """
    Fetch per-(userName, date) referral metrics from hf-referral-index-v1.

    Counts (referral totals, age splits, malaria test results, ec values) use aggregations
    on Data.additionalDetails.ageInMonths, feverQ2, feverQ5, and ec*Value fields.
    'Went to HF' age splits still require a scroll (clientAuditDetails.createdBy != lastModifiedBy).

    Returns dict keyed by (userName, raw_date yyyy-MM-dd).
    """
    hf_code = hf_info["hf_code"]

    base_must = [
        {"term": {CAMPAIGN_FILTER_FIELD: CAMPAIGN_IDENTIFIER}},
        {"range": {"Data.hfReferral.auditDetails.lastModifiedTime": {"gte": gteTime, "lte": lteTime}}},
        {"term": {"Data.boundaryHierarchyCode.healthFacility.keyword": hf_code}},
    ]

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
                            "fever_pos_3_11": {
                                "filter": {"bool": {"must": [
                                    {"term": {"Data.additionalDetails.feverQ2.keyword": "POSITIVE"}},
                                    {"range": {"Data.additionalDetails.ageInMonths": {"gte": 3, "lte": 11}}}
                                ]}}
                            },
                            "fever_pos_12_59": {
                                "filter": {"bool": {"must": [
                                    {"term": {"Data.additionalDetails.feverQ2.keyword": "POSITIVE"}},
                                    {"range": {"Data.additionalDetails.ageInMonths": {"gte": 12, "lte": 59}}}
                                ]}}
                            },
                            "fever_neg_spaq_3_11": {
                                "filter": {"bool": {"must": [
                                    {"term": {"Data.additionalDetails.feverQ2.keyword": "NEGATIVE"}},
                                    {"term": {"Data.additionalDetails.feverQ5.keyword": "YES"}},
                                    {"range": {"Data.additionalDetails.ageInMonths": {"gte": 3, "lte": 11}}}
                                ]}}
                            },
                            "fever_neg_spaq_12_59": {
                                "filter": {"bool": {"must": [
                                    {"term": {"Data.additionalDetails.feverQ2.keyword": "NEGATIVE"}},
                                    {"term": {"Data.additionalDetails.feverQ5.keyword": "YES"}},
                                    {"range": {"Data.additionalDetails.ageInMonths": {"gte": 12, "lte": 59}}}
                                ]}}
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
                "ref_3_11":             date_bucket["ref_3_11"]["doc_count"],
                "ref_12_59":            date_bucket["ref_12_59"]["doc_count"],
                "ref_went_hf_3_11":     0,
                "ref_went_hf_12_59":    0,
                "fever_pos_3_11":       date_bucket["fever_pos_3_11"]["doc_count"],
                "fever_pos_12_59":      date_bucket["fever_pos_12_59"]["doc_count"],
                "fever_neg_spaq_3_11":  date_bucket["fever_neg_spaq_3_11"]["doc_count"],
                "fever_neg_spaq_12_59": date_bucket["fever_neg_spaq_12_59"]["doc_count"],
                "spaq_taken_4weeks":    date_bucket["spaq_taken_4weeks"]["doc_count"],
                "with_fever":           date_bucket["with_fever"]["doc_count"],
                "very_sick":            date_bucket["very_sick"]["doc_count"],
                "signs_allergies":      date_bucket["signs_allergies"]["doc_count"],
            }

    # Scroll for age-split "went to HF": clientAuditDetails.createdBy != lastModifiedBy
    scroll_query = {
        "size": 5000,
        "query": {"bool": {"must": base_must}},
        "_source": [
            "Data.userName",
            "Data.taskDates",
            "Data.additionalDetails.ageInMonths",
            "Data.hfReferral.clientAuditDetails",
        ]
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
            if key not in result:
                continue
            client_audit = data.get("hfReferral", {}).get("clientAuditDetails", {})
            created_by = client_audit.get("createdBy", "")
            last_modified_by = client_audit.get("lastModifiedBy", "")
            if not (created_by and last_modified_by and created_by != last_modified_by):
                continue
            try:
                age = int(data.get("additionalDetails", {}).get("ageInMonths") or 0)
            except (ValueError, TypeError):
                age = 0
            if 3 <= age <= 11:
                result[key]["ref_went_hf_3_11"] += 1
            elif 12 <= age <= 59:
                result[key]["ref_went_hf_12_59"] += 1

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
    """Combine all metrics for a single HF into report rows."""
    lga = hf_info["lga"]
    ward = hf_info["ward"]
    health_facility = hf_info["healthFacility"]

    spaq_metrics, name_map = fetch_spaq_metrics_for_hf(hf_info)
    referral_metrics = fetch_referral_metrics_for_hf(hf_info)
    eligible_metrics = fetch_eligible_for_hf(hf_info)

    all_keys = set(spaq_metrics.keys()) | set(referral_metrics.keys()) | set(eligible_metrics.keys())
    rows = []

    empty_spaq = {
        "spaq1_total": 0, "spaq2_total": 0,
        "redose_spaq1": 0, "redose_spaq2": 0,
        "ineligible_total": 0,
    }
    empty_ref = {
        "ref_3_11": 0, "ref_12_59": 0,
        "ref_went_hf_3_11": 0, "ref_went_hf_12_59": 0,
        "fever_pos_3_11": 0, "fever_pos_12_59": 0,
        "fever_neg_spaq_3_11": 0, "fever_neg_spaq_12_59": 0,
        "spaq_taken_4weeks": 0, "with_fever": 0, "very_sick": 0, "signs_allergies": 0,
    }
    empty_eligible = {"eligible_total": 0, "eligible_3_11": 0, "eligible_12_59": 0}

    for (recorder_username, raw_date) in all_keys:
        spaq = spaq_metrics.get((recorder_username, raw_date), empty_spaq)
        ref = referral_metrics.get((recorder_username, raw_date), empty_ref)
        eligible = eligible_metrics.get((recorder_username, raw_date), empty_eligible)

        rows.append({
            "LGA": lga,
            "Ward": ward,
            "Health Facility": health_facility,
            "Date of Visit": format_date(raw_date),
            "CDD Name": name_map.get(recorder_username, ""),
            "CDD Username": recorder_username,
            "Number of Children who Received SPAQ 3 to <12 Months": spaq["spaq1_total"],
            "Number of Children who Received SPAQ 12 to 59 Months": spaq["spaq2_total"],
            "Number of Children who were Re-Dosed with SPAQ 3 to <12 Months": spaq["redose_spaq1"],
            "Number of Children who were Re-Dosed with SPAQ 12 to 59 Months": spaq["redose_spaq2"],
            "Number of Children who were Referred 3 to <12 Months": ref["ref_3_11"],
            "Number of Children who were Referred 12 to 59 Months": ref["ref_12_59"],
            "Number of children REFERRED who PRESENTED at the HF 3 to <12 Months": ref["ref_went_hf_3_11"],
            "Number of children REFERRED who PRESENTED at the HF 12 to 59 Months": ref["ref_went_hf_12_59"],
            "Number of children REFERRED with FEVER who tested POSITIVE for malaria 3 to <12 Months": ref["fever_pos_3_11"],
            "Number of children REFERRED with FEVER who tested POSITIVE for malaria 12 to 59 Months": ref["fever_pos_12_59"],
            "Number of children REFERRED with FEVER who tested NEGATIVE for malaria and given SPAQ 3 to <12 Months": ref["fever_neg_spaq_3_11"],
            "Number of children REFERRED with FEVER who tested NEGATIVE for malaria and given SPAQ 12 to 59 Months": ref["fever_neg_spaq_12_59"],
            "Number of Children ineligible": spaq["ineligible_total"],
            "Number of Children eligible": eligible["eligible_total"],
            "Number of Children eligible 3 to 11 month": eligible["eligible_3_11"],
            "Number of Children eligible 12 to 59 month": eligible["eligible_12_59"],
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

# === EXPORT TO EXCEL ===
df = pd.DataFrame(all_rows, columns=[
    "LGA", "Ward", "Health Facility", "Date of Visit", "CDD Name", "CDD Username",
    "Number of Children who Received SPAQ 3 to <12 Months",
    "Number of Children who Received SPAQ 12 to 59 Months",
    "Number of Children who were Re-Dosed with SPAQ 3 to <12 Months",
    "Number of Children who were Re-Dosed with SPAQ 12 to 59 Months",
    "Number of Children who were Referred 3 to <12 Months",
    "Number of Children who were Referred 12 to 59 Months",
    "Number of children REFERRED who PRESENTED at the HF 3 to <12 Months",
    "Number of children REFERRED who PRESENTED at the HF 12 to 59 Months",
    "Number of children REFERRED with FEVER who tested POSITIVE for malaria 3 to <12 Months",
    "Number of children REFERRED with FEVER who tested POSITIVE for malaria 12 to 59 Months",
    "Number of children REFERRED with FEVER who tested NEGATIVE for malaria and given SPAQ 3 to <12 Months",
    "Number of children REFERRED with FEVER who tested NEGATIVE for malaria and given SPAQ 12 to 59 Months",
    "Number of Children ineligible",
    "Number of Children eligible",
    "Number of Children eligible 3 to 11 month",
    "Number of Children eligible 12 to 59 month",
    "Number of Children SPAQ Taken in Last 4 Weeks",
    "Number of Children with Fever",
    "Number of Children Very Sick",
    "Number of Children Show Signs Allergies (ADR)",
])

output_file = f"{FILE_NAME}.xlsx"
output_path = os.path.join(os.getcwd(), output_file)
df.to_excel(output_path, index=False)

print(f"Report saved to: {output_path} ({len(all_rows)} rows)")
