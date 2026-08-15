import os
import sys
import warnings
import pandas as pd
import argparse
from collections import defaultdict

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
file_path = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
sys.path.append(file_path)

from REPORTS_GENERATION.COMMON_UTILS.custom_date_utils import get_custom_dates_of_reports
from REPORTS_GENERATION.COMMON_UTILS.common_utils import get_resp, es_index_url, es_scroll_url

warnings.filterwarnings("ignore", message="Unverified HTTPS request is being made.*")

# === ES URLS ===
ES_STOCK_V1 = es_index_url("stock-index-v1")
ES_STOCK_UPDATED_V1 = es_index_url("stock-index-updated-v1")
ES_TASK_V1 = es_index_url("project-task-index-v1")
ES_SCROLL_API = es_scroll_url()

# === DATE RANGE ===
lteTime, gteTime, start_date_str, end_date_str = get_custom_dates_of_reports(START_DATE, END_DATE)

# === CAMPAIGN FILTER ===
if IDENTIFIER_TYPE == "projectTypeId":
    CAMPAIGN_FILTER_FIELD = "Data.projectTypeId.keyword"
    print(f"Using projectTypeId filter: {CAMPAIGN_IDENTIFIER}")
else:
    CAMPAIGN_FILTER_FIELD = "Data.campaignNumber.keyword"
    print(f"Using campaignNumber filter: {CAMPAIGN_IDENTIFIER}")

SCROLL_TIME = "10m"
BATCH_SIZE = 5000


def get_boundary_key(data):
    boundary = data.get("boundaryHierarchy", {})
    return (
        boundary.get("state", ""),
        boundary.get("lga", ""),
        boundary.get("ward", ""),
        boundary.get("healthFacility", ""),
    )


def classify_product(product_name):
    """Return 'SPAQ 1', 'SPAQ 2', or None based on exact product name."""
    if product_name == "SPAQ 1":
        return "SPAQ 1"
    if product_name == "SPAQ 2":
        return "SPAQ 2"
    return None


def get_quantity(data):
    try:
        return float(data.get("physicalCount") or 0)
    except (ValueError, TypeError):
        return 0.0


def scroll_all(index_url, query):
    """Scroll through all results from an ES index and yield _source Data dicts."""
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


def base_query(extra_must_clauses, source_fields):
    return {
        "size": BATCH_SIZE,
        "query": {
            "bool": {
                "must": [
                    {"term": {CAMPAIGN_FILTER_FIELD: CAMPAIGN_IDENTIFIER}},
                    {"range": {"Data.createdTime": {"gte": gteTime, "lte": lteTime}}},
                ] + extra_must_clauses
            }
        },
        "_source": source_fields
    }


COMMON_SOURCE = [
    "Data.boundaryHierarchy",
    "Data.facilityType",
    "Data.transactingFacilityType",
    "Data.productName",
    "Data.physicalCount",
    "Data.reason",
    "Data.id",
]


# ===========================
# 1. State → HF (updated-v1)
#    status=ACCEPTED, facilityType=Health Facility, transactingFacilityType=State Facility
# ===========================
def fetch_state_to_hf():
    print("Fetching State → HF transactions from stock-index-updated-v1...")
    query = base_query(
        [
            {"term": {"Data.additionalDetails.status.keyword": "ACCEPTED"}},
            {"term": {"Data.facilityType.keyword": "Health Facility"}},
            {"term": {"Data.transactingFacilityType.keyword": "State Facility"}},
        ],
        COMMON_SOURCE
    )
    counts = defaultdict(lambda: defaultdict(float))
    for data in scroll_all(ES_STOCK_UPDATED_V1, query):
        key = get_boundary_key(data)
        product = classify_product(data.get("productName", ""))
        if product:
            counts[key][product] += get_quantity(data)
    print(f"State → HF: found {len(counts)} HF entries")
    return counts


# ===========================
# 2. HF → State (complex)
#    v1: facilityType=Health Facility, transactingFacilityType=State Facility, reason=RETURNED
#    then validate stock IDs against updated-v1 ACCEPTED
# ===========================
def fetch_hf_to_state():
    print("Fetching HF → State transactions from stock-index-v1 (reason=RETURNED)...")
    query = base_query(
        [
            {"term": {"Data.facilityType.keyword": "Health Facility"}},
            {"term": {"Data.transactingFacilityType.keyword": "State Facility"}},
            {"term": {"Data.reason.keyword": "RETURNED"}},
        ],
        COMMON_SOURCE + ["Data.id"]
    )

    v1_records = []
    stock_ids = []
    for data in scroll_all(ES_STOCK_V1, query):
        stock_id = data.get("id", "")
        if stock_id:
            v1_records.append(data)
            stock_ids.append(stock_id)

    print(f"HF → State: {len(v1_records)} RETURNED records in v1, validating against updated-v1...")

    accepted_ids = fetch_accepted_ids_from_updated_v1(stock_ids)
    print(f"HF → State: {len(accepted_ids)} accepted in updated-v1")

    counts = defaultdict(lambda: defaultdict(float))
    for data in v1_records:
        if data.get("id", "") in accepted_ids:
            key = get_boundary_key(data)
            product = classify_product(data.get("productName", ""))
            if product:
                counts[key][product] += get_quantity(data)
    return counts


