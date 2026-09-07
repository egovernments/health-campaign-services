"""
device_token_transformation.py

Bronze -> silver transformation DAG for the `device_token` entity
(DeviceToken -> DeviceTokenIndexV1 in the Java reference). Triggered
exclusively by bronze_to_silver_orchestrator with
conf={"start_time": ..., "end_time": ...}; not scheduled on its own.

Java resolves project/campaign context and the boundary source off
deviceToken.getUserId() via a userId -> ProjectStaff -> Project bridge (the
same bridge pattern pgr_transformation.py builds off last_modified_by), then
only fetches the boundary hierarchy if that bridge found a project -- both
lookups here key off device_token's own user_id (unlike PGR, there's no
separate "display user" vs "staff bridge user" split on this entity).
campaign_id stays "" with the same TODO every other entity carries -- no
project-factory/campaign-search integration exists in this repo yet.
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

DAG_ID = "device_token_transformation"

BRONZE_TABLE = "analytics.stg_device_tokens"
PROJECT_STAFF_TABLE = "analytics.stg_project_staff"
PROJECT_TABLE = "analytics.stg_project"
PROJECT_ADDRESS_TABLE = "analytics.stg_project_address"
CHUNK_SIZE_VARIABLE = "bronze_to_silver_chunk_size"
DEFAULT_CHUNK_SIZE = 5000

SILVER_TABLE = "device_token_entity"

_EPOCH_DATE = pendulum.Date(1970, 1, 1)

SILVER_COLUMNS = [
    "id", "user_id", "device_type", "tenant_id", "created_by", "last_modified_by",
    "created_time", "last_modified_time", "facility_id", "user_roles", "user_name", "role",
    "level_one_code", "level_two_code", "level_three_code", "level_four_code", "level_five_code",
    "level_six_code", "level_seven_code", "level_eight_code", "level_nine_code", "hierarchy_type",
    "task_dates", "synced_date",
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


def _fetch_device_token_rows(client, device_token_ids: list[str]) -> list[dict]:
    """Bronze is a single flat table for this entity -- no per-row join
    needed at fetch time (unlike pgr's address sub-table)."""
    result = client.query(
        f"SELECT * FROM {BRONZE_TABLE} WHERE id IN %(device_token_ids)s",
        parameters={"device_token_ids": device_token_ids},
    )
    return list(result.named_results())


def _extract_staff_lookup_keys(rows: list[dict]) -> dict[str, set[str]]:
    """Keyed on device_token's own user_id -- Java calls
    projectService.searchProjectStaff([deviceToken.getUserId()]) directly,
    with no separate created_by/last_modified_by split like PGR has."""
    lookup_keys: dict[str, set[str]] = {}
    for row in rows:
        user_id = row.get("user_id")
        if user_id:
            lookup_keys.setdefault(row["tenant_id"], set()).add(user_id)
    return lookup_keys


def _resolve_user_project_boundary(client, lookup_keys: dict[str, set[str]]) -> dict[tuple[str, str], dict]:
    """
    One query per tenant (zero if empty): LEFT JOINs stg_project_staff ->
    stg_project -> stg_project_address and uses ClickHouse's `LIMIT 1 BY` to
    pick each user's lowest-id non-deleted stg_project_staff row -- the same
    entity-agnostic bridge pgr_transformation.py builds off last_modified_by
    (mirrors ProjectStaffRepository's real ORDER BY id ASC), extended with
    the project_address join in the same round trip so the resolved
    project's own boundary source (address_boundary + additional_details for
    hierarchy_type) comes back alongside project/campaign context -- a
    single 3-hop bridge stage rather than folding the join into the main
    per-chunk device_token fetch.
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
                paddr.boundary        AS address_boundary
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
                "address_boundary": r["address_boundary"],
            }
    return resolved


def _attach_project_boundary_context(rows: list[dict], user_project_boundary: dict) -> None:
    """Rows with no ProjectStaff/Project match get empty project/campaign
    context and no boundary source -- the direct equivalent of Java's
    "no ProjectStaff found -> empty ProjectInfo, no boundary fetch" branch;
    _get_boundary_lookup_key below short-circuits to None in that case."""
    for row in rows:
        row["project_id"] = ""
        row["project_type"] = ""
        row["project_type_id"] = ""
        row["project_name"] = ""
        row["campaign_number"] = ""
        row["project_additional_details"] = None
        row["address_boundary"] = None
        details = user_project_boundary.get((row["tenant_id"], row.get("user_id")))
        if details:
            row["project_id"] = details["project_id"]
            row["project_type"] = details["project_type"]
            row["project_type_id"] = details["project_type_id"]
            row["project_name"] = details["project_name"]
            row["campaign_number"] = details["campaign_number"]
            row["project_additional_details"] = details["project_additional_details"]
            row["address_boundary"] = details["address_boundary"]


def _get_boundary_lookup_key(row: dict) -> tuple[str, str, str] | None:
    """Mirrors project_transformation.py's own boundary key: hierarchy_type
    comes from the resolved project's additional_details, boundary code from
    that project's own address -- both only present if the staff bridge
    found a project for this user."""
    hierarchy_type = parse_hierarchy_type(row.get("project_additional_details"))
    if not hierarchy_type:
        return None
    code = row.get("address_boundary")
    if not code:
        return None
    return row["tenant_id"], hierarchy_type, code


def _get_user_lookup_key(row: dict) -> tuple[str, str] | None:
    """Same user_id as the staff bridge -- Java's userService.getUserInfo
    also keys off deviceToken.getUserId()."""
    user_id = row.get("user_id")
    if not user_id:
        return None
    return row["tenant_id"], user_id


def _default_str(value) -> str:
    return value if value is not None else ""


def _default_int(value) -> int:
    return 0 if value is None else int(round(value))


def _default_date(epoch_ms):
    return pendulum.from_timestamp(epoch_ms / 1000, tz="UTC").date() if epoch_ms else _EPOCH_DATE


def _build_silver_row(row: dict) -> dict:
    """
    Maps one fully-enriched row onto device_token_entity's exact column set.
    task_dates and synced_date both derive from last_modified_time -- Java
    computes both from the same auditDetails.getLastModifiedTime(), and
    DeviceToken has no separate client-audit trail. user_roles stays a
    single comma-separated string on both sides, matching Java's
    DeviceToken.userRoles (no explosion into rows/array).
    """
    return {
        "id": row["id"],
        "user_id": _default_str(row.get("user_id")),
        "device_type": _default_str(row.get("device_type")),
        "tenant_id": _default_str(row.get("tenant_id")),
        "created_by": _default_str(row.get("created_by")),
        "last_modified_by": _default_str(row.get("last_modified_by")),
        "created_time": _default_int(row.get("created_time")),
        "last_modified_time": _default_int(row.get("last_modified_time")),
        "facility_id": _default_str(row.get("facility_id")),
        "user_roles": _default_str(row.get("user_roles")),
        "user_name": _default_str(row.get("user_name")),
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
        "task_dates": _default_date(row.get("last_modified_time")),
        "synced_date": _default_date(row.get("last_modified_time")),
        "project_id": _default_str(row.get("project_id")),
        "project_type": _default_str(row.get("project_type")),
        "project_type_id": _default_str(row.get("project_type_id")),
        "project_name": _default_str(row.get("project_name")),
        "campaign_number": _default_str(row.get("campaign_number")),
        "campaign_id": "",  # TODO: needs project-factory service integration (not yet built)
    }


def _build_silver_rows(rows: list[dict]) -> list[dict]:
    """Builds each row independently; a malformed row is logged and
    skipped rather than failing the whole chunk's write."""
    silver_rows = []
    for row in rows:
        try:
            silver_rows.append(_build_silver_row(row))
        except Exception:
            log.exception(
                "device_token: failed to build silver row for device_token id=%s; skipping this row",
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
    description="Transforms device_token bronze events into the device_token_entity silver table.",
    schedule=None,
    start_date=pendulum.datetime(2024, 1, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    tags=["bronze-to-silver", "device_token"],
)
def device_token_transformation():

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
        Reads device_token bronze rows for this run's window in fixed-size
        chunks via keyset pagination, transforms, and writes each chunk to
        device_token_entity before moving to the next chunk.

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
            "device_token bronze records ingested in [%s, %s): %d (chunk_size=%d)",
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
                "device_token chunk %d: %d device_token rows (cumulative %d/%d)",
                chunk_num, len(chunk), rows_seen, total,
            )

            device_token_ids = [row["id"] for row in chunk]
            rows = _fetch_device_token_rows(client, device_token_ids)

            staff_lookup_keys = _extract_staff_lookup_keys(rows)
            unique_staff_count = sum(len(user_ids) for user_ids in staff_lookup_keys.values())
            user_project_boundary = _resolve_user_project_boundary(client, staff_lookup_keys)
            _attach_project_boundary_context(rows, user_project_boundary)
            log.info(
                "device_token chunk %d: resolved project-staff bridge for %d/%d unique user(s)",
                chunk_num, len(user_project_boundary), unique_staff_count,
            )

            lookup_keys = extract_boundary_lookup_keys(rows, _get_boundary_lookup_key)
            resolved_levels = resolve_boundary_levels(lookup_keys)
            attach_boundary_levels(rows, resolved_levels, _get_boundary_lookup_key)
            log.info(
                "device_token chunk %d: attached boundary hierarchy levels to %d rows",
                chunk_num, len(rows),
            )

            user_lookup_keys = extract_user_lookup_keys(rows, _get_user_lookup_key)
            resolved_user_info = resolve_user_info(user_lookup_keys)
            for row in rows:
                info = resolved_user_info.get(_get_user_lookup_key(row)) or {}
                row["user_name"] = info.get("USERNAME") or ""
                row["role"] = info.get("ROLE") or ""
            log.info(
                "device_token chunk %d: attached user info to %d rows (%d unique user(s))",
                chunk_num, len(rows), len(user_lookup_keys),
            )

            silver_rows = _build_silver_rows(rows)
            _write_silver_chunk(client, silver_rows)
            log.info(
                "device_token chunk %d: wrote %d/%d rows to %s",
                chunk_num, len(silver_rows), len(rows), SILVER_TABLE,
            )

    transform_bronze_to_silver(parse_time_window())


device_token_transformation()
