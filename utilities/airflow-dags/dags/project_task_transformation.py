"""
project_task_transformation.py

Bronze -> silver transformation DAG for the `project_task` entity.
Triggered exclusively by bronze_to_silver_orchestrator with
conf={"start_time": ..., "end_time": ...}; not scheduled on its own.
"""
from __future__ import annotations

import logging
import os
import sys
import json

import pendulum
from airflow.decorators import dag, task
from airflow.exceptions import AirflowFailException
from airflow.models import Variable

# Airflow 3's DAG bundle loader doesn't guarantee the dags/ folder is on
# sys.path, unlike plain-module imports elsewhere -- add it explicitly so
# sibling helper modules (clickhouse_utils.py) are importable.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from clickhouse_utils import get_clickhouse_client  # noqa: E402
from egov_api_utils import (  # noqa: E402
    attach_boundary_levels,
    calculate_age_in_months,
    extract_boundary_lookup_keys,
    extract_user_lookup_keys,
    fetch_cycle_index,
    get_project_cycles,
    parse_additional_fields,
    parse_hierarchy_type,
    parse_project_beneficiary_type,
    resolve_boundary_levels,
    resolve_user_info,
)

log = logging.getLogger(__name__)

DAG_ID = "project_task_transformation"

BRONZE_TABLE = "analytics.stg_project_task"
TASK_RESOURCE_TABLE = "analytics.stg_task_resource"
ADDRESS_TABLE = "analytics.stg_address"
PROJECT_TABLE = "analytics.stg_project"
PROJECT_ADDRESS_TABLE = "analytics.stg_project_address"
PRODUCT_TABLE = "analytics.stg_product_variant"
PROJECT_BENEFICIARY_TABLE = "analytics.stg_project_beneficiary"
HOUSEHOLD_TABLE = "analytics.stg_household"
INDIVIDUAL_TABLE = "analytics.stg_individual"
CHUNK_SIZE_VARIABLE = "bronze_to_silver_chunk_size"
DEFAULT_CHUNK_SIZE = 5000
JOIN_ROW_COUNT_WARNING_MULTIPLIER = 3

SILVER_TABLE = "project_task_entity"
_EPOCH_DATE = pendulum.Date(1970, 1, 1)
_EPOCH_DATETIME = pendulum.datetime(1970, 1, 1, tz="UTC")

SILVER_COLUMNS = [
    "id", "task_id", "task_type", "status", "tenant_id", "administration_status",
    "client_reference_id", "task_client_reference_id", "project_beneficiary_client_reference_id",
    "created_by", "last_modified_by", "created_time", "last_modified_time",
    "product_variant", "product_name", "quantity", "delivered_to", "is_delivered", "delivery_comments",
    "household_id", "member_count", "individual_id",
    "user_name", "name_of_user", "role", "user_address",
    "latitude", "longitude", "location_accuracy", "boundary_code", "geo_point",
    "level_one_code", "level_two_code", "level_three_code", "level_four_code", "level_five_code",
    "level_six_code", "level_seven_code", "level_eight_code", "level_nine_code", "hierarchy_type",
    "age", "gender", "date_of_birth",
    "cycleIndex", "doseIndex", "delivery_strategy",
    "synced_time_stamp", "synced_date", "synced_time", "task_dates",
    "additional_details",
    "project_id", "project_type", "project_type_id", "project_name", "campaign_number", "campaign_id",
]


def _parse_window_bound(iso_ts: str):
    return pendulum.parse(iso_ts)

def _count_bronze_records(client, start_dt, end_dt) -> int:
    """
    Deliberately no FINAL here (unlike the enrichment queries below):
    _ingested_at is not this table's ReplacingMergeTree version column
    (last_modified_time is), so FINAL could make the row that "wins" a
    given id's dedup carry a different _ingested_at than the duplicate that
    actually fell inside [start_dt, end_dt) -- undercounting or pagination
    drift against _iter_bronze_chunks below, not just a cost tradeoff.
    """
    result = client.query(
        f"SELECT count() FROM {BRONZE_TABLE} "
        f"WHERE _ingested_at >= %(start_dt)s AND _ingested_at < %(end_dt)s",
        parameters={"start_dt": start_dt, "end_dt": end_dt},
    )
    return result.result_rows[0][0]


