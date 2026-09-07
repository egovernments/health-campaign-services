"""
service_task_transformation.py

Bronze -> silver transformation DAG for the `service_task` entity (Service ->
ServiceIndexV1 in the Java reference). Triggered exclusively by
bronze_to_silver_orchestrator with conf={"start_time": ..., "end_time": ...};
not scheduled on its own.

Project resolution is two-tier: primary is service.account_id used directly
as the project id (inlined in the main query); fallback (only when
account_id is empty) resolves via ServiceDefinition.code's dot-encoded
"<projectName>.<checklistName>.<supervisorLevel>" scheme, searching
stg_project by name. checklist_name/supervisor_level always come from that
same code split, regardless of which project-resolution tier applies.

service_task_entity has no `attributes` column (removed from the silver
DDL since this DAG was first planned) -- that data instead goes into the
separate service_task_attribute_entity table, ALSO populated by this same
DAG: for each chunk's service ids, the matching stg_service_attribute_value
rows (joined on stg_service.id = stg_service_attribute_value.reference_id)
are written through almost unchanged, except `value`, which bronze stores
wrapped as {"value": <actual value>} and this DAG unwraps into a plain
string regardless of the inner type.
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
    extract_user_lookup_keys,
    fetch_cycle_index,
    get_project_cycles,
    parse_boundary_code,
    parse_hierarchy_type,
    resolve_boundary_levels,
    resolve_user_info,
)

log = logging.getLogger(__name__)

DAG_ID = "service_task_transformation"

BRONZE_TABLE = "analytics.stg_service"
PROJECT_TABLE = "analytics.stg_project"
SERVICE_DEFINITION_TABLE = "analytics.stg_service_definition"
SERVICE_ATTRIBUTE_VALUE_TABLE = "analytics.stg_service_attribute_value"
CHUNK_SIZE_VARIABLE = "bronze_to_silver_chunk_size"
DEFAULT_CHUNK_SIZE = 5000

SILVER_TABLE = "service_task_entity"
ATTRIBUTE_SILVER_TABLE = "service_task_attribute_entity"

_EPOCH_DATE = pendulum.Date(1970, 1, 1)
_EPOCH_DATETIME = pendulum.datetime(1970, 1, 1, tz="UTC")

SILVER_COLUMNS = [
    "id", "created_time", "created_by", "supervisor_level", "checklist_name", "service_definition_id",
    "user_name", "name_of_user", "role", "user_address",
    "level_one_code", "level_two_code", "level_three_code", "level_four_code", "level_five_code",
    "level_six_code", "level_seven_code", "level_eight_code", "level_nine_code", "hierarchy_type",
    "tenant_id", "user_id", "client_reference_id", "synced_time_stamp", "synced_time",
    "task_dates", "additional_details", "latitude", "longitude",
    "project_id", "project_type", "project_type_id", "project_name", "campaign_number", "campaign_id",
]

ATTRIBUTE_SILVER_COLUMNS = [
    "id", "reference_id", "attribute_code", "value", "created_by", "last_modified_by",
    "created_time", "last_modified_time", "additional_details", "client_reference_id",
    "service_client_reference_id",
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


def _fetch_enriched_service_rows(client, service_ids: list[str]) -> list[dict]:
    """
    Primary-tier project resolution only (account_id direct, a simple
    1-hop FK, inlined per this session's own "1-2 hop direct FK -> fine to
    inline" guidance). Rows where s.account_id is empty simply get no
    match here -- the fallback bridge fills them in. s's own columns are
    individually aliased rather than `s.*` -- a single join happens not to
    trigger ClickHouse's column-qualifying behavior on this instance
    today, but would silently break (any s column colliding with p's,
    e.g. id, tenant_id, additional_details, created_by, last_modified_by,
    created_time, last_modified_time) the moment a second join is added
    here, same failure mode as attendee_transformation.py's reported
    crash -- explicit aliasing removes that landmine proactively. FINAL is
    used on the joined table to avoid row versions from un-merged
    ReplacingMergeTree duplicates.
    """
    result = client.query(
        f"""
        SELECT
            s.id                  AS id,
            s.tenant_id           AS tenant_id,
            s.service_def_id      AS service_def_id,
            s.reference_id        AS reference_id,
            s.account_id          AS account_id,
            s.client_id           AS client_id,
            s.additional_details  AS additional_details,
            s.created_by          AS created_by,
            s.last_modified_by    AS last_modified_by,
            s.created_time        AS created_time,
            s.last_modified_time  AS last_modified_time,
            p.project_type        AS project_type,
            p.project_type_id     AS project_type_id,
            p.name                AS project_name,
            p.reference_id        AS campaign_number,
            p.additional_details  AS project_additional_details
        FROM {BRONZE_TABLE} AS s
        LEFT JOIN {PROJECT_TABLE} AS p FINAL
            ON p.id = s.account_id AND p.tenant_id = s.tenant_id
        WHERE s.id IN %(service_ids)s
        """,
        parameters={"service_ids": service_ids},
    )
    return list(result.named_results())


def _extract_service_definition_lookup_keys(rows: list[dict]) -> dict[str, set[str]]:
    lookup_keys: dict[str, set[str]] = {}
    for row in rows:
        key = row.get("service_def_id")
        if key:
            lookup_keys.setdefault(row["tenant_id"], set()).add(key)
    return lookup_keys


def _resolve_service_definition_context(client, lookup_keys: dict[str, set[str]]) -> dict[tuple[str, str], dict]:
    """stg_service_definition, keyed on id -- a single small table, LIMIT 1
    BY id as the standard defensive tie-break."""
    resolved: dict[tuple[str, str], dict] = {}
    for tenant_id, keys in lookup_keys.items():
        result = client.query(
            f"""
            SELECT id, code
            FROM {SERVICE_DEFINITION_TABLE}
            WHERE tenant_id = %(tenant_id)s AND id IN %(keys)s
            ORDER BY id ASC
            LIMIT 1 BY id
            """,
            parameters={"tenant_id": tenant_id, "keys": list(keys)},
        )
        for r in result.named_results():
            resolved[(tenant_id, r["id"])] = {"service_definition_code": r["code"]}
    return resolved


def _attach_service_definition_context(rows: list[dict], resolved: dict) -> None:
    for row in rows:
        row["service_definition_code"] = None
        details = resolved.get((row["tenant_id"], row.get("service_def_id")))
        if details:
            row.update(details)


def _parse_service_definition_code(code):
    """
    Mirrors serviceDefinition.getCode().split("\\."): "<projectName>.
    <checklistName>.<supervisorLevel>". A malformed/short code (fewer than
    3 parts) degrades gracefully to (None, None, None), not an IndexError.
    """
    if not code:
        return None, None, None
    parts = code.split(".")
    if len(parts) < 3:
        return None, None, None
    return parts[0], parts[1], parts[2]


def _extract_project_by_name_lookup_keys(rows: list[dict]) -> dict[str, set[str]]:
    """Only rows where the primary (account_id) tier found nothing AND a
    project name was parsed from the service definition's code."""
    lookup_keys: dict[str, set[str]] = {}
    for row in rows:
        if row.get("account_id"):
            continue
        project_name, _, _ = _parse_service_definition_code(row.get("service_definition_code"))
        if project_name:
            lookup_keys.setdefault(row["tenant_id"], set()).add(project_name)
    return lookup_keys


