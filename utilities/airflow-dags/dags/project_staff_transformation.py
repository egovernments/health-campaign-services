"""
project_staff_transformation.py

Bronze -> silver transformation DAG for the `project_staff` entity.
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
    build_project_additional_details,
    extract_boundary_lookup_keys,
    extract_user_lookup_keys,
    get_project_dates_list,
    parse_hierarchy_type,
    resolve_boundary_levels,
    resolve_user_info,
)

log = logging.getLogger(__name__)

DAG_ID = "project_staff_transformation"

BRONZE_TABLE = "analytics.stg_project_staff"
PROJECT_TABLE = "analytics.stg_project"
PROJECT_ADDRESS_TABLE = "analytics.stg_project_address"
CHUNK_SIZE_VARIABLE = "bronze_to_silver_chunk_size"
DEFAULT_CHUNK_SIZE = 5000

SILVER_TABLE = "project_staff_entity"

SILVER_COLUMNS = [
    "id", "tenant_id", "user_id", "user_name", "name_of_user", "user_address", "role",
    "boundary_code", "is_deleted", "created_by", "created_time", "task_dates", "additional_details",
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
    Left-joins this chunk's stg_project_staff rows with their parent
    stg_project row and that project's stg_project_address row.

    stg_project_staff and stg_project share ~8 column names verbatim (id,
    tenant_id, additional_details, created_by, created_time,
    last_modified_by, last_modified_time, start_date, end_date, row_version,
    is_deleted) -- far more overlap than any other entity's joins have had
    so far. `ps.*`/`p.*` would silently collide on all of those, so this
    query explicitly selects and aliases only the columns actually needed,
    rather than following the `pt.*`/`p.*` wildcard pattern used by the
    other two entity DAGs.

    project_staff_entity has no fan-out concern: stg_project_staff is the
    driving (1-row-per-staff) table, and both joins are single-row-per-
    project relationships (same assumption project_transformation.py's own
    project->address join already makes) -- a missing project/address row
    just leaves those columns empty, never drops or duplicates the staff
    row, so no placeholder-synthesis function or fan-out warning is needed
    here (unlike project_task/project).
    """
    result = client.query(
        f"""
        SELECT
            ps.id                 AS id,
            ps.tenant_id          AS tenant_id,
            ps.project_id         AS project_id,
            ps.staff_id           AS staff_id,
            ps.is_deleted         AS is_deleted,
            ps.created_by         AS created_by,
            ps.created_time       AS created_time,
            p.additional_details  AS project_additional_details,
            p.project_type        AS project_type,
            p.project_type_id     AS project_type_id,
            p.name                AS project_name,
            p.reference_id        AS campaign_number,
            p.start_date          AS project_start_date,
            p.end_date            AS project_end_date,
            paddr.boundary        AS address_boundary
        FROM {BRONZE_TABLE} AS ps
        LEFT JOIN {PROJECT_TABLE} AS p
            ON p.id = ps.project_id AND p.tenant_id = ps.tenant_id
        LEFT JOIN {PROJECT_ADDRESS_TABLE} AS paddr
            ON paddr.project_id = p.id AND paddr.tenant_id = p.tenant_id
        WHERE ps.id IN %(staff_ids)s
        """,
        parameters={"staff_ids": staff_ids},
    )
    return list(result.named_results())


def _get_boundary_lookup_key(row: dict) -> tuple[str, str, str] | None:
    """
    Returns (tenant_id, hierarchy_type, code) for boundary hierarchy
    resolution, derived from the linked project's own address boundary --
    project_staff has no address of its own. Mirrors
    project_transformation.py's _get_boundary_lookup_key exactly, just
    reading the joined project's additional_details/boundary instead of a
    project row's own.
    """
    hierarchy_type = parse_hierarchy_type(row.get("project_additional_details"))
    if not hierarchy_type:
        return None
    code = row.get("address_boundary")
    if not code:
        return None
    return row["tenant_id"], hierarchy_type, code