def _iter_bronze_chunks(client, start_dt, end_dt, chunk_size: int):
    """
    Yields lists of row dicts, walking the [start_dt, end_dt) bronze-ingestion
    window via keyset pagination on (id, _ingested_at) -- id first, not
    _ingested_at, because id is the only column here guaranteed to round-trip
    exactly through clickhouse-connect's query parameters. _ingested_at
    (DateTime64(3)) gets serialized as a plain string truncated to whole
    seconds when sent back as a cursor value -- confirmed directly against
    ClickHouse (a parameterized SELECT of a millisecond-precision datetime
    came back as the literal string with sub-second precision silently
    dropped). With _ingested_at as the *first* tuple element (as this used to
    be ordered), any batch of rows sharing one ingest timestamp -- e.g. a
    bulk bronze load done in a single insert, or a bulk raw-event-store-to-
    bronze flush inserting more rows than one chunk_size at once -- would
    have every row's true (sub-second) _ingested_at compare greater than the
    truncated cursor on that first element alone, so id (the correct
    tiebreaker) would never even be reached: the same page would be returned
    forever, a real infinite loop (reproduced directly against ClickHouse).
    id is unique per row and a plain string, immune to this truncation.
    _ingested_at is kept as a tiebreaker for id itself only out of caution
    (ids are already unique, so it's not expected to matter in practice) --
    compared as whole milliseconds (toUnixTimestamp64Milli), an integer, for
    the same round-trip-exactness reason, so even that tiebreaker can't
    silently lose precision either.
    """
    cursor_id, cursor_ms = None, None
    while True:
        cursor_clause = ""
        params = {"start_dt": start_dt, "end_dt": end_dt, "limit": chunk_size}
        if cursor_id is not None:
            cursor_clause = "AND (id, toUnixTimestamp64Milli(_ingested_at)) > (%(cursor_id)s, %(cursor_ms)s)"
            params["cursor_id"] = cursor_id
            params["cursor_ms"] = cursor_ms

        result = client.query(
            f"""
            SELECT id, toUnixTimestamp64Milli(_ingested_at) AS ingested_at_ms
            FROM {BRONZE_TABLE}
            WHERE _ingested_at >= %(start_dt)s AND _ingested_at < %(end_dt)s
            {cursor_clause}
            ORDER BY id, ingested_at_ms
            LIMIT %(limit)s
            """,
            parameters=params,
        )
        rows = list(result.named_results())
        if not rows:
            return

        yield rows

        last_row = rows[-1]
        cursor_id, cursor_ms = last_row["id"], last_row["ingested_at_ms"]
        if len(rows) < chunk_size:
            return


def _fetch_enriched_task_rows(client, task_ids: list[str]) -> list[dict]:
    """
    Left-joins this chunk's stg_project_task rows with their stg_task_resource
    rows (a task can have zero or many resources), their stg_address row (a
    task has zero or one address), and their stg_project row (a task belongs
    to exactly one project -- only its additional_details column is needed),
    done entirely in ClickHouse to avoid Python-side dict merging. No LIMIT is
    applied on any join -- every resource/address for every task_id passed in
    is returned, so nothing is ever cut off mid-task by a page boundary.
    stg_task_resource, stg_address, stg_project, and stg_project_address all
    share column names with stg_project_task (id, tenant_id, created_by,
    created_time, last_modified_by, last_modified_time, is_deleted,
    additional_details, client_reference_id, project_id, depending on the
    pair) -- `pt.*` would silently collide on all of those (ClickHouse
    renames pt's copy to "pt.<col>" in the result set whenever a same-named
    column exists *anywhere* in the joined tables, even if that joined
    column is never itself selected), so this query explicitly selects and
    aliases only the stg_project_task columns actually needed, rather than
    using pt.*. pt.address_id doesn't need to be selected at all -- the addr
    join condition references it directly, and the resolved address is read
    downstream via the explicit resolved_address_id/address_* aliases below.

    addr.id is aliased to resolved_address_id (not address_id) and
    proj.additional_details is aliased to project_additional_details, both
    to keep them distinct from stg_project_task's own address_id/
    additional_details.
    """

    '''TODO: Modify the query so that the bigger tables are on the left side of the query because in clickhouse
    the right table should always be smaller
    '''
    result = client.query(
        f"""
        SELECT
            pt.id                                       AS id,
            pt.tenant_id                                AS tenant_id,
            pt.project_id                               AS project_id,
            pt.project_beneficiary_client_reference_id  AS project_beneficiary_client_reference_id,
            pt.additional_details                       AS additional_details,
            pt.created_time                             AS created_time,
            pt.last_modified_time                       AS last_modified_time,
            pt.client_created_time                      AS client_created_time,
            pt.client_last_modified_time                AS client_last_modified_time,
            pt.client_created_by                        AS client_created_by,
            pt.client_last_modified_by                  AS client_last_modified_by,
            pt.client_reference_id                      AS client_reference_id,
            pt.status                                   AS status,
            tr.id                      AS resource_id,
            tr.product_variant_id      AS resource_product_variant_id,
            tr.quantity                AS resource_quantity,
            tr.is_delivered            AS resource_is_delivered,
            tr.reason_if_not_delivered AS resource_reason_if_not_delivered,
            tr.client_reference_id     AS resource_client_reference_id,
            tr.additional_details      AS resource_additional_details,
            addr.id                    AS resolved_address_id,
            addr.latitude              AS address_latitude,
            addr.longitude             AS address_longitude,
            addr.location_accuracy     AS address_location_accuracy,
            addr.locality_code         AS address_locality_code,
            proj.additional_details    AS project_additional_details,
            proj.project_type,
            proj.project_type_id,
            proj.name,
            proj.reference_id          AS campaign_number,
            product.sku                AS product_name,
            paddr.boundary             AS project_boundary_code
        FROM {BRONZE_TABLE} AS pt FINAL
        LEFT JOIN {TASK_RESOURCE_TABLE} AS tr FINAL
            ON tr.task_id = pt.id
            AND tr.tenant_id = pt.tenant_id
            AND tr.is_deleted = false
        LEFT JOIN {ADDRESS_TABLE} AS addr FINAL
            ON addr.id = pt.address_id
            AND addr.tenant_id = pt.tenant_id
        LEFT JOIN {PROJECT_TABLE} AS proj FINAL
            ON proj.id = pt.project_id
            AND proj.tenant_id = pt.tenant_id
        LEFT JOIN {PRODUCT_TABLE} as product FINAL
            ON product.id = tr.product_variant_id
            AND product.tenant_id = pt.tenant_id
        LEFT JOIN {PROJECT_ADDRESS_TABLE} AS paddr FINAL
            ON paddr.project_id = proj.id
            AND paddr.tenant_id = proj.tenant_id
        WHERE pt.id IN %(task_ids)s
        """,
        parameters={"task_ids": task_ids},
    )
    return list(result.named_results())