def _resolve_project_by_name_context(client, lookup_keys: dict[str, set[str]]) -> dict[tuple[str, str], dict]:
    """stg_project, keyed on name (Project.getProjectByName's own search
    key) -- LIMIT 1 BY name, same defensive tie-break as every bridge."""
    resolved: dict[tuple[str, str], dict] = {}
    for tenant_id, names in lookup_keys.items():
        result = client.query(
            f"""
            SELECT name, id, project_type, project_type_id, name AS project_name,
                   reference_id AS campaign_number, additional_details AS project_additional_details
            FROM {PROJECT_TABLE}
            WHERE tenant_id = %(tenant_id)s AND name IN %(names)s
            ORDER BY id ASC
            LIMIT 1 BY name
            """,
            parameters={"tenant_id": tenant_id, "names": list(names)},
        )
        for r in result.named_results():
            resolved[(tenant_id, r["name"])] = {
                "resolved_project_id": r["id"],
                "project_type": r["project_type"] or "",
                "project_type_id": r["project_type_id"] or "",
                "project_name": r["project_name"] or "",
                "campaign_number": r["campaign_number"] or "",
                "project_additional_details": r["project_additional_details"],
            }
    return resolved


def _attach_project_by_name_context(rows: list[dict], resolved: dict) -> None:
    """Only applies to rows with no primary-tier (account_id) match --
    project_id in the final row builder is account_id or resolved_project_id."""
    for row in rows:
        row["resolved_project_id"] = None
        if row.get("account_id"):
            continue
        project_name, _, _ = _parse_service_definition_code(row.get("service_definition_code"))
        details = resolved.get((row["tenant_id"], project_name))
        if details:
            row.update(details)


