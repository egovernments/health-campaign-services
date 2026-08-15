import argparse
import gc
import json
import os
import random
import re
import sys
import time
import warnings
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from operator import attrgetter

import requests
import xlsxwriter
from tqdm import tqdm

# ============================================================================
# COHORT REPORT  (single sheet, streaming)
#
# One row per child, written straight to Excel. The 4x "Child_Cycle_Mapping"
# explosion that earlier versions produced is gone entirely - per-cycle detail
# still ships, collapsed into the comma-joined *List columns on the same row
# (Cycle Index List / Age List / Quantity Administered List / Redose Quantity
# Administered List / Date of Administration List).
#
# ---------------------------------------------------------------------------
# WHAT DROPPING THE CYCLE SHEET ACTUALLY BUYS  (measured, 150k children)
# ---------------------------------------------------------------------------
#   Cells written : 109N -> 21N   (the cycle sheet was 4N rows x 22 cols, i.e.
#                                  ~81% of every cell the report ever wrote)
#   Step 4 time   : large win - the write is ~95% of step 4, and 81% of it is
#                   now simply not performed.
#   Peak RSS      : almost no change. Peak RSS is set by the per-child objects
#                   held after steps 1-3, not by the write: xlsxwriter runs in
#                   constant_memory mode, so a sheet costs one row of memory at
#                   a time regardless of how many rows go through it.
#   Output file   : much smaller, and far less temp-file churn on /tmp.
#
# So: big time saving, negligible RSS saving. If peak RSS still needs to come
# down, the lever is steps 1-3 (fewer children in memory at once), not step 4.
#
# ---------------------------------------------------------------------------
# THE REST OF THE PERFORMANCE WORK (carried over)
# ---------------------------------------------------------------------------
# The older implementation built the output with pandas/numpy and spiked hard
# at step 4. Removed here, in descending order of cost:
#   * np.repeat(list_of_python_strings, 4) coerced each static column into a
#     FIXED-WIDTH unicode array ('<U<maxlen>'), padding every element to the
#     longest string at 4 bytes/char - 16 such blocks, allocated contiguously.
#     The single biggest memory spike, and moot now that nothing is exploded.
#   * df.where(pd.notnull(df), None) built a full bool frame plus a complete
#     second copy of the frame.
#   * pd.DataFrame(list_of_dicts) twice, alive alongside the source objects.
#   * One Python function call per cell (_cell_value over df.itertuples()).
#   * Repeated gen-2 GC passes walking the whole surviving object graph.
#   * importing pandas + numpy at all (~100-150 MB RSS, ~0.5s startup).
#
# Still in place:
#   * Rows stream into xlsxwriter's constant_memory writer; each child is
#     released as soon as it is written, so RSS falls through step 4.
#   * Per-child state is a __slots__ object (~184 bytes) rather than a 25-key
#     dict (~1184 bytes).
#   * Low-cardinality strings (state / lga / ward / facility / username /
#     product / gender / dates) are de-duplicated to one shared str object.
#   * Date normalization is memoized per calendar day - the old code paid up to
#     four datetime.strptime attempts (~10 us each) per task document.
#   * The cyclic GC is frozen after ingest and disabled for the write loop.
#
# ---------------------------------------------------------------------------
# ELASTICSEARCH ACCESS - deliberately unchanged from the previous version
# ---------------------------------------------------------------------------
# All ES calls go through common_utils.get_resp(), the same helper every other
# report uses, with the same ?scroll=10m keep-alive as before.
#
# An earlier revision of this file replaced that with a bespoke HTTP layer
# (custom retry/backoff, shorter 5m keep-alive, track_total_hits=false, an
# explicit clear-scroll on exit). That caused two production failures:
#   1. ES rejects track_total_hits=false in a scroll context outright (HTTP 400).
#   2. The custom retry window (up to 28 min) far exceeded the 5m keep-alive, so
#      any transient blip mid-scroll outlived the scroll context and every
#      subsequent page failed with search_context_missing_exception - losing the
#      whole scroll after ~846k records.
# All of that has been reverted. This file now only changes how the fetched data
# is held in memory and written out - not how it talks to Elasticsearch.
#
# ---------------------------------------------------------------------------
# BEHAVIOUR NOTE
# ---------------------------------------------------------------------------
# Data.taskDates is handled when it arrives as a list, matching
# beneficiary_visit_list.py. The older code fell through to str(list) and wrote
# "['2025-01-01']" into the date columns.
# ============================================================================

# === COMMAND LINE ARGUMENTS ===
parser = argparse.ArgumentParser()
parser.add_argument('--campaign_identifier', default='',
                    help='Campaign identifier (can be campaignNumber or projectTypeId). '
                         'Leave empty to include all campaigns within the date window.')
parser.add_argument('--identifier_type', default='campaignNumber',
                    help='Type of identifier: "campaignNumber" or "projectTypeId"')
