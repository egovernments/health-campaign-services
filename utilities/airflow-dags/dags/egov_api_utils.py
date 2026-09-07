"""
Shared helpers for calling external eGov DIGIT services and working with
their responses. Entity-transformation DAGs should only need the row-level
extraction of the FROM-what-column an entity's lookup key comes -- that part
is inherently entity-specific and stays local to each DAG file -- everything
else here (calling the API, grouping, attaching results back onto rows) is
reusable as-is across entities.
"""
from __future__ import annotations

import json
import logging
import os
import time
from typing import Callable

import pendulum
import requests
from dotenv import load_dotenv

log = logging.getLogger(__name__)


_ENV_FILE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), ".env")
load_dotenv(_ENV_FILE)

# Each DIGIT service can live on its own host, so each gets its own env var
# rather than sharing one base URL (boundary-service and user-service are on
# different hosts/ports even in this local dev setup). These are env vars,
# not Airflow Variables, because they're read once per row/chunk across
# every entity DAG -- Variable.get() is a network round-trip to the Airflow
# webserver/metadata DB per call, which balloons task time/memory at that
# call volume, whereas os.getenv() is a zero-cost in-process read. Read once
# here at import time into a plain constant (no wrapper function) so every
# call site below just uses the value directly. In production these are set
# directly on the worker container by the Helm chart; for local dev, fill in
# airflow_dags/.env (see README.md) 
BOUNDARY_SERVICE_BASE_URL = os.getenv("EGOV_BOUNDARY_SERVICE_BASE_URL")
USER_SERVICE_BASE_URL = os.getenv("EGOV_USER_SERVICE_BASE_URL")
MDMS_SERVICE_BASE_URL = os.getenv("EGOV_MDMS_SERVICE_BASE_URL")
WORKFLOW_SERVICE_BASE_URL = os.getenv("EGOV_WORKFLOW_SERVICE_BASE_URL")

# Sticky "warn once, then skip" tracker: a call site checks its own base URL
# and calls _warn_url_missing_once instead of attempting (and then logging
# the failure of) a doomed request -- these constants can't change mid
# process, so there's nothing to gain from retrying, only redundant log
# noise to avoid.
_url_missing_warned: set[str] = set()


def _warn_url_missing_once(env_var_name: str, service_label: str) -> None:
    if env_var_name in _url_missing_warned:
        return
    _url_missing_warned.add(env_var_name)
    log.warning(
        "%s is not set; skipping %s calls for the rest of this process.",
        env_var_name, service_label,
    )

BOUNDARY_RELATIONSHIP_SEARCH_PATH = "/boundary-service/boundary-relationships/_search"
USER_SEARCH_PATH = "/user/_search"
# Verified live against a local MDMS instance: this deployment runs the
# classic MDMS v1 service, not the /mdms-v2/v1/_search path the Java
# config referenced (that config was for a different environment) --
# request/response shape (MdmsCriteria in, MdmsRes out) is otherwise
# identical between the two.
MDMS_SEARCH_PATH = "/mdms-v2/v1/_search"
PROJECT_STAFF_ROLES_MODULE = "HCM-PROJECT-STAFF-ROLES"
PROJECT_STAFF_ROLES_MASTER = "projectStaffRoles"
# Standard DIGIT egov-workflow-v2 search path -- NOT verified against a live
# instance in this repo (unlike BOUNDARY_RELATIONSHIP_SEARCH_PATH/USER_SEARCH_PATH/
# MDMS_SEARCH_PATH, which were).
WORKFLOW_PROCESS_SEARCH_PATH = "/egov-wf/process/_search"

BOUNDARY_LEVEL_ORDINALS = [
    "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
]

_boundary_path_cache: dict[tuple[str, str, str], list[tuple[str, str]]] = {}
_boundary_tree_cache: dict[tuple[str, str], list[dict]] = {}
_bulk_codes_supported = True  # sticky; set False on first bulk failure, never retried this process
_user_info_cache: dict[tuple[str, str], dict] = {}
_project_staff_role_rank_cache: dict[str, dict[str, int]] = {}


def _build_request_info() -> dict:
    # No authToken/userInfo -- internal network call, no auth required.
    return {
        "apiId": "hcm",
        "ver": ".01",
        "ts": int(time.time() * 1000),
        "action": "_search",
        "did": "1",
        "key": "1",
    }


def _extract_roots(payload: dict) -> list[dict]:
    roots: list[dict] = []
    for hierarchy_relation in payload.get("TenantBoundary", []):
        roots.extend(hierarchy_relation.get("boundary", []))
    return roots