def _get_geo_point(row: dict) -> tuple[float, float] | None:
    """Mirrors CommonUtils.getGeoPointFromAdditionalFields's additionalDetails-
    only path (additionalFields is always absent from bronze -- see module
    docstring): a flat object with lat/lng keys -> (longitude, latitude),
    else None."""
    try:
        parsed = json.loads(row.get("additional_details") or "")
    except (TypeError, ValueError):
        return None
    if not isinstance(parsed, dict) or "lat" not in parsed or "lng" not in parsed:
        return None
    try:
        return float(parsed["lng"]), float(parsed["lat"])
    except (TypeError, ValueError):
        return None


def _get_boundary_lookup_key(row: dict) -> tuple[str, str, str] | None:
    """Locality code via parse_boundary_code (same shape, reused directly),
    hierarchy_type from the resolved project's own additional_details."""
    hierarchy_type = parse_hierarchy_type(row.get("project_additional_details"))
    if not hierarchy_type:
        return None
    code = parse_boundary_code(row.get("additional_details")) or row.get("project_boundary_code")
    if not code:
        return None
    return row["tenant_id"], hierarchy_type, code


def _get_user_lookup_key(row: dict) -> tuple[str, str] | None:
    user_id = row.get("created_by")
    if not user_id:
        return None
    return row["tenant_id"], user_id


def _format_cycle_index(row: dict) -> str | None:
    """Same "%02d" formatting/server-audit-createdTime source as
    referral_transformation.py's own helper."""
    cycles = get_project_cycles(row.get("project_additional_details"))
    if not cycles:
        return None
    matched_id = fetch_cycle_index(cycles, row.get("created_time"))
    return f"{matched_id:02d}" if matched_id is not None else None


def _default_str(value) -> str:
    return value if value is not None else ""


def _default_int(value) -> int:
    return 0 if value is None else int(round(value))


def _default_date(epoch_ms):
    return pendulum.from_timestamp(epoch_ms / 1000, tz="UTC").date() if epoch_ms else _EPOCH_DATE


def _default_datetime(epoch_ms):
    return pendulum.from_timestamp(epoch_ms / 1000, tz="UTC") if epoch_ms else _EPOCH_DATETIME


def _build_derived_additional_details(row: dict) -> dict:
    """
    Mirrors Java's in-place mutation: start from service's own
    additionalDetails object (if it IS an object), then add cycleIndex on
    top -- service_task_entity has only ONE additional_details column, no
    raw/derived split like most other entities.
    """
    try:
        parsed = json.loads(row.get("additional_details") or "")
    except (TypeError, ValueError):
        parsed = None
    details = dict(parsed) if isinstance(parsed, dict) else {}
    details["cycleIndex"] = _format_cycle_index(row)
    return details