def _get_boundary_lookup_key(row: dict) -> tuple[str, str, str] | None:
    """
    Returns (tenant_id, hierarchy_type, code) for boundary hierarchy
    resolution. Prefers the task's own address locality code; if that's
    missing, falls back to the project's own boundary code (mirrors
    ProjectTaskTransformationService.java's else-branch, which resolves via
    project.getAddress().getBoundary() instead of the task's address in
    that case). Returns None only if hierarchy_type can't be resolved, or
    neither a task nor project boundary code is available.
    """
    hierarchy_type = parse_hierarchy_type(row.get("project_additional_details"))
    if not hierarchy_type:
        return None
    code = row.get("address_locality_code") or row.get("project_boundary_code")
    if not code:
        return None
    return row["tenant_id"], hierarchy_type, code


def _get_user_lookup_key(row: dict) -> tuple[str, str] | None:
    """
    project_task-specific: the user to resolve is the task's *client* audit
    createdBy (client_created_by), not the server-side created_by --
    matching ProjectTaskTransformationService.java's
    task.getClientAuditDetails().getCreatedBy(). Returns None if missing, so
    the row is skipped for user-info resolution rather than failing the
    whole chunk.
    """
    user_id = row.get("client_created_by")
    if not user_id:
        return None
    return row["tenant_id"], user_id


def _attach_beneficiary_type(joined_rows: list[dict]) -> None:
    """
    project_task-specific: stores the project's beneficiaryType (parsed from
    project_additional_details, e.g. "HOUSEHOLD") into the delivered_to
    column, per explicit instruction -- delivered_to is an existing
    project_task_entity column being repurposed for this, not a new one.
    """
    for row in joined_rows:
        row["delivered_to"] = parse_project_beneficiary_type(row.get("project_additional_details")) or ""


def _extract_beneficiary_lookup_keys(joined_rows: list[dict]) -> tuple[dict[str, set[str]], dict[str, set[str]]]:
    """
    Splits rows by beneficiary_type into two {tenant_id: set(project_beneficiary_client_reference_id)}
    groupings, so household/individual resolution can run as at most two
    targeted queries total (one per type, grouped by tenant) instead of
    joining both stg_household and stg_individual onto every row of the
    already-wide main task join -- household and individual are mutually
    exclusive per task, so at most one of those joins could ever match for
    any given row. A type with zero rows in this chunk ends up with an
    empty dict here, which makes its resolver below run zero queries.
    """
    household_keys: dict[str, set[str]] = {}
    individual_keys: dict[str, set[str]] = {}
    for row in joined_rows:
        task_ref = row.get("project_beneficiary_client_reference_id")
        if not task_ref:
            continue
        beneficiary_type = parse_project_beneficiary_type(row.get("project_additional_details"))
        if not beneficiary_type:
            continue
        tenant_id = row["tenant_id"]
        if beneficiary_type.upper() == "HOUSEHOLD":
            household_keys.setdefault(tenant_id, set()).add(task_ref)
        elif beneficiary_type.upper() == "INDIVIDUAL":
            individual_keys.setdefault(tenant_id, set()).add(task_ref)
    return household_keys, individual_keys