def _find_boundary_paths(nodes, target_codes, path=None) -> dict[str, list[tuple[str, str]]]:
    """
    Single DFS pass over a (possibly branching) boundary tree, collecting the
    root-to-target path for every code in target_codes found anywhere in the
    tree. A bulk `codes=` response for N codes sharing ancestors comes back
    as one merged tree that branches where the requested codes diverge, not
    N independent single-branch chains, so a real multi-target search is
    required here (this replaces the earlier "children[0]" linear walk, which
    only worked because a single requested code can't branch).
    """
    path = path or []
    found: dict[str, list[tuple[str, str]]] = {}
    for node in nodes:
        node_path = path + [(node.get("boundaryType"), node.get("code"))]
        code = node.get("code")
        if code in target_codes:
            found[code] = node_path
        found.update(_find_boundary_paths(node.get("children") or [], target_codes, node_path))
    return found


def _fetch_boundary_paths_bulk(tenant_id: str, hierarchy_type: str, codes: list[str]) -> dict:
    """
    One API call resolving multiple codes at once via comma-separated
    `codes=` -- verified against a live boundary-service to return the
    minimal subtree spanning just the requested codes and their ancestors,
    not the whole tree.
    """
    response = requests.post(
        f"{BOUNDARY_SERVICE_BASE_URL}{BOUNDARY_RELATIONSHIP_SEARCH_PATH}",
        params={
            "tenantId": tenant_id,
            "hierarchyType": hierarchy_type,
            "codes": ",".join(codes),
            "includeParents": "true",
            "includeChildren": "true",
        },
        json={"RequestInfo": _build_request_info()},
        timeout=30,
    )
    response.raise_for_status()
    return _find_boundary_paths(_extract_roots(response.json()), set(codes))


def _fetch_boundary_tree(tenant_id: str, hierarchy_type: str) -> list[dict]:
    """
    Whole-tree fallback, used only if the bulk codes= call fails. Cached per
    (tenant_id, hierarchy_type) for the rest of this process, since
    re-fetching the whole tree per chunk would defeat falling back to it.
    """
    cache_key = (tenant_id, hierarchy_type)
    if cache_key in _boundary_tree_cache:
        return _boundary_tree_cache[cache_key]

    try:
        response = requests.post(
            f"{BOUNDARY_SERVICE_BASE_URL}{BOUNDARY_RELATIONSHIP_SEARCH_PATH}",
            params={
                "tenantId": tenant_id,
                "hierarchyType": hierarchy_type,
                "includeParents": "true",
                "includeChildren": "true",
            },
            json={"RequestInfo": _build_request_info()},
            timeout=60,
        )
        response.raise_for_status()
        roots = _extract_roots(response.json())
    except Exception:
        log.exception(
            "whole-tree boundary fetch failed for %s/%s; returning empty tree for this "
            "call (not cached, will retry on next lookup)",
            tenant_id, hierarchy_type,
        )
        return []

    _boundary_tree_cache[cache_key] = roots
    return roots


def get_boundary_hierarchy_levels_bulk(tenant_id: str, hierarchy_type: str, boundary_codes: list[str]) -> dict:
    """
    Resolves many boundary codes at once, minimizing calls to the
    boundary-service: one bulk `codes=` call per batch of not-yet-cached
    codes, falling back to a single whole-tree-per-(tenant,hierarchy) fetch
    (cached for the rest of this run) if the bulk call fails. Returns
    {boundary_code: {level_one_code: ..., ..., hierarchy_type: ...}}.

    Unlike BoundaryService.java, this does NOT call MDMS or the localization
    service to resolve a display name for each level -- only raw boundary
    codes are returned, so the schema never needs to change when a new/deeper
    hierarchy is introduced. See airflow_dags/CLAUDE.md, "Boundary hierarchy
    flattening" for the level_one_code..level_nine_code + hierarchy_type
    convention this builds.
    """
    global _bulk_codes_supported

    uncached = [
        c for c in dict.fromkeys(boundary_codes)
        if (tenant_id, hierarchy_type, c) not in _boundary_path_cache
    ]

    if uncached:
        if not BOUNDARY_SERVICE_BASE_URL:
            _warn_url_missing_once("EGOV_BOUNDARY_SERVICE_BASE_URL", "boundary-service")
            for code in uncached:
                _boundary_path_cache[(tenant_id, hierarchy_type, code)] = []
        else:
            resolved: dict = {}
            if _bulk_codes_supported:
                try:
                    resolved = _fetch_boundary_paths_bulk(tenant_id, hierarchy_type, uncached)
                except Exception:
                    log.warning(
                        "bulk codes= boundary lookup failed for %s/%s; falling back to "
                        "whole-tree fetch for the rest of this run",
                        tenant_id, hierarchy_type,
                    )
                    _bulk_codes_supported = False

            if not _bulk_codes_supported:
                tree = _fetch_boundary_tree(tenant_id, hierarchy_type)
                resolved = _find_boundary_paths(tree, set(uncached))

            for code in uncached:
                path = resolved.get(code)
                if path is None:
                    log.warning("boundary code %s not found for %s/%s", code, tenant_id, hierarchy_type)
                    path = []
                _boundary_path_cache[(tenant_id, hierarchy_type, code)] = path

    result = {}
    for code in boundary_codes:
        path = _boundary_path_cache[(tenant_id, hierarchy_type, code)]
        if len(path) > len(BOUNDARY_LEVEL_ORDINALS):
            log.warning(
                "boundary code %s in %s/%s resolved to %d levels, more than the "
                "%d supported -- truncating",
                code, tenant_id, hierarchy_type, len(path), len(BOUNDARY_LEVEL_ORDINALS),
            )
        levels = {
            f"level_{ordinal}_code": (path[i][1] if i < len(path) else "")
            for i, ordinal in enumerate(BOUNDARY_LEVEL_ORDINALS)
        }
        levels["hierarchy_type"] = hierarchy_type
        result[code] = levels
    return result