def _build_silver_row(row: dict) -> dict:
    _, checklist_name, supervisor_level = _parse_service_definition_code(row.get("service_definition_code"))
    geo_point = _get_geo_point(row)
    project_id = row.get("account_id") or row.get("resolved_project_id") or ""

    return {
        "id": row["id"],
        "created_time": _default_int(row.get("created_time")),
        "created_by": _default_str(row.get("created_by")),
        "supervisor_level": _default_str(supervisor_level),
        "checklist_name": _default_str(checklist_name),
        "service_definition_id": _default_str(row.get("service_def_id")),
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
        "tenant_id": _default_str(row.get("tenant_id")),
        "user_id": _default_str(row.get("account_id")),
        "client_reference_id": _default_str(row.get("client_id")),
        "synced_time_stamp": _default_datetime(row.get("created_time")),
        "synced_time": _default_int(row.get("last_modified_time")),
        "task_dates": _default_date(row.get("last_modified_time")),
        "additional_details": json.dumps(_build_derived_additional_details(row)),
        "latitude": geo_point[1] if geo_point else 0.0,
        "longitude": geo_point[0] if geo_point else 0.0,
        "project_id": _default_str(project_id),
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
                "service_task: failed to build silver row for service id=%s; skipping this row",
                row.get("id"),
            )
    return silver_rows


def _write_silver_chunk(client, silver_rows: list[dict]) -> None:
    if not silver_rows:
        return
    data = [[row[column] for column in SILVER_COLUMNS] for row in silver_rows]
    client.insert(SILVER_TABLE, data, column_names=SILVER_COLUMNS)


def _fetch_service_attribute_rows(client, service_ids: list[str]) -> list[dict]:
    """stg_service_attribute_value rows for this chunk's services, on
    stg_service.id = stg_service_attribute_value.reference_id -- a plain
    filter, not a SQL JOIN, since this table has no other columns to
    enrich from the service side."""
    result = client.query(
        f"SELECT * FROM {SERVICE_ATTRIBUTE_VALUE_TABLE} WHERE reference_id IN %(service_ids)s",
        parameters={"service_ids": service_ids},
    )
    return list(result.named_results())


def _extract_attribute_value(raw_value) -> str:
    """
    Bronze's `value` column stores a wrapper object, {"value": "Apple"}
    (or a number/nested object under that same "value" key) -- not the
    bare value itself. Unwraps and stringifies whatever's inside: a string
    stays as-is; anything else (number, bool, nested object/array) is
    json.dumps'd into its string form. Falls back to the raw text if it's
    not valid JSON or doesn't have this shape.
    """
    if not raw_value:
        return ""
    try:
        parsed = json.loads(raw_value)
    except (TypeError, ValueError):
        return raw_value if isinstance(raw_value, str) else ""
    inner = parsed.get("value") if isinstance(parsed, dict) and "value" in parsed else parsed
    return inner if isinstance(inner, str) else json.dumps(inner)


def _build_attribute_silver_row(row: dict) -> dict:
    """A straight passthrough -- service_task_attribute_entity mirrors
    stg_service_attribute_value's columns exactly, no enrichment needed --
    except `value`, which is unwrapped via _extract_attribute_value."""
    return {
        "id": row["id"],
        "reference_id": _default_str(row.get("reference_id")),
        "attribute_code": _default_str(row.get("attribute_code")),
        "value": _extract_attribute_value(row.get("value")),
        "created_by": _default_str(row.get("created_by")),
        "last_modified_by": _default_str(row.get("last_modified_by")),
        "created_time": _default_int(row.get("created_time")),
        "last_modified_time": _default_int(row.get("last_modified_time")),
        "additional_details": _default_str(row.get("additional_details")),
        "client_reference_id": _default_str(row.get("client_reference_id")),
        "service_client_reference_id": _default_str(row.get("service_client_reference_id")),
    }