parser.add_argument('--start_date', default='')
parser.add_argument('--end_date', default='')
parser.add_argument('--file_name', required=True)
# --- ES load knobs. Defaults are deliberately conservative; raise only if the
# --- cluster has headroom, lower them if the report is competing with ingest.
parser.add_argument('--scroll_size', type=int, default=int(os.getenv('COHORT_SCROLL_SIZE', '3000')),
                    help='Hits per scroll page from project-task-index (default 3000)')
parser.add_argument('--batch_size', type=int, default=int(os.getenv('COHORT_BATCH_SIZE', '1000')),
                    help='Ids per terms lookup for the enrichment steps (default 1000)')
parser.add_argument('--max_workers', type=int, default=int(os.getenv('COHORT_MAX_WORKERS', '4')),
                    help='Concurrent enrichment requests against ES (default 4)')
args = parser.parse_args()

CAMPAIGN_IDENTIFIER = args.campaign_identifier
IDENTIFIER_TYPE = args.identifier_type
START_DATE = args.start_date
END_DATE = args.end_date
FILE_NAME = args.file_name

SCROLL_SIZE = max(500, args.scroll_size)
BATCH_SIZE = max(100, args.batch_size)
MAX_FETCH_WORKERS = max(1, args.max_workers)

# === PATH SETUP ===
file_path = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.append(file_path)

from COMMON_UTILS.custom_date_utils import get_custom_dates_of_reports
from COMMON_UTILS.common_utils import get_resp, es_index_url, es_scroll_url

warnings.filterwarnings("ignore", message="Unverified HTTPS request is being made.*")
try:
    import urllib3

    urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
except Exception:
    pass

# === CONSTANTS ===
ES_PROJECT_TASK_INDEX = es_index_url("project-task-index-v1")
ES_INDIVIDUAL_INDEX = es_index_url("individual-index-v1")
ES_HOUSEHOLD_MEMBER_INDEX = es_index_url("household-member-index-v1")
ES_SCROLL_API = es_scroll_url()
DECRYPT_URL = "http://egov-enc-service.egov:8080/egov-enc-service/crypto/v1/_decrypt"

# Matches the value this report used before: each scroll request refreshes it.
SCROLL_KEEPALIVE = "10m"
DECRYPT_CHUNK = 1000
DECRYPT_FAILED_SENTINEL = "DECRYPT_FAILED"

EXCEL_MAX_ROWS = 1048576

ADMIN_STATUSES = [
    "ADMINISTRATION_SUCCESS",
    "BENEFICIARY_INELIGIBLE",
    "BENEFICIARY_REFERRED",
    "BENEFICIARY_REFUSED",
    "CLOSED_HOUSEHOLD",
]
FLAG_STATUSES = frozenset((
    "BENEFICIARY_INELIGIBLE", "BENEFICIARY_REFERRED",
    "BENEFICIARY_REFUSED", "CLOSED_HOUSEHOLD",
))

# === SHARED HTTP SESSION (enc-service only) ===
# Elasticsearch access goes through common_utils.get_resp() - the same helper every
# other report uses. It retries every non-200 for ~150s and logs each attempt.
SESSION = requests.Session()


def parallel_fetch(batches, fetch_fn, desc):
    """Run fetch_fn over each batch concurrently, preserving a tqdm bar."""
    out = []
    if not batches:
        return out
    workers = min(MAX_FETCH_WORKERS, len(batches))
    with ThreadPoolExecutor(max_workers=workers) as ex:
        with tqdm(total=len(batches), desc=desc, unit=" batch", dynamic_ncols=True) as pbar:
            for res in ex.map(fetch_fn, batches):
                out.append(res)
                pbar.update(1)
    return out


def chunked(items, size):
    return [items[i:i + size] for i in range(0, len(items), size)]


# === CAMPAIGN FILTER ===
if CAMPAIGN_IDENTIFIER:
    if IDENTIFIER_TYPE == "projectTypeId":
        CAMPAIGN_FILTER_FIELD = "Data.projectTypeId.keyword"
        print(f"Using projectTypeId filter: {CAMPAIGN_IDENTIFIER}")
    else:
        CAMPAIGN_FILTER_FIELD = "Data.campaignNumber.keyword"
        print(f"Using campaignNumber filter: {CAMPAIGN_IDENTIFIER}")
else:
    CAMPAIGN_FILTER_FIELD = None
    print("No campaign identifier provided — including all campaigns within the date window.")

# === DATE RANGE ===
lteTime, gteTime, start_date_str, end_date_str = get_custom_dates_of_reports(START_DATE, END_DATE)

print(f"Reports start date: {start_date_str}")
print(f"Reports end date  : {end_date_str}")
print(f"ES load profile   : scroll_size={SCROLL_SIZE} batch_size={BATCH_SIZE} workers={MAX_FETCH_WORKERS}")
print("\n===== Generating report : COHORT REPORT (single sheet)\n")