def _resolve_household_details(client, household_keys: dict[str, set[str]]) -> dict[tuple[str, str], dict]:
    """
    Runs one query per tenant in household_keys (zero queries if empty),
    joining stg_project_beneficiary -> stg_household only -- never touches
    stg_individual. LEFT JOIN so household_id still resolves from the
    bridge's own reference even if the household bronze row is momentarily
    missing (member_count just stays None in that case, avoiding the
    NPE-shaped assumption the Java reference makes by reading
    households.get(0) before its own empty check).
    """
    resolved: dict[tuple[str, str], dict] = {}
    for tenant_id, task_refs in household_keys.items():
        result = client.query(
            f"""
            SELECT
                pb.client_reference_id             AS task_beneficiary_ref,
                pb.beneficiary_client_reference_id AS resolved_beneficiary_ref,
                hh.member_count                    AS member_count
            FROM {PROJECT_BENEFICIARY_TABLE} AS pb FINAL
            LEFT JOIN {HOUSEHOLD_TABLE} AS hh FINAL
                ON hh.client_reference_id = pb.beneficiary_client_reference_id
                AND hh.tenant_id = pb.tenant_id
                AND hh.is_deleted = false
            WHERE pb.tenant_id = %(tenant_id)s
                AND pb.client_reference_id IN %(task_refs)s
                AND pb.is_deleted = false
            """,
            parameters={"tenant_id": tenant_id, "task_refs": list(task_refs)},
        )
        for r in result.named_results():
            resolved[(tenant_id, r["task_beneficiary_ref"])] = {
                "household_id": r["resolved_beneficiary_ref"] or "",
                "member_count": r["member_count"],
            }
    return resolved


def _resolve_individual_details(client, individual_keys: dict[str, set[str]]) -> dict[tuple[str, str], dict]:
    """Mirrors _resolve_household_details, joining stg_project_beneficiary ->
    stg_individual only -- never touches stg_household."""
    resolved: dict[tuple[str, str], dict] = {}
    for tenant_id, task_refs in individual_keys.items():
        result = client.query(
            f"""
            SELECT
                pb.client_reference_id             AS task_beneficiary_ref,
                pb.beneficiary_client_reference_id AS resolved_beneficiary_ref,
                ind.date_of_birth                  AS date_of_birth,
                ind.gender                         AS gender
            FROM {PROJECT_BENEFICIARY_TABLE} AS pb FINAL
            LEFT JOIN {INDIVIDUAL_TABLE} AS ind FINAL
                ON ind.client_reference_id = pb.beneficiary_client_reference_id
                AND ind.tenant_id = pb.tenant_id
                AND ind.is_deleted = false
            WHERE pb.tenant_id = %(tenant_id)s
                AND pb.client_reference_id IN %(task_refs)s
                AND pb.is_deleted = false
            """,
            parameters={"tenant_id": tenant_id, "task_refs": list(task_refs)},
        )
        for r in result.named_results():
            resolved[(tenant_id, r["task_beneficiary_ref"])] = {
                "individual_id": r["resolved_beneficiary_ref"] or "",
                "date_of_birth": r["date_of_birth"],
                "gender": r["gender"] or "",
            }
    return resolved


def _attach_beneficiary_details(joined_rows: list[dict], household_details: dict, individual_details: dict) -> None:
    """
    Mirrors ProjectTaskTransformationService.java's getProjectBeneficiaryDetails,
    resolved via the targeted queries above instead of an HTTP search call.
    Matches Java's behavior of only populating the fields for the matching
    branch -- the other branch's fields stay at their empty/None default,
    and an unresolved/unknown beneficiary_type leaves everything at default
    rather than raising.
    """
    for row in joined_rows:
        row["household_id"] = ""
        row["member_count"] = None
        row["individual_id"] = ""
        row["date_of_birth"] = None
        row["age"] = None
        row["gender"] = ""

        task_ref = row.get("project_beneficiary_client_reference_id")
        if not task_ref:
            continue
        beneficiary_type = parse_project_beneficiary_type(row.get("project_additional_details"))
        key = (row["tenant_id"], task_ref)

        if beneficiary_type and beneficiary_type.upper() == "HOUSEHOLD":
            details = household_details.get(key)
            if details:
                row["household_id"] = details["household_id"]
                row["member_count"] = details["member_count"]
        elif beneficiary_type and beneficiary_type.upper() == "INDIVIDUAL":
            details = individual_details.get(key)
            if details:
                row["individual_id"] = details["individual_id"]
                row["date_of_birth"] = details["date_of_birth"]
                row["gender"] = details["gender"]
                row["age"] = calculate_age_in_months(details["date_of_birth"])


