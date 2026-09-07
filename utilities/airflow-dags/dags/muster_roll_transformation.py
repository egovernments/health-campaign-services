"""
muster_roll_transformation.py

Bronze -> silver transformation DAG for the `muster_roll` entity
(MusterRoll -> MusterRollIndexV1 in the Java reference). Triggered
exclusively by bronze_to_silver_orchestrator with
conf={"start_time": ..., "end_time": ...}; not scheduled on its own.

Diverges from the Java reference in one deliberate way: Java nests the
whole MusterRoll object (including individualEntries) into ONE ES
document per muster roll. Our silver table (muster_roll_entity) is
relational and denormalized instead -- one row per stg_attendance_summary
entry belonging to a muster roll, produced by a LEFT JOIN of
stg_muster_roll (driving) with stg_attendance_summary. Java's own
transform method never reads individualEntries/attendanceSummary at all --
that denormalization is new work, not a port of existing per-entry logic.
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

DAG_ID = "muster_roll_transformation"

BRONZE_TABLE = "analytics.stg_muster_roll"
CHUNK_SIZE_VARIABLE = "bronze_to_silver_chunk_size"
DEFAULT_CHUNK_SIZE = 5000

SILVER_TABLE = "muster_roll_entity"

SILVER_COLUMNS = [
    "id", "tenant_id", "muster_roll_number", "register_id", "status", "muster_roll_status",
    "start_date", "end_date", "individual_entry_id", "individual_id", "actual_total_attendance",
    "reference_id", "service_code", "billing_period_id", "additional_details", "created_by",
    "last_modified_by", "created_time", "last_modified_time", "edited", "user_name", "name_of_user",
    "role", "level_one_code", "level_two_code", "level_three_code", "level_four_code",
    "level_five_code", "level_six_code", "level_seven_code", "level_eight_code", "level_nine_code",
    "hierarchy_type", "project_id", "project_type", "project_type_id", "project_name",
    "campaign_number", "campaign_id",
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


def _fetch_enriched_muster_roll_rows(client, muster_roll_ids: list[str]) -> list[dict]:
    """
    LEFT JOIN of stg_muster_roll with stg_attendance_summary (per your
    instruction -- muster_roll is the driving/left table), fanning out one
    result row per attendance-summary entry (or one row with empty
    individual_entry_id/individual_id if a muster roll has none).

    Also resolves both project-staff bridges inline as plain LEFT JOINs,
    per your instruction: 5 distinct tables total (muster_roll,
    attendance_summary, project_staff, project, project_address) is small
    enough to stay one query -- no separate extract/resolve/attach bridge
    functions (those are for longer chains). The createdBy bridge
    (cb_staff/cb_proj/cb_addr) feeds ONLY the boundary lookup below; the
    lastModifiedBy bridge (lm_staff/lm_proj) feeds project_id/project_type/
    project_type_id/project_name/campaign_number directly, already aliased
    to their final silver column names.

    mr's own columns are individually aliased rather than `mr.*` -- with
    2+ joined tables, ClickHouse silently qualifies any mr column whose
    bare name collides with a column in asum/cb_proj/lm_proj (e.g. id,
    tenant_id, additional_details, created_by, last_modified_by,
    created_time, last_modified_time, reference_id) as `mr.<col>` in the
    result set, breaking downstream lookups expecting bare names --
    including `row["tenant_id"]`, the same failure mode as the reported
    attendee_transformation.py crash. FINAL is added on
    stg_attendance_summary/stg_project/stg_project_address to avoid
    duplicate *versions* of the same joined row without changing the
    intentional one-row-per-attendance-summary-entry fan-out; cb_staff/
    lm_staff already dedupe via their own ORDER BY/LIMIT BY subqueries and
    are left unchanged.
    """
    result = client.query(
        f"""
        SELECT
            mr.id                     AS id,
            mr.tenant_id              AS tenant_id,
            mr.musterroll_number      AS musterroll_number,
            mr.attendance_register_id AS attendance_register_id,
            mr.start_date             AS start_date,
            mr.end_date               AS end_date,
            mr.musterroll_status      AS musterroll_status,
            mr.status                 AS status,
            mr.additional_details     AS additional_details,
            mr.created_by             AS created_by,
            mr.last_modified_by       AS last_modified_by,
            mr.created_time           AS created_time,
            mr.last_modified_time     AS last_modified_time,
            mr.reference_id           AS reference_id,
            mr.service_code           AS service_code,
            mr.billing_period_id      AS billing_period_id,
            asum.id                       AS individual_entry_id,
            asum.individual_id            AS individual_id,
            asum.actual_total_attendance  AS actual_total_attendance,
            cb_proj.additional_details    AS created_by_project_additional_details,
            cb_addr.boundary              AS created_by_project_boundary_code,
            lm_proj.id                    AS project_id,
            lm_proj.project_type          AS project_type,
            lm_proj.project_type_id       AS project_type_id,
            lm_proj.name                  AS project_name,
            lm_proj.reference_id          AS campaign_number
        FROM {BRONZE_TABLE} AS mr
        LEFT JOIN analytics.stg_attendance_summary AS asum FINAL
            ON asum.muster_roll_id = mr.id
        LEFT JOIN (
            SELECT staff_id, project_id, tenant_id
            FROM analytics.stg_project_staff
            WHERE is_deleted = false
            ORDER BY id ASC
            LIMIT 1 BY staff_id
        ) AS cb_staff
            ON cb_staff.staff_id = mr.created_by AND cb_staff.tenant_id = mr.tenant_id
        LEFT JOIN analytics.stg_project AS cb_proj FINAL
            ON cb_proj.id = cb_staff.project_id AND cb_proj.tenant_id = cb_staff.tenant_id
        LEFT JOIN analytics.stg_project_address AS cb_addr FINAL
            ON cb_addr.project_id = cb_proj.id AND cb_addr.tenant_id = cb_proj.tenant_id
        LEFT JOIN (
            SELECT staff_id, project_id, tenant_id
            FROM analytics.stg_project_staff
            WHERE is_deleted = false
            ORDER BY id ASC
            LIMIT 1 BY staff_id
        ) AS lm_staff
            ON lm_staff.staff_id = mr.last_modified_by AND lm_staff.tenant_id = mr.tenant_id
        LEFT JOIN analytics.stg_project AS lm_proj FINAL
            ON lm_proj.id = lm_staff.project_id AND lm_proj.tenant_id = lm_staff.tenant_id
        WHERE mr.id IN %(muster_roll_ids)s
        """,
        parameters={"muster_roll_ids": muster_roll_ids},
    )
    return list(result.named_results())


def _get_boundary_lookup_key(row: dict) -> tuple[str, str, str] | None:
    """
    Boundary hierarchy is resolved via the CREATED-by user's project (Java's
    projectDetailsFromUserId(createdBy) -> getBoundaryHierarchyWithProjectId)
    -- deliberately NOT the lastModifiedBy bridge below, which drives the
    project_id/type/name/campaign_number trailer instead. Two independent
    lookups, confirmed with you directly.
    """
    hierarchy_type = parse_hierarchy_type(row.get("created_by_project_additional_details"))
    if not hierarchy_type:
        return None
    code = row.get("created_by_project_boundary_code")
    if not code:
        return None
    return row["tenant_id"], hierarchy_type, code


def _get_user_lookup_key(row: dict) -> tuple[str, str] | None:
    """
    userService.getUserInfo(tenantId, lastModifiedBy) -- last_modified_by is
    already a user id on stg_muster_roll directly, no individual-resolution
    hop needed (unlike attendance_staff's individual.user_uuid indirection).
    """
    user_id = row.get("last_modified_by")
    if not user_id:
        return None
    return row["tenant_id"], user_id


def _get_edit_timestamp(additional_details) -> str | None:
    """
    Mirrors Java's getEditTimestamp(): additionalDetails.editInfo.attendanceUpdatedAtEpochMs,
    if present. Java uses the value to suffix a duplicate document's id; per
    your instruction we don't duplicate rows here, only the boolean presence
    matters (see _build_silver_row's `edited` field).
    """
    try:
        parsed = json.loads(additional_details or "")
    except (TypeError, ValueError):
        return None
    if not isinstance(parsed, dict):
        return None
    edit_info = parsed.get("editInfo")
    if not isinstance(edit_info, dict):
        return None
    ts = edit_info.get("attendanceUpdatedAtEpochMs")
    return str(ts) if ts is not None else None


def _default_str(value) -> str:
    return value if value is not None else ""


def _default_int(value) -> int:
    return 0 if value is None else int(round(value))


def _default_decimal(value):
    return value if value is not None else 0


def _build_silver_row(row: dict) -> dict:
    """
    Maps one fully-enriched joined row onto muster_roll_entity's exact
    column set. individual_entry_id/individual_id/actual_total_attendance
    default to ""/""/0 for a muster roll with zero attendance-summary
    entries (LEFT JOIN miss) -- still a valid, unique row under
    ORDER BY (tenant_id, id, individual_entry_id) since only one such row
    exists per muster roll. campaign_id stays "" -- no project-factory
    service integration built anywhere in this codebase yet.
    """
    return {
        "id": row["id"],
        "tenant_id": _default_str(row.get("tenant_id")),
        "muster_roll_number": _default_str(row.get("musterroll_number")),
        "register_id": _default_str(row.get("attendance_register_id")),
        "status": _default_str(row.get("status")),
        "muster_roll_status": _default_str(row.get("musterroll_status")),
        "start_date": _default_int(row.get("start_date")),
        "end_date": _default_int(row.get("end_date")),
        "individual_entry_id": _default_str(row.get("individual_entry_id")),
        "individual_id": _default_str(row.get("individual_id")),
        "actual_total_attendance": _default_decimal(row.get("actual_total_attendance")),
        "reference_id": _default_str(row.get("reference_id")),
        "service_code": _default_str(row.get("service_code")),
        "billing_period_id": _default_str(row.get("billing_period_id")),
        "additional_details": _default_str(row.get("additional_details")),
        "created_by": _default_str(row.get("created_by")),
        "last_modified_by": _default_str(row.get("last_modified_by")),
        "created_time": _default_int(row.get("created_time")),
        "last_modified_time": _default_int(row.get("last_modified_time")),
        "edited": _get_edit_timestamp(row.get("additional_details")) is not None,
        "user_name": _default_str(row.get("user_name")),
        "name_of_user": _default_str(row.get("name_of_user")),
        "role": _default_str(row.get("role")),
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
                "muster_roll: failed to build silver row for muster roll id=%s; skipping this row",
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
    description="Transforms muster-roll bronze events into the muster_roll_entity silver table.",
    schedule=None,
    start_date=pendulum.datetime(2024, 1, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    tags=["bronze-to-silver", "muster_roll"],
)
def muster_roll_transformation():

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
        Reads muster-roll bronze rows for this run's window in fixed-size
        chunks via keyset pagination, LEFT JOINs each chunk against
        stg_attendance_summary (plus both inline project-staff bridges),
        transforms, and writes each chunk to muster_roll_entity before
        moving to the next chunk.

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
            "muster_roll bronze records ingested in [%s, %s): %d (chunk_size=%d)",
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
                "muster_roll chunk %d: %d muster roll rows (cumulative %d/%d)",
                chunk_num, len(chunk), rows_seen, total,
            )

            muster_roll_ids = [row["id"] for row in chunk]
            joined_rows = _fetch_enriched_muster_roll_rows(client, muster_roll_ids)
            log.info(
                "muster_roll chunk %d: %d rows after LEFT JOIN with attendance_summary + project bridges",
                chunk_num, len(joined_rows),
            )

            lookup_keys = extract_boundary_lookup_keys(joined_rows, _get_boundary_lookup_key)
            resolved_levels = resolve_boundary_levels(lookup_keys)
            attach_boundary_levels(joined_rows, resolved_levels, _get_boundary_lookup_key)
            log.info(
                "muster_roll chunk %d: attached boundary hierarchy levels to %d rows",
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
                "muster_roll chunk %d: attached user info to %d rows (%d unique user(s))",
                chunk_num, len(joined_rows), len(user_lookup_keys),
            )

            silver_rows = _build_silver_rows(joined_rows)
            _write_silver_chunk(client, silver_rows)
            log.info(
                "muster_roll chunk %d: wrote %d/%d rows to %s",
                chunk_num, len(silver_rows), len(joined_rows), SILVER_TABLE,
            )

    transform_bronze_to_silver(parse_time_window())


muster_roll_transformation()