# === PER-CHILD STATE ===
class Child(object):
    """
    One row of the report.

    __slots__ rather than a dict: a 25-key dict costs ~1184 bytes per child, an
    18-slot instance ~160. Across a few hundred thousand children that is the
    difference between ~300 MB and ~45 MB of pure container overhead, before a
    single field value is counted.

    `cycles` and `rows` stay parallel (rows[i] describes cycles[i]) and are
    appended in first-seen order, so the joined *List columns keep the ordering
    the report has always had.
    """
    __slots__ = (
        "state", "lga", "ward", "health_facility", "username",
        "child_name", "gender", "beneficiary_id",
        "household_ref", "head_name", "product_name",
        "ineligible", "referred", "refused", "closed_household",
        "reg_date", "cycles", "rows",
    )

    def __init__(self, state, lga, ward, health_facility, username, gender, product_name):
        self.state = state
        self.lga = lga
        self.ward = ward
        self.health_facility = health_facility
        self.username = username
        self.gender = gender
        self.product_name = product_name
        self.child_name = ""
        self.beneficiary_id = ""
        self.household_ref = ""
        self.head_name = ""
        self.ineligible = "no"
        self.referred = "no"
        self.refused = "no"
        self.closed_household = "no"
        self.reg_date = ""
        self.cycles = []          # ["01", "02", ...]  first-seen order
        self.rows = []            # [(age, task_date, qty, redose_qty), ...]


children = {}
raw_status_counts = {status: 0 for status in ADMIN_STATUSES}

# === STRING DE-DUPLICATION ===
# json.loads hands back a brand new str object for every occurrence of every
# value. For low-cardinality columns (a few dozen wards, a few thousand
# usernames) that means hundreds of thousands of ~60-byte duplicates. Funnelling
# them through a cache collapses each distinct value to one shared object.
_str_pool = {}
_STR_POOL_CAP = 500000


def pooled(value):
    if not value:
        return ""
    if not isinstance(value, str):
        value = str(value)
    shared = _str_pool.get(value)
    if shared is not None:
        return shared
    if len(_str_pool) < _STR_POOL_CAP:
        _str_pool[value] = value
    return value


# === DATE NORMALIZATION (memoized) ===
# This runs once per task document, and for string dates the old code paid up
# to four datetime.strptime attempts (~10 us each) every time. Task dates only
# span the campaign window, so the distinct-value count is in the dozens -
# memoize on the calendar day (for epoch-ms) or on the raw string, and the cost
# collapses to a dict lookup.
_day_cache = {}
_raw_date_cache = {}
_MS_PER_DAY = 86400000
_DATE_FORMATS = ("%Y-%m-%d", "%Y-%m-%dT%H:%M:%S.%fZ", "%Y-%m-%dT%H:%M:%SZ", "%Y-%m-%dT%H:%M:%S")


def normalize_task_date(raw):
    """
    Data.taskDates arrives as epoch-ms, as a formatted date/date-time string, or
    (as beneficiary_visit_list.py already handles) as a single-element list of
    either. Always returns a clean 'YYYY-MM-DD' string, or "".
    """
    if raw is None or raw == "":
        return ""
    if isinstance(raw, list):
        raw = raw[0] if raw else ""
        if raw is None or raw == "":
            return ""

    # Case 1: epoch-ms. Bucket on the day so the cache stays tiny even if the
    # source carries millisecond precision.
    try:
        day = int(raw) // _MS_PER_DAY
    except (TypeError, ValueError):
        day = None
    if day is not None:
        cached = _day_cache.get(day)
        if cached is None:
            try:
                cached = datetime.fromtimestamp(day * 86400, tz=timezone.utc).strftime('%Y-%m-%d')
            except (OverflowError, OSError, ValueError):
                cached = ""
            _day_cache[day] = cached
        return cached

    # Case 2: already a string date/date-time.
    raw_str = str(raw).strip()
    cached = _raw_date_cache.get(raw_str)
    if cached is not None:
        return cached
    parsed = raw_str  # fallback: never silently drop an unparseable value
    for fmt in _DATE_FORMATS:
        try:
            parsed = datetime.strptime(raw_str, fmt).strftime('%Y-%m-%d')
            break
        except ValueError:
            continue
    parsed = pooled(parsed)
    if len(_raw_date_cache) < 100000:
        _raw_date_cache[raw_str] = parsed
    return parsed


# cycleIndex is a small int; str().zfill(2) per document is pure waste.
_cycle_label_cache = {}


def cycle_label(cycle_index):
    label = _cycle_label_cache.get(cycle_index)
    if label is None:
        label = str(cycle_index).zfill(2)
        if len(_cycle_label_cache) < 1000:
            _cycle_label_cache[cycle_index] = label
    return label


