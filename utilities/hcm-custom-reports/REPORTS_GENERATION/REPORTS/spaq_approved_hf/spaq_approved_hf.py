import os
import sys
import warnings
import pandas as pd
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed

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
ES_STOCK_UPDATED_V1 = es_index_url("stock-index-updated-v1")
THREAD_POOL_SIZE = 5
USER_BUCKET_SIZE = 500

# === DATE RANGE ===
lteTime, gteTime, start_date_str, end_date_str = get_custom_dates_of_reports(START_DATE, END_DATE)

# === CAMPAIGN FILTER ===
if IDENTIFIER_TYPE == "projectTypeId":
    CAMPAIGN_FILTER_FIELD = "Data.projectTypeId.keyword"
    print(f"Using projectTypeId filter: {CAMPAIGN_IDENTIFIER}")
else:
    CAMPAIGN_FILTER_FIELD = "Data.campaignNumber.keyword"
    print(f"Using campaignNumber filter: {CAMPAIGN_IDENTIFIER}")


# === BASE FILTERS (reused in every query) ===
BASE_MUST = [
    {"term": {CAMPAIGN_FILTER_FIELD: CAMPAIGN_IDENTIFIER}},
    {"range": {"Data.createdTime": {"gte": gteTime, "lte": lteTime}}},
    {"term": {"Data.additionalDetails.status.keyword": "ACCEPTED"}},
    {"term": {"Data.facilityType.keyword": "STAFF"}},
    {"term": {"Data.transactingFacilityType.keyword": "Health Facility"}},
]


def discover_campaign_dates():
    """
    Query stock-index-updated-v1 for all distinct Data.taskDates that have at least
    one matching document. Returns a sorted list of date strings (yyyy-MM-dd).
    """
    query = {
        "size": 0,
        "query": {"bool": {"must": BASE_MUST}},
        "aggs": {
            "distinct_dates": {
                "terms": {
                    "field": "Data.taskDates",
                    "size": 366,
                    "min_doc_count": 1,
                    "order": {"_key": "asc"},
                }
            }
        },
    }
    resp = get_resp(ES_STOCK_UPDATED_V1, query, True).json()
    dates = [
        bucket["key"]
        for bucket in resp.get("aggregations", {}).get("distinct_dates", {}).get("buckets", [])
    ]
    print(f"Discovered {len(dates)} campaign dates: {dates}")
    return dates


CAMPAIGN_DATES = discover_campaign_dates()


def discover_hf_combinations():
    """
    Discover all unique (state, lga, ward, healthFacility, hf_code) combinations
    from stock-index-updated-v1 via composite aggregation.
    """
    results = []
    after_key = None

    while True:
        sources = [
            {"state":         {"terms": {"field": "Data.boundaryHierarchy.state.keyword"}}},
            {"lga":           {"terms": {"field": "Data.boundaryHierarchy.lga.keyword"}}},
            {"ward":          {"terms": {"field": "Data.boundaryHierarchy.ward.keyword"}}},
            {"healthFacility":{"terms": {"field": "Data.boundaryHierarchy.healthFacility.keyword"}}},
            {"hf_code":       {"terms": {"field": "Data.boundaryHierarchyCode.healthFacility.keyword"}}},
        ]

        composite = {"size": 1000, "sources": sources}
        if after_key:
            composite["after"] = after_key

        query = {
            "size": 0,
            "query": {"bool": {"must": BASE_MUST}},
            "aggs": {"composite_agg": {"composite": composite}},
        }

        resp = get_resp(ES_STOCK_UPDATED_V1, query, True).json()
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