def _get_user_lookup_key(row: dict) -> tuple[str, str] | None:
    user_id = row.get("staff_id")
    if not user_id:
        return None
    return row["tenant_id"], user_id


def _default_str(value) -> str:
    return value if value is not None else ""


def _default_int(value) -> int:
    return 0 if value is None else int(round(value))


def _default_bool(value) -> bool:
    return bool(value) if value is not None else False


def _build_silver_row(row: dict) -> dict:
    """
    Maps one fully-enriched joined_rows dict onto project_staff_entity's
    exact column set. `tenant_id` is populated from bronze even though
    Java's ProjectStaffIndexV1.tenantId is never actually set anywhere in
    ProjectStaffTransformationService.transform() (a declared field left
    unpopulated -- confirmed by inspection, not a deliberate omission worth
    replicating) -- our own ORDER BY (tenant_id, campaign_number, id)
    requires a real value, and bronze already has it.

    task_dates/additional_details deliberately use the joined PROJECT's own
    start_date/end_date/additional_details, not project_staff's own bronze
    start_date/end_date columns -- matches Java exactly (ProjectStaff's own
    start/end dates are read from bronze in _fetch_enriched_staff_rows...
    actually not selected at all here, since Java's transform() never reads
    projectStaff.getStartDate()/getEndDate(), only project.getStartDate()/
    getEndDate()).
    """
    return {
        "id": row["id"],
        "tenant_id": _default_str(row.get("tenant_id")),
        "user_id": _default_str(row.get("staff_id")),
        "user_name": _default_str(row.get("user_name")),
        "name_of_user": _default_str(row.get("name_of_user")),
        "user_address": _default_str(row.get("user_address")),
        "role": _default_str(row.get("role")),
        "boundary_code": _default_str(row.get("address_boundary")),
        "is_deleted": _default_bool(row.get("is_deleted")),
        "created_by": _default_str(row.get("created_by")),
        "created_time": _default_int(row.get("created_time")),
        "task_dates": json.dumps(get_project_dates_list(row.get("project_start_date"), row.get("project_end_date"))),
        "additional_details": build_project_additional_details(row.get("project_additional_details")),
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
                "project_staff: failed to build silver row for staff id=%s; skipping this row",
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
    description="Transforms project_staff bronze events into the project_staff_entity silver table.",
    schedule=None,
    start_date=pendulum.datetime(2024, 1, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    tags=["bronze-to-silver", "project_staff"],
)
def project_staff_transformation():

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
        Reads project_staff bronze rows for this run's window in fixed-size
        chunks via keyset pagination, transforms, and writes each chunk to
        project_staff_entity before moving to the next chunk.

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
            "project_staff bronze records ingested in [%s, %s): %d (chunk_size=%d)",
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
                "project_staff chunk %d: %d staff rows (cumulative %d/%d)",
                chunk_num, len(chunk), rows_seen, total,
            )

            staff_ids = [row["id"] for row in chunk]
            joined_rows = _fetch_enriched_staff_rows(client, staff_ids)
            log.info(
                "project_staff chunk %d: %d staff+project+address rows after LEFT JOIN",
                chunk_num, len(joined_rows),
            )

            lookup_keys = extract_boundary_lookup_keys(joined_rows, _get_boundary_lookup_key)
            resolved_levels = resolve_boundary_levels(lookup_keys)
            attach_boundary_levels(joined_rows, resolved_levels, _get_boundary_lookup_key)
            log.info(
                "project_staff chunk %d: attached boundary hierarchy levels to %d rows",
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
                "project_staff chunk %d: attached user info to %d rows (%d unique user(s))",
                chunk_num, len(joined_rows), len(user_lookup_keys),
            )

            silver_rows = _build_silver_rows(joined_rows)
            _write_silver_chunk(client, silver_rows)
            log.info(
                "project_staff chunk %d: wrote %d/%d rows to %s",
                chunk_num, len(silver_rows), len(joined_rows), SILVER_TABLE,
            )

    transform_bronze_to_silver(parse_time_window())


project_staff_transformation()
