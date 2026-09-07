"""
household_transformation.py

Bronze -> silver transformation DAG for the `household` entity.
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
    extract_user_lookup_keys,
    fetch_cycle_index,
    get_project_cycles,
    parse_hierarchy_type,
    resolve_boundary_levels,
    resolve_user_info,
)

log = logging.getLogger(__name__)

DAG_ID = "household_transformation"

BRONZE_TABLE = "analytics.stg_household"
ADDRESS_TABLE = "analytics.stg_address"
PROJECT_STAFF_TABLE = "analytics.stg_project_staff"
PROJECT_TABLE = "analytics.stg_project"
CHUNK_SIZE_VARIABLE = "bronze_to_silver_chunk_size"
DEFAULT_CHUNK_SIZE = 5000

SILVER_TABLE = "household_entity"

# Mirrors HouseholdService.java's ADDITIONAL_DETAILS_INTEGER_FIELDS.
HOUSEHOLD_INT_FIELDS = {"pregnantWomen", "children", "noOfRooms", "menCount", "womenCount"}

_EPOCH_DATE = pendulum.Date(1970, 1, 1)
_EPOCH_DATETIME = pendulum.datetime(1970, 1, 1, tz="UTC")

SILVER_COLUMNS = [
    "id", "tenant_id", "client_reference_id", "member_count", "is_deleted", "row_version",
    "address_id", "address_latitude", "address_longitude", "address_location_accuracy",
    "address_type", "address_locality", "household_additional_fields",
    "created_by", "last_modified_by", "created_time", "last_modified_time",
    "client_created_by", "client_last_modified_by", "client_created_time", "client_last_modified_time",
    "user_name", "name_of_user", "role", "user_address",
    "task_dates", "synced_date", "synced_time_stamp",
    "level_one_code", "level_two_code", "level_three_code", "level_four_code", "level_five_code",
    "level_six_code", "level_seven_code", "level_eight_code", "level_nine_code", "hierarchy_type",
    "geo_point_lat", "geo_point_lon", "additional_details",
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


def _fetch_enriched_household_rows(client, household_ids: list[str]) -> list[dict]:
    """
    Left-joins this chunk's stg_household rows with their own stg_address
    row (household has zero or one address, via address_id). hh's own
    columns are individually aliased rather than `hh.*` -- stg_address
    does share several column names with stg_household (id, tenant_id,
    client_reference_id), and ClickHouse can silently qualify those as
    `hh.<col>` once another join is added, breaking downstream lookups
    expecting bare names; explicit aliasing avoids that landmine even
    though this specific 2-table join doesn't trigger it today. FINAL is
    used on the joined bronze table to avoid row fan-out from un-merged
    ReplacingMergeTree duplicate versions.
    """
    result = client.query(
        f"""
        SELECT
            hh.id                         AS id,
            hh.tenant_id                  AS tenant_id,
            hh.client_reference_id        AS client_reference_id,
            hh.member_count               AS member_count,
            hh.address_id                 AS address_id,
            hh.additional_details         AS additional_details,
            hh.created_by                 AS created_by,
            hh.last_modified_by           AS last_modified_by,
            hh.created_time               AS created_time,
            hh.last_modified_time         AS last_modified_time,
            hh.client_created_time        AS client_created_time,
            hh.client_last_modified_time  AS client_last_modified_time,
            hh.client_created_by          AS client_created_by,
            hh.client_last_modified_by    AS client_last_modified_by,
            hh.row_version                AS row_version,
            hh.is_deleted                 AS is_deleted,
            hh.household_type             AS household_type,
            addr.latitude          AS address_latitude,
            addr.longitude         AS address_longitude,
            addr.location_accuracy AS address_location_accuracy,
            addr.type               AS address_type,
            addr.locality_code      AS address_locality_code
        FROM {BRONZE_TABLE} AS hh
        LEFT JOIN {ADDRESS_TABLE} AS addr FINAL
            ON addr.id = hh.address_id AND addr.tenant_id = hh.tenant_id
        WHERE hh.id IN %(household_ids)s
        """,
        parameters={"household_ids": household_ids},
    )
    return list(result.named_results())


def _extract_staff_lookup_keys(joined_rows: list[dict]) -> dict[str, set[str]]:
    """Groups the last-modifying user ids needing a project-staff bridge
    lookup by tenant."""
    lookup_keys: dict[str, set[str]] = {}
    for row in joined_rows:
        user_id = row.get("client_last_modified_by")
        if user_id:
            lookup_keys.setdefault(row["tenant_id"], set()).add(user_id)
    return lookup_keys


def _resolve_user_project_context(client, lookup_keys: dict[str, set[str]]) -> dict[tuple[str, str], dict]:
    """
    One query per tenant (zero if empty). LEFT JOINs stg_project_staff ->
    stg_project directly and uses ClickHouse's `LIMIT 1 BY` (top-1-per-
    group) to pick each user's lowest-id non-deleted stg_project_staff row
    in the same query -- mirrors ProjectStaffRepository's real
    `ORDER BY id ASC` exactly (confirmed against the live project-service
    source: GenericRepository's default ordering, no custom query).
    Resolving the staff assignment and its project in one joined query
    (rather than two separate queries merged in Python) keeps this both
    cheaper and simpler.
    """
    resolved: dict[tuple[str, str], dict] = {}
    for tenant_id, user_ids in lookup_keys.items():
        result = client.query(
            f"""
            SELECT
                ps.staff_id           AS user_id,
                p.id                  AS project_id,
                p.project_type        AS project_type,
                p.project_type_id     AS project_type_id,
                p.name                AS project_name,
                p.reference_id        AS campaign_number,
                p.additional_details  AS project_additional_details
            FROM {PROJECT_STAFF_TABLE} AS ps
            LEFT JOIN {PROJECT_TABLE} AS p
                ON p.id = ps.project_id AND p.tenant_id = ps.tenant_id
            WHERE ps.tenant_id = %(tenant_id)s
                AND ps.staff_id IN %(user_ids)s
                AND ps.is_deleted = false
            ORDER BY ps.id ASC
            LIMIT 1 BY ps.staff_id
            """,
            parameters={"tenant_id": tenant_id, "user_ids": list(user_ids)},
        )
        for r in result.named_results():
            resolved[(tenant_id, r["user_id"])] = {
                "project_id": r["project_id"] or "",
                "project_type": r["project_type"] or "",
                "project_type_id": r["project_type_id"] or "",
                "project_name": r["project_name"] or "",
                "campaign_number": r["campaign_number"] or "",
                "project_additional_details": r["project_additional_details"],
            }
    return resolved


def _attach_project_context(joined_rows: list[dict], user_project_context: dict) -> None:
    """
    Sets bridged_project_id/project_type/project_type_id/project_name/
    campaign_number/bridged_project_additional_details on every row --
    all default to empty/None when the bridge doesn't resolve (the
    last-modifying user has no current staff assignment), never raises.
    """
    for row in joined_rows:
        row["bridged_project_id"] = ""
        row["project_type"] = ""
        row["project_type_id"] = ""
        row["project_name"] = ""
        row["campaign_number"] = ""
        row["bridged_project_additional_details"] = None

        user_id = row.get("client_last_modified_by")
        details = user_project_context.get((row["tenant_id"], user_id)) if user_id else None
        if details:
            row["bridged_project_id"] = details["project_id"]
            row["project_type"] = details["project_type"]
            row["project_type_id"] = details["project_type_id"]
            row["project_name"] = details["project_name"]
            row["campaign_number"] = details["campaign_number"]
            row["bridged_project_additional_details"] = details["project_additional_details"]


def _get_boundary_lookup_key(row: dict) -> tuple[str, str, str] | None:
    """
    hierarchy_type only ever comes from the BRIDGED project's own
    additionalDetails (household has no additionalDetails.hierarchyType
    path of its own); boundary_code is household's OWN address locality --
    no project-address fallback, unlike project_staff/project_beneficiary.
    Must run after _attach_project_context.
    """
    hierarchy_type = parse_hierarchy_type(row.get("bridged_project_additional_details"))
    if not hierarchy_type:
        return None
    code = row.get("address_locality_code")
    if not code:
        return None
    return row["tenant_id"], hierarchy_type, code


def _get_user_lookup_key(row: dict) -> tuple[str, str] | None:
    """Server-side created_by -- for DISPLAY user info, NOT the same user
    id used for the project-staff bridge (client_last_modified_by)."""
    user_id = row.get("created_by")
    if not user_id:
        return None
    return row["tenant_id"], user_id


def _parse_int_or_zero(value) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0


def _build_household_additional_details(raw_additional_details) -> dict:
    """
    Mirrors HouseholdService.java's additionalFieldsToDetails + the inline
    isVulnerable derivation in HouseholdTransformationService.transform.
    Reads the raw fields list directly (not via parse_additional_fields)
    because Java's semantics here differ from project_beneficiary's own
    additionalFields coercion: first-occurrence-wins on a duplicate key
    (not last-wins), and a parse failure on an integer field falls back to
    0 (not the raw string).
    """
    details: dict = {}
    if raw_additional_details:
        try:
            parsed = json.loads(raw_additional_details)
        except (TypeError, ValueError):
            parsed = None
        fields = parsed.get("fields") if isinstance(parsed, dict) else None
        if isinstance(fields, list):
            for f in fields:
                if not isinstance(f, dict) or "key" not in f:
                    continue
                key = f["key"]
                if key in details:
                    continue  # first occurrence wins
                value = f.get("value")
                details[key] = _parse_int_or_zero(value) if key in HOUSEHOLD_INT_FIELDS else value

    if details.get("pregnantWomen", 0) > 0 or details.get("children", 0) > 0:
        details["isVulnerable"] = True
    return details


def _resolve_household_cycle_index(project_additional_details, client_created_time) -> str | None:
    """
    Reuses the shared cycle-matching logic (get_project_cycles/
    fetch_cycle_index -- the exact CommonUtils.fetchCycleIndex method Java
    itself shares between project_task and household), formatted as
    zero-padded width-2 (Java's String.format("%02d", ...)) -- distinct
    from the "0"+id prefix-concat quirk used for project/project_task's
    cycle LISTS.
    """
    cycles = get_project_cycles(project_additional_details)
    if not cycles or not client_created_time:
        return None
    matched_id = fetch_cycle_index(cycles, client_created_time)
    return f"{matched_id:02d}" if matched_id is not None else None


def _default_str(value) -> str:
    return value if value is not None else ""


def _default_int(value) -> int:
    return 0 if value is None else int(round(value))


def _default_float(value) -> float:
    return 0.0 if value is None else float(value)


def _default_bool(value) -> bool:
    return bool(value) if value is not None else False


def _default_date(epoch_ms):
    return pendulum.from_timestamp(epoch_ms / 1000, tz="UTC").date() if epoch_ms else _EPOCH_DATE


def _default_datetime(epoch_ms):
    return pendulum.from_timestamp(epoch_ms / 1000, tz="UTC") if epoch_ms else _EPOCH_DATETIME


def _build_silver_row(row: dict) -> dict:
    """
    Maps one fully-enriched joined_rows dict onto household_entity's exact
    column set. `household_additional_fields` holds the RAW bronze
    additionalFields blob (mirrors Java embedding the whole original
    Household object) -- `additional_details` holds the separate, DERIVED
    blob (coerced fields + isVulnerable + cycleIndex). task_dates uses the
    CLIENT audit's last_modified_time; synced_date/synced_time_stamp use
    the SERVER audit's -- same split already established elsewhere.
    `address_locality` is represented as a minimal {"code": ...} JSON
    object since bronze stg_address only carries the locality CODE, not
    the full Boundary sub-object Java's Address.locality field would hold.
    """
    additional_details_dict = _build_household_additional_details(row.get("additional_details"))
    additional_details_dict["cycleIndex"] = _resolve_household_cycle_index(
        row.get("bridged_project_additional_details"), row.get("client_created_time"))

    locality_code = row.get("address_locality_code")

    return {
        "id": row["id"],
        "tenant_id": _default_str(row.get("tenant_id")),
        "client_reference_id": _default_str(row.get("client_reference_id")),
        "member_count": _default_int(row.get("member_count")),
        "is_deleted": _default_bool(row.get("is_deleted")),
        "row_version": _default_int(row.get("row_version")),
        "address_id": _default_str(row.get("address_id")),
        "address_latitude": _default_float(row.get("address_latitude")),
        "address_longitude": _default_float(row.get("address_longitude")),
        "address_location_accuracy": _default_float(row.get("address_location_accuracy")),
        "address_type": _default_str(row.get("address_type")),
        "address_locality": json.dumps({"code": locality_code}) if locality_code else "",
        "household_additional_fields": _default_str(row.get("additional_details")),
        "created_by": _default_str(row.get("created_by")),
        "last_modified_by": _default_str(row.get("last_modified_by")),
        "created_time": _default_int(row.get("created_time")),
        "last_modified_time": _default_int(row.get("last_modified_time")),
        "client_created_by": _default_str(row.get("client_created_by")),
        "client_last_modified_by": _default_str(row.get("client_last_modified_by")),
        "client_created_time": _default_int(row.get("client_created_time")),
        "client_last_modified_time": _default_int(row.get("client_last_modified_time")),
        "user_name": _default_str(row.get("user_name")),
        "name_of_user": _default_str(row.get("name_of_user")),
        "role": _default_str(row.get("role")),
        "user_address": _default_str(row.get("user_address")),
        "task_dates": _default_date(row.get("client_last_modified_time")),
        "synced_date": _default_date(row.get("last_modified_time")),
        "synced_time_stamp": _default_datetime(row.get("last_modified_time")),
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
        "geo_point_lat": _default_float(row.get("address_latitude")),
        "geo_point_lon": _default_float(row.get("address_longitude")),
        "additional_details": json.dumps(additional_details_dict),
        "project_id": _default_str(row.get("bridged_project_id")),
        "project_type": _default_str(row.get("project_type")),
        "project_type_id": _default_str(row.get("project_type_id")),
        "project_name": _default_str(row.get("project_name")),
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
                "household: failed to build silver row for household id=%s; skipping this row",
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
    description="Transforms household bronze events into the household_entity silver table.",
    schedule=None,
    start_date=pendulum.datetime(2024, 1, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    tags=["bronze-to-silver", "household"],
)
def household_transformation():

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
        Reads household bronze rows for this run's window in fixed-size
        chunks via keyset pagination, transforms, and writes each chunk to
        household_entity before moving to the next chunk.

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
            "household bronze records ingested in [%s, %s): %d (chunk_size=%d)",
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
                "household chunk %d: %d household rows (cumulative %d/%d)",
                chunk_num, len(chunk), rows_seen, total,
            )

            household_ids = [row["id"] for row in chunk]
            joined_rows = _fetch_enriched_household_rows(client, household_ids)
            log.info(
                "household chunk %d: %d household+address rows after LEFT JOIN",
                chunk_num, len(joined_rows),
            )

            staff_lookup_keys = _extract_staff_lookup_keys(joined_rows)
            unique_user_count = sum(len(user_ids) for user_ids in staff_lookup_keys.values())
            user_project_context = _resolve_user_project_context(client, staff_lookup_keys)
            _attach_project_context(joined_rows, user_project_context)
            log.info(
                "household chunk %d: resolved project-staff bridge for %d/%d unique user(s)",
                chunk_num, len(user_project_context), unique_user_count,
            )

            lookup_keys = extract_boundary_lookup_keys(joined_rows, _get_boundary_lookup_key)
            resolved_levels = resolve_boundary_levels(lookup_keys)
            attach_boundary_levels(joined_rows, resolved_levels, _get_boundary_lookup_key)
            log.info(
                "household chunk %d: attached boundary hierarchy levels to %d rows",
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
                "household chunk %d: attached user info to %d rows (%d unique user(s))",
                chunk_num, len(joined_rows), len(user_lookup_keys),
            )

            silver_rows = _build_silver_rows(joined_rows)
            _write_silver_chunk(client, silver_rows)
            log.info(
                "household chunk %d: wrote %d/%d rows to %s",
                chunk_num, len(silver_rows), len(joined_rows), SILVER_TABLE,
            )

    transform_bronze_to_silver(parse_time_window())


household_transformation()