def fetch_metrics_for_hf(hf_info):
    """
    For a single HF, aggregate by user → taskDate → productName with physicalCount sum.
    Returns dict keyed by userName → {nameOfUser, dates: {date → {spaq1, spaq2}}}.
    """
    hf_code = hf_info["hf_code"]

    query = {
        "size": 0,
        "query": {
            "bool": {
                "must": BASE_MUST + [
                    {"term": {"Data.boundaryHierarchyCode.healthFacility.keyword": hf_code}}
                ]
            }
        },
        "aggs": {
            "by_user": {
                "terms": {"field": "Data.userName.keyword", "size": USER_BUCKET_SIZE},
                "aggs": {
                    "name_of_user": {
                        "top_hits": {
                            "size": 1,
                            "_source": ["Data.nameOfUser"]
                        }
                    },
                    "by_date": {
                        "terms": {"field": "Data.taskDates", "size": 200},
                        "aggs": {
                            "spaq1_sum": {
                                "filter": {"term": {"Data.productName.keyword": "SPAQ 1"}},
                                "aggs": {"qty": {"sum": {"field": "Data.physicalCount"}}}
                            },
                            "spaq2_sum": {
                                "filter": {"term": {"Data.productName.keyword": "SPAQ 2"}},
                                "aggs": {"qty": {"sum": {"field": "Data.physicalCount"}}}
                            }
                        }
                    }
                }
            }
        }
    }

    resp = get_resp(ES_STOCK_UPDATED_V1, query, True).json()
    result = {}

    for user_bucket in resp.get("aggregations", {}).get("by_user", {}).get("buckets", []):
        username = user_bucket["key"]

        top_hits = user_bucket.get("name_of_user", {}).get("hits", {}).get("hits", [])
        name_of_user = ""
        if top_hits:
            name_of_user = top_hits[0].get("_source", {}).get("Data", {}).get("nameOfUser", "")

        date_data = {}
        for date_bucket in user_bucket.get("by_date", {}).get("buckets", []):
            task_date = date_bucket["key"]
            spaq1 = date_bucket["spaq1_sum"]["qty"]["value"] or 0
            spaq2 = date_bucket["spaq2_sum"]["qty"]["value"] or 0
            date_data[task_date] = {"spaq1": spaq1, "spaq2": spaq2}

        result[username] = {"nameOfUser": name_of_user, "dates": date_data}

    return result


def process_hf(hf_info):
    """Build report rows for a single HF — one row per (HF, user)."""
    state = hf_info["state"]
    lga = hf_info["lga"]
    ward = hf_info["ward"]
    health_facility = hf_info["healthFacility"]

    user_metrics = fetch_metrics_for_hf(hf_info)
    rows = []

    for username, info in user_metrics.items():
        name_of_user = info["nameOfUser"]
        date_data = info["dates"]
        worked_dates = sorted(date_data.keys())

        row = {
            "State": state,
            "LGA": lga,
            "Ward": ward,
            "Health Facility": health_facility,
            "OIC Full Name": name_of_user,
            "OIC Phone Number": "",
            "OIC Username": username,
        }

        total_spaq1 = 0
        total_spaq2 = 0

        for d in CAMPAIGN_DATES:
            spaq1 = date_data.get(d, {}).get("spaq1", 0)
            spaq2 = date_data.get(d, {}).get("spaq2", 0)
            row[f"Sum of Approved SPAQ1 ({d})"] = spaq1
            row[f"Sum of Approved SPAQ2 ({d})"] = spaq2
            total_spaq1 += spaq1
            total_spaq2 += spaq2

        row["Sum of Approved SPAQ1 (COMPLETE)"] = total_spaq1
        row["Sum of Approved SPAQ2 (COMPLETE)"] = total_spaq2
        row["Start Day"] = worked_dates[0] if worked_dates else ""
        row["End Day"] = worked_dates[-1] if worked_dates else ""
        row["No of Worked Days"] = len(worked_dates)

        rows.append(row)

    return rows


def build_column_order():
    cols = ["State", "LGA", "Ward", "Health Facility",
            "OIC Full Name", "OIC Phone Number", "OIC Username"]
    for d in CAMPAIGN_DATES:
        cols.append(f"Sum of Approved SPAQ1 ({d})")
        cols.append(f"Sum of Approved SPAQ2 ({d})")
    cols += [
        "Sum of Approved SPAQ1 (COMPLETE)",
        "Sum of Approved SPAQ2 (COMPLETE)",
        "Start Day",
        "End Day",
        "No of Worked Days",
    ]
    return cols


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

columns = build_column_order()
df = pd.DataFrame(all_rows, columns=columns)

df.sort_values(
    by=["State", "LGA", "Ward", "Health Facility", "OIC Username"],
    ascending=True,
    inplace=True,
    ignore_index=True
)

output_file = f"{FILE_NAME}.xlsx"
output_path = os.path.join(os.getcwd(), output_file)
df.to_excel(output_path, index=False)

print(f"Report saved to: {output_path} ({len(all_rows)} rows)")