# === STEP 1: FETCH PROJECT TASK DATA ===
def fetch_project_tasks():
    """
    Scroll project-task-index-v1 with the CLI-configured campaign filter.

    Query shape, keep-alive and retry behaviour are exactly as they were before
    this file was rewritten: bool.must, no sort clause, ?scroll=10m, and
    common_utils.get_resp() doing the retrying. Only the in-memory handling of
    the fetched documents below is new.
    """
    must_clauses = [
        {"range": {"Data.@timestamp": {"gte": gteTime, "lte": lteTime}}},
        {"terms": {"Data.administrationStatus.keyword": ADMIN_STATUSES}},
    ]
    if CAMPAIGN_FILTER_FIELD:
        must_clauses.append({"term": {CAMPAIGN_FILTER_FIELD: CAMPAIGN_IDENTIFIER}})

    query = {
        "size": SCROLL_SIZE,
        "query": {"bool": {"must": must_clauses}},
        "_source": [
            "Data.boundaryHierarchy", "Data.age", "Data.additionalDetails.gender", "Data.individualId",
            "Data.userName", "Data.quantity", "Data.uniqueBeneficiaryID",
            "Data.administrationStatus", "Data.additionalDetails.reAdministered",
            "Data.additionalDetails.beneficiaryId",
            # Already carries the child's household on the task doc, which lets us
            # skip the child -> household lookup against household-member-index for
            # every child that has it (see step 3).
            "Data.additionalDetails.householdClientReferenceId",
            "Data.taskDates",
            "Data.productName", "Data.additionalDetails.cycleIndex",
        ],
    }

    scroll_url = f"{ES_PROJECT_TASK_INDEX}?scroll={SCROLL_KEEPALIVE}"
    scroll_id = None
    total_fetched = 0
    empty_dict = {}

    print("[Step 1/4] Fetching project-task data...")
    with tqdm(desc="  Scrolling pages", unit=" page", dynamic_ncols=True) as pbar:
        while True:
            if scroll_id is None:
                resp = get_resp(scroll_url, query, True)
            else:
                resp = get_resp(ES_SCROLL_API,
                                {"scroll": SCROLL_KEEPALIVE, "scroll_id": scroll_id}, True)

            if not resp:
                print("  Warning: Project Task ES returned nothing. Breaking scroll.")
                break

            try:
                resp_json = resp.json()
            except Exception as e:
                print(f"  Error parsing Project Task response: {e}")
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

                additional = data.get("additionalDetails") or empty_dict

                child = children.get(indv_id)
                if child is None:
                    boundary = data.get("boundaryHierarchy") or empty_dict
                    child = Child(
                        pooled(boundary.get("state", "")),
                        pooled(boundary.get("lga", "")),
                        pooled(boundary.get("ward", "")),
                        pooled(boundary.get("healthFacility", "")),
                        pooled(data.get("userName", "")),
                        pooled(additional.get("gender", "")),
                        pooled(data.get("productName", "")),
                    )
                    children[indv_id] = child

                # The task doc usually carries the child's household directly, which
                # saves a whole household-member-index lookup wave in step 3. Backfill
                # from any later doc that has it, the same way beneficiaryId is handled:
                # not every task record for a child necessarily carries it.
                if not child.household_ref:
                    hh_ref = additional.get("householdClientReferenceId")
                    if hh_ref:
                        child.household_ref = str(hh_ref).strip()

                if not child.beneficiary_id:
                    beneficiary_id = additional.get("beneficiaryId")
                    if beneficiary_id:
                        child.beneficiary_id = str(beneficiary_id).strip()

                task_date = normalize_task_date(data.get("taskDates", ""))

                if task_date and (not child.reg_date or task_date < child.reg_date):
                    child.reg_date = task_date

                cycle_index = additional.get("cycleIndex")
                if cycle_index is not None:
                    label = cycle_label(cycle_index)
                    if label not in child.cycles:
                        status = data.get("administrationStatus", "")
                        quantity = data.get("quantity", 0)
                        re_administered = additional.get("reAdministered")
                        is_redose = (re_administered is True or
                                     (isinstance(re_administered, str) and
                                      re_administered.lower() == "true"))
                        child.cycles.append(label)
                        child.rows.append((
                            data.get("age", ""),
                            task_date,
                            quantity if status == "ADMINISTRATION_SUCCESS" else 0,
                            quantity if is_redose else 0,
                        ))

                status = data.get("administrationStatus", "")
                if status in raw_status_counts:
                    raw_status_counts[status] += 1
                if status in FLAG_STATUSES:
                    if status == "BENEFICIARY_INELIGIBLE":
                        child.ineligible = "yes"
                    elif status == "BENEFICIARY_REFERRED":
                        child.referred = "yes"
                    elif status == "BENEFICIARY_REFUSED":
                        child.refused = "yes"
                    else:
                        child.closed_household = "yes"

            total_fetched += len(hits)
            pbar.update(1)
            pbar.set_postfix({"records": total_fetched})
            del hits, resp_json

    print(f"  Total records fetched   : {total_fetched}")
    print(f"  Unique individuals      : {len(children)}")
    print(f"  Distinct pooled strings : {len(_str_pool)}\n")


# === STEP 2: ENRICH CHILD DETAILS ===
def _terms_query(field, values, source, extra_filter=None):
    musts = [{"terms": {field: values}}]
    if extra_filter:
        musts.append(extra_filter)
    return {
        "size": len(values),
        "query": {"bool": {"must": musts}},
        "_source": source,
    }


