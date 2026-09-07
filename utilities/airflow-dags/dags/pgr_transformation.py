"""
pgr_transformation.py

Bronze -> silver transformation DAG for the `pgr` (complaints) entity.
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
    resolve_boundary_levels,
    resolve_user_info,
)

log = logging.getLogger(__name__)

DAG_ID = "pgr_transformation"

BRONZE_TABLE = "analytics.stg_pgr_service"
PGR_ADDRESS_TABLE = "analytics.stg_pgr_address"
PROJECT_STAFF_TABLE = "analytics.stg_project_staff"
PROJECT_TABLE = "analytics.stg_project"
CHUNK_SIZE_VARIABLE = "bronze_to_silver_chunk_size"
DEFAULT_CHUNK_SIZE = 5000

SILVER_TABLE = "pgr_complaints_entity"

_EPOCH_DATE = pendulum.Date(1970, 1, 1)

SILVER_COLUMNS = [
    "id", "tenant_id", "service_code", "service_request_id", "description", "account_id", "rating",
    "application_status", "source", "active", "self_complaint", "service_additional_detail",
    "complainant_id", "complainant_user_name", "complainant_name", "complainant_type",
    "complainant_mobile_number", "complainant_email_id", "complainant_tenant_id", "complainant_uuid",
    "complainant_active", "complainant_roles",
    "address_id", "address_locality", "address_addition_details", "address_geo_lat", "address_geo_lon",
    "address_geo_additional_details",
    "created_by", "last_modified_by", "created_time", "last_modified_time",
    "user_name", "name_of_user", "role", "user_address",
    "level_one_code", "level_two_code", "level_three_code", "level_four_code", "level_five_code",
    "level_six_code", "level_seven_code", "level_eight_code", "level_nine_code", "hierarchy_type",
    "task_dates", "boundary_code", "additional_details",
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
    silently lose precision either. Independent of stg_pgr_service's own
    ORDER BY (tenant_id, service_request_id).
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


def _fetch_enriched_pgr_rows(client, service_ids: list[str]) -> list[dict]:
    """
    Left-joins this chunk's stg_pgr_service rows with their own
    stg_pgr_address row. stg_pgr_address has no FK pointing to it from
    stg_pgr_service -- it instead carries its own parent_id pointing back
    to the owning service (the CDC extraction pattern for this embedded
    sub-object), so the join direction is the reverse of the usual
    child-has-fk-to-parent shape.

    st's own columns are individually aliased rather than `st.*` -- despite
    the previous claim, stg_pgr_service and stg_pgr_address DO share
    column names (id, tenant_id, additional_details, created_by/time,
    last_modified_by/time), it just happens that a single join doesn't
    trigger ClickHouse's column-qualifying behavior on this instance
    today; explicit aliasing removes that landmine for the next join
    added here, same failure mode as attendee_transformation.py's
    reported crash. FINAL is used on the joined table to avoid row
    versions from un-merged ReplacingMergeTree duplicates; no fan-out
    guard needed beyond that -- one address per complaint.
    """
    result = client.query(
        f"""
        SELECT
            st.id                  AS id,
            st.tenant_id           AS tenant_id,
            st.service_code        AS service_code,
            st.service_request_id  AS service_request_id,
            st.description         AS description,
            st.account_id          AS account_id,
            st.additional_details  AS additional_details,
            st.application_status  AS application_status,
            st.rating              AS rating,
            st.source              AS source,
            st.created_by          AS created_by,
            st.created_time        AS created_time,
            st.last_modified_by    AS last_modified_by,
            st.last_modified_time  AS last_modified_time,
            st.active              AS active,
            st.self_complaint      AS self_complaint,
            st.hierarchy_type      AS hierarchy_type,
            addr.id                 AS address_id,
            addr.locality           AS address_locality_raw,
            addr.latitude           AS address_geo_lat,
            addr.longitude          AS address_geo_lon,
            addr.additional_details AS address_additional_details_raw
        FROM {BRONZE_TABLE} AS st
        LEFT JOIN {PGR_ADDRESS_TABLE} AS addr FINAL
            ON addr.parent_id = st.id AND addr.tenant_id = st.tenant_id
        WHERE st.id IN %(service_ids)s
        """,
        parameters={"service_ids": service_ids},
    )
    return list(result.named_results())


def _get_boundary_lookup_key(row: dict) -> tuple[str, str, str] | None:
    """
    hierarchy_type is a direct bronze column here (stg_pgr_service has no
    project to derive it from, unlike every other entity) -- boundary code
    is the joined address's own `locality` column (confirmed to be a real
    boundary code, not a display string). Feeds the exact same
    extract_boundary_lookup_keys/resolve_boundary_levels/attach_boundary_levels
    helpers every other entity uses, with the same level_one_code..
    level_nine_code/hierarchy_type column names on write (see
    _build_silver_row).
    """
    hierarchy_type = row.get("hierarchy_type")
    if not hierarchy_type:
        return None
    code = row.get("address_locality_raw")
    if not code:
        return None
    return row["tenant_id"], hierarchy_type, code


def _get_user_lookup_key(row: dict) -> tuple[str, str] | None:
    """Display user info is keyed on created_by; the project-staff bridge
    below uses last_modified_by -- two different purposes off PGR's single
    (non client/server-split) audit block, matching Java exactly."""
    user_id = row.get("created_by")
    if not user_id:
        return None
    return row["tenant_id"], user_id


def _extract_staff_lookup_keys(joined_rows: list[dict]) -> dict[str, set[str]]:
    lookup_keys: dict[str, set[str]] = {}
    for row in joined_rows:
        user_id = row.get("last_modified_by")
        if user_id:
            lookup_keys.setdefault(row["tenant_id"], set()).add(user_id)
    return lookup_keys


def _resolve_user_project_context(client, lookup_keys: dict[str, set[str]]) -> dict[tuple[str, str], dict]:
    """
    One query per tenant (zero if empty): LEFT JOINs stg_project_staff ->
    stg_project and uses ClickHouse's `LIMIT 1 BY` to pick each user's
    lowest-id non-deleted stg_project_staff row -- mirrors
    ProjectStaffRepository's real ORDER BY id ASC (confirmed against the
    live project-service source). Same bridge already built for
    household/household_member/stock, copied verbatim since it's
    entity-agnostic.
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
                p.reference_id        AS campaign_number
            FROM {PROJECT_STAFF_TABLE} AS ps
            LEFT JOIN {PROJECT_TABLE} AS p
                ON p.id = ps.project_id AND p.tenant_id = ps.tenant_id
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
            }
    return resolved


