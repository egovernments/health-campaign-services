"""
attendance_staff_transformation.py

Bronze -> silver transformation DAG for the `attendance_staff` entity
(StaffPermission -> AttendanceStaffIndexV1 in the Java reference). Triggered
exclusively by bronze_to_silver_orchestrator with
conf={"start_time": ..., "end_time": ...}; not scheduled on its own.
"""
from __future__ import annotations

import logging
import os
import sys

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
    parse_hierarchy_type,
    resolve_boundary_levels,
    resolve_user_info,
)

log = logging.getLogger(__name__)

DAG_ID = "attendance_staff_transformation"

BRONZE_TABLE = "analytics.stg_attendance_staff"
INDIVIDUAL_TABLE = "analytics.stg_individual"
REGISTER_TABLE = "analytics.stg_attendance_register"
PROJECT_STAFF_TABLE = "analytics.stg_project_staff"
PROJECT_TABLE = "analytics.stg_project"
PROJECT_ADDRESS_TABLE = "analytics.stg_project_address"
CHUNK_SIZE_VARIABLE = "bronze_to_silver_chunk_size"
DEFAULT_CHUNK_SIZE = 5000

SILVER_TABLE = "attendance_staff_entity"

SILVER_COLUMNS = [
    "id", "tenant_id", "register_id", "user_id", "enrollment_date", "denrollment_date",
    "created_by", "last_modified_by", "created_time", "last_modified_time", "additional_details",
    "user_name", "name_of_user", "role", "register_service_code", "register_name", "register_number",
    "level_one_code", "level_two_code", "level_three_code", "level_four_code", "level_five_code",
    "level_six_code", "level_seven_code", "level_eight_code", "level_nine_code", "hierarchy_type",
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


def _fetch_enriched_staff_rows(client, staff_ids: list[str]) -> list[dict]:
    """
    Left-joins this chunk's stg_attendance_staff rows with the individual
    they belong to (StaffPermission.userId is an Individual's own id in
    Java, not a client_reference_id -- so this joins on stg_individual.id,
    not client_reference_id) and the register they're enrolled in.

    st's own columns are individually aliased rather than `st.*` -- with
    2+ joined tables, ClickHouse silently qualifies any st column whose
    bare name collides with a column in ind/reg as `st.<col>` in the
    result set, breaking downstream lookups expecting bare names. FINAL
    is used on both joined tables to avoid row versions from un-merged
    ReplacingMergeTree duplicates; no fan-out guard needed beyond that --
    both joins are by primary key.
    """
    result = client.query(
        f"""
        SELECT
            st.id                 AS id,
            st.individual_id      AS individual_id,
            st.register_id        AS register_id,
            st.tenant_id          AS tenant_id,
            st.enrollment_date    AS enrollment_date,
            st.deenrollment_date  AS deenrollment_date,
            st.additional_details AS additional_details,
            st.created_by         AS created_by,
            st.last_modified_by   AS last_modified_by,
            st.created_time       AS created_time,
            st.last_modified_time AS last_modified_time,
            st.staff_type         AS staff_type,
            ind.user_uuid       AS resolved_user_uuid,
            reg.name            AS register_name_raw,
            reg.service_code    AS register_service_code_raw,
            reg.register_number AS register_number_raw
        FROM {BRONZE_TABLE} AS st
        LEFT JOIN {INDIVIDUAL_TABLE} AS ind FINAL
            ON ind.id = st.individual_id AND ind.tenant_id = st.tenant_id AND ind.is_deleted = false
        LEFT JOIN {REGISTER_TABLE} AS reg FINAL
            ON reg.id = st.register_id AND reg.tenant_id = st.tenant_id
        WHERE st.id IN %(staff_ids)s
        """,
        parameters={"staff_ids": staff_ids},
    )
    return list(result.named_results())


def _extract_staff_lookup_keys(joined_rows: list[dict]) -> dict[str, set[str]]:
    lookup_keys: dict[str, set[str]] = {}
    for row in joined_rows:
        user_uuid = row.get("resolved_user_uuid")
        if user_uuid:
            lookup_keys.setdefault(row["tenant_id"], set()).add(user_uuid)
    return lookup_keys


def _resolve_user_project_context(client, lookup_keys: dict[str, set[str]]) -> dict[tuple[str, str], dict]:
    """
    LEFT JOIN stg_project_staff -> stg_project -> stg_project_address,
    LIMIT 1 BY staff_id -- same tie-break already established for
    household/household_member/stock/pgr (confirmed against the live
    project-service source: GenericRepository's default ORDER BY id ASC).
    This is the ONLY boundary path for staff -- no locality-code fallback
    exists in Java for this specific file.
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
                p.additional_details  AS project_additional_details,
                paddr.boundary        AS project_boundary_code
            FROM {PROJECT_STAFF_TABLE} AS ps
            LEFT JOIN {PROJECT_TABLE} AS p
                ON p.id = ps.project_id AND p.tenant_id = ps.tenant_id
            LEFT JOIN {PROJECT_ADDRESS_TABLE} AS paddr
                ON paddr.project_id = p.id AND paddr.tenant_id = p.tenant_id
            WHERE ps.tenant_id = %(tenant_id)s AND ps.staff_id IN %(user_ids)s AND ps.is_deleted = false
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
                "project_boundary_code": r["project_boundary_code"],
            }
    return resolved


def _attach_project_context(joined_rows: list[dict], user_project_context: dict) -> None:
    for row in joined_rows:
        row["project_id"] = ""
        row["project_type"] = ""
        row["project_type_id"] = ""
        row["project_name"] = ""
        row["campaign_number"] = ""
        row["project_additional_details"] = None
        row["project_boundary_code"] = None
        details = user_project_context.get((row["tenant_id"], row.get("resolved_user_uuid")))
        if details:
            row.update(details)


def _get_boundary_lookup_key(row: dict) -> tuple[str, str, str] | None:
    """
    Project-id-based only -- no locality fallback, faithfully matching
    Java's getBoundaryHierarchyByCodeOrProjectId for THIS file (the sibling
    attendee transformer has a different, locality-aware version -- not
    replicated here).
    """
    hierarchy_type = parse_hierarchy_type(row.get("project_additional_details"))
    if not hierarchy_type:
        return None
    code = row.get("project_boundary_code")
    if not code:
        return None
    return row["tenant_id"], hierarchy_type, code


def _get_user_lookup_key(row: dict) -> tuple[str, str] | None:
    user_uuid = row.get("resolved_user_uuid")
    if not user_uuid:
        return None
    return row["tenant_id"], user_uuid


def _default_str(value) -> str:
    return value if value is not None else ""


def _default_int(value) -> int:
    return 0 if value is None else int(round(value))


def _build_silver_row(row: dict) -> dict:
    """
    Maps one fully-enriched joined row onto attendance_staff_entity's exact
    column set. user_id is bronze's raw individual_id (StaffPermission's
    own userId field), NOT resolved_user_uuid -- the resolved uuid only
    drives the user-info/project-staff/boundary lookups. additional_details
    is passed through as-is -- Java builds no derived blob for staff.
    register_service_code/register_name/register_number fall back to ""
    if the register join missed, matching Java's null-guarded builder
    fields. No user_address column exists on this silver table (unlike
    most entities), so get_user_info's CITY isn't surfaced here.
    """
    return {
        "id": row["id"],
        "tenant_id": _default_str(row.get("tenant_id")),
        "register_id": _default_str(row.get("register_id")),
        "user_id": _default_str(row.get("individual_id")),
        "enrollment_date": _default_int(row.get("enrollment_date")),
        "denrollment_date": _default_int(row.get("deenrollment_date")),
        "created_by": _default_str(row.get("created_by")),
        "last_modified_by": _default_str(row.get("last_modified_by")),
        "created_time": _default_int(row.get("created_time")),
        "last_modified_time": _default_int(row.get("last_modified_time")),
        "additional_details": _default_str(row.get("additional_details")),
        "user_name": _default_str(row.get("user_name")),
        "name_of_user": _default_str(row.get("name_of_user")),
        "role": _default_str(row.get("role")),
        "register_service_code": _default_str(row.get("register_service_code_raw")),
        "register_name": _default_str(row.get("register_name_raw")),
        "register_number": _default_str(row.get("register_number_raw")),
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
        "project_id": _default_str(row.get("project_id")),
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
                "attendance_staff: failed to build silver row for staff id=%s; skipping this row",
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
    description="Transforms attendance-staff bronze events into the attendance_staff_entity silver table.",
    schedule=None,
    start_date=pendulum.datetime(2024, 1, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    tags=["bronze-to-silver", "attendance_staff"],
)
def attendance_staff_transformation():

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
        Reads attendance-staff bronze rows for this run's window in
        fixed-size chunks via keyset pagination, transforms, and writes each
        chunk to attendance_staff_entity before moving to the next chunk.

        Filtered on _ingested_at (bronze arrival time), not
        last_modified_time (source modification time) -- see
        airflow_dags/CLAUDE.md's "Bronze read window column" convention for
        why.
        """
        start_dt = _parse_window_bound(time_window["start_time"])
        end_dt = _parse_window_bound(time_window["end_time"])
        chunk_size = int(Variable.get(CHUNK_SIZE_VARIABLE, default_var=DEFAULT_CHUNK_SIZE))

        client = get_clickhouse_client()

        total = _count_bronze_records(client, start_dt, end_dt)
        log.info(
            "attendance_staff bronze records ingested in [%s, %s): %d (chunk_size=%d)",
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
                "attendance_staff chunk %d: %d staff rows (cumulative %d/%d)",
                chunk_num, len(chunk), rows_seen, total,
            )

            staff_ids = [row["id"] for row in chunk]
            joined_rows = _fetch_enriched_staff_rows(client, staff_ids)
            log.info(
                "attendance_staff chunk %d: %d rows after LEFT JOIN with individual/register",
                chunk_num, len(joined_rows),
            )

            staff_lookup_keys = _extract_staff_lookup_keys(joined_rows)
            unique_staff_count = sum(len(user_ids) for user_ids in staff_lookup_keys.values())
            user_project_context = _resolve_user_project_context(client, staff_lookup_keys)
            _attach_project_context(joined_rows, user_project_context)
            log.info(
                "attendance_staff chunk %d: resolved project-staff bridge for %d/%d unique user(s)",
                chunk_num, len(user_project_context), unique_staff_count,
            )

            lookup_keys = extract_boundary_lookup_keys(joined_rows, _get_boundary_lookup_key)
            resolved_levels = resolve_boundary_levels(lookup_keys)
            attach_boundary_levels(joined_rows, resolved_levels, _get_boundary_lookup_key)
            log.info(
                "attendance_staff chunk %d: attached boundary hierarchy levels to %d rows",
                chunk_num, len(joined_rows),
            )

            user_lookup_keys = extract_user_lookup_keys(joined_rows, _get_user_lookup_key)
            resolved_user_info = resolve_user_info(user_lookup_keys)
            for row in joined_rows:
                info = resolved_user_info.get(_get_user_lookup_key(row)) or {}
                row["user_name"] = info.get("USERNAME") or ""
                row["name_of_user"] = info.get("NAME") or ""
                row["role"] = info.get("ROLE") or ""
            log.info(
                "attendance_staff chunk %d: attached user info to %d rows (%d unique user(s))",
                chunk_num, len(joined_rows), len(user_lookup_keys),
            )

            silver_rows = _build_silver_rows(joined_rows)
            _write_silver_chunk(client, silver_rows)
            log.info(
                "attendance_staff chunk %d: wrote %d/%d rows to %s",
                chunk_num, len(silver_rows), len(joined_rows), SILVER_TABLE,
            )

    transform_bronze_to_silver(parse_time_window())


attendance_staff_transformation()