def get_boundary_hierarchy_levels(tenant_id: str, hierarchy_type: str, boundary_code: str) -> dict:
    """Single-code convenience wrapper around get_boundary_hierarchy_levels_bulk."""
    return get_boundary_hierarchy_levels_bulk(tenant_id, hierarchy_type, [boundary_code])[boundary_code]


# --- Reusable per-chunk orchestration ---------------------------------------
#
# Everything below is entity-agnostic: it works on plain row dicts and a
# caller-supplied `get_key` callback that knows how to pull
# (tenant_id, hierarchy_type, boundary_code) out of ONE entity's joined row
# shape (e.g. project_task_transformation.py's own column names). That
# extraction is the only part that's inherently entity-specific -- grouping,
# calling the API, and attaching results back onto rows is identical for any
# entity that needs boundary hierarchy flattening, so it lives here once
# instead of being copy-pasted into every <entity>_transformation.py.

BoundaryLookupKeyFn = Callable[[dict], "tuple[str, str, str] | None"]


def parse_hierarchy_type(additional_details) -> str | None:
    """
    Reads the `hierarchyType` key from an additional_details JSON blob (a
    per-project/campaign configuration in DIGIT). Returns None if the value
    is empty, not valid JSON, or has no hierarchyType key -- callers treat
    that as "can't resolve boundaries for this row" rather than an error.
    """
    if not additional_details:
        return None
    try:
        parsed = json.loads(additional_details)
    except (TypeError, ValueError):
        return None
    hierarchy_type = parsed.get("hierarchyType") if isinstance(parsed, dict) else None
    return hierarchy_type or None


def parse_project_beneficiary_type(additional_details) -> str | None:
    """
    Reads additionalDetails.projectType.beneficiaryType from a project's
    additional_details JSON blob. Mirrors what ProjectService.java's
    getProjectBeneficiaryType computes, but skips its MDMS
    (PROJECT_TYPES-master) lookup entirely -- this deployment already embeds
    beneficiaryType directly in additionalDetails, so no service call is
    needed. Returns None if the value is empty, not valid JSON, or the
    nested path is missing.
    """
    if not additional_details:
        return None
    try:
        parsed = json.loads(additional_details)
    except (TypeError, ValueError):
        return None
    project_type = parsed.get("projectType") if isinstance(parsed, dict) else None
    beneficiary_type = project_type.get("beneficiaryType") if isinstance(project_type, dict) else None
    return beneficiary_type or None


def parse_boundary_code(additional_details) -> str | None:
    """
    Mirrors CommonUtils.java's getLocalityCodeFromAdditionalFields(null, additionalDetails):
    an object with a `boundaryCode` key -> that value; a bare JSON string -> that
    string itself; anything else (missing/malformed/other JSON type) -> None.
    Distinct from parse_hierarchy_type (additionalDetails.hierarchyType) and
    parse_additional_fields ({"fields":[...]} shape) -- this is a third,
    narrower additionalDetails shape used only by attendee/attendance-log
    boundary resolution.
    """
    if not additional_details:
        return None
    try:
        parsed = json.loads(additional_details)
    except (TypeError, ValueError):
        return None
    if isinstance(parsed, dict):
        return parsed.get("boundaryCode") or None
    if isinstance(parsed, str):
        return parsed or None
    return None


DAY_MILLIS = 86_400_000
MAX_TASK_DATES = 3660  # ~10 years; guards against corrupt start/end_date pairs, not real campaigns


def get_project_dates_list(start_ms, end_ms) -> list[str]:
    """
    Mirrors CommonUtils.java's getProjectDatesList, including its inclusive
    off-by-one (the loop condition is `timestamp <= endDate + DAY_MILLIS`,
    so the list runs one calendar day past end_date). Capped at
    MAX_TASK_DATES to guard against corrupt start/end_date pairs producing
    an unbounded list -- real campaigns never approach this. Shared by any
    entity whose silver row needs a project's day-range (project itself,
    project staff, ...) -- Java's own getProjectDatesList is likewise a
    shared CommonUtils method, not owned by one transformation service.
    """
    if not start_ms or not end_ms:
        return []
    dates = []
    ts = start_ms
    while ts <= end_ms + DAY_MILLIS:
        dates.append(pendulum.from_timestamp(ts / 1000, tz="UTC").to_date_string())
        ts += DAY_MILLIS
        if len(dates) >= MAX_TASK_DATES:
            log.warning("get_project_dates_list: date list exceeded %d days, truncating", MAX_TASK_DATES)
            break
    return dates