def fetch_child_names_batch(batch):
    """Names only. The `identifiers` array (encrypted blobs) is fetched separately."""
    resp = get_resp(ES_INDIVIDUAL_INDEX,
                    _terms_query("clientReferenceId.keyword", batch, ["clientReferenceId", "name"]), True)
    body = resp.json() if resp else None
    if not body:
        return {}
    out = {}
    for doc in body.get("hits", {}).get("hits", []):
        src = doc["_source"]
        name = src.get("name") or {}
        full_name = f"{name.get('givenName', '')} {name.get('familyName', '')}".replace("None", "").strip()
        out[src["clientReferenceId"]] = full_name
    return out


def fetch_identifiers_batch(batch):
    """Only for children still missing a plaintext beneficiaryId."""
    resp = get_resp(ES_INDIVIDUAL_INDEX,
                    _terms_query("clientReferenceId.keyword", batch, ["clientReferenceId", "identifiers"]), True)
    body = resp.json() if resp else None
    if not body:
        return {}
    out = {}
    for doc in body.get("hits", {}).get("hits", []):
        src = doc["_source"]
        for ident in src.get("identifiers") or ():
            if ident.get("identifierType") == "UNIQUE_BENEFICIARY_ID":
                enc_id = ident.get("identifierId")
                if enc_id:
                    out[src["clientReferenceId"]] = enc_id
                break
    return out


# === STEP 3: HOUSEHOLD LINKS / HEADS ===
def fetch_household_link_batch(batch):
    resp = get_resp(ES_HOUSEHOLD_MEMBER_INDEX, _terms_query(
        "Data.householdMember.individualClientReferenceId.keyword", batch,
        ["Data.householdMember.householdClientReferenceId",
         "Data.householdMember.individualClientReferenceId"]), True)
    body = resp.json() if resp else None
    if not body:
        return {}
    out = {}
    for doc in body.get("hits", {}).get("hits", []):
        src = doc["_source"]["Data"]["householdMember"]
        # Not pooled: household ids are ~1:1 with households, so the pool would
        # only pay dict overhead for values that never repeat.
        out[src["individualClientReferenceId"]] = src["householdClientReferenceId"]
    return out


def fetch_household_head_batch(batch):
    resp = get_resp(ES_HOUSEHOLD_MEMBER_INDEX, _terms_query(
        "Data.householdMember.householdClientReferenceId.keyword", batch,
        ["Data.householdMember.householdClientReferenceId",
         "Data.householdMember.individualClientReferenceId"],
        extra_filter={"term": {"Data.householdMember.isHeadOfHousehold": True}}), True)
    body = resp.json() if resp else None
    if not body:
        return {}
    out = {}
    for doc in body.get("hits", {}).get("hits", []):
        src = doc["_source"]["Data"]["householdMember"]
        out[src["householdClientReferenceId"]] = src["individualClientReferenceId"]
    return out


def merged_batches(items, fetch_fn, desc):
    merged = {}
    for part in parallel_fetch(chunked(items, BATCH_SIZE), fetch_fn, desc):
        if part:
            merged.update(part)
    return merged


# === BULK / RETRYING DECRYPTION (fallback path only) ===
def decrypt_identifiers_bulk(encrypted_ids):
    unique_ids = sorted(set(e for e in encrypted_ids if e))
    result = {}
    headers = {"Content-Type": "application/json"}
    fail_count = 0

    chunks = chunked(unique_ids, DECRYPT_CHUNK)
    with tqdm(chunks, desc="  Decrypting beneficiary IDs", unit=" chunk", dynamic_ncols=True) as pbar:
        for chunk in pbar:
            decrypted = None
            for attempt in range(3):
                try:
                    resp = SESSION.post(DECRYPT_URL, headers=headers,
                                        data=json.dumps(chunk), timeout=120)
                    if resp.status_code == 200:
                        decrypted = resp.json()
                        resp.close()
                        break
                    resp.close()
                except Exception:
                    pass
                time.sleep(2 ** attempt + random.uniform(0, 0.5))

            if isinstance(decrypted, list) and len(decrypted) == len(chunk):
                for enc, dec in zip(chunk, decrypted):
                    result[enc] = (dec or "").strip() if isinstance(dec, str) else str(dec)
            else:
                for enc in chunk:
                    result[enc] = DECRYPT_FAILED_SENTINEL
                fail_count += len(chunk)

            pbar.set_postfix({"failed": fail_count})

    if fail_count:
        print(f"  WARNING: {fail_count} beneficiary ids could not be decrypted "
              f"(marked '{DECRYPT_FAILED_SENTINEL}').")
    return result


# === SHEET WRITER ===
DETAILED_HEADER = [
    "State", "LGA", "Ward", "Health Facility", "Username", "Child Name",
    "Age List", "Gender", "Beneficiary ID (Child)", "Household Client Reference ID",
    "Household Head Name", "Product Name", "Cycle Index List",
    "Quantity Administered List", "Redose Quantity Administered List",
    "BENEFICIARY_INELIGIBLE", "BENEFICIARY_REFERRED", "BENEFICIARY_REFUSED",
    "CLOSED_HOUSEHOLD", "Date of Registration", "Date of Administration List",
]