def _parse_uint8(value) -> int | None:
    """
    Returns value as an int if it parses AND fits UInt8 (0-255), else None.
    cycleIndex/doseIndex are UInt8 in project_task_entity, but their source
    (an arbitrary upstream string in the task's own additionalDetails) has
    no such guarantee -- e.g. one observed task's cycleIndex is the literal
    string "-1" (an apparent sentinel for "not applicable"). Unlike a
    Python-level parse error, an out-of-range int is still "valid" as far
    as int() and the rest of this function is concerned, so it would
    otherwise reach clickhouse-connect's native insert and fail there --
    and since native inserts are columnar/batched, one bad value fails the
    *entire chunk's* write, not just this one row.
    """
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        return None
    return parsed if 0 <= parsed <= 255 else None


def _resolve_cycle_index(row: dict, task_fields: dict) -> int:
    existing = _parse_uint8(task_fields.get("cycleIndex"))
    if existing is not None:
        return existing

    cycles = get_project_cycles(row.get("project_additional_details"))
    task_date = row.get("client_created_time")
    if not cycles or not task_date:
        return 0
    return _parse_uint8(fetch_cycle_index(cycles, task_date)) or 0


def _resolve_dose_index(task_fields: dict) -> int:
    """
    Per explicit instruction: doseIndex is read directly from the task's
    own additionalDetails.fields if present -- there is no Java reference
    for computing it (a project cycle's doseCriteria/deliveryStrategy data
    isn't sufficient to unambiguously determine which dose a given task
    represents), so no computation is attempted here. Defaults to 0 if
    absent, unparseable, or out of UInt8 range.
    """
    return _parse_uint8(task_fields.get("doseIndex")) or 0


def _attach_cycle_dose_delivery(joined_rows: list[dict]) -> None:
    for row in joined_rows:
        task_fields = parse_additional_fields(row.get("additional_details"))
        row["delivery_strategy"] = task_fields.get("deliveryStrategy") or ""

        beneficiary_type = parse_project_beneficiary_type(row.get("project_additional_details"))
        if beneficiary_type and beneficiary_type.upper() == "INDIVIDUAL":
            row["cycleIndex"] = _resolve_cycle_index(row, task_fields)
            row["doseIndex"] = _resolve_dose_index(task_fields)
        else:
            row["cycleIndex"] = 0
            row["doseIndex"] = 0


def _build_geo_point(latitude, longitude) -> str:
    """
    Mirrors CommonUtils.java's getGeoPoint, which returns
    [longitude, latitude] (that exact order) as a List<Double> -- not a
    string. Since project_task_entity.geo_point is a plain String with no
    existing convention in this repo to match, this represents it as a
    JSON array string, preserving Java's exact value/order unambiguously.
    """
    if latitude is None or longitude is None:
        return ""
    return json.dumps([longitude, latitude])


def _attach_task_core_fields(joined_rows: list[dict]) -> None:
    """
    Ports the small, mechanical field-mapping pieces of
    ProjectTaskTransformationService.java's builder chain that need no
    external call or query. Note synced_date (server audit
    last_modified_time) and task_dates (CLIENT audit last_modified_time)
    are genuinely different source columns despite both being
    "last-modified"-derived dates.
    """
    for row in joined_rows:
        row["task_type"] = "DELIVERY"
        row["administration_status"] = row.get("status") or ""
        row["product_variant"] = row.get("resource_product_variant_id") or ""
        row["boundary_code"] = row.get("address_locality_code") or ""
        row["geo_point"] = _build_geo_point(row.get("address_latitude"), row.get("address_longitude"))

        created_time = row.get("created_time")
        row["synced_time_stamp"] = pendulum.from_timestamp(created_time / 1000, tz="UTC") if created_time else None

        last_modified_time = row.get("last_modified_time")
        row["synced_date"] = (
            pendulum.from_timestamp(last_modified_time / 1000, tz="UTC").date() if last_modified_time else None
        )
        row["synced_time"] = last_modified_time

        client_last_modified_time = row.get("client_last_modified_time")
        row["task_dates"] = (
            pendulum.from_timestamp(client_last_modified_time / 1000, tz="UTC").date()
            if client_last_modified_time else None
        )