# ===========================
# 3. HF → CDD (updated-v1)
#    status=ACCEPTED, facilityType=STAFF, transactingFacilityType=Health Facility
# ===========================
def fetch_hf_to_cdd():
    print("Fetching HF → CDD transactions from stock-index-updated-v1...")
    query = base_query(
        [
            {"term": {"Data.additionalDetails.status.keyword": "ACCEPTED"}},
            {"term": {"Data.facilityType.keyword": "STAFF"}},
            {"term": {"Data.transactingFacilityType.keyword": "Health Facility"}},
        ],
        COMMON_SOURCE
    )
    counts = defaultdict(lambda: defaultdict(float))
    for data in scroll_all(ES_STOCK_UPDATED_V1, query):
        key = get_boundary_key(data)
        product = classify_product(data.get("productName", ""))
        if product:
            counts[key][product] += get_quantity(data)
    print(f"HF → CDD: found {len(counts)} HF entries")
    return counts


# ===========================
# 4. CDD → HF (complex)
#    v1: facilityType=STAFF, transactingFacilityType=Health Facility, reason=RETURNED
#    then validate stock IDs against updated-v1 ACCEPTED
# ===========================
def fetch_cdd_to_hf():
    print("Fetching CDD → HF transactions from stock-index-v1 (reason=RETURNED)...")
    query = base_query(
        [
            {"term": {"Data.facilityType.keyword": "STAFF"}},
            {"term": {"Data.transactingFacilityType.keyword": "Health Facility"}},
            {"term": {"Data.reason.keyword": "RETURNED"}},
        ],
        COMMON_SOURCE + ["Data.id"]
    )

    v1_records = []
    stock_ids = []
    for data in scroll_all(ES_STOCK_V1, query):
        stock_id = data.get("id", "")
        if stock_id:
            v1_records.append(data)
            stock_ids.append(stock_id)

    print(f"CDD → HF: {len(v1_records)} RETURNED records in v1, validating against updated-v1...")

    accepted_ids = fetch_accepted_ids_from_updated_v1(stock_ids)
    print(f"CDD → HF: {len(accepted_ids)} accepted in updated-v1")

    counts = defaultdict(lambda: defaultdict(float))
    for data in v1_records:
        if data.get("id", "") in accepted_ids:
            key = get_boundary_key(data)
            product = classify_product(data.get("productName", ""))
            if product:
                counts[key][product] += get_quantity(data)
    return counts


TASK_SOURCE = [
    "Data.boundaryHierarchy",
    "Data.administrationStatus",
    "Data.productName",
    "Data.quantity",
]


def get_task_quantity(data):
    try:
        return float(data.get("quantity") or 0)
    except (ValueError, TypeError):
        return 0.0


# ===========================
# 5. CDD to BNF (project-task-index-v1)
#    administrationStatus=ADMINISTRATION_SUCCESS, sum of quantity, split by SPAQ 1/2
# ===========================
def fetch_cdd_to_bnf():
    print("Fetching CDD to BNF from project-task-index-v1 (ADMINISTRATION_SUCCESS)...")
    query = base_query(
        [{"term": {"Data.administrationStatus.keyword": "ADMINISTRATION_SUCCESS"}}],
        TASK_SOURCE
    )
    counts = defaultdict(lambda: defaultdict(float))
    for data in scroll_all(ES_TASK_V1, query):
        key = get_boundary_key(data)
        product = classify_product(data.get("productName", ""))
        if product:
            counts[key][product] += get_task_quantity(data)
    print(f"CDD to BNF: found {len(counts)} HF entries")
    return counts


# ===========================
# 6. Redose (project-task-index-v1)
#    administrationStatus=VISITED, count of records, split by SPAQ 1/2
# ===========================
def fetch_redose():
    print("Fetching Redose from project-task-index-v1 (VISITED)...")
    query = base_query(
        [{"term": {"Data.administrationStatus.keyword": "VISITED"}}],
        TASK_SOURCE
    )
    counts = defaultdict(lambda: defaultdict(int))
    for data in scroll_all(ES_TASK_V1, query):
        key = get_boundary_key(data)
        product = classify_product(data.get("productName", ""))
        if product:
            counts[key][product] += 1
    print(f"Redose: found {len(counts)} HF entries")
    return counts


def fetch_accepted_ids_from_updated_v1(stock_ids):
    """
    Given a list of stock IDs, return the subset that have status=ACCEPTED in stock-index-updated-v1.
    Uses batched terms queries to avoid overly large payloads.
    """
    if not stock_ids:
        return set()

    TERMS_BATCH = 1000
    accepted = set()

    for i in range(0, len(stock_ids), TERMS_BATCH):
        batch = stock_ids[i: i + TERMS_BATCH]
        query = {
            "size": len(batch),
            "_source": ["Data.id"],
            "query": {
                "bool": {
                    "must": [
                        {"terms": {"Data.id.keyword": batch}},
                        {"term": {"Data.additionalDetails.status.keyword": "ACCEPTED"}},
                    ]
                }
            }
        }
        resp = get_resp(ES_STOCK_UPDATED_V1, query, True).json()
        for doc in resp.get("hits", {}).get("hits", []):
            sid = doc["_source"].get("Data", {}).get("id", "")
            if sid:
                accepted.add(sid)

    return accepted