# --- Escape-memoizing worksheet ---------------------------------------------
# Profiling shows the overwhelming majority of step 4's CPU inside xlsxwriter's
# per-cell XML serialization, and most of that is escaping: for every string
# cell, constant_memory mode runs two unconditional re.sub() calls
# (_escape_control_characters) plus three re.search() calls (_escape_data,
# _escape_attributes, preserve_whitespace) - about five regex operations per
# cell.
#
# Twelve of this sheet's 21 columns are low-cardinality (state / lga / ward /
# facility / username / product / gender / the four flags / registration date),
# so the same handful of values are re-escaped once per child. Caching the
# finished XML fragment per distinct string turns a cache hit into one dict
# lookup and one write.
#
# add_worksheet(worksheet_class=...) is a documented public hook, so this is a
# subclass rather than a monkey-patch. Verified to produce byte-identical
# sheet XML to the stock writer, including for values containing %, &, <, >,
# quotes, control characters, literal "_x0000_" and leading/trailing spaces.
# Anything it is not sure about is delegated back to the stock implementation,
# and if a future xlsxwriter changes these internals the capability probe below
# falls back to the stock class entirely.
try:
    from xlsxwriter.utility import xl_rowcol_to_cell_fast
    from xlsxwriter.worksheet import Worksheet as _XlsxWorksheet

    # Do NOT import xlsxwriter's whitespace helper by name: it is public
    # `preserve_whitespace` up to 3.2.x and private `_preserve_whitespace` from
    # 3.2.9 on, so pinning to either name silently disables this whole fast path
    # on the other version (which is exactly what happened in the built image,
    # where requirements.txt's unpinned `xlsxwriter>=3.1.2` resolved to 3.2.9).
    # It is two regexes, identical in both versions - own it here instead.
    _RE_LEADING_WS = re.compile(r"^\s")
    _RE_TRAILING_WS = re.compile(r"\s$")

    def preserve_whitespace(string):
        return bool(_RE_LEADING_WS.search(string) or _RE_TRAILING_WS.search(string))


    class FastWorksheet(_XlsxWorksheet):
        """Worksheet that memoizes escaped inline-string cell XML."""
        _FRAG_CAP = 20000

        def __init__(self, *a, **kw):
            super(FastWorksheet, self).__init__(*a, **kw)
            self._frag_cache = {}

        def _write_cell(self, row, col, cell):
            # Fast path only for plain unformatted strings on a sheet with no
            # row/column formats - exactly what this report writes.
            if (cell.__class__.__name__ == "String" and cell.format is None
                    and not self.set_rows and not self.col_info):
                value = cell.string
                frag = self._frag_cache.get(value)
                if frag is None:
                    escaped = self._escape_control_characters(value)
                    if escaped.startswith("<r>") and escaped.endswith("</r>"):
                        # Rich string - let the stock writer handle it.
                        return super(FastWorksheet, self)._write_cell(row, col, cell)
                    t_attr = ' xml:space="preserve"' if preserve_whitespace(escaped) else ""
                    # Built by concatenation, never by %-formatting: the escaped
                    # payload may itself contain a '%' (e.g. "50% Coverage").
                    frag = (' t="inlineStr"><is><t' + t_attr + '>'
                            + self._escape_data(escaped) + '</t></is></c>')
                    if len(self._frag_cache) >= self._FRAG_CAP:
                        # Clear-on-full rather than stop-inserting, so a burst of
                        # high-cardinality values (names, beneficiary ids) can't
                        # permanently lock the low-cardinality columns out.
                        self._frag_cache.clear()
                    self._frag_cache[value] = frag
                self.fh.write('<c r="' + xl_rowcol_to_cell_fast(row, col) + '"' + frag)
                return
            return super(FastWorksheet, self)._write_cell(row, col, cell)


    _probe = FastWorksheet()
    if not (hasattr(_probe, "_escape_control_characters")
            and hasattr(_probe, "_escape_data")
            and hasattr(_probe, "set_rows") and hasattr(_probe, "col_info")):
        raise AttributeError("xlsxwriter internals differ from expected shape")
    del _probe
    WORKSHEET_CLASS = FastWorksheet
except Exception as _e:  # pragma: no cover - defensive
    print(f"  NOTE: fast worksheet writer unavailable ({_e}); using stock xlsxwriter.")
    WORKSHEET_CLASS = None