def _default_str(value) -> str:
    return value if value is not None else ""


def _default_int(value) -> int:
    return 0 if value is None else int(round(value))


def _default_float(value) -> float:
    return 0.0 if value is None else float(value)


def _default_bool(value) -> bool:
    return bool(value) if value is not None else False


def _default_date(value):
    return value if value is not None else _EPOCH_DATE


def _default_datetime(value):
    return value if value is not None else _EPOCH_DATETIME


def _resolve_resource_fields(row: dict) -> dict:
    """
    Mirrors ProjectTaskTransformationService.java's constructTaskResourceIfNull.
    _fetch_enriched_task_rows's LEFT JOIN against stg_task_resource already
    guarantees one row per task even with zero resources (resource_id comes
    back empty) -- this only supplies the placeholder VALUES Java would have
    synthesized in that case, not any additional/fewer rows.
    """
    if row.get("resource_id"):
        return {
            "id": row["resource_id"],
            "client_reference_id": row.get("resource_client_reference_id") or "",
            "product_variant_id": row.get("resource_product_variant_id") or "",
            "quantity": row.get("resource_quantity"),
            "is_delivered": row.get("resource_is_delivered"),
            "delivery_comment": row.get("resource_reason_if_not_delivered") or "",
        }

    task_status = row.get("status") or ""
    placeholder_id = f"{task_status}-{row['id']}"
    placeholder_client_ref = f"{task_status}-{row.get('client_reference_id') or ''}"

    task_fields = parse_additional_fields(row.get("additional_details"))
    product_variant_id, quantity, delivery_comment = None, None, None
    if task_fields:
        product_variant_id = task_fields.get("productVariantId")
        task_status_field = task_fields.get("taskStatus")
        if task_status_field and task_status_field.upper() == "BENEFICIARY_REFERRED" and product_variant_id:
            quantity = 2  # Constants.RE_ADMINISTERED_DOSES
            delivery_comment = "ADMINISTRATION_NOT_SUCCESSFUL"  # Constants.ADMINISTRATION_NOT_SUCCESSFUL

    return {
        "id": placeholder_id,
        "client_reference_id": placeholder_client_ref,
        "product_variant_id": product_variant_id or "",
        "quantity": quantity,
        "is_delivered": False,
        "delivery_comment": delivery_comment or "",
    }


def _build_silver_row(row: dict) -> dict:
    """
    Maps one fully-enriched joined_rows dict onto project_task_entity's
    exact column set. `id`/`client_reference_id` come from the resolved
    task_resource (real or placeholder), NOT the task's own id/reference
    (those land in task_id/task_client_reference_id instead); created_by/
    last_modified_by/created_time/last_modified_time come from the task's
    CLIENT audit columns, not the plain server-side ones of the same name
    already present in `row` via pt.* -- both are deliberate, since Java's
    builder chain reads those specific fields.
    """
    resource = _resolve_resource_fields(row)
    return {
        "id": resource["id"],
        "task_id": row["id"],
        "task_type": _default_str(row.get("task_type")),
        "status": _default_str(row.get("status")),
        "tenant_id": _default_str(row.get("tenant_id")),
        "administration_status": _default_str(row.get("administration_status")),
        "client_reference_id": resource["client_reference_id"],
        "task_client_reference_id": _default_str(row.get("client_reference_id")),
        "project_beneficiary_client_reference_id": _default_str(row.get("project_beneficiary_client_reference_id")),
        "created_by": _default_str(row.get("client_created_by")),
        "last_modified_by": _default_str(row.get("client_last_modified_by")),
        "created_time": _default_int(row.get("client_created_time")),
        "last_modified_time": _default_int(row.get("client_last_modified_time")),
        "product_variant": _default_str(resource["product_variant_id"]),
        "product_name": _default_str(row.get("product_name")),
        "quantity": _default_int(resource["quantity"]),
        "delivered_to": _default_str(row.get("delivered_to")),
        "is_delivered": _default_bool(resource["is_delivered"]),
        "delivery_comments": _default_str(resource["delivery_comment"]),
        "household_id": _default_str(row.get("household_id")),
        "member_count": _default_int(row.get("member_count")),
        "individual_id": _default_str(row.get("individual_id")),
        "user_name": _default_str(row.get("user_name")),
        "name_of_user": _default_str(row.get("name_of_user")),
        "role": _default_str(row.get("role")),
        "user_address": _default_str(row.get("user_address")),
        "latitude": _default_float(row.get("address_latitude")),
        "longitude": _default_float(row.get("address_longitude")),
        "location_accuracy": _default_float(row.get("address_location_accuracy")),
        "boundary_code": _default_str(row.get("boundary_code")),
        "geo_point": _default_str(row.get("geo_point")),
        "level_one_code": _default_str(row.get("level_one_code")),
        "level_two_code": _default_str(row.get("level_two_code")),
        "level_three_code": _default_str(row.get("level_three_code")),
        "level_four_code": _default_str(row.get("level_four_code")),
        "level_five_code": _default_str(row.get("level_five_code")),
        "level_six_code": _default_str(row.get("level_six_code")),
        "level_seven_code": _default_str(row.get("level_seven_code")),
        "level_eight_code": _default_str(row.get("level_eight_code")),
        "level_nine_code": _default_str(row.get("level_nine_code")),
        "hierarchy_type": _default_str(row.get("hierarchy_type")),
        "age": _default_int(row.get("age")),
        "gender": _default_str(row.get("gender")),
        "date_of_birth": _default_date(row.get("date_of_birth")),
        "cycleIndex": _default_int(row.get("cycleIndex")),
        "doseIndex": _default_int(row.get("doseIndex")),
        "delivery_strategy": _default_str(row.get("delivery_strategy")),
        "synced_time_stamp": _default_datetime(row.get("synced_time_stamp")),
        "synced_date": _default_date(row.get("synced_date")),
        "synced_time": _default_int(row.get("synced_time")),
        "task_dates": _default_date(row.get("task_dates")),
        # Interim passthrough of the task's own raw additional_details JSON --
        # not Java's merged AGE/GENDER/HEIGHT/isVulnerable/reasonOfRefusal blob,
        # which is deferred pending a flattened-columns-vs-JSON design decision.
        "additional_details": _default_str(row.get("additional_details")),
        "project_id": _default_str(row.get("project_id")),
        "project_type": _default_str(row.get("project_type")),
        "project_type_id": _default_str(row.get("project_type_id")),
        "project_name": _default_str(row.get("name")),
        "campaign_number": _default_str(row.get("campaign_number")),
        "campaign_id": "",  # TODO: needs project-factory service integration (not yet built)
    }


