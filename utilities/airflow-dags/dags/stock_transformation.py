"""
stock_transformation.py

Bronze -> silver transformation DAG for the `stock` entity.
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
    extract_boundary_lookup_keys,
    fetch_cycle_index,
    get_project_cycles,
    parse_additional_fields,
    parse_hierarchy_type,
    resolve_boundary_levels,
    resolve_user_info,
)

log = logging.getLogger(__name__)

DAG_ID = "stock_transformation"

BRONZE_TABLE = "analytics.stg_stock"
FACILITY_TABLE = "analytics.stg_facility"
ADDRESS_TABLE = "analytics.stg_address"
PRODUCT_VARIANT_TABLE = "analytics.stg_product_variant"
PROJECT_TABLE = "analytics.stg_project"
PROJECT_ADDRESS_TABLE = "analytics.stg_project_address"
PROJECT_STAFF_TABLE = "analytics.stg_project_staff"
CHUNK_SIZE_VARIABLE = "bronze_to_silver_chunk_size"
DEFAULT_CHUNK_SIZE = 5000

SILVER_TABLE = "stock_entity"

_EPOCH_DATE = pendulum.Date(1970, 1, 1)
_EPOCH_DATETIME = pendulum.datetime(1970, 1, 1, tz="UTC")

SILVER_COLUMNS = [
    "id", "facility_id", "transacting_facility_id", "facility_name", "transacting_facility_name",
    "product_variant", "product_name", "physical_count", "event_type", "reason",
    "user_name", "name_of_user", "role", "user_address", "date_of_entry",
    "level_one_code", "level_two_code", "level_three_code", "level_four_code", "level_five_code",
    "level_six_code", "level_seven_code", "level_eight_code", "level_nine_code", "hierarchy_type",
    "created_by", "last_modified_by", "created_time", "last_modified_time",
    "synced_time_stamp", "synced_time", "additional_fields", "client_reference_id", "tenant_id",
    "facility_type", "transacting_facility_type", "facility_level", "transacting_facility_level",
    "facility_target", "task_dates", "synced_date", "additional_details", "waybill_number",
    "project_id", "project_type", "project_type_id", "project_name", "campaign_number", "campaign_id",
]


def _parse_window_bound(iso_ts: str):
    return pendulum.parse(iso_ts)


def _count_bronze_records(client, start_dt, end_dt) -> int:
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


def _fetch_enriched_stock_rows(client, stock_ids: list[str]) -> list[dict]:
    """
    Derives facility_id/facility_type/transacting_facility_id/
    transacting_facility_type from sender_id/sender_type/receiver_id/
    receiver_type via the same RECEIVED-based direction swap Java's
    getFacilityId/getTransactingFacilityId/getFacilityType/
    getTransactingFacilityType use -- computed in SQL (a CASE expression)
    so the two stg_facility joins land on the correctly-swapped id
    directly, rather than resolving pieces separately and merging in
    Python. The legacy facility_id/transacting_party_id/transacting_party_type
    bronze columns predate sender_id/receiver_id and are not used here.

    Both stg_facility joins degrade gracefully to empty when the derived id
    is actually a STAFF/user id (no facility row will match) -- exactly the
    signal used downstream to take the STAFF branch. addr is only joined
    for the facility side (boundary is never resolved from the transacting
    party's address). No fan-out risk -- every join is by primary key or a
    single-address-per-facility/project assumption already used elsewhere.

    st's own columns are individually aliased rather than `st.*` -- with 5
    joined tables, ClickHouse silently qualifies any st column whose bare
    name collides with a column in fac/addr/tfac/pv/p/paddr as `st.<col>`
    in the result set, breaking downstream lookups expecting bare names.
    The CASE WHEN derived columns still reference `st.<col>` directly (the
    source alias in FROM, not the wildcard expansion), so they're
    unaffected and left as-is. FINAL is used on every joined table to
    avoid row versions from un-merged ReplacingMergeTree duplicates.
    """
    result = client.query(
        f"""
        SELECT
            st.id                         AS id,
            st.client_reference_id        AS client_reference_id,
            st.tenant_id                  AS tenant_id,
            st.facility_id                AS facility_id,
            st.product_variant_id         AS product_variant_id,
            st.quantity                   AS quantity,
            st.waybill_number             AS waybill_number,
            st.date_of_entry              AS date_of_entry,
            st.campaign_number            AS campaign_number,
            st.reference_id               AS reference_id,
            st.reference_id_type          AS reference_id_type,
            st.transaction_type           AS transaction_type,
            st.transaction_reason         AS transaction_reason,
            st.transacting_party_id       AS transacting_party_id,
            st.transacting_party_type     AS transacting_party_type,
            st.sender_type                AS sender_type,
            st.sender_id                  AS sender_id,
            st.receiver_type              AS receiver_type,
            st.receiver_id                AS receiver_id,
            st.additional_details         AS additional_details,
            st.created_by                 AS created_by,
            st.created_time               AS created_time,
            st.last_modified_by           AS last_modified_by,
            st.last_modified_time         AS last_modified_time,
            st.client_created_time        AS client_created_time,
            st.client_last_modified_time  AS client_last_modified_time,
            st.client_created_by          AS client_created_by,
            st.client_last_modified_by    AS client_last_modified_by,
            st.row_version                AS row_version,
            st.is_deleted                 AS is_deleted,
            CASE WHEN lower(st.transaction_type) = 'received' THEN st.receiver_id   ELSE st.sender_id   END AS facility_id_derived,
            CASE WHEN lower(st.transaction_type) = 'received' THEN st.receiver_type ELSE st.sender_type END AS facility_type_derived,
            CASE WHEN lower(st.transaction_type) = 'received' THEN st.sender_id     ELSE st.receiver_id   END AS transacting_facility_id_derived,
            CASE WHEN lower(st.transaction_type) = 'received' THEN st.sender_type   ELSE st.receiver_type END AS transacting_facility_type_derived,
            fac.name                   AS facility_name_raw,
            fac.usage                  AS facility_usage,
            fac.is_permanent           AS facility_is_permanent,
            fac.additional_details     AS facility_additional_details_raw,
            addr.locality_code         AS facility_locality_code,
            tfac.name                  AS transacting_facility_name_raw,
            tfac.usage                 AS transacting_facility_usage,
            tfac.is_permanent          AS transacting_facility_is_permanent,
            tfac.additional_details    AS transacting_facility_additional_details_raw,
            pv.sku                     AS product_sku,
            p.project_type             AS project_type,
            p.project_type_id          AS project_type_id,
            p.name                     AS project_name,
            p.additional_details       AS project_additional_details,
            paddr.boundary              AS project_boundary_code
        FROM {BRONZE_TABLE} AS st
        LEFT JOIN {FACILITY_TABLE} AS fac FINAL
            ON fac.id = (CASE WHEN lower(st.transaction_type) = 'received' THEN st.receiver_id ELSE st.sender_id END)
            AND fac.tenant_id = st.tenant_id AND fac.is_deleted = false
        LEFT JOIN {ADDRESS_TABLE} AS addr FINAL
            ON addr.id = fac.address_id AND addr.tenant_id = fac.tenant_id
        LEFT JOIN {FACILITY_TABLE} AS tfac FINAL
            ON tfac.id = (CASE WHEN lower(st.transaction_type) = 'received' THEN st.sender_id ELSE st.receiver_id END)
            AND tfac.tenant_id = st.tenant_id AND tfac.is_deleted = false
        LEFT JOIN {PRODUCT_VARIANT_TABLE} AS pv FINAL
            ON pv.id = st.product_variant_id AND pv.tenant_id = st.tenant_id
        LEFT JOIN {PROJECT_TABLE} AS p FINAL
            ON p.id = st.reference_id AND p.tenant_id = st.tenant_id
        LEFT JOIN {PROJECT_ADDRESS_TABLE} AS paddr FINAL
            ON paddr.project_id = p.id AND paddr.tenant_id = p.tenant_id
        WHERE st.id IN %(stock_ids)s
        """,
        parameters={"stock_ids": stock_ids},
    )
    return list(result.named_results())


def _resolve_facility_level(usage, is_permanent) -> str | None:
    if not usage:
        return None
    if usage.upper() == "WAREHOUSE":
        return "DISTRICT_WAREHOUSE" if is_permanent else "SATELLITE_WAREHOUSE"
    return None


def _resolve_facility_target(facility_additional_details_raw) -> int | None:
    """Java's Long.valueOf(...) here has no try/catch (an uncaught
    NumberFormatException on bad data) -- deliberately not replicated;
    falls back to None instead of crashing the chunk."""
    fields = parse_additional_fields(facility_additional_details_raw)
    value = fields.get("target")
    if value is None:
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def _resolve_facility_type(default_type, facility_additional_details_raw) -> str:
    """default_type is the derived sender/receiver type (the real
    Java-equivalent default), overridden by the facility's own
    additionalFields "type" key if present."""
    fields = parse_additional_fields(facility_additional_details_raw)
    override = fields.get("type")
    return override if override else (default_type or "")


def _extract_staff_boundary_lookup_keys(joined_rows: list[dict]) -> dict[str, set[str]]:
    """Only rows whose DERIVED facility_type is STAFF need this bridge --
    Java's fallback for a STAFF-type held facility. The transacting side
    never needs it (Java never resolves boundary from the transacting
    party)."""
    lookup_keys: dict[str, set[str]] = {}
    for row in joined_rows:
        if (row.get("facility_type_derived") or "").upper() == "STAFF":
            staff_id = row.get("facility_id_derived")
            if staff_id:
                lookup_keys.setdefault(row["tenant_id"], set()).add(staff_id)
    return lookup_keys


def _resolve_staff_boundary_context(client, lookup_keys: dict[str, set[str]]) -> dict[tuple[str, str], dict]:
    """
    One query per tenant (zero if empty): LEFT JOINs stg_project_staff ->
    stg_project -> stg_project_address and uses ClickHouse's `LIMIT 1 BY`
    to pick each staff user's lowest-id non-deleted stg_project_staff row
    -- same tie-break already established for household/household_member,
    confirmed against the live project-service source. Resolved purely for
    this one boundary fallback -- NOT used for the stock's own
    project/campaign context, which always comes from reference_id
    directly.
    """
    resolved: dict[tuple[str, str], dict] = {}
    for tenant_id, staff_ids in lookup_keys.items():
        result = client.query(
            f"""
            SELECT
                ps.staff_id           AS staff_id,
                p.additional_details  AS project_additional_details,
                paddr.boundary         AS project_boundary_code
            FROM {PROJECT_STAFF_TABLE} AS ps
            LEFT JOIN {PROJECT_TABLE} AS p
                ON p.id = ps.project_id AND p.tenant_id = ps.tenant_id
            LEFT JOIN {PROJECT_ADDRESS_TABLE} AS paddr
                ON paddr.project_id = p.id AND paddr.tenant_id = p.tenant_id
            WHERE ps.tenant_id = %(tenant_id)s AND ps.staff_id IN %(staff_ids)s AND ps.is_deleted = false
            ORDER BY ps.id ASC
            LIMIT 1 BY ps.staff_id
            """,
            parameters={"tenant_id": tenant_id, "staff_ids": list(staff_ids)},
        )
        for r in result.named_results():
            resolved[(tenant_id, r["staff_id"])] = {
                "project_additional_details": r["project_additional_details"],
                "project_boundary_code": r["project_boundary_code"],
            }
    return resolved


def _attach_staff_boundary_context(joined_rows: list[dict], staff_boundary_context: dict) -> None:
    for row in joined_rows:
        row["staff_boundary_project_additional_details"] = None
        row["staff_boundary_code"] = None
        if (row.get("facility_type_derived") or "").upper() == "STAFF":
            details = staff_boundary_context.get((row["tenant_id"], row.get("facility_id_derived")))
            if details:
                row["staff_boundary_project_additional_details"] = details["project_additional_details"]
                row["staff_boundary_code"] = details["project_boundary_code"]


def _get_boundary_lookup_key(row: dict) -> tuple[str, str, str] | None:
    """
    STAFF-derived facility: boundary comes exclusively from the
    project-staff bridge (must run after _attach_staff_boundary_context).
    Otherwise: the facility's own address locality, falling back to the
    linked (reference_id) project's own address if reference_id_type is
    PROJECT. hierarchy_type always comes from whichever project is in play
    for that tier.
    """
    if (row.get("facility_type_derived") or "").upper() == "STAFF":
        hierarchy_type = parse_hierarchy_type(row.get("staff_boundary_project_additional_details"))
        code = row.get("staff_boundary_code")
    else:
        hierarchy_type = parse_hierarchy_type(row.get("project_additional_details"))
        code = row.get("facility_locality_code")
        if not code and (row.get("reference_id_type") or "").upper() == "PROJECT":
            code = row.get("project_boundary_code")
    if not hierarchy_type or not code:
        return None
    return row["tenant_id"], hierarchy_type, code


def _extract_user_lookup_keys(joined_rows: list[dict]) -> set[tuple[str, str]]:
    """
    Combines three purposes into one batched resolve_user_info pass: the
    display user (client_created_by), and -- symmetrically, since either
    side can be STAFF -- facility_id_derived and/or
    transacting_facility_id_derived wherever their derived type is STAFF.
    Matches resolve_user_info's own flat set[tuple[tenant_id, user_id]]
    input shape (user lookups are resolved one-by-one via a process-lifetime
    cache, not batched per-tenant like boundary lookups).
    """
    lookup_keys: set[tuple[str, str]] = set()
    for row in joined_rows:
        tenant_id = row["tenant_id"]
        created_by = row.get("client_created_by")
        if created_by:
            lookup_keys.add((tenant_id, created_by))
        if (row.get("facility_type_derived") or "").upper() == "STAFF":
            fid = row.get("facility_id_derived")
            if fid:
                lookup_keys.add((tenant_id, fid))
        if (row.get("transacting_facility_type_derived") or "").upper() == "STAFF":
            tid = row.get("transacting_facility_id_derived")
            if tid:
                lookup_keys.add((tenant_id, tid))
    return lookup_keys


ADDITIONAL_DETAILS_DOUBLE_FIELDS = {"lat", "lng"}


def _coerce_double_or_none(value):
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _build_stock_additional_details(row: dict) -> dict:
    """
    additionalFields coercion mirrors Java's addAdditionalDetails: only
    lat/lng get numeric coercion (falling back to JSON-null on a parse
    failure, matching Java's catch -> put(key, null)), every other key
    passes through raw -- exactly parse_additional_fields' own last-wins,
    no-coercion semantics for the non-numeric keys, so no bespoke merge is
    needed beyond the lat/lng coercion layer.
    """
    fields = parse_additional_fields(row.get("additional_details"))
    details = {
        key: (_coerce_double_or_none(value) if key in ADDITIONAL_DETAILS_DOUBLE_FIELDS else value)
        for key, value in fields.items()
    }
    cycles = get_project_cycles(row.get("project_additional_details"))
    matched_id = fetch_cycle_index(cycles, row.get("created_time")) if cycles and row.get("created_time") else None
    details["cycleIndex"] = f"{matched_id:02d}" if matched_id is not None else None
    return details


def _default_str(value) -> str:
    return value if value is not None else ""


def _default_int(value) -> int:
    return 0 if value is None else int(round(value))


def _default_date(epoch_ms):
    return pendulum.from_timestamp(epoch_ms / 1000, tz="UTC").date() if epoch_ms else _EPOCH_DATE


def _default_datetime(epoch_ms):
    return pendulum.from_timestamp(epoch_ms / 1000, tz="UTC") if epoch_ms else _EPOCH_DATETIME


def _build_silver_row(row: dict, resolved_user_info: dict) -> dict:
    """
    Maps one fully-enriched joined_rows dict onto stock_entity's exact
    column set. created_by/last_modified_by/created_time/last_modified_time
    map from the CLIENT audit columns here (stock_entity has no separate
    client_* block of its own) -- the inverse of every other entity's
    convention. synced_time_stamp/synced_time/synced_date use the SERVER
    audit's last_modified_time instead. campaign_number comes straight
    from bronze (a direct column), not a project join.
    """
    facility_is_staff = (row.get("facility_type_derived") or "").upper() == "STAFF"
    transacting_is_staff = (row.get("transacting_facility_type_derived") or "").upper() == "STAFF"
    tenant_id = row["tenant_id"]

    if facility_is_staff:
        facility_info = resolved_user_info.get((tenant_id, row.get("facility_id_derived"))) or {}
        facility_name = facility_info.get("USERNAME") or _default_str(row.get("facility_id_derived"))
        facility_type = row.get("facility_type_derived") or ""
        facility_level = ""
        facility_target = 0
    else:
        facility_name = row.get("facility_name_raw") or _default_str(row.get("facility_id_derived"))
        facility_type = _resolve_facility_type(row.get("facility_type_derived"), row.get("facility_additional_details_raw"))
        facility_level = _resolve_facility_level(row.get("facility_usage"), row.get("facility_is_permanent")) or ""
        facility_target = _default_int(_resolve_facility_target(row.get("facility_additional_details_raw")))

    if transacting_is_staff:
        transacting_info = resolved_user_info.get((tenant_id, row.get("transacting_facility_id_derived"))) or {}
        transacting_facility_name = transacting_info.get("USERNAME") or _default_str(row.get("transacting_facility_id_derived"))
        transacting_facility_type = row.get("transacting_facility_type_derived") or ""
        transacting_facility_level = ""
    else:
        transacting_facility_name = row.get("transacting_facility_name_raw") or _default_str(row.get("transacting_facility_id_derived"))
        transacting_facility_type = _resolve_facility_type(
            row.get("transacting_facility_type_derived"), row.get("transacting_facility_additional_details_raw"))
        transacting_facility_level = _resolve_facility_level(
            row.get("transacting_facility_usage"), row.get("transacting_facility_is_permanent")) or ""

    display_user_info = resolved_user_info.get((tenant_id, row.get("client_created_by"))) or {}

    return {
        "id": row["id"],
        "facility_id": _default_str(row.get("facility_id_derived")),
        "transacting_facility_id": _default_str(row.get("transacting_facility_id_derived")),
        "facility_name": facility_name,
        "transacting_facility_name": transacting_facility_name,
        "product_variant": _default_str(row.get("product_variant_id")),
        "product_name": row.get("product_sku") or _default_str(row.get("product_variant_id")),
        "physical_count": _default_int(row.get("quantity")),
        "event_type": _default_str(row.get("transaction_type")),
        "reason": _default_str(row.get("transaction_reason")),
        "user_name": _default_str(display_user_info.get("USERNAME")),
        "name_of_user": _default_str(display_user_info.get("NAME")),
        "role": _default_str(display_user_info.get("ROLE")),
        "user_address": _default_str(display_user_info.get("CITY")),
        "date_of_entry": _default_int(row.get("date_of_entry") or row.get("last_modified_time")),
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
        "created_by": _default_str(row.get("client_created_by")),
        "last_modified_by": _default_str(row.get("client_last_modified_by")),
        "created_time": _default_int(row.get("client_created_time")),
        "last_modified_time": _default_int(row.get("client_last_modified_time")),
        "synced_time_stamp": _default_datetime(row.get("last_modified_time")),
        "synced_time": _default_int(row.get("last_modified_time")),
        "additional_fields": _default_str(row.get("additional_details")),
        "client_reference_id": _default_str(row.get("client_reference_id")),
        "tenant_id": _default_str(tenant_id),
        "facility_type": facility_type,
        "transacting_facility_type": transacting_facility_type,
        "facility_level": facility_level,
        "transacting_facility_level": transacting_facility_level,
        "facility_target": facility_target,
        "task_dates": _default_date(row.get("client_last_modified_time")),
        "synced_date": _default_date(row.get("last_modified_time")),
        "additional_details": json.dumps(_build_stock_additional_details(row)),
        "waybill_number": _default_str(row.get("waybill_number")),
        "project_id": _default_str(row.get("reference_id")),
        "project_type": _default_str(row.get("project_type")),
        "project_type_id": _default_str(row.get("project_type_id")),
        "project_name": _default_str(row.get("project_name")),
        "campaign_number": _default_str(row.get("campaign_number")),
        "campaign_id": "",  # TODO: needs project-factory service integration (not yet built)
    }


def _build_silver_rows(joined_rows: list[dict], resolved_user_info: dict) -> list[dict]:
    """Builds each row independently; a malformed row is logged and
    skipped rather than failing the whole chunk's write."""
    silver_rows = []
    for row in joined_rows:
        try:
            silver_rows.append(_build_silver_row(row, resolved_user_info))
        except Exception:
            log.exception(
                "stock: failed to build silver row for stock id=%s; skipping this row",
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
    description="Transforms stock bronze events into the stock_entity silver table.",
    schedule=None,
    start_date=pendulum.datetime(2024, 1, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    tags=["bronze-to-silver", "stock"],
)
def stock_transformation():

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
        Reads stock bronze rows for this run's window in fixed-size chunks
        via keyset pagination, transforms, and writes each chunk to
        stock_entity before moving to the next chunk.

        Filtered on _ingested_at (bronze arrival time), not last_modified_time
        (source modification time) -- see airflow_dags/CLAUDE.md's "Bronze
        read window column" convention for why.
        """
        start_dt = _parse_window_bound(time_window["start_time"])
        end_dt = _parse_window_bound(time_window["end_time"])
        chunk_size = int(Variable.get(CHUNK_SIZE_VARIABLE, default_var=DEFAULT_CHUNK_SIZE))

        client = get_clickhouse_client()

        total = _count_bronze_records(client, start_dt, end_dt)
        log.info(
            "stock bronze records ingested in [%s, %s): %d (chunk_size=%d)",
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
                "stock chunk %d: %d stock rows (cumulative %d/%d)",
                chunk_num, len(chunk), rows_seen, total,
            )

            stock_ids = [row["id"] for row in chunk]
            joined_rows = _fetch_enriched_stock_rows(client, stock_ids)
            log.info(
                "stock chunk %d: %d stock+facility+product+project rows after LEFT JOIN",
                chunk_num, len(joined_rows),
            )

            staff_boundary_keys = _extract_staff_boundary_lookup_keys(joined_rows)
            staff_boundary_context = _resolve_staff_boundary_context(client, staff_boundary_keys)
            _attach_staff_boundary_context(joined_rows, staff_boundary_context)
            log.info(
                "stock chunk %d: resolved STAFF-facility boundary bridge for %d staff user(s)",
                chunk_num, len(staff_boundary_context),
            )

            lookup_keys = extract_boundary_lookup_keys(joined_rows, _get_boundary_lookup_key)
            resolved_levels = resolve_boundary_levels(lookup_keys)
            attach_boundary_levels(joined_rows, resolved_levels, _get_boundary_lookup_key)
            log.info(
                "stock chunk %d: attached boundary hierarchy levels to %d rows",
                chunk_num, len(joined_rows),
            )

            user_lookup_keys = _extract_user_lookup_keys(joined_rows)
            resolved_user_info = resolve_user_info(user_lookup_keys)
            log.info(
                "stock chunk %d: resolved user info for %d unique user(s)",
                chunk_num, len(user_lookup_keys),
            )

            silver_rows = _build_silver_rows(joined_rows, resolved_user_info)
            _write_silver_chunk(client, silver_rows)
            log.info(
                "stock chunk %d: wrote %d/%d rows to %s",
                chunk_num, len(silver_rows), len(joined_rows), SILVER_TABLE,
            )

    transform_bronze_to_silver(parse_time_window())


stock_transformation()
