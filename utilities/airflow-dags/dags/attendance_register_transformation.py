"""
attendance_register_transformation.py

Bronze -> silver transformation DAG for the `attendance_register` entity
(AttendanceRegister -> AttendanceRegisterIndexV1 in the Java reference,
including the attendees/staff rollup that AttendanceStaffTransformationService
triggers as a side effect). Triggered exclusively by
bronze_to_silver_orchestrator with conf={"start_time": ..., "end_time": ...};
not scheduled on its own.
"""
from __future__ import annotations

import json
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
    parse_hierarchy_type,
    resolve_boundary_levels,
    resolve_user_info,
)

log = logging.getLogger(__name__)

DAG_ID = "attendance_register_transformation"

BRONZE_TABLE = "analytics.stg_attendance_register"
ATTENDEE_TABLE = "analytics.stg_attendance_attendee"
STAFF_TABLE = "analytics.stg_attendance_staff"
INDIVIDUAL_TABLE = "analytics.stg_individual"
PROJECT_TABLE = "analytics.stg_project"
PROJECT_ADDRESS_TABLE = "analytics.stg_project_address"
CHUNK_SIZE_VARIABLE = "bronze_to_silver_chunk_size"
DEFAULT_CHUNK_SIZE = 5000

SILVER_TABLE = "attendance_register_entity"