class SheetWriter(object):
    """
    Append-only row writer over an xlsxwriter worksheet that rolls over to
    `<name>_2`, `<name>_3`, ... when it hits Excel's 1,048,576-row ceiling.

    With one row per child that ceiling is only reached above ~1.05M children,
    but the guard is kept: past the limit xlsxwriter emits a warning per
    out-of-bounds cell and drops the row, which is a silent truncation.
    """
    __slots__ = ("wb", "base", "header", "part", "row", "total", "ws")

    def __init__(self, workbook, base_name, header):
        self.wb = workbook
        self.base = base_name
        self.header = header
        self.part = 0
        self.total = 0
        self.row = 0
        self.ws = None
        self._new_sheet()

    def _new_sheet(self):
        self.part += 1
        name = self.base if self.part == 1 else f"{self.base}_{self.part}"
        self.ws = self.wb.add_worksheet(name[:31], worksheet_class=WORKSHEET_CLASS)
        self.ws.write_row(0, 0, self.header)
        self.row = 1

    def write(self, values):
        if self.row >= EXCEL_MAX_ROWS:
            self._new_sheet()
        self.ws.write_row(self.row, 0, values)
        self.row += 1
        self.total += 1


# ============================================================================
# MAIN EXECUTION
# ============================================================================
run_started = time.monotonic()

fetch_project_tasks()

all_individuals = list(children.keys())
total_children = len(all_individuals)

ids_from_task = sum(1 for c in children.values() if c.beneficiary_id)
print(f"  Beneficiary IDs from task additionalDetails: {ids_from_task}/{total_children}")

# --- Step 2 -----------------------------------------------------------------
print("[Step 2/4] Enriching child details...")
name_map = merged_batches(all_individuals, fetch_child_names_batch, "  Individual batches")
for indv_id, full_name in name_map.items():
    child = children.get(indv_id)
    if child is not None:
        child.child_name = full_name
del name_map

# Only pull the (much larger) identifiers array for children that still need it.
needs_decrypt = [cid for cid, c in children.items() if not c.beneficiary_id]
decrypted_map = {}
if needs_decrypt:
    print(f"  Decryption fallback candidates: {len(needs_decrypt)}")
    enc_map = merged_batches(needs_decrypt, fetch_identifiers_batch, "  Identifier batches")
    if enc_map:
        pending = defaultdict(list)  # encrypted id -> [child ids]
        for indv_id, enc_id in enc_map.items():
            pending[enc_id].append(indv_id)
        print(f"  Decrypting {len(pending)} distinct beneficiary id(s)...")
        decrypted_map = decrypt_identifiers_bulk(list(pending.keys()))
        for enc_id, indv_ids in pending.items():
            value = decrypted_map.get(enc_id, "")
            for indv_id in indv_ids:
                children[indv_id].beneficiary_id = value
        del pending
    del enc_map
else:
    print("  Decryption fallback not needed — all beneficiary ids came from project-task.")
del needs_decrypt

names_matched = sum(1 for c in children.values() if c.child_name)
ids_resolved = sum(1 for c in children.values()
                   if c.beneficiary_id and c.beneficiary_id != DECRYPT_FAILED_SENTINEL)
print(f"  Child names matched     : {names_matched}/{total_children}")
print(f"  Beneficiary IDs resolved: {ids_resolved}/{total_children}\n")

# --- Step 3 -----------------------------------------------------------------
print("[Step 3/4] Fetching household links + head names...")

# child -> household comes off the task doc for most children (captured in step 1),
# so household-member-index only has to answer for the stragglers. Same result,
# far fewer terms queries against that index.
from_task = sum(1 for c in children.values() if c.household_ref)
needs_hh_lookup = [cid for cid, c in children.items() if not c.household_ref]
print(f"  Household refs from task additionalDetails: {from_task}/{total_children}"
      f"  (falling back to household-member-index for {len(needs_hh_lookup)})")

if needs_hh_lookup:
    fallback_links = merged_batches(needs_hh_lookup, fetch_household_link_batch,
                                    "  HH link batches (fallback)")
    for child_id, hh_id in fallback_links.items():
        child = children.get(child_id)
        if child is not None and hh_id:
            child.household_ref = hh_id
    del fallback_links
del needs_hh_lookup

household_ids = list({c.household_ref for c in children.values() if c.household_ref})
hh_to_head_ref = merged_batches(household_ids, fetch_household_head_batch, "  HH head-ref batches")
del household_ids

# Heads that are themselves children we already enriched need no second lookup.
head_refs = set(hh_to_head_ref.values())
already_known = {}
to_lookup = []
for ref in head_refs:
    known = children.get(ref)
    if known is not None and known.child_name:
        already_known[ref] = known.child_name
    else:
        to_lookup.append(ref)
del head_refs
print(f"  Head names already known from children: {len(already_known)} "
      f"(looking up {len(to_lookup)})")

head_names = merged_batches(to_lookup, fetch_child_names_batch, "  Head-name batches")
head_names.update(already_known)
del to_lookup, already_known

heads_filled = 0
for child_id, child in children.items():
    # household_ref is already set - from the task doc, or from the fallback above.
    head_ref = hh_to_head_ref.get(child.household_ref, "")
    if head_ref:
        name = head_names.get(head_ref, "")
        if name:
            # Already a single shared str per head in head_names - all the
            # siblings in a household point at that one object.
            child.head_name = name
            heads_filled += 1