def build_project_additional_details(additional_details) -> str:
    """
    Mirrors ProjectService.java's extractProjectCycleAndDoseIndexes: reads
    additionalDetails.projectType.cycles directly off a project's own
    bronze row (Java re-fetches the Project from project-service purely to
    read this same field -- dropped here as incidental complexity, since
    callers already have it in their own joined row). cycleIndex is every
    cycle's id; doseIndex is only the FIRST cycle's deliveries' ids,
    matching Java exactly (not a per-cycle union). The "0"-prefix (not
    zero-padding) on each id is a literal port of Java's
    PREFIX_ZERO + id.asText(), preserved even though it looks odd for
    double-digit ids. Shared by any entity whose silver row embeds a
    project's cycle/dose index blob (project itself, project staff, ...) --
    Java's own fetchProjectAdditionalDetails is likewise a shared
    ProjectService method, not owned by one transformation service.
    """
    if not additional_details:
        return ""
    try:
        parsed = json.loads(additional_details)
    except (TypeError, ValueError):
        return ""
    project_type = parsed.get("projectType") if isinstance(parsed, dict) else None
    cycles = project_type.get("cycles") if isinstance(project_type, dict) else None
    if not isinstance(cycles, list) or not cycles:
        return ""
    cycle_index = [f"0{c['id']}" for c in cycles if isinstance(c, dict) and "id" in c]
    deliveries = cycles[0].get("deliveries") if isinstance(cycles[0], dict) else None
    dose_index = (
        [f"0{d['id']}" for d in deliveries if isinstance(d, dict) and "id" in d]
        if isinstance(deliveries, list) else []
    )
    return json.dumps({"doseIndex": dose_index, "cycleIndex": cycle_index})


def get_project_cycles(project_additional_details) -> list[dict] | None:
    """
    Reads additionalDetails.projectType.cycles from a project's
    additional_details JSON blob (mirrors ProjectService.java's
    fetchProjectTypeFromProject + CYCLES access). Each cycle's id/startDate/
    endDate are normalized to int (the source JSON sends startDate/endDate
    as numeric strings). Returns None if missing/malformed. Shared by any
    entity that needs to match a timestamp against a project's cycle
    windows (project_task's cycleIndex/doseIndex, household's cycleIndex,
    ...).
    """
    if not project_additional_details:
        return None
    try:
        parsed = json.loads(project_additional_details)
    except (TypeError, ValueError):
        return None
    project_type = parsed.get("projectType") if isinstance(parsed, dict) else None
    cycles = project_type.get("cycles") if isinstance(project_type, dict) else None
    if not isinstance(cycles, list):
        return None
    normalized = []
    for cycle in cycles:
        if not isinstance(cycle, dict):
            continue
        try:
            normalized.append({
                "id": int(cycle["id"]),
                "start_date": int(cycle["startDate"]),
                "end_date": int(cycle["endDate"]),
            })
        except (KeyError, TypeError, ValueError):
            continue
    return normalized or None


def fetch_cycle_index(cycles: list[dict], task_date_ms: int) -> int | None:
    """
    Mirrors CommonUtils.java's fetchCycleIndex: walks cycles in array
    order, returning the first cycle's id where task_date_ms falls within
    [start_date, end_date], OR strictly between that cycle's end_date and
    the NEXT cycle's start_date -- the gap AFTER a cycle (before the next
    one starts) is attributed to that same (preceding) cycle, not the next
    one, exactly as Java's isBetweenCycles does. No match (before the first
    cycle's start, or after the last cycle's end with no next cycle) ->
    None. This is the single shared method Java itself calls from both
    ProjectTaskTransformationService (cycleIndex/doseIndex) and
    HouseholdTransformationService (cycleIndex) -- callers format the
    matched id differently (see each entity file for its own formatting).
    """
    for i, cycle in enumerate(cycles):
        if cycle["start_date"] <= task_date_ms <= cycle["end_date"]:
            return cycle["id"]
        if i < len(cycles) - 1:
            next_cycle = cycles[i + 1]
            if cycle["end_date"] < task_date_ms < next_cycle["start_date"]:
                return cycle["id"]
    return None


def parse_additional_fields(additional_details) -> dict:
    """
    Parses a DIGIT AdditionalFields-style additionalDetails blob --
    {"fields": [{"key": ..., "value": ...}, ...], "schema": ..., "version": ...}
    -- into a flat {key: value} dict, mirroring Java's addAdditionalDetails
    (which copies each field's key/value pair verbatim into the output).
    Returns {} if the value is empty, not valid JSON, or has no "fields"
    list -- callers treat a missing key the same as an absent value. Shared
    by any entity whose own bronze row (or a related entity's) carries this
    fields-array additionalDetails shape (project_task, project_beneficiary,
    ...) -- distinct from a project's own additionalDetails, which nests a
    plain "projectType" object instead (see parse_project_beneficiary_type).
    """
    if not additional_details:
        return {}
    try:
        parsed = json.loads(additional_details)
    except (TypeError, ValueError):
        return {}
    fields = parsed.get("fields") if isinstance(parsed, dict) else None
    if not isinstance(fields, list):
        return {}
    return {f["key"]: f.get("value") for f in fields if isinstance(f, dict) and "key" in f}