def _build_silver_rows(joined_rows: list[dict]) -> list[dict]:
    """Builds each row independently; a malformed row is logged and
    skipped rather than failing the whole chunk's write."""
    silver_rows = []
    for row in joined_rows:
        try:
            silver_rows.append(_build_silver_row(row))
        except Exception:
            log.exception(
                "project_task: failed to build silver row for task id=%s; skipping this row",
                row.get("id"),
            )
    return silver_rows


def _write_silver_chunk(client, silver_rows: list[dict]) -> None:
    if not silver_rows:
        return
    data = [[row[column] for column in SILVER_COLUMNS] for row in silver_rows]
    client.insert(SILVER_TABLE, data, column_names=SILVER_COLUMNS)


@dag(
    dag_id=DAG_ID,
    description="Transforms project_task bronze events into the project_task_entity silver table.",
    schedule=None,
    start_date=pendulum.datetime(2024, 1, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    tags=["bronze-to-silver", "project_task"],
)
def project_task_transformation():

    @task
    def parse_time_window(**context) -> dict:
        """Reads and validates start_time/end_time injected by the orchestrator."""
        conf = context["dag_run"].conf or {}
        start_time_raw = conf.get("start_time")
        end_time_raw = conf.get("end_time")

        if not start_time_raw or not end_time_raw:
            raise AirflowFailException(
                f"{DAG_ID} requires 'start_time' and 'end_time' in dag_run.conf; "
                f"got conf={conf!r}. This DAG must be triggered by "
                f"bronze_to_silver_orchestrator (or manually with an equivalent conf)."
            )

        start_time = pendulum.parse(start_time_raw)
        end_time = pendulum.parse(end_time_raw)
        return {
            "start_time": start_time.to_iso8601_string(),
            "end_time": end_time.to_iso8601_string(),
        }

    @task
    def transform_bronze_to_silver(time_window: dict) -> None:
        """
        Reads project_task bronze rows for this run's window in fixed-size
        chunks via keyset pagination, so the per-row transform (not yet
        implemented -- see TODO below) can operate on manageable batches
        instead of one giant result set.

        Filtered on _ingested_at (bronze arrival time), not last_modified_time
        (source modification time): because of pipeline latency (Debezium ->
        Kafka -> raw event store -> the raw-to-bronze DAG), a row modified at
        the source within this window may not land in bronze until well after
        the window has already been processed. _ingested_at correctly captures
        "what showed up in bronze during this run", regardless of when the
        source-side edit happened. last_modified_time remains the correct
        column for the silver-side ReplacingMergeTree versioning -- that is a
        separate concern from this read-side window filter.
        """
        start_dt = _parse_window_bound(time_window["start_time"])
        end_dt = _parse_window_bound(time_window["end_time"])
        chunk_size = int(Variable.get(CHUNK_SIZE_VARIABLE, default_var=DEFAULT_CHUNK_SIZE))

        client = get_clickhouse_client()

        total = _count_bronze_records(client, start_dt, end_dt)
        log.info(
            "project_task bronze records ingested in [%s, %s): %d (chunk_size=%d)",
            time_window["start_time"], time_window["end_time"], total, chunk_size,
        )
        if total == 0:
            return

        chunk_num = 0
        rows_seen = 0
        for chunk in _iter_bronze_chunks(client, start_dt, end_dt, chunk_size):
            chunk_num += 1
            rows_seen += len(chunk)
            log.info(
                "project_task chunk %d: %d task rows (cumulative %d/%d)",
                chunk_num, len(chunk), rows_seen, total,
            )

            task_ids = [row["id"] for row in chunk]
            joined_rows = _fetch_enriched_task_rows(client, task_ids)
            if len(joined_rows) > chunk_size * JOIN_ROW_COUNT_WARNING_MULTIPLIER:
                log.warning(
                    "project_task chunk %d: joined rows (%d) exceed %dx chunk_size (%d) "
                    "-- some tasks in this chunk have unusually high resource fan-out",
                    chunk_num, len(joined_rows), JOIN_ROW_COUNT_WARNING_MULTIPLIER, chunk_size,
                )
            log.info(
                "project_task chunk %d: %d task+resource+address rows after LEFT JOIN",
                chunk_num, len(joined_rows),
            )

            lookup_keys = extract_boundary_lookup_keys(joined_rows, _get_boundary_lookup_key)
            unique_codes = sum(len(codes) for codes in lookup_keys.values())
            log.info(
                "project_task chunk %d: %d unique (tenant, hierarchyType) group(s), "
                "%d unique boundary code(s) to resolve",
                chunk_num, len(lookup_keys), unique_codes,
            )

            resolved_levels = resolve_boundary_levels(lookup_keys)
            attach_boundary_levels(joined_rows, resolved_levels, _get_boundary_lookup_key)
            log.info(
                "project_task chunk %d: attached boundary hierarchy levels to %d rows",
                chunk_num, len(joined_rows),
            )

            user_lookup_keys = extract_user_lookup_keys(joined_rows, _get_user_lookup_key)
            resolved_user_info = resolve_user_info(user_lookup_keys)
            for row in joined_rows:
                info = resolved_user_info.get(_get_user_lookup_key(row)) or {}
                row["user_name"] = info.get("USERNAME") or ""
                row["name_of_user"] = info.get("NAME") or ""
                row["role"] = info.get("ROLE") or ""
                row["user_address"] = info.get("CITY") or ""
            log.info(
                "project_task chunk %d: attached user info to %d rows (%d unique user(s))",
                chunk_num, len(joined_rows), len(user_lookup_keys),
            )

            _attach_beneficiary_type(joined_rows)
            log.info(
                "project_task chunk %d: attached beneficiary_type (delivered_to) to %d rows",
                chunk_num, len(joined_rows),
            )

            household_keys, individual_keys = _extract_beneficiary_lookup_keys(joined_rows)
            household_details = _resolve_household_details(client, household_keys)
            individual_details = _resolve_individual_details(client, individual_keys)
            _attach_beneficiary_details(joined_rows, household_details, individual_details)
            log.info(
                "project_task chunk %d: attached beneficiary details "
                "(%d household lookup(s), %d individual lookup(s))",
                chunk_num,
                sum(len(v) for v in household_keys.values()),
                sum(len(v) for v in individual_keys.values()),
            )

            _attach_cycle_dose_delivery(joined_rows)
            log.info(
                "project_task chunk %d: attached cycleIndex/doseIndex/delivery_strategy to %d rows",
                chunk_num, len(joined_rows),
            )

            _attach_task_core_fields(joined_rows)
            log.info(
                "project_task chunk %d: attached task_type/administration_status/product_variant/"
                "boundary_code/geo_point/synced_*/task_dates to %d rows",
                chunk_num, len(joined_rows),
            )

            silver_rows = _build_silver_rows(joined_rows)
            _write_silver_chunk(client, silver_rows)
            log.info(
                "project_task chunk %d: wrote %d/%d rows to %s",
                chunk_num, len(silver_rows), len(joined_rows), SILVER_TABLE,
            )

    transform_bronze_to_silver(parse_time_window())


project_task_transformation()
