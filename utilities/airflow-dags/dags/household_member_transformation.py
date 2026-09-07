"""
household_member_transformation.py

Bronze -> silver transformation DAG for the `household_member` entity.
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
    resolve_boundary_levels,
    resolve_user_info,
)

log = logging.getLogger(__name__)

DAG_ID = "household_member_transformation"

BRONZE_TABLE = "analytics.stg_household_member"
HOUSEHOLD_TABLE = "analytics.stg_household"
ADDRESS_TABLE = "analytics.stg_address"
INDIVIDUAL_TABLE = "analytics.stg_individual"
PROJECT_STAFF_TABLE = "analytics.stg_project_staff"
PROJECT_TABLE = "analytics.stg_project"
CHUNK_SIZE_VARIABLE = "bronze_to_silver_chunk_size"
DEFAULT_CHUNK_SIZE = 5000

SILVER_TABLE = "household_member_entity"

_EPOCH_DATE = pendulum.Date(1970, 1, 1)
_EPOCH_DATETIME = pendulum.datetime(1970, 1, 1, tz="UTC")

SILVER_COLUMNS = [
    "id", "tenant_id", "client_reference_id", "household_id", "household_client_reference_id",
    "individual_id", "individual_client_reference_id", "is_head_of_household", "is_deleted", "row_version",
    "member_additional_fields",
    "created_by", "last_modified_by", "created_time", "last_modified_time",
    "client_created_by", "client_last_modified_by", "client_created_time", "client_last_modified_time",
    "level_one_code", "level_two_code", "level_three_code", "level_four_code", "level_five_code",
    "level_six_code", "level_seven_code", "level_eight_code", "level_nine_code", "hierarchy_type",
    "date_of_birth", "age", "gender",
    "user_name", "name_of_user", "role", "user_address",
    "task_dates", "synced_date", "synced_time_stamp",
    "geo_point_lat", "geo_point_lon", "boundary_code", "additional_details",
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


def _fetch_enriched_household_member_rows(client, household_member_ids: list[str]) -> list[dict]:
    """
    Left-joins this chunk's stg_household_member rows with their parent
    stg_household row (household_member has no address of its own, so
    boundary/geo resolution must bridge through the household's), that
    household's own stg_address row, and the linked stg_individual row
    (single-hop -- household_member already carries individual_client_
    reference_id directly, unlike project_task's two-hop beneficiary
    bridge).

    Bridges to the parent household via household_client_reference_id, NOT
    household_id -- Java calls householdService.searchHousehold(household
    Member.getHouseholdClientReferenceId(), ...), the client-reference-id
    path, so joining on the plain id column here would be a real bug.

    hm's own columns are individually aliased rather than `hm.*` -- with
    3+ joined tables, ClickHouse silently qualifies any hm column whose
    bare name collides with a column in *any* joined table (even one
    never explicitly selected) as `hm.<col>` in the result set, which
    breaks every downstream lookup expecting bare names like `id` or
    `client_last_modified_by`. hh/ind are filtered is_deleted=false
    (bridge/child-style lookups, consistent with every other household/
    individual join in this codebase); stg_address has no is_deleted
    column. FINAL is used on every joined bronze table to avoid row
    fan-out from un-merged ReplacingMergeTree duplicate versions.
    """
    result = client.query(
        f"""
        SELECT
            hm.id                             AS id,
            hm.client_reference_id            AS client_reference_id,
            hm.tenant_id                      AS tenant_id,
            hm.individual_id                  AS individual_id,
            hm.individual_client_reference_id AS individual_client_reference_id,
            hm.household_id                   AS household_id,
            hm.household_client_reference_id  AS household_client_reference_id,
            hm.is_head_of_household           AS is_head_of_household,
            hm.additional_details             AS additional_details,
            hm.created_by                     AS created_by,
            hm.created_time                   AS created_time,
            hm.last_modified_by               AS last_modified_by,
            hm.last_modified_time             AS last_modified_time,
            hm.client_created_time            AS client_created_time,
            hm.client_last_modified_time      AS client_last_modified_time,
            hm.client_created_by              AS client_created_by,
            hm.client_last_modified_by        AS client_last_modified_by,
            hm.row_version                    AS row_version,
            hm.is_deleted                     AS is_deleted,
            hh.additional_details             AS household_additional_details_raw,
            addr.latitude                     AS address_latitude,
            addr.longitude                    AS address_longitude,
            addr.locality_code                AS address_locality_code,
            ind.date_of_birth                 AS individual_date_of_birth,
            ind.gender                        AS individual_gender,
            ind.additional_details            AS individual_additional_details_raw
        FROM {BRONZE_TABLE} AS hm
        LEFT JOIN {HOUSEHOLD_TABLE} AS hh FINAL
            ON hh.client_reference_id = hm.household_client_reference_id AND hh.tenant_id = hm.tenant_id
            AND hh.is_deleted = false
        LEFT JOIN {ADDRESS_TABLE} AS addr FINAL
            ON addr.id = hh.address_id AND addr.tenant_id = hh.tenant_id
        LEFT JOIN {INDIVIDUAL_TABLE} AS ind FINAL
            ON ind.client_reference_id = hm.individual_client_reference_id AND ind.tenant_id = hm.tenant_id
            AND ind.is_deleted = false
        WHERE hm.id IN %(household_member_ids)s
        """,
        parameters={"household_member_ids": household_member_ids},
    )
    return list(result.named_results())


def _extract_staff_lookup_keys(joined_rows: list[dict]) -> dict[str, set[str]]:
    """Groups the last-modifying user ids needing a project-staff bridge
    lookup by tenant -- same user id also used for display user info
    (_get_user_lookup_key), unlike household's own split of two different
    users for these two purposes."""
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
    source: GenericRepository's default ordering, no custom query). Same
    bridge already built for household_transformation.py, copied verbatim
    since it's entity-agnostic.
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
    """Sets bridged_project_id/project_type/project_type_id/project_name/
    campaign_number/bridged_project_additional_details on every row --
    all default to empty/None when the bridge doesn't resolve (the
    last-modifying user has no current staff assignment), never raises."""
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
    """hierarchy_type from the BRIDGED project's additionalDetails (same as
    household); boundary_code from the PARENT HOUSEHOLD's address locality
    (household_member has none of its own). Must run after
    _attach_project_context."""
    hierarchy_type = parse_hierarchy_type(row.get("bridged_project_additional_details"))
    if not hierarchy_type:
        return None
    code = row.get("address_locality_code")
    if not code:
        return None
    return row["tenant_id"], hierarchy_type, code


def _get_user_lookup_key(row: dict) -> tuple[str, str] | None:
    """Unlike household (created_by for display, client_last_modified_by
    for the bridge), household_member's Java uses client_last_modified_by
    for BOTH purposes -- confirmed from the Java source, not an assumption."""
    user_id = row.get("client_last_modified_by")
    if not user_id:
        return None
    return row["tenant_id"], user_id


def _resolve_individual_extra_fields(individual_additional_details_raw) -> dict:
    """
    height/disabilityType from the linked Individual's OWN additionalFields
    -- added only when BOTH keys are present (Java's conjunctive
    containsKey check; note containsKey is true even if height's value
    ended up None from a parse failure, so presence-of-key, not
    presence-of-non-null-value, gates this). height parse failure ->
    None/JSON-null (matches Java's catch -> put(key, null)), NOT a raw
    string fallback (project_beneficiary's convention) and NOT a zero
    fallback (household's own convention) -- a third, distinct fallback
    behavior.
    """
    fields = parse_additional_fields(individual_additional_details_raw)
    if "height" not in fields or "disabilityType" not in fields:
        return {}
    try:
        height = int(fields["height"])
    except (TypeError, ValueError):
        height = None
    return {"height": height, "disabilityType": fields["disabilityType"]}


def _resolve_cycle_index_str(project_additional_details, client_created_time) -> str | None:
    """Same shared cycle-matching logic/formatting as household's own
    cycleIndex (household_member's Java call site is byte-for-byte
    identical)."""
    cycles = get_project_cycles(project_additional_details)
    if not cycles or not client_created_time:
        return None
    matched_id = fetch_cycle_index(cycles, client_created_time)
    return f"{matched_id:02d}" if matched_id is not None else None


def _build_household_member_additional_details(row: dict) -> dict:
    """
    Mirrors HouseholdMemberTransformationService.transform's additionalDetails
    construction: household's own fields first, household_member's own
    fields merged on top (member's value wins on a shared key -- Java
    processes household fields into the ObjectNode first, member fields
    second, both via bare put()). This is exactly parse_additional_fields'
    own last-wins/no-coercion semantics, so no bespoke merge function is
    needed for this part (unlike household's own bespoke first-wins/
    zero-fallback coercion).
    """
    details = {
        **parse_additional_fields(row.get("household_additional_details_raw")),
        **parse_additional_fields(row.get("additional_details")),
    }
    details.update(_resolve_individual_extra_fields(row.get("individual_additional_details_raw")))
    details["cycleIndex"] = _resolve_cycle_index_str(
        row.get("bridged_project_additional_details"), row.get("client_created_time"))
    return details


def _date_to_epoch_ms(value) -> int | None:
    """stg_individual.date_of_birth comes back as a Python date (Date32);
    household_member_entity.date_of_birth is Int64 (epoch ms, matching
    Java's Date.getTime()) -- convert back rather than passing the date
    through directly."""
    if not value:
        return None
    return int(pendulum.datetime(value.year, value.month, value.day, tz="UTC").timestamp() * 1000)


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
    Maps one fully-enriched joined_rows dict onto household_member_entity's
    exact column set. `member_additional_fields` holds the RAW bronze
    additionalFields blob (mirrors Java embedding the whole original
    HouseholdMember object) -- `additional_details` holds the separate,
    DERIVED blob (household + member + individual-extras + cycleIndex).
    `date_of_birth` is epoch ms (Int64), not a calendar date, unlike every
    other date-of-birth-shaped field so far. task_dates uses the CLIENT
    audit's last_modified_time; synced_date/synced_time_stamp use the
    SERVER audit's -- same split already established elsewhere.
    """
    return {
        "id": row["id"],
        "tenant_id": _default_str(row.get("tenant_id")),
        "client_reference_id": _default_str(row.get("client_reference_id")),
        "household_id": _default_str(row.get("household_id")),
        "household_client_reference_id": _default_str(row.get("household_client_reference_id")),
        "individual_id": _default_str(row.get("individual_id")),
        "individual_client_reference_id": _default_str(row.get("individual_client_reference_id")),
        "is_head_of_household": _default_bool(row.get("is_head_of_household")),
        "is_deleted": _default_bool(row.get("is_deleted")),
        "row_version": _default_int(row.get("row_version")),
        "member_additional_fields": _default_str(row.get("additional_details")),
        "created_by": _default_str(row.get("created_by")),
        "last_modified_by": _default_str(row.get("last_modified_by")),
        "created_time": _default_int(row.get("created_time")),
        "last_modified_time": _default_int(row.get("last_modified_time")),
        "client_created_by": _default_str(row.get("client_created_by")),
        "client_last_modified_by": _default_str(row.get("client_last_modified_by")),
        "client_created_time": _default_int(row.get("client_created_time")),
        "client_last_modified_time": _default_int(row.get("client_last_modified_time")),
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
        "date_of_birth": _default_int(_date_to_epoch_ms(row.get("individual_date_of_birth"))),
        "age": _default_int(calculate_age_in_months(row.get("individual_date_of_birth"))),
        "gender": _default_str(row.get("individual_gender")),
        "user_name": _default_str(row.get("user_name")),
        "name_of_user": _default_str(row.get("name_of_user")),
        "role": _default_str(row.get("role")),
        "user_address": _default_str(row.get("user_address")),
        "task_dates": _default_date(row.get("client_last_modified_time")),
        "synced_date": _default_date(row.get("last_modified_time")),
        "synced_time_stamp": _default_datetime(row.get("last_modified_time")),
        "geo_point_lat": _default_float(row.get("address_latitude")),
        "geo_point_lon": _default_float(row.get("address_longitude")),
        "boundary_code": _default_str(row.get("address_locality_code")),
        "additional_details": json.dumps(_build_household_member_additional_details(row)),
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
                "household_member: failed to build silver row for household_member id=%s; skipping this row",
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
    description="Transforms household_member bronze events into the household_member_entity silver table.",
    schedule=None,
    start_date=pendulum.datetime(2024, 1, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    tags=["bronze-to-silver", "household_member"],
)
def household_member_transformation():

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
        Reads household_member bronze rows for this run's window in
        fixed-size chunks via keyset pagination, transforms, and writes
        each chunk to household_member_entity before moving to the next
        chunk.

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
            "household_member bronze records ingested in [%s, %s): %d (chunk_size=%d)",
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
                "household_member chunk %d: %d household_member rows (cumulative %d/%d)",
                chunk_num, len(chunk), rows_seen, total,
            )

            household_member_ids = [row["id"] for row in chunk]
            joined_rows = _fetch_enriched_household_member_rows(client, household_member_ids)
            log.info(
                "household_member chunk %d: %d household_member+household+address+individual rows after LEFT JOIN",
                chunk_num, len(joined_rows),
            )

            staff_lookup_keys = _extract_staff_lookup_keys(joined_rows)
            unique_user_count = sum(len(user_ids) for user_ids in staff_lookup_keys.values())
            user_project_context = _resolve_user_project_context(client, staff_lookup_keys)
            _attach_project_context(joined_rows, user_project_context)
            log.info(
                "household_member chunk %d: resolved project-staff bridge for %d/%d unique user(s)",
                chunk_num, len(user_project_context), unique_user_count,
            )

            lookup_keys = extract_boundary_lookup_keys(joined_rows, _get_boundary_lookup_key)
            resolved_levels = resolve_boundary_levels(lookup_keys)
            attach_boundary_levels(joined_rows, resolved_levels, _get_boundary_lookup_key)
            log.info(
                "household_member chunk %d: attached boundary hierarchy levels to %d rows",
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
                "household_member chunk %d: attached user info to %d rows (%d unique user(s))",
                chunk_num, len(joined_rows), len(user_lookup_keys),
            )

            silver_rows = _build_silver_rows(joined_rows)
            _write_silver_chunk(client, silver_rows)
            log.info(
                "household_member chunk %d: wrote %d/%d rows to %s",
                chunk_num, len(silver_rows), len(joined_rows), SILVER_TABLE,
            )

    transform_bronze_to_silver(parse_time_window())


household_member_transformation()