def calculate_age_in_months(date_of_birth) -> int | None:
    """
    Mirrors Java's calculateAgeInMonthsFromDOB. Bronze *.date_of_birth
    columns are ClickHouse Date32 (calendar date, no time component), and
    clickhouse-connect returns Date32 columns as native Python date
    objects, so no epoch conversion is needed here at all. Shared by any
    entity that resolves an individual's age from their date_of_birth
    (project_task's beneficiary details, project_beneficiary's mandatory-
    field backfill, ...).
    """
    if not date_of_birth:
        return None
    now = pendulum.now("UTC").date()
    return (now.year - date_of_birth.year) * 12 + (now.month - date_of_birth.month)


def empty_boundary_levels(hierarchy_type: str = "") -> dict:
    """All-empty level_one_code..level_nine_code + hierarchy_type, used for
    rows a boundary lookup key couldn't be determined for at all -- matches
    the same not-found convention get_boundary_hierarchy_levels_bulk itself
    uses for a code that doesn't resolve, rather than a different,
    inconsistent shape."""
    levels = {f"level_{ordinal}_code": "" for ordinal in BOUNDARY_LEVEL_ORDINALS}
    levels["hierarchy_type"] = hierarchy_type
    return levels


def extract_boundary_lookup_keys(rows: list[dict], get_key: BoundaryLookupKeyFn) -> dict:
    """
    Groups rows by (tenant_id, hierarchy_type) -> the set of unique boundary
    codes needing resolution, so a caller can call
    get_boundary_hierarchy_levels_bulk once per group instead of once per
    row. `get_key(row)` is entity-specific -- it returns
    (tenant_id, hierarchy_type, boundary_code) for a row, or None if this
    particular row has no resolvable lookup key (e.g. missing address or
    campaign config) -- such rows are skipped (and counted) rather than
    raising, since that shouldn't fail the whole chunk.
    """
    lookup_keys: dict[tuple[str, str], set[str]] = {}
    skipped = 0

    for row in rows:
        key = get_key(row)
        if key is None:
            skipped += 1
            continue
        tenant_id, hierarchy_type, boundary_code = key
        lookup_keys.setdefault((tenant_id, hierarchy_type), set()).add(boundary_code)

    if skipped:
        log.warning(
            "%d rows had no resolvable boundary lookup key and were skipped",
            skipped,
        )

    return lookup_keys


def resolve_boundary_levels(lookup_keys: dict) -> dict:
    """
    Calls get_boundary_hierarchy_levels_bulk once per (tenant_id,
    hierarchy_type) group in lookup_keys -- not once per row, not once per
    code -- and flattens the results into a single mapping keyed on
    (tenant_id, hierarchy_type, boundary_code), ready for a per-row lookup.
    """
    resolved: dict[tuple[str, str, str], dict] = {}
    for (tenant_id, hierarchy_type), codes in lookup_keys.items():
        levels_by_code = get_boundary_hierarchy_levels_bulk(tenant_id, hierarchy_type, list(codes))
        for code, levels in levels_by_code.items():
            resolved[(tenant_id, hierarchy_type, code)] = levels
    return resolved


def attach_boundary_levels(rows: list[dict], resolved_levels: dict, get_key: BoundaryLookupKeyFn) -> None:
    """
    Merges each row's resolved level_one_code..level_nine_code +
    hierarchy_type into the row in place, using the same entity-specific
    `get_key(row)` used to build `resolved_levels` via
    extract_boundary_lookup_keys + resolve_boundary_levels. Rows with no
    resolvable lookup key get all-empty levels rather than being dropped --
    a row missing address or campaign config data shouldn't disappear from
    the pipeline, just lack geography.

    The lookup key is intentionally all-or-nothing: if get_key(row) returns
    None because e.g. only the boundary code is missing but hierarchy_type
    was technically derivable, hierarchy_type is still left empty rather
    than partially populated. Keeping get_key's contract to a single
    complete-triple-or-None decision, instead of letting callers dribble out
    partial information, is a deliberate simplicity tradeoff.
    """
    for row in rows:
        key = get_key(row)
        levels = resolved_levels.get(key) if key is not None else None
        hierarchy_type = key[1] if key is not None else ""
        row.update(levels if levels is not None else empty_boundary_levels(hierarchy_type))