# ===========================
# MAIN
# ===========================
print("Starting HF Stock Summary Report generation...")

state_to_hf = fetch_state_to_hf()
hf_to_state = fetch_hf_to_state()
hf_to_cdd = fetch_hf_to_cdd()
cdd_to_hf = fetch_cdd_to_hf()
cdd_to_bnf = fetch_cdd_to_bnf()
redose = fetch_redose()

# Collect all unique HF keys
all_keys = set(state_to_hf) | set(hf_to_state) | set(hf_to_cdd) | set(cdd_to_hf) | set(cdd_to_bnf) | set(redose)
print(f"Total unique HF boundary combinations: {len(all_keys)}")

rows = []
for key in all_keys:
    state, lga, ward, hf = key

    hf_to_cdd_spaq1 = hf_to_cdd[key].get("SPAQ 1", 0)
    hf_to_cdd_spaq2 = hf_to_cdd[key].get("SPAQ 2", 0)
    cdd_to_bnf_spaq1 = cdd_to_bnf[key].get("SPAQ 1", 0)
    cdd_to_bnf_spaq2 = cdd_to_bnf[key].get("SPAQ 2", 0)
    redose_spaq1 = redose[key].get("SPAQ 1", 0)
    redose_spaq2 = redose[key].get("SPAQ 2", 0)
    cdd_to_hf_spaq1 = cdd_to_hf[key].get("SPAQ 1", 0)
    cdd_to_hf_spaq2 = cdd_to_hf[key].get("SPAQ 2", 0)

    rows.append({
        "State": state,
        "LGA": lga,
        "Ward": ward,
        "Health Facility": hf,
        "State To HF - SPAQ 1": state_to_hf[key].get("SPAQ 1", 0),
        "State To HF - SPAQ 2": state_to_hf[key].get("SPAQ 2", 0),
        "HF To State - SPAQ 1": hf_to_state[key].get("SPAQ 1", 0),
        "HF To State - SPAQ 2": hf_to_state[key].get("SPAQ 2", 0),
        "HF to CDD - SPAQ 1": hf_to_cdd_spaq1,
        "HF to CDD - SPAQ 2": hf_to_cdd_spaq2,
        "CDD to HF - SPAQ 1": cdd_to_hf_spaq1,
        "CDD to HF - SPAQ 2": cdd_to_hf_spaq2,
        "CDD to BNF - SPAQ1": cdd_to_bnf_spaq1,
        "CDD to BNF - SPAQ2": cdd_to_bnf_spaq2,
        "Redose - SPAQ1": redose_spaq1,
        "Redose - SPAQ2": redose_spaq2,
        "Current Stock at HF - SPAQ1": (state_to_hf[key].get("SPAQ 1", 0) + cdd_to_hf_spaq1) - (hf_to_state[key].get("SPAQ 1", 0) + hf_to_cdd_spaq1),
        "Current Stock at HF - SPAQ2": (state_to_hf[key].get("SPAQ 2", 0) + cdd_to_hf_spaq2) - (hf_to_state[key].get("SPAQ 2", 0) + hf_to_cdd_spaq2),
        "Current Stock at CDD - SPAQ1": hf_to_cdd_spaq1 - (cdd_to_bnf_spaq1 + redose_spaq1 + cdd_to_hf_spaq1),
        "Current Stock at CDD - SPAQ2": hf_to_cdd_spaq2 - (cdd_to_bnf_spaq2 + redose_spaq2 + cdd_to_hf_spaq2),
    })

df = pd.DataFrame(rows, columns=[
    "State", "LGA", "Ward", "Health Facility",
    "State To HF - SPAQ 1", "State To HF - SPAQ 2",
    "HF To State - SPAQ 1", "HF To State - SPAQ 2",
    "HF to CDD - SPAQ 1", "HF to CDD - SPAQ 2",
    "CDD to HF - SPAQ 1", "CDD to HF - SPAQ 2",
    "CDD to BNF - SPAQ1", "CDD to BNF - SPAQ2",
    "Redose - SPAQ1", "Redose - SPAQ2",
    "Current Stock at HF - SPAQ1", "Current Stock at HF - SPAQ2",
    "Current Stock at CDD - SPAQ1", "Current Stock at CDD - SPAQ2",
])

df.sort_values(
    by=["State", "LGA", "Ward", "Health Facility"],
    ascending=True,
    inplace=True,
    ignore_index=True
)

output_file = f"{FILE_NAME}.xlsx"
output_path = os.path.join(os.getcwd(), output_file)
df.to_excel(output_path, index=False)

print(f"Report saved to: {output_path} ({len(rows)} rows)")
