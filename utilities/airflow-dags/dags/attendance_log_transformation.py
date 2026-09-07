"""
attendance_log_transformation.py

Bronze -> silver transformation DAG for the `attendance_log` entity
(AttendanceLog -> AttendanceLogIndexV1 in the Java reference). Triggered
exclusively by bronze_to_silver_orchestrator with
conf={"start_time": ..., "end_time": ...}; not scheduled on its own.

Unlike AttendeeTransformationService, AttendanceTransformationService has no
register-rollup side effect at all -- it only reads the register for display
fields (name/service_code/register_number), so there's no duplicate-logic
scope note needed here.
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
    parse_boundary_code,
    parse_hierarchy_type,
    resolve_boundary_levels,
    resolve_user_info,
)

log = logging.getLogger(__name__)

DAG_ID = "attendance_log_transformation"

BRONZE_TABLE = "analytics.stg_attendance_log"
INDIVIDUAL_TABLE = "analytics.stg_individual"
REGISTER_TABLE = "analytics.stg_attendance_register"
PROJECT_STAFF_TABLE = "analytics.stg_project_staff"
PROJECT_TABLE = "analytics.stg_project"
PROJECT_ADDRESS_TABLE = "analytics.stg_project_address"
CHUNK_SIZE_VARIABLE = "bronze_to_silver_chunk_size"
DEFAULT_CHUNK_SIZE = 5000

SILVER_TABLE = "attendance_log_entity"

SILVER_COLUMNS = [
    "id", "tenant_id", "register_id", "individual_id", "log_user_name", "time", "type", "status",
    "document_ids", "log_additional_details", "created_by", "last_modified_by", "created_time",
    "last_modified_time", "attendance_taker_user_name", "attendance_taker_name_of_user",
    "user_name", "name_of_user", "role", "attendance_time",
    "register_service_code", "register_name", "register_number",
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


def _fetch_enriched_log_rows(client, log_ids: list[str]) -> list[dict]:
    """
    resolved_attendee_user_uuid mirrors fetchAttendeesInfo's own
    individual-id -> user_uuid hop for the log's attendee; the
    attendance-taker's id (created_by) is already a user id, no hop needed
    for it. lg's own columns are individually aliased rather than `lg.*` --
    with 2+ joined tables, ClickHouse silently qualifies any lg column
    whose bare name collides with a column in ind/reg as `lg.<col>` in the
    result set, breaking downstream lookups expecting bare names. FINAL is
    used on both joined tables to avoid row versions from un-merged
    ReplacingMergeTree duplicates; no fan-out guard needed beyond that --
    both joins by primary key.
    """
    result = client.query(
        f"""
        SELECT
            lg.id                        AS id,
            lg.individual_id             AS individual_id,
            lg.register_id               AS register_id,
            lg.status                    AS status,
            lg.time                      AS time,
            lg.event_type                AS event_type,
            lg.additional_details        AS additional_details,
            lg.created_by                AS created_by,
            lg.last_modified_by          AS last_modified_by,
            lg.created_time              AS created_time,
            lg.last_modified_time        AS last_modified_time,
            lg.tenant_id                 AS tenant_id,
            lg.client_reference_id       AS client_reference_id,
            lg.client_created_by         AS client_created_by,
            lg.client_last_modified_by   AS client_last_modified_by,
            lg.client_created_time       AS client_created_time,
            lg.client_last_modified_time AS client_last_modified_time,
            ind.user_uuid       AS resolved_attendee_user_uuid,
            reg.name            AS register_name_raw,
            reg.service_code    AS register_service_code_raw,
            reg.register_number AS register_number_raw
        FROM {BRONZE_TABLE} AS lg
        LEFT JOIN {INDIVIDUAL_TABLE} AS ind FINAL
            ON ind.id = lg.individual_id AND ind.tenant_id = lg.tenant_id AND ind.is_deleted = false
        LEFT JOIN {REGISTER_TABLE} AS reg FINAL
            ON reg.id = lg.register_id AND reg.tenant_id = lg.tenant_id
        WHERE lg.id IN %(log_ids)s
        """,
        parameters={"log_ids": log_ids},
    )
    return list(result.named_results())


def _extract_bridge_lookup_keys(joined_rows: list[dict]) -> dict[str, set[str]]:
    """
    Union of created_by (boundary fallback key) and last_modified_by
    (project/campaign trailer key) per tenant -- two different purposes off
    the same single audit block, resolved in one round trip.
    """
    lookup_keys: dict[str, set[str]] = {}
    for row in joined_rows:
        for user_id in (row.get("created_by"), row.get("last_modified_by")):
            if user_id:
                lookup_keys.setdefault(row["tenant_id"], set()).add(user_id)
    return lookup_keys


def _resolve_user_project_context(client, lookup_keys: dict[str, set[str]]) -> dict[tuple[str, str], dict]:
    """
    LEFT JOIN stg_project_staff -> stg_project -> stg_project_address,
    LIMIT 1 BY staff_id -- same tie-break already established for
    household/household_member/stock/pgr/attendance_staff/attendee
    (confirmed against the live project-service source:
    GenericRepository's default ORDER BY id ASC).
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


def _attach_boundary_project_context(joined_rows: list[dict], user_project_context: dict) -> None:
    """Boundary-fallback project context, keyed on created_by."""
    for row in joined_rows:
        row["boundary_project_additional_details"] = None
        row["boundary_project_boundary_code"] = None
        details = user_project_context.get((row["tenant_id"], row.get("created_by")))
        if details:
            row["boundary_project_additional_details"] = details["project_additional_details"]
            row["boundary_project_boundary_code"] = details["project_boundary_code"]


def _attach_project_context(joined_rows: list[dict], user_project_context: dict) -> None:
    """
    Trailer columns, keyed on last_modified_by -- deliberately a DIFFERENT
    key than the boundary fallback above (same class of asymmetry already
    found and preserved in pgr_transformation.py: two different purposes
    off one audit block, matching Java exactly).
    """
    for row in joined_rows:
        row["project_id"] = ""
        row["project_type"] = ""
        row["project_type_id"] = ""
        row["project_name"] = ""
        row["campaign_number"] = ""
        details = user_project_context.get((row["tenant_id"], row.get("last_modified_by")))
        if details:
            row["project_id"] = details["project_id"]
            row["project_type"] = details["project_type"]
            row["project_type_id"] = details["project_type_id"]
            row["project_name"] = details["project_name"]
            row["campaign_number"] = details["campaign_number"]


def _get_boundary_lookup_key(row: dict) -> tuple[str, str, str] | None:
    hierarchy_type = parse_hierarchy_type(row.get("boundary_project_additional_details"))
    if not hierarchy_type:
        return None
    code = parse_boundary_code(row.get("additional_details")) or row.get("boundary_project_boundary_code")
    if not code:
        return None
    return row["tenant_id"], hierarchy_type, code


def _get_attendee_user_lookup_key(row: dict) -> tuple[str, str] | None:
    user_uuid = row.get("resolved_attendee_user_uuid")
    if not user_uuid:
        return None
    return row["tenant_id"], user_uuid


def _get_taker_user_lookup_key(row: dict) -> tuple[str, str] | None:
    created_by = row.get("created_by")
    if not created_by:
        return None
    return row["tenant_id"], created_by


def _format_attendance_time(epoch_ms) -> str:
    """
    Mirrors CommonUtils.java's getTimeStampFromEpoch -- 'yyyy-MM-ddTHH:mm:ss.SSSZ'
    (literal 'Z' suffix, not real offset notation, matching the Java
    SimpleDateFormat pattern exactly). Timezone assumed UTC -- Java's
    egov.timestamp.timeZone isn't configured anywhere in this repo; every
    other timestamp in this pipeline already defaults to UTC too.
    """
    if not epoch_ms:
        return ""
    return pendulum.from_timestamp(epoch_ms / 1000, tz="UTC").strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"


def _default_str(value) -> str:
    return value if value is not None else ""


def _default_int(value) -> int:
    return 0 if value is None else int(round(value))


def _build_silver_row(row: dict) -> dict:
    """
    Maps one fully-enriched joined row onto attendance_log_entity's exact
    column set. log_user_name/document_ids have no bronze source at all
    (AttendanceLog.userName and .documentIds aren't modeled in bronze) and
    default to "" (same TODO pattern as PGR's complainant_* fields). type
    is bronze's own event_type column, renamed to match AttendanceLog.java's
    field name exactly. user_name/name_of_user resolve the attendee's own
    info; attendance_taker_user_name/attendance_taker_name_of_user/role
    resolve the person who logged the attendance -- role deliberately comes
    ONLY from the taker, never the attendee, per Java.
    """
    return {
        "id": row["id"],
        "tenant_id": _default_str(row.get("tenant_id")),
        "register_id": _default_str(row.get("register_id")),
        "individual_id": _default_str(row.get("individual_id")),
        "log_user_name": "",  # TODO: AttendanceLog.userName has no bronze source
        "time": _default_int(row.get("time")),
        "type": _default_str(row.get("event_type")),
        "status": _default_str(row.get("status")),
        "document_ids": "",  # TODO: List<Document> not modeled in bronze
        "log_additional_details": _default_str(row.get("additional_details")),
        "created_by": _default_str(row.get("created_by")),
        "last_modified_by": _default_str(row.get("last_modified_by")),
        "created_time": _default_int(row.get("created_time")),
        "last_modified_time": _default_int(row.get("last_modified_time")),
        "attendance_taker_user_name": _default_str(row.get("attendance_taker_user_name")),
        "attendance_taker_name_of_user": _default_str(row.get("attendance_taker_name_of_user")),
        "user_name": _default_str(row.get("user_name")),
        "name_of_user": _default_str(row.get("name_of_user")),
        "role": _default_str(row.get("role")),
        "attendance_time": _format_attendance_time(row.get("time")),
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
                "attendance_log: failed to build silver row for log id=%s; skipping this row",
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
    description="Transforms attendance-log bronze events into the attendance_log_entity silver table.",
    schedule=None,
    start_date=pendulum.datetime(2024, 1, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    tags=["bronze-to-silver", "attendance_log"],
)
def attendance_log_transformation():

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
        Reads attendance-log bronze rows for this run's window in
        fixed-size chunks via keyset pagination, transforms, and writes
        each chunk to attendance_log_entity before moving to the next
        chunk.

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
            "attendance_log bronze records ingested in [%s, %s): %d (chunk_size=%d)",
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
                "attendance_log chunk %d: %d log rows (cumulative %d/%d)",
                chunk_num, len(chunk), rows_seen, total,
            )

            log_ids = [row["id"] for row in chunk]
            joined_rows = _fetch_enriched_log_rows(client, log_ids)
            log.info(
                "attendance_log chunk %d: %d rows after LEFT JOIN with individual/register",
                chunk_num, len(joined_rows),
            )

            bridge_lookup_keys = _extract_bridge_lookup_keys(joined_rows)
            unique_bridge_count = sum(len(user_ids) for user_ids in bridge_lookup_keys.values())
            user_project_context = _resolve_user_project_context(client, bridge_lookup_keys)
            _attach_boundary_project_context(joined_rows, user_project_context)
            _attach_project_context(joined_rows, user_project_context)
            log.info(
                "attendance_log chunk %d: resolved project-staff bridge for %d/%d unique user(s) "
                "(union of created_by and last_modified_by)",
                chunk_num, len(user_project_context), unique_bridge_count,
            )

            lookup_keys = extract_boundary_lookup_keys(joined_rows, _get_boundary_lookup_key)
            resolved_levels = resolve_boundary_levels(lookup_keys)
            attach_boundary_levels(joined_rows, resolved_levels, _get_boundary_lookup_key)
            log.info(
                "attendance_log chunk %d: attached boundary hierarchy levels to %d rows",
                chunk_num, len(joined_rows),
            )

            attendee_keys = extract_user_lookup_keys(joined_rows, _get_attendee_user_lookup_key)
            taker_keys = extract_user_lookup_keys(joined_rows, _get_taker_user_lookup_key)
            resolved_user_info = resolve_user_info(attendee_keys | taker_keys)
            for row in joined_rows:
                attendee_info = resolved_user_info.get(_get_attendee_user_lookup_key(row)) or {}
                row["user_name"] = attendee_info.get("USERNAME") or ""
                row["name_of_user"] = attendee_info.get("NAME") or ""
                taker_info = resolved_user_info.get(_get_taker_user_lookup_key(row)) or {}
                row["attendance_taker_user_name"] = taker_info.get("USERNAME") or ""
                row["attendance_taker_name_of_user"] = taker_info.get("NAME") or ""
                row["role"] = taker_info.get("ROLE") or ""
            log.info(
                "attendance_log chunk %d: attached user info to %d rows "
                "(%d unique attendee(s), %d unique taker(s))",
                chunk_num, len(joined_rows), len(attendee_keys), len(taker_keys),
            )

            silver_rows = _build_silver_rows(joined_rows)
            _write_silver_chunk(client, silver_rows)
            log.info(
                "attendance_log chunk %d: wrote %d/%d rows to %s",
                chunk_num, len(silver_rows), len(joined_rows), SILVER_TABLE,
            )

    transform_bronze_to_silver(parse_time_window())


attendance_log_transformation()