# --- User search + info ------------------------------------------------------
#
# Mirrors UserService.java's getUsers()/getUserInfo(). get_user_info (a
# standalone single-uuid lookup) still calls _fetch_users one uuid at a time,
# matching Java's own usage. resolve_user_info -- the bulk per-chunk entry
# point every entity DAG actually uses -- batches instead, via
# _fetch_users_batch: with a chunk of thousands of rows resolving down to
# only a few thousand unique (tenant_id, user_id) pairs, one call per user
# means, if user-service is down for any part of a run, thousands of
# individual failed-call log lines (each with a full traceback) -- exactly
# the kind of Airflow-log-flooding this batches away. A short outage that
# self-resolves mid-run (a real possibility under Kubernetes -- a pod
# restart, a rolling deploy) isn't treated as "down for the rest of this
# process" by anything here; every batch is still attempted independently,
# so recovery is picked up on the very next batch with no special handling
# needed, and any rows that stay unresolved just get re-processed cleanly on
# a later run of the same window (project_task_entity's own
# ReplacingMergeTree dedupes on rewrite) via the orchestrator's window
# override (see bronze-to-silver_orcestrator.py's
# bronze_to_silver_window_start_override/_end_override).
USER_SEARCH_BATCH_SIZE = 50  # not verified against a live instance -- a reasonable default, tune if the real service enforces a different limit


def _fetch_users(tenant_id: str, user_id: str) -> list[dict]:
    """Mirrors UserService.java's getUsers() -- exactly one uuid per call.
    Only used by the standalone get_user_info lookup; resolve_user_info uses
    _fetch_users_batch instead."""
    if not USER_SERVICE_BASE_URL:
        _warn_url_missing_once("EGOV_USER_SERVICE_BASE_URL", "user-service")
        return []
    try:
        response = requests.post(
            f"{USER_SERVICE_BASE_URL}{USER_SEARCH_PATH}",
            json={"RequestInfo": _build_request_info(), "tenantId": tenant_id, "uuid": [user_id]},
            timeout=30,
        )
        response.raise_for_status()
        return response.json().get("user", [])
    except Exception:
        log.exception("user search failed for %s/%s; returning empty result", tenant_id, user_id)
        return []


def _fetch_users_batch(tenant_id: str, user_ids: list[str]) -> list[dict] | None:
    """
    Batched version of _fetch_users -- the _search API's `uuid` field already
    accepts a list (the DTO supports it; Java's own usage just never batches
    it). Returns whatever subset of user_ids the service found (possibly
    empty/partial -- a normal "not found" outcome for the missing ones, left
    for the caller to handle silently, same as today). Returns None
    (distinct from an empty list) only when the call itself failed, so the
    caller can tell "nobody in this batch exists" apart from "couldn't ask
    at all" and aggregate just the latter into one summary log instead of
    logging here per batch.

    Matches each returned user back to its uuid via `user["uuid"]` --
    verified live against a local user-service: a 3-uuid batch (2 real, 1
    nonexistent) returned exactly the 2 real users, each tagged with its own
    "uuid" field matching what was requested; "id" in the same response is a
    separate internal numeric key (already used as-is for the ID output
    field below), not the requested identifier.
    """
    if not USER_SERVICE_BASE_URL:
        _warn_url_missing_once("EGOV_USER_SERVICE_BASE_URL", "user-service")
        return None
    try:
        response = requests.post(
            f"{USER_SERVICE_BASE_URL}{USER_SEARCH_PATH}",
            json={"RequestInfo": _build_request_info(), "tenantId": tenant_id, "uuid": user_ids},
            timeout=30,
        )
        response.raise_for_status()
        return response.json().get("user", [])
    except Exception:
        return None


def _fetch_project_staff_role_ranks(tenant_id: str) -> dict[str, int]:
    """
    Mirrors MdmsService.java's getProjectStaffRoles(): role-code -> rank for
    the HCM-PROJECT-STAFF-ROLES/projectStaffRoles MDMS master, cached per
    root tenant. tenantId is truncated to its root (tenant_id.split(".")[0])
    before querying, since MDMS module config is registered at the
    state/root-tenant level, not per sub-tenant.

    Verified live against a local MDMS instance -- request/response shape
    (MdmsCriteria/MdmsRes, module/master names, {code, rank} objects) matches
    the Java reference exactly; only the endpoint path differed (see
    MDMS_SEARCH_PATH).
    """
    root_tenant_id = tenant_id.split(".")[0]
    if root_tenant_id in _project_staff_role_rank_cache:
        return _project_staff_role_rank_cache[root_tenant_id]

    if not MDMS_SERVICE_BASE_URL:
        _warn_url_missing_once("EGOV_MDMS_SERVICE_BASE_URL", "MDMS")
        return {}

    try:
        response = requests.post(
            f"{MDMS_SERVICE_BASE_URL}{MDMS_SEARCH_PATH}",
            json={
                "RequestInfo": _build_request_info(),
                "MdmsCriteria": {
                    "tenantId": root_tenant_id,
                    "moduleDetails": [
                        {"moduleName": PROJECT_STAFF_ROLES_MODULE, "masterDetails": [{"name": PROJECT_STAFF_ROLES_MASTER}]}
                    ],
                },
            },
            timeout=30,
        )
        response.raise_for_status()
        roles = response.json().get("MdmsRes", {}).get(PROJECT_STAFF_ROLES_MODULE, {}).get(PROJECT_STAFF_ROLES_MASTER, [])
        rank_by_code = {r["code"]: r["rank"] for r in roles}
    except Exception:
        log.exception(
            "MDMS project-staff-role lookup failed for tenant %s; returning empty ranks",
            root_tenant_id,
        )
        return {}

    _project_staff_role_rank_cache[root_tenant_id] = rank_by_code
    return rank_by_code