def _attach_project_context(joined_rows: list[dict], user_project_context: dict) -> None:
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


def _build_silver_row(row: dict) -> dict:
    """
    Maps one fully-enriched joined_rows dict onto pgr_complaints_entity's
    exact column set. level_one_code..level_nine_code/hierarchy_type are
    passed straight through from attach_boundary_levels, which already
    resolved them the same way every other entity does -- no new
    boundary logic. complainant_* has no bronze source at all (Service.user
    isn't modeled in bronze yet) and is defaulted; additional_details is
    always "" (PGRIndex has no such field in Java); service_code/
    application_status are the raw bronze codes, not MDMS-localized text.
    """
    return {
        "id": row["id"],
        "tenant_id": _default_str(row.get("tenant_id")),
        "service_code": _default_str(row.get("service_code")),
        "service_request_id": _default_str(row.get("service_request_id")),
        "description": _default_str(row.get("description")),
        "account_id": _default_str(row.get("account_id")),
        "rating": _default_int(row.get("rating")),
        "application_status": _default_str(row.get("application_status")),
        "source": _default_str(row.get("source")),
        "active": _default_bool(row.get("active")),
        "self_complaint": _default_bool(row.get("self_complaint")),
        "service_additional_detail": _default_str(row.get("additional_details")),
        # TODO: complainant_* has no bronze source yet -- Service.user (the
        # complainant sub-object) isn't modeled in any bronze table.
        "complainant_id": 0,
        "complainant_user_name": "",
        "complainant_name": "",
        "complainant_type": "",
        "complainant_mobile_number": "",
        "complainant_email_id": "",
        "complainant_tenant_id": "",
        "complainant_uuid": "",
        "complainant_active": False,
        "complainant_roles": "",
        "address_id": _default_str(row.get("address_id")),
        "address_locality": (
            json.dumps({"code": row["address_locality_raw"]}) if row.get("address_locality_raw") else ""
        ),
        "address_addition_details": _default_str(row.get("address_additional_details_raw")),
        "address_geo_lat": _default_float(row.get("address_geo_lat")),
        "address_geo_lon": _default_float(row.get("address_geo_lon")),
        "address_geo_additional_details": "",  # TODO: no bronze source (GeoLocation not modeled separately)
        "created_by": _default_str(row.get("created_by")),
        "last_modified_by": _default_str(row.get("last_modified_by")),
        "created_time": _default_int(row.get("created_time")),
        "last_modified_time": _default_int(row.get("last_modified_time")),
        "user_name": _default_str(row.get("user_name")),
        "name_of_user": _default_str(row.get("name_of_user")),
        "role": _default_str(row.get("role")),
        "user_address": _default_str(row.get("user_address")),
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
        "boundary_code": _default_str(row.get("address_locality_raw")),
        "additional_details": "",  # PGRIndex has no additionalDetails field in Java
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
                "pgr: failed to build silver row for service id=%s; skipping this row",
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
    description="Transforms PGR complaint bronze events into the pgr_complaints_entity silver table.",
    schedule=None,
    start_date=pendulum.datetime(2024, 1, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    tags=["bronze-to-silver", "pgr"],
)
def pgr_transformation():

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
        Reads PGR bronze rows for this run's window in fixed-size chunks
        via keyset pagination, transforms, and writes each chunk to
        pgr_complaints_entity before moving to the next chunk.

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
            "pgr bronze records ingested in [%s, %s): %d (chunk_size=%d)",
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
                "pgr chunk %d: %d service rows (cumulative %d/%d)",
                chunk_num, len(chunk), rows_seen, total,
            )

            service_ids = [row["id"] for row in chunk]
            joined_rows = _fetch_enriched_pgr_rows(client, service_ids)
            log.info(
                "pgr chunk %d: %d service+address rows after LEFT JOIN",
                chunk_num, len(joined_rows),
            )

            staff_lookup_keys = _extract_staff_lookup_keys(joined_rows)
            unique_staff_count = sum(len(user_ids) for user_ids in staff_lookup_keys.values())
            user_project_context = _resolve_user_project_context(client, staff_lookup_keys)
            _attach_project_context(joined_rows, user_project_context)
            log.info(
                "pgr chunk %d: resolved project-staff bridge for %d/%d unique user(s)",
                chunk_num, len(user_project_context), unique_staff_count,
            )

            lookup_keys = extract_boundary_lookup_keys(joined_rows, _get_boundary_lookup_key)
            resolved_levels = resolve_boundary_levels(lookup_keys)
            attach_boundary_levels(joined_rows, resolved_levels, _get_boundary_lookup_key)
            log.info(
                "pgr chunk %d: attached boundary hierarchy levels to %d rows",
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
                "pgr chunk %d: attached user info to %d rows (%d unique user(s))",
                chunk_num, len(joined_rows), len(user_lookup_keys),
            )

            silver_rows = _build_silver_rows(joined_rows)
            _write_silver_chunk(client, silver_rows)
            log.info(
                "pgr chunk %d: wrote %d/%d rows to %s",
                chunk_num, len(silver_rows), len(joined_rows), SILVER_TABLE,
            )

    transform_bronze_to_silver(parse_time_window())


pgr_transformation()
