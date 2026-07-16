import os
import sys
import warnings
import pandas as pd
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone

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
THREAD_POOL_SIZE = 5
USER_BUCKET_SIZE = 500
INACTIVE_THRESHOLD_HOURS = 4

# Capture report generation time once so syncGap is consistent across all rows
REPORT_GENERATED_AT = datetime.now(timezone.utc)

# === DATE RANGE ===
lteTime, gteTime, start_date_str, end_date_str = get_custom_dates_of_reports(START_DATE, END_DATE)

# === CAMPAIGN FILTER ===
if IDENTIFIER_TYPE == "projectTypeId":
    CAMPAIGN_FILTER_FIELD = "Data.projectTypeId.keyword"
    print(f"Using projectTypeId filter: {CAMPAIGN_IDENTIFIER}")
else:
    CAMPAIGN_FILTER_FIELD = "Data.campaignNumber.keyword"
    print(f"Using campaignNumber filter: {CAMPAIGN_IDENTIFIER}")


def format_geo_point(geo):
    """Format [lon, lat] as 'lat, lon' string."""
    if not geo or not isinstance(geo, list) or len(geo) < 2:
        return ""
    return f"{geo[1]}, {geo[0]}"


def compute_sync_gap(synced_ts_str):
    """
    Parse ISO syncedTimeStamp and return (gap_seconds, gap_str, status).
    gap_str is formatted as e.g. '2h 30m'.
    status is 'ACTIVE' or 'INACTIVE' based on INACTIVE_THRESHOLD_HOURS.
    Returns ('', '', '') if timestamp is missing/unparseable.
    """
    if not synced_ts_str:
        return None, "", "INACTIVE"
    try:
        sync_dt = datetime.fromisoformat(synced_ts_str.replace("Z", "+00:00"))
        delta = REPORT_GENERATED_AT - sync_dt
        total_seconds = max(int(delta.total_seconds()), 0)
        hours = total_seconds // 3600
        minutes = (total_seconds % 3600) // 60
        gap_str = f"{hours}h {minutes}m"
        status = "INACTIVE" if hours >= INACTIVE_THRESHOLD_HOURS else "ACTIVE"
        return total_seconds, gap_str, status
    except (ValueError, TypeError):
        return None, "", "INACTIVE"


def discover_hf_combinations():
    """
    Discover all unique (state, lga, ward, healthFacility, hf_code) combinations
    from project-task-index-v1 via composite aggregation.
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
            "query": {
                "bool": {
                    "must": [
                        {"term": {CAMPAIGN_FILTER_FIELD: CAMPAIGN_IDENTIFIER}},
                        {"range": {"Data.lastModifiedTime": {"gte": gteTime, "lte": lteTime}}},
                    ]
                }
            },
            "aggs": {"composite_agg": {"composite": composite}},
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


def fetch_users_for_hf(hf_info):
    """
    For a single HF, aggregate by userName and fetch the latest record per user
    (sorted by syncedTime desc) to get syncedTimeStamp, geoPoint, role, nameOfUser.
    Returns list of user dicts.
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
                ]
            }
        },
        "aggs": {
            "by_user": {
                "terms": {"field": "Data.userName.keyword", "size": USER_BUCKET_SIZE},
                "aggs": {
                    "latest_record": {
                        "top_hits": {
                            "size": 1,
                            "sort": [{"Data.syncedTime": {"order": "desc"}}],
                            "_source": [
                                "Data.nameOfUser",
                                "Data.userName",
                                "Data.role",
                                "Data.syncedTimeStamp",
                                "Data.geoPoint",
                            ]
                        }
                    }
                }
            }
        }
    }

    resp = get_resp(ES_PROJECT_TASK_INDEX, query, True).json()
    users = []

    for user_bucket in resp.get("aggregations", {}).get("by_user", {}).get("buckets", []):
        hits = user_bucket.get("latest_record", {}).get("hits", {}).get("hits", [])
        if not hits:
            continue

        d = hits[0].get("_source", {}).get("Data", {})
        users.append({
            "nameOfUser":      d.get("nameOfUser", ""),
            "userName":        d.get("userName", ""),
            "role":            d.get("role", ""),
            "syncedTimeStamp": d.get("syncedTimeStamp", ""),
            "geoPoint":        d.get("geoPoint"),
        })

    return users


def process_hf(hf_info):
    """Build report rows for a single HF — one row per user."""
    state          = hf_info["state"]
    lga            = hf_info["lga"]
    ward           = hf_info["ward"]
    health_facility = hf_info["healthFacility"]

    users = fetch_users_for_hf(hf_info)
    rows = []

    for u in users:
        gap_seconds, gap_str, status = compute_sync_gap(u["syncedTimeStamp"])

        rows.append({
            "State":              state,
            "LGA":                lga,
            "Ward":               ward,
            "HF":                 health_facility,
            "userRoles":          u["role"],
            "name":               u["nameOfUser"],
            "userName":           u["userName"],
            "phoneNumber":        "",
            "lastSync":           u["syncedTimeStamp"],
            "lastKnownLocation":  format_geo_point(u["geoPoint"]),
            "syncGap":            gap_str,
            "status":             status,
            # internal sort key — dropped before export
            "_syncGap_seconds":   gap_seconds if gap_seconds is not None else float("inf"),
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
            print(f"Processed HF: {hf.get('healthFacility', '')} — {len(rows)} user(s)")
        except Exception as e:
            print(f"Error processing HF {hf.get('healthFacility', hf)}: {e}")

COLUMNS = [
    "State", "LGA", "Ward", "HF",
    "userRoles", "name", "userName", "phoneNumber",
    "lastSync", "lastKnownLocation", "syncGap", "status",
]

if not all_rows:
    print("No sync data found for campaign in the given date range. Generating empty report.")
    df = pd.DataFrame(columns=COLUMNS)
else:
    df = pd.DataFrame(all_rows)

    # Sort: syncGap descending first, then boundary hierarchy
    df.sort_values(
        by=["_syncGap_seconds", "State", "LGA", "Ward", "HF"],
        ascending=[False, True, True, True, True],
        inplace=True,
        ignore_index=True,
    )

    df = df[COLUMNS]

output_file = f"{FILE_NAME}.xlsx"
output_path = os.path.join(os.getcwd(), output_file)
df.to_excel(output_path, index=False)

print(f"Report saved to: {output_path} ({len(all_rows)} rows)")
print(f"Report generated at: {REPORT_GENERATED_AT.strftime('%Y-%m-%d %H:%M:%S UTC')}")