def _get_staff_role(tenant_id: str, users: list[dict]) -> str | None:
    """
    Mirrors UserService.java's getStaffRole(). 0 roles -> None without
    calling MDMS (Java calls MDMS in this case too, but it's a no-op given 0
    roles -- deliberately skipped here since the outcome is identical
    either way; the one intentional deviation from Java). 1 role -> returned
    directly, no MDMS call. >1 role -> MDMS project-staff-role rank lookup,
    lowest rank wins.
    """
    if not users:
        return None
    role_codes = [r["code"] for r in (users[0].get("roles") or [])]
    if not role_codes:
        return None
    if len(role_codes) == 1:
        return role_codes[0]
    rank_by_code = _fetch_project_staff_role_ranks(tenant_id)
    ranked = [(rank_by_code[code], code) for code in role_codes if code in rank_by_code]
    return min(ranked)[1] if ranked else None


def _not_found_user_info(user_id: str) -> dict:
    """Shared not-found shape for get_user_info and resolve_user_info --
    NOT cached by either caller, matching Java: a user could plausibly land
    in the service between now and the next lookup."""
    return {"USERNAME": user_id, "NAME": None, "ROLE": None, "ID": None, "CITY": None}


def _build_user_info(tenant_id: str, user: dict) -> dict:
    """Shared {USERNAME, NAME, ROLE, ID, CITY} shape for get_user_info and
    resolve_user_info, built from one already-fetched user object."""
    return {
        "USERNAME": user.get("userName"),
        "NAME": user.get("name"),
        "ROLE": _get_staff_role(tenant_id, [user]),
        "ID": user.get("id"),
        "CITY": user.get("correspondenceAddress"),
    }


def get_user_info(tenant_id: str, user_id: str) -> dict:
    """
    Mirrors UserService.java's getUserInfo(): cached wrapper around
    _fetch_users returning {USERNAME, NAME, ROLE, ID, CITY}. Standalone
    single-uuid lookup -- resolve_user_info (the bulk per-chunk path every
    entity DAG actually uses) calls _fetch_users_batch directly instead of
    this, so it can batch multiple users into one call.
    """
    cache_key = (tenant_id, user_id)
    if cache_key in _user_info_cache:
        return _user_info_cache[cache_key]

    users = _fetch_users(tenant_id, user_id)
    if not users:
        return _not_found_user_info(user_id)

    info = _build_user_info(tenant_id, users[0])
    _user_info_cache[cache_key] = info
    return info


def extract_user_lookup_keys(rows: list[dict], get_key) -> set[tuple[str, str]]:
    """
    Returns the set of unique (tenant_id, user_id) pairs across rows, so a
    caller resolves each once via resolve_user_info instead of once per row.
    get_key(row) is entity-specific (e.g. which column holds the relevant
    user uuid); rows it can't resolve a key for are skipped (and counted)
    rather than failing the whole chunk.
    """
    keys: set[tuple[str, str]] = set()
    skipped = 0
    for row in rows:
        key = get_key(row)
        if key is None:
            skipped += 1
            continue
        keys.add(key)
    if skipped:
        log.warning("%d rows had no resolvable user lookup key and were skipped", skipped)
    return keys


def resolve_user_info(lookup_keys: set[tuple[str, str]]) -> dict[tuple[str, str], dict]:
    """
    Resolves every (tenant_id, user_id) key, serving already-cached ones
    straight from _user_info_cache and batching the rest into groups of
    USER_SEARCH_BATCH_SIZE per tenant via _fetch_users_batch, instead of one
    HTTP call per user. If a batch call fails outright, every key in that
    batch gets the not-found fallback (same shape as a genuine "not found",
    uncached) and is added to one aggregated warning logged once at the end
    -- not one log line per failed user. A user genuinely absent from a
    successful batch response is left out of that warning entirely; that's
    an expected outcome, not a failure, same as it's always been.
    """
    resolved: dict[tuple[str, str], dict] = {}
    by_tenant: dict[str, list[str]] = {}
    for tenant_id, user_id in lookup_keys:
        cache_key = (tenant_id, user_id)
        if cache_key in _user_info_cache:
            resolved[cache_key] = _user_info_cache[cache_key]
        else:
            by_tenant.setdefault(tenant_id, []).append(user_id)

    failed_keys: list[tuple[str, str]] = []
    failed_batch_count = 0

    for tenant_id, user_ids in by_tenant.items():
        for i in range(0, len(user_ids), USER_SEARCH_BATCH_SIZE):
            batch = user_ids[i:i + USER_SEARCH_BATCH_SIZE]
            users = _fetch_users_batch(tenant_id, batch)

            if users is None:
                failed_batch_count += 1
                failed_keys.extend((tenant_id, uid) for uid in batch)
                for uid in batch:
                    resolved[(tenant_id, uid)] = _not_found_user_info(uid)
                continue

            users_by_uuid = {u["uuid"]: u for u in users if u.get("uuid")}
            for uid in batch:
                cache_key = (tenant_id, uid)
                user = users_by_uuid.get(uid)
                if user is None:
                    resolved[cache_key] = _not_found_user_info(uid)
                    continue
                info = _build_user_info(tenant_id, user)
                _user_info_cache[cache_key] = info
                resolved[cache_key] = info

    if failed_keys:
        shown = failed_keys[:50]
        suffix = f" ...and {len(failed_keys) - 50} more" if len(failed_keys) > 50 else ""
        log.warning(
            "user-service lookup failed for %d (tenant_id, user_id) pairs across %d batch call(s); "
            "each falls back to a uuid-only result for this run: %s%s",
            len(failed_keys), failed_batch_count, shown, suffix,
        )

    return resolved