SILVER_COLUMNS = [
    "id", "tenant_id", "register_number", "name", "reference_id", "service_code",
    "start_date", "end_date", "status", "register_additional_details",
    "created_by", "last_modified_by", "created_time", "last_modified_time",
    "attendees_info", "transformer_time_stamp", "staffs_count", "attendees_count",
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


def _fetch_enriched_register_rows(client, register_ids: list[str]) -> list[dict]:
    """
    reg.reference_id is assumed to be a project id (the only path Java's
    own model supports) -- joins straight through to stg_project and its
    address, same shape as every other project-linked entity. reg's own
    columns are individually aliased rather than `reg.*` -- with 2+ joined
    tables, ClickHouse silently qualifies any reg column whose bare name
    collides with a column in p/paddr as `reg.<col>` in the result set,
    breaking downstream lookups expecting bare names. FINAL is used on
    both joined tables to avoid row fan-out from un-merged
    ReplacingMergeTree duplicates.
    """
    result = client.query(
        f"""
        SELECT
            reg.id                  AS id,
            reg.tenant_id            AS tenant_id,
            reg.register_number      AS register_number,
            reg.name                 AS name,
            reg.start_date           AS start_date,
            reg.end_date             AS end_date,
            reg.status               AS status,
            reg.additional_details   AS additional_details,
            reg.created_by           AS created_by,
            reg.last_modified_by     AS last_modified_by,
            reg.created_time         AS created_time,
            reg.last_modified_time   AS last_modified_time,
            reg.reference_id         AS reference_id,
            reg.service_code         AS service_code,
            reg.locality_code        AS locality_code,
            reg.review_status        AS review_status,
            reg.period_statuses      AS period_statuses,
            reg.campaign_number      AS campaign_number,
            reg.is_deleted           AS is_deleted,
            p.project_type       AS project_type,
            p.project_type_id    AS project_type_id,
            p.name               AS project_name,
            p.additional_details AS project_additional_details,
            paddr.boundary        AS project_boundary_code
        FROM {BRONZE_TABLE} AS reg
        LEFT JOIN {PROJECT_TABLE} AS p FINAL
            ON p.id = reg.reference_id AND p.tenant_id = reg.tenant_id
        LEFT JOIN {PROJECT_ADDRESS_TABLE} AS paddr FINAL
            ON paddr.project_id = p.id AND paddr.tenant_id = p.tenant_id
        WHERE reg.id IN %(register_ids)s
        """,
        parameters={"register_ids": register_ids},
    )
    return list(result.named_results())


def _fetch_child_rows(client, table: str, register_ids: list[str]) -> list[dict]:
    """
    Shared shape for both stg_attendance_attendee and stg_attendance_staff
    -- each register's attendees/staff live in a separate child table keyed
    by register_id, not an embedded list (unlike Java's AttendanceRegister
    model).
    """
    result = client.query(
        f"""
        SELECT
            child.register_id AS register_id,
            child.tenant_id   AS tenant_id,
            ind.user_uuid     AS user_uuid
        FROM {table} AS child
        LEFT JOIN {INDIVIDUAL_TABLE} AS ind
            ON ind.id = child.individual_id AND ind.tenant_id = child.tenant_id AND ind.is_deleted = false
        WHERE child.register_id IN %(register_ids)s
        """,
        parameters={"register_ids": register_ids},
    )
    return list(result.named_results())


def _build_register_rollups(client, register_ids: list[str]) -> dict[str, dict]:
    """
    Per register_id: attendees_count/staffs_count (raw child-row counts,
    matching Java's attendeesIndIds.size()/staffsIndIds.size() -- counts
    every child row regardless of whether its individual/user lookup
    succeeded), and attendees_info (a {user_uuid: user_info_map} dict for
    that register's attendees only -- staffsInfo is deliberately not
    persisted, matching attendance_register_entity's own schema, which has
    no staffs_info column).
    """
    attendee_rows = _fetch_child_rows(client, ATTENDEE_TABLE, register_ids)
    staff_rows = _fetch_child_rows(client, STAFF_TABLE, register_ids)

    all_user_keys = {
        (row["tenant_id"], row["user_uuid"])
        for row in attendee_rows if row.get("user_uuid")
    }
    resolved_user_info = resolve_user_info(all_user_keys)

    rollups: dict[str, dict] = {
        register_id: {"attendees_count": 0, "staffs_count": 0, "attendees_info": {}}
        for register_id in register_ids
    }

    for row in attendee_rows:
        rollup = rollups[row["register_id"]]
        rollup["attendees_count"] += 1
        if row.get("user_uuid"):
            info = resolved_user_info.get((row["tenant_id"], row["user_uuid"]))
            if info:
                rollup["attendees_info"][row["user_uuid"]] = info

    for row in staff_rows:
        rollups[row["register_id"]]["staffs_count"] += 1

    return rollups


def _get_boundary_lookup_key(row: dict) -> tuple[str, str, str] | None:
    """
    Two-tier boundary source: bronze's own locality_code first (a direct
    boundary code the register carries beyond what Java's model has), then
    the reference_id-linked project's address -- the only path Java's own
    (locality-less) model supports.
    """
    hierarchy_type = parse_hierarchy_type(row.get("project_additional_details"))
    if not hierarchy_type:
        return None
    code = row.get("locality_code") or row.get("project_boundary_code")
    if not code:
        return None
    return row["tenant_id"], hierarchy_type, code


def _default_str(value) -> str:
    return value if value is not None else ""


def _default_int(value) -> int:
    return 0 if value is None else int(round(value))


def _build_silver_row(row: dict, rollup: dict, transformer_time_stamp) -> dict:
    """
    Maps one fully-enriched joined row plus its precomputed rollup onto
    attendance_register_entity's exact column set. register_additional_details
    is bronze's additional_details as-is -- Java builds no derived blob for
    the register either. campaign_number is read straight from bronze's own
    column (not derived via the project join), same pattern as stock's
    bronze-native campaign_number. review_status/period_statuses have no
    corresponding silver column and are not surfaced.
    """
    return {
        "id": row["id"],
        "tenant_id": _default_str(row.get("tenant_id")),
        "register_number": _default_str(row.get("register_number")),
        "name": _default_str(row.get("name")),
        "reference_id": _default_str(row.get("reference_id")),
        "service_code": _default_str(row.get("service_code")),
        "start_date": _default_int(row.get("start_date")),
        "end_date": _default_int(row.get("end_date")),
        "status": _default_str(row.get("status")),
        "register_additional_details": _default_str(row.get("additional_details")),
        "created_by": _default_str(row.get("created_by")),
        "last_modified_by": _default_str(row.get("last_modified_by")),
        "created_time": _default_int(row.get("created_time")),
        "last_modified_time": _default_int(row.get("last_modified_time")),
        "attendees_info": json.dumps(rollup["attendees_info"]),
        "transformer_time_stamp": transformer_time_stamp,
        "staffs_count": rollup["staffs_count"],
        "attendees_count": rollup["attendees_count"],
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
        "project_id": _default_str(row.get("reference_id")),
        "project_type": _default_str(row.get("project_type")),
        "project_type_id": _default_str(row.get("project_type_id")),
        "project_name": _default_str(row.get("project_name")),
        "campaign_number": _default_str(row.get("campaign_number")),
        "campaign_id": "",  # TODO: needs project-factory service integration (not yet built)
    }


def _build_silver_rows(joined_rows: list[dict], rollups: dict[str, dict], transformer_time_stamp) -> list[dict]:
    """Builds each row independently; a malformed row is logged and
    skipped rather than failing the whole chunk's write."""
    silver_rows = []
    for row in joined_rows:
        try:
            rollup = rollups.get(row["id"], {"attendees_count": 0, "staffs_count": 0, "attendees_info": {}})
            silver_rows.append(_build_silver_row(row, rollup, transformer_time_stamp))
        except Exception:
            log.exception(
                "attendance_register: failed to build silver row for register id=%s; skipping this row",
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
    description="Transforms attendance-register bronze events into the attendance_register_entity silver table.",
    schedule=None,
    start_date=pendulum.datetime(2024, 1, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    tags=["bronze-to-silver", "attendance_register"],
)
def attendance_register_transformation():

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
        Reads attendance-register bronze rows for this run's window in
        fixed-size chunks via keyset pagination, transforms, and writes each
        chunk to attendance_register_entity before moving to the next chunk.

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
            "attendance_register bronze records ingested in [%s, %s): %d (chunk_size=%d)",
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
                "attendance_register chunk %d: %d register rows (cumulative %d/%d)",
                chunk_num, len(chunk), rows_seen, total,
            )

            # Computed once per chunk (not per row) -- Java's own
            # System.currentTimeMillis() is likewise one "processed at"
            # wall-clock read per batch, not per record.
            transformer_time_stamp = pendulum.now("UTC")

            register_ids = [row["id"] for row in chunk]
            joined_rows = _fetch_enriched_register_rows(client, register_ids)
            log.info(
                "attendance_register chunk %d: %d rows after LEFT JOIN with project/address",
                chunk_num, len(joined_rows),
            )

            rollups = _build_register_rollups(client, register_ids)
            log.info(
                "attendance_register chunk %d: built attendee/staff rollups for %d registers",
                chunk_num, len(rollups),
            )

            lookup_keys = extract_boundary_lookup_keys(joined_rows, _get_boundary_lookup_key)
            resolved_levels = resolve_boundary_levels(lookup_keys)
            attach_boundary_levels(joined_rows, resolved_levels, _get_boundary_lookup_key)
            log.info(
                "attendance_register chunk %d: attached boundary hierarchy levels to %d rows",
                chunk_num, len(joined_rows),
            )

            silver_rows = _build_silver_rows(joined_rows, rollups, transformer_time_stamp)
            _write_silver_chunk(client, silver_rows)
            log.info(
                "attendance_register chunk %d: wrote %d/%d rows to %s",
                chunk_num, len(silver_rows), len(joined_rows), SILVER_TABLE,
            )

    transform_bronze_to_silver(parse_time_window())


attendance_register_transformation()