def _build_attribute_silver_rows(rows: list[dict]) -> list[dict]:
    """Same per-row try/except skip-and-log resilience as every other
    silver row builder in this file."""
    silver_rows = []
    for row in rows:
        try:
            silver_rows.append(_build_attribute_silver_row(row))
        except Exception:
            log.exception(
                "service_task: failed to build attribute silver row for id=%s; skipping this row",
                row.get("id"),
            )
    return silver_rows


def _write_attribute_silver_chunk(client, silver_rows: list[dict]) -> None:
    if not silver_rows:
        return
    data = [[row[column] for column in ATTRIBUTE_SILVER_COLUMNS] for row in silver_rows]
    client.insert(ATTRIBUTE_SILVER_TABLE, data, column_names=ATTRIBUTE_SILVER_COLUMNS)


@dag(
    dag_id=DAG_ID,
    description="Transforms service_task bronze events into the service_task_entity silver table.",
    schedule=None,
    start_date=pendulum.datetime(2024, 1, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    tags=["bronze-to-silver", "service_task"],
)
def service_task_transformation():

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
        Reads service bronze rows for this run's window in fixed-size
        chunks via keyset pagination, transforms, and writes each chunk to
        service_task_entity AND service_task_attribute_entity (the latter
        driven off the same chunk's service ids, not its own independent
        _ingested_at scan) before moving to the next chunk.

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
            "service_task bronze records ingested in [%s, %s): %d (chunk_size=%d)",
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
                "service_task chunk %d: %d service rows (cumulative %d/%d)",
                chunk_num, len(chunk), rows_seen, total,
            )

            service_ids = [row["id"] for row in chunk]
            joined_rows = _fetch_enriched_service_rows(client, service_ids)
            log.info(
                "service_task chunk %d: %d rows after LEFT JOIN with project",
                chunk_num, len(joined_rows),
            )

            attribute_rows = _fetch_service_attribute_rows(client, service_ids)
            attribute_silver_rows = _build_attribute_silver_rows(attribute_rows)
            _write_attribute_silver_chunk(client, attribute_silver_rows)
            log.info(
                "service_task chunk %d: wrote %d/%d attribute rows to %s",
                chunk_num, len(attribute_silver_rows), len(attribute_rows), ATTRIBUTE_SILVER_TABLE,
            )

            sd_lookup_keys = _extract_service_definition_lookup_keys(joined_rows)
            unique_sd_count = sum(len(keys) for keys in sd_lookup_keys.values())
            sd_context = _resolve_service_definition_context(client, sd_lookup_keys)
            _attach_service_definition_context(joined_rows, sd_context)
            log.info(
                "service_task chunk %d: resolved service-definition bridge for %d/%d unique id(s)",
                chunk_num, len(sd_context), unique_sd_count,
            )

            pn_lookup_keys = _extract_project_by_name_lookup_keys(joined_rows)
            pn_context = _resolve_project_by_name_context(client, pn_lookup_keys)
            _attach_project_by_name_context(joined_rows, pn_context)
            log.info(
                "service_task chunk %d: resolved fallback project-by-name for %d row(s) needing it",
                chunk_num, sum(len(keys) for keys in pn_lookup_keys.values()),
            )

            lookup_keys = extract_boundary_lookup_keys(joined_rows, _get_boundary_lookup_key)
            resolved_levels = resolve_boundary_levels(lookup_keys)
            attach_boundary_levels(joined_rows, resolved_levels, _get_boundary_lookup_key)
            log.info(
                "service_task chunk %d: attached boundary hierarchy levels to %d rows",
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
                "service_task chunk %d: attached user info to %d rows (%d unique user(s))",
                chunk_num, len(joined_rows), len(user_lookup_keys),
            )

            silver_rows = _build_silver_rows(joined_rows)
            _write_silver_chunk(client, silver_rows)
            log.info(
                "service_task chunk %d: wrote %d/%d rows to %s",
                chunk_num, len(silver_rows), len(joined_rows), SILVER_TABLE,
            )

    transform_bronze_to_silver(parse_time_window())


service_task_transformation()