# --- Workflow summary --------------------------------------------------------
#
# Mirrors BillService.java's getWorkflowSummary()/searchProcessInstances().
# Unlike boundary/user lookups, this is deliberately NOT cached indefinitely
# (no module-level cache dict) -- workflow status is far more volatile than a
# user's name or a boundary tree, so caching it for a whole (potentially
# long-lived) worker process risks serving stale status. Each call re-resolves
# fresh; only within-one-call duplicates are avoided, via the caller's own set
# dedup (same as every other resolve_* function here).


def _fetch_process_instances(tenant_id: str, business_id: str) -> list[dict]:
    """Mirrors BillService.java's searchProcessInstances(businessId, tenantId, history=true)."""
    if not WORKFLOW_SERVICE_BASE_URL:
        _warn_url_missing_once("EGOV_WORKFLOW_SERVICE_BASE_URL", "workflow-service")
        return []
    try:
        response = requests.post(
            f"{WORKFLOW_SERVICE_BASE_URL}{WORKFLOW_PROCESS_SEARCH_PATH}",
            params={"tenantId": tenant_id, "businessIds": business_id, "history": "true"},
            json={"RequestInfo": _build_request_info()},
            timeout=30,
        )
        response.raise_for_status()
        return response.json().get("ProcessInstances") or []
    except Exception:
        log.exception(
            "workflow process-instance search failed for %s/%s; returning empty result",
            tenant_id, business_id,
        )
        return []


def get_workflow_summary(tenant_id: str, business_id: str) -> dict:
    """
    Mirrors BillService.java's getWorkflowSummary(): currentStatus (latest
    instance's applicationStatus), timeTakenFromInitialState (minutes,
    latest-vs-oldest createdTime), statusTransitionTimes (a map of
    "{older_status}_TO_{newer_status}" -> minutes, over each adjacent pair,
    latest-first per the assumed response order -- skipping any pair with a
    missing state rather than raising). Returns {} if no process instances
    are found. Also returns the raw latest instance under "_latestInstance"
    for callers approximating a raw process_instance passthrough column
    (Java's own Bill.processInstance/.wfStatus fields aren't computed here,
    they arrive pre-populated on the incoming payload -- not part of Java's
    own wfStatusInfo shape, an addition specific to this port).
    """
    instances = _fetch_process_instances(tenant_id, business_id)
    if not instances:
        return {}

    latest, oldest = instances[0], instances[-1]
    result: dict = {"_latestInstance": latest}

    if latest.get("state"):
        result["currentStatus"] = latest["state"].get("applicationStatus")

    latest_created = (latest.get("auditDetails") or {}).get("createdTime")
    oldest_created = (oldest.get("auditDetails") or {}).get("createdTime")
    if latest_created is not None and oldest_created is not None:
        result["timeTakenFromInitialState"] = int((latest_created - oldest_created) / 60000)

    transitions = {}
    for i in range(len(instances) - 1):
        newer, older = instances[i], instances[i + 1]
        if not newer.get("state") or not older.get("state"):
            continue
        newer_created = (newer.get("auditDetails") or {}).get("createdTime")
        older_created = (older.get("auditDetails") or {}).get("createdTime")
        if newer_created is None or older_created is None:
            continue
        key = f"{older['state'].get('applicationStatus')}_TO_{newer['state'].get('applicationStatus')}"
        transitions[key] = int((newer_created - older_created) / 60000)
    result["statusTransitionTimes"] = transitions

    return result


def resolve_workflow_summaries(lookup_keys: set[tuple[str, str]]) -> dict[tuple[str, str], dict]:
    """Calls get_workflow_summary once per unique (tenant_id, business_id) key."""
    return {key: get_workflow_summary(*key) for key in lookup_keys}