print(f"  Household head names filled: {heads_filled}/{total_children}\n")
del hh_to_head_ref, head_names, all_individuals

# --- Step 4 -----------------------------------------------------------------
print("[Step 4/4] Streaming per-child rows straight into Excel...")

# Sort the child objects once (C-level key extraction). The list holds
# references only - no data is copied.
ordered = sorted(children.values(), key=attrgetter("child_name"))
children.clear()
del children

# Every object that survives to here is long-lived. gc.freeze() moves them to a
# permanent generation the collector never scans again, and disabling the
# collector for the write loop stops gen-2 passes from walking the graph while
# we allocate short-lived tuples. Nothing in the loop below creates a reference
# cycle, so plain refcounting still frees everything promptly.
gc.collect()
gc.freeze()
gc.disable()

output_dir = os.path.join(file_path, "FINAL_REPORTS")
os.makedirs(output_dir, exist_ok=True)
output_file = os.path.join(output_dir, f"{FILE_NAME}.xlsx")

# constant_memory: each row is serialized to the worksheet's temp file and then
# discarded, so peak memory for the write is one row rather than one workbook.
# The strings_to_* options switch off xlsxwriter's per-cell regex probing for
# URLs / formulas / numeric strings.
workbook = xlsxwriter.Workbook(output_file, {
    "constant_memory": True,
    "strings_to_urls": False,
    "strings_to_formulas": False,
    "strings_to_numbers": False,
})

detailed_sheet = SheetWriter(workbook, "Detailed Report", DETAILED_HEADER)

missing_child_name = 0
missing_head_name = 0
missing_beneficiary_id = 0

with tqdm(total=len(ordered), desc="  Writing children", unit=" child", dynamic_ncols=True) as pbar:
    for idx in range(len(ordered)):
        child = ordered[idx]
        # Release each child as soon as it is written so RSS falls through
        # step 4 instead of peaking at the end.
        ordered[idx] = None

        rows = child.rows

        if not child.child_name:
            missing_child_name += 1
        if not child.head_name:
            missing_head_name += 1
        # Blank-only: ids that came back as the DECRYPT_FAILED sentinel are
        # reported on their own line below.
        if not child.beneficiary_id:
            missing_beneficiary_id += 1

        # The per-cycle detail lives in the comma-joined *List columns, built
        # inline per child and discarded immediately.
        detailed_sheet.write((
            child.state, child.lga, child.ward, child.health_facility, child.username,
            child.child_name,
            ", ".join(str(r[0]) for r in rows),
            child.gender, child.beneficiary_id, child.household_ref, child.head_name,
            child.product_name,
            ", ".join(child.cycles),
            ", ".join(str(r[2]) for r in rows),
            ", ".join(str(r[3]) for r in rows),
            child.ineligible, child.referred, child.refused, child.closed_household,
            child.reg_date,
            ", ".join(str(r[1]) for r in rows),
        ))

        del child
        pbar.update(1)

del ordered
gc.enable()

workbook.close()

detailed_rows = detailed_sheet.total
detailed_parts = detailed_sheet.part

print(f"\n  Output : {output_file}")
print(f"  Detailed Report rows      : {detailed_rows}"
      + (f"  (split across {detailed_parts} sheets — Excel's 1,048,576-row limit)"
         if detailed_parts > 1 else ""))

# === STATUS-WISE SUMMARY ===
print("\n" + "=" * 80)
print("STATUS-WISE SUMMARY (raw project-task events)")
print("=" * 80)
grand_total = sum(raw_status_counts.values())
print(f"\n{'Administration Status':<30} {'Records':>10} {'Percentage':>10}")
print("-" * 52)
for status, count in raw_status_counts.items():
    pct = 100 * count / grand_total if grand_total > 0 else 0
    print(f"  {status:<28} {count:>10} {pct:>9.2f}%")
print("-" * 52)
print(f"  {'GRAND TOTAL':<28} {grand_total:>10} {'100.00%':>9}")

# === DATA QUALITY SUMMARY ===
# Counted during the write pass rather than rebuilt from the finished sheet.
print("\n" + "=" * 80)
print("DATA QUALITY SUMMARY (per child)")
print("=" * 80)
for label, miss in (("Child Name", missing_child_name),
                    ("Household Head Name", missing_head_name),
                    ("Beneficiary ID (Child)", missing_beneficiary_id)):
    pct = 100 * miss / total_children if total_children > 0 else 0
    print(f"  {label:<30}: {miss:>7} missing ({pct:>6.2f}%)")

decrypt_failed = sum(1 for v in decrypted_map.values() if v == DECRYPT_FAILED_SENTINEL)
if decrypt_failed:
    print(f"  {'Beneficiary IDs (decrypt failed)':<30}: {decrypt_failed:>7}")

print("\n" + "=" * 80)
print(f"Total children in cohort : {total_children}")
print(f"Output file              : {output_file}")
print(f"Elapsed                  : {time.monotonic() - run_started:.1f}s")
print("=" * 80 + "\n")
