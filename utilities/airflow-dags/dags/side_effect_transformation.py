"""
side_effect_transformation.py

Bronze -> silver transformation DAG for the `side_effect` entity (SideEffect
-> SideEffectsIndexV1 in the Java reference). Triggered exclusively by
bronze_to_silver_orchestrator with conf={"start_time": ..., "end_time": ...};
not scheduled on its own.

This entity has TWO INDEPENDENT project resolutions: boundary uses the
linked TASK's own direct project (via a new task bridge below), while the
project/campaign trailer columns come from the project-staff bridge keyed
on side_effect's own CLIENT audit lastModifiedBy -- these can resolve to
different projects entirely. Both are ported faithfully; see each bridge's
own docstring.
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
    calculate_age_in_months,
    extract_boundary_lookup_keys,
    extract_user_lookup_keys,
    parse_additional_fields,
    parse_hierarchy_type,
    resolve_boundary_levels,
    resolve_user_info,
)

log = logging.getLogger(__name__)

DAG_ID = "side_effect_transformation"

BRONZE_TABLE = "analytics.stg_side_effect"
PROJECT_TASK_TABLE = "analytics.stg_project_task"
ADDRESS_TABLE = "analytics.stg_address"
PROJECT_BENEFICIARY_TABLE = "analytics.stg_project_beneficiary"
INDIVIDUAL_TABLE = "analytics.stg_individual"
PROJECT_STAFF_TABLE = "analytics.stg_project_staff"
PROJECT_TABLE = "analytics.stg_project"
PROJECT_ADDRESS_TABLE = "analytics.stg_project_address"
CHUNK_SIZE_VARIABLE = "bronze_to_silver_chunk_size"
DEFAULT_CHUNK_SIZE = 5000

SILVER_TABLE = "side_effect_entity"

_EPOCH_DATE = pendulum.Date(1970, 1, 1)

SILVER_COLUMNS = [
    "id", "client_reference_id", "task_id", "task_client_reference_id", "project_beneficiary_id",
    "project_beneficiary_client_reference_id", "raw_symptoms", "tenant_id", "is_deleted", "row_version",
    "created_by", "last_modified_by", "created_time", "last_modified_time", "client_created_by",
    "client_last_modified_by", "client_created_time", "client_last_modified_time", "additional_fields",
    "date_of_birth", "age",
    "level_one_code", "level_two_code", "level_three_code", "level_four_code", "level_five_code",
    "level_six_code", "level_seven_code", "level_eight_code", "level_nine_code", "hierarchy_type",
    "boundary_code", "individual_id", "gender", "symptoms", "user_name", "name_of_user", "role",
    "user_address", "task_dates", "synced_date", "additional_details",
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


def _fetch_side_effect_rows(client, side_effect_ids: list[str]) -> list[dict]:
    """No joins here at all -- everything else is resolved via bridges
    against deduped keys (see module docstring)."""
    result = client.query(
        f"SELECT * FROM {BRONZE_TABLE} WHERE id IN %(side_effect_ids)s",
        parameters={"side_effect_ids": side_effect_ids},
    )
    return list(result.named_results())


def _extract_task_lookup_keys(rows: list[dict]) -> dict[str, set[str]]:
    lookup_keys: dict[str, set[str]] = {}
    for row in rows:
        key = row.get("task_client_reference_id")
        if key:
            lookup_keys.setdefault(row["tenant_id"], set()).add(key)
    return lookup_keys


def _resolve_task_context(client, lookup_keys: dict[str, set[str]]) -> dict[tuple[str, str], dict]:
    """
    stg_project_task -> stg_address (task's own address) + stg_project ->
    stg_project_address (task's own project, for boundary hierarchy_type
    and the fallback code ONLY -- never the trailer, see module docstring).
    Resolved once per unique task_client_reference_id, not joined per
    side-effect row.
    """
    resolved: dict[tuple[str, str], dict] = {}
    for tenant_id, keys in lookup_keys.items():
        result = client.query(
            f"""
            SELECT
                t.client_reference_id AS t_client_reference_id,
                t.project_beneficiary_client_reference_id AS task_project_beneficiary_client_reference_id,
                t.additional_details  AS task_additional_details,
                addr.locality_code    AS task_locality_code,
                p.additional_details  AS project_additional_details,
                paddr.boundary        AS project_boundary_code
            FROM {PROJECT_TASK_TABLE} AS t
            LEFT JOIN {ADDRESS_TABLE} AS addr
                ON addr.id = t.address_id AND addr.tenant_id = t.tenant_id
            LEFT JOIN {PROJECT_TABLE} AS p
                ON p.id = t.project_id AND p.tenant_id = t.tenant_id
            LEFT JOIN {PROJECT_ADDRESS_TABLE} AS paddr
                ON paddr.project_id = p.id AND paddr.tenant_id = p.tenant_id
            WHERE t.tenant_id = %(tenant_id)s AND t.client_reference_id IN %(keys)s
            ORDER BY t.id ASC
            LIMIT 1 BY t.client_reference_id
            """,
            parameters={"tenant_id": tenant_id, "keys": list(keys)},
        )
        for r in result.named_results():
            resolved[(tenant_id, r["t_client_reference_id"])] = {
                "task_project_beneficiary_client_reference_id": r["task_project_beneficiary_client_reference_id"],
                "task_additional_details": r["task_additional_details"],
                "task_locality_code": r["task_locality_code"],
                "project_additional_details": r["project_additional_details"],
                "project_boundary_code": r["project_boundary_code"],
            }
    return resolved


def _attach_task_context(rows: list[dict], resolved: dict) -> None:
    for row in rows:
        row["task_project_beneficiary_client_reference_id"] = None
        row["task_additional_details"] = None
        row["task_locality_code"] = None
        row["project_additional_details"] = None
        row["project_boundary_code"] = None
        details = resolved.get((row["tenant_id"], row.get("task_client_reference_id")))
        if details:
            row.update(details)


def _extract_project_beneficiary_lookup_keys(rows: list[dict]) -> dict[str, set[str]]:
    """Keyed on the TASK's own project_beneficiary_client_reference_id
    (attached in _attach_task_context), NOT side_effect's own column of the
    same concept -- see module docstring."""
    lookup_keys: dict[str, set[str]] = {}
    for row in rows:
        key = row.get("task_project_beneficiary_client_reference_id")
        if key:
            lookup_keys.setdefault(row["tenant_id"], set()).add(key)
    return lookup_keys


def _resolve_project_beneficiary_context(client, lookup_keys: dict[str, set[str]]) -> dict[tuple[str, str], dict]:
    """Only beneficiary_client_reference_id is needed here (project fields
    already come from the task bridge) -- simpler than
    referral_transformation.py's own copy of this query."""
    resolved: dict[tuple[str, str], dict] = {}
    for tenant_id, keys in lookup_keys.items():
        result = client.query(
            f"""
            SELECT
                pb.client_reference_id             AS pb_client_reference_id,
                pb.beneficiary_client_reference_id AS beneficiary_client_reference_id
            FROM {PROJECT_BENEFICIARY_TABLE} AS pb
            WHERE pb.tenant_id = %(tenant_id)s AND pb.client_reference_id IN %(keys)s
            ORDER BY pb.id ASC
            LIMIT 1 BY pb.client_reference_id
            """,
            parameters={"tenant_id": tenant_id, "keys": list(keys)},
        )
        for r in result.named_results():
            resolved[(tenant_id, r["pb_client_reference_id"])] = {
                "beneficiary_client_reference_id": r["beneficiary_client_reference_id"],
            }
    return resolved


def _attach_project_beneficiary_context(rows: list[dict], resolved: dict) -> None:
    for row in rows:
        row["beneficiary_client_reference_id"] = None
        details = resolved.get((row["tenant_id"], row.get("task_project_beneficiary_client_reference_id")))
        if details:
            row.update(details)


def _extract_individual_lookup_keys(rows: list[dict]) -> dict[str, set[str]]:
    lookup_keys: dict[str, set[str]] = {}
    for row in rows:
        key = row.get("beneficiary_client_reference_id")
        if key:
            lookup_keys.setdefault(row["tenant_id"], set()).add(key)
    return lookup_keys


def _resolve_individual_context(client, lookup_keys: dict[str, set[str]]) -> dict[tuple[str, str], dict]:
    """
    stg_individual only -- no address lookup here (unlike
    referral_transformation.py's copy), since side_effect's boundary comes
    from the TASK's address, not the individual's. Only demographic fields
    are needed.
    """
    resolved: dict[tuple[str, str], dict] = {}
    for tenant_id, keys in lookup_keys.items():
        result = client.query(
            f"""
            SELECT
                ind.client_reference_id AS ind_client_reference_id,
                ind.date_of_birth       AS date_of_birth,
                ind.gender              AS gender,
                ind.additional_details  AS individual_additional_details
            FROM {INDIVIDUAL_TABLE} AS ind
            WHERE ind.tenant_id = %(tenant_id)s AND ind.client_reference_id IN %(keys)s
            ORDER BY ind.id ASC
            LIMIT 1 BY ind.client_reference_id
            """,
            parameters={"tenant_id": tenant_id, "keys": list(keys)},
        )
        for r in result.named_results():
            resolved[(tenant_id, r["ind_client_reference_id"])] = {
                "date_of_birth": r["date_of_birth"],
                "gender": r["gender"],
                "individual_additional_details": r["individual_additional_details"],
            }
    return resolved


def _attach_individual_context(rows: list[dict], resolved: dict) -> None:
    for row in rows:
        row["date_of_birth"] = None
        row["gender"] = None
        row["individual_additional_details"] = None
        details = resolved.get((row["tenant_id"], row.get("beneficiary_client_reference_id")))
        if details:
            row.update(details)


def _extract_staff_lookup_keys(rows: list[dict]) -> dict[str, set[str]]:
    lookup_keys: dict[str, set[str]] = {}
    for row in rows:
        user_id = row.get("client_last_modified_by")
        if user_id:
            lookup_keys.setdefault(row["tenant_id"], set()).add(user_id)
    return lookup_keys


def _resolve_user_project_context(client, lookup_keys: dict[str, set[str]]) -> dict[tuple[str, str], dict]:
    """
    LEFT JOIN stg_project_staff -> stg_project -> stg_project_address,
    LIMIT 1 BY staff_id -- same tie-break already established for every
    prior entity needing this bridge. Used ONLY for the project/campaign
    trailer columns here, keyed on client_last_modified_by -- completely
    independent from the task bridge's own project (see module docstring).
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


def _attach_project_context(rows: list[dict], resolved: dict) -> None:
    for row in rows:
        row["project_id"] = ""
        row["project_type"] = ""
        row["project_type_id"] = ""
        row["project_name"] = ""
        row["campaign_number"] = ""
        details = resolved.get((row["tenant_id"], row.get("client_last_modified_by")))
        if details:
            row.update(details)


def _get_boundary_lookup_key(row: dict) -> tuple[str, str, str] | None:
    """
    hierarchy_type from the TASK's own project (task bridge), NEVER the
    project-staff bridge's project -- see module docstring. Code is the
    task's own locality_code first, else that same task-project's boundary
    address.
    """
    hierarchy_type = parse_hierarchy_type(row.get("project_additional_details"))
    if not hierarchy_type:
        return None
    code = row.get("task_locality_code") or row.get("project_boundary_code")
    if not code:
        return None
    return row["tenant_id"], hierarchy_type, code


def _get_user_lookup_key(row: dict) -> tuple[str, str] | None:
    user_id = row.get("client_created_by")  # CLIENT audit, not server
    if not user_id:
        return None
    return row["tenant_id"], user_id


def _build_symptoms(raw_symptoms) -> str:
    """String.join(",", symptoms) equivalent -- bronze stores the raw JSON
    array text; this re-parses and comma-joins it."""
    if not raw_symptoms:
        return ""
    try:
        parsed = json.loads(raw_symptoms)
    except (TypeError, ValueError):
        return ""
    return ",".join(parsed) if isinstance(parsed, list) else ""


def _get_individual_extra_fields(row: dict) -> dict:
    """Same shape as referral_transformation.py's own helper -- HEIGHT
    (int-coerced) + DISABILITY_TYPE, only included together."""
    fields = parse_additional_fields(row.get("individual_additional_details"))
    if "height" not in fields or "disabilityType" not in fields:
        return {}
    try:
        height = int(fields["height"])
    except (TypeError, ValueError):
        return {}
    return {"height": height, "disabilityType": fields["disabilityType"]}


def _build_derived_additional_details(row: dict) -> dict:
    """Layered merge, matching Java's own mutate-in-place order:
    side_effect's own additionalFields first, then the task's cycleIndex
    (if present), then individual height/disabilityType (if both present)."""
    details = dict(parse_additional_fields(row.get("additional_details")))
    task_fields = parse_additional_fields(row.get("task_additional_details"))
    if "cycleIndex" in task_fields:
        details["cycleIndex"] = task_fields["cycleIndex"]
    details.update(_get_individual_extra_fields(row))
    return details


def _default_str(value) -> str:
    return value if value is not None else ""


def _default_int(value) -> int:
    return 0 if value is None else int(round(value))


def _date_to_epoch_ms(value) -> int | None:
    """stg_individual.date_of_birth comes back as a Python date (Date32);
    side_effect_entity.date_of_birth is Int64 (epoch ms, matching Java's
    Date.getTime()) -- convert back rather than passing the date through
    directly (which would crash _default_int, which expects a number)."""
    if not value:
        return None
    return int(pendulum.datetime(value.year, value.month, value.day, tz="UTC").timestamp() * 1000)


def _default_date(epoch_ms):
    return pendulum.from_timestamp(epoch_ms / 1000, tz="UTC").date() if epoch_ms else _EPOCH_DATE


def _build_silver_row(row: dict) -> dict:
    """
    Maps one fully-enriched joined row onto side_effect_entity's exact
    column set. additional_fields is bronze's raw additional_details
    passthrough; additional_details (derived) is the layered blob Java
    builds itself. boundary_code mirrors whichever code
    _get_boundary_lookup_key resolved.
    """
    boundary_code = row.get("task_locality_code") or row.get("project_boundary_code")

    return {
        "id": row["id"],
        "client_reference_id": _default_str(row.get("client_reference_id")),
        "task_id": _default_str(row.get("task_id")),
        "task_client_reference_id": _default_str(row.get("task_client_reference_id")),
        "project_beneficiary_id": _default_str(row.get("project_beneficiary_id")),
        "project_beneficiary_client_reference_id": _default_str(row.get("project_beneficiary_client_reference_id")),
        "raw_symptoms": _default_str(row.get("symptoms")),
        "tenant_id": _default_str(row.get("tenant_id")),
        "is_deleted": bool(row.get("is_deleted")),
        "row_version": _default_int(row.get("row_version")),
        "created_by": _default_str(row.get("created_by")),
        "last_modified_by": _default_str(row.get("last_modified_by")),
        "created_time": _default_int(row.get("created_time")),
        "last_modified_time": _default_int(row.get("last_modified_time")),
        "client_created_by": _default_str(row.get("client_created_by")),
        "client_last_modified_by": _default_str(row.get("client_last_modified_by")),
        "client_created_time": _default_int(row.get("client_created_time")),
        "client_last_modified_time": _default_int(row.get("client_last_modified_time")),
        "additional_fields": _default_str(row.get("additional_details")),
        "date_of_birth": _default_int(_date_to_epoch_ms(row.get("date_of_birth"))),
        "age": calculate_age_in_months(row.get("date_of_birth")) or 0,
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
        "boundary_code": _default_str(boundary_code),
        "individual_id": _default_str(row.get("beneficiary_client_reference_id")),
        "gender": _default_str(row.get("gender")),
        "symptoms": _build_symptoms(row.get("symptoms")),
        "user_name": _default_str(row.get("user_name")),
        "name_of_user": _default_str(row.get("name_of_user")),
        "role": _default_str(row.get("role")),
        "user_address": _default_str(row.get("user_address")),
        "task_dates": _default_date(row.get("client_last_modified_time")),
        "synced_date": _default_date(row.get("last_modified_time")),
        "additional_details": json.dumps(_build_derived_additional_details(row)),
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
                "side_effect: failed to build silver row for side effect id=%s; skipping this row",
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
    description="Transforms side-effect bronze events into the side_effect_entity silver table.",
    schedule=None,
    start_date=pendulum.datetime(2024, 1, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    tags=["bronze-to-silver", "side_effect"],
)
def side_effect_transformation():

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
        Reads side-effect bronze rows for this run's window in fixed-size
        chunks via keyset pagination, transforms, and writes each chunk to
        side_effect_entity before moving to the next chunk.

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
            "side_effect bronze records ingested in [%s, %s): %d (chunk_size=%d)",
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
                "side_effect chunk %d: %d side effect rows (cumulative %d/%d)",
                chunk_num, len(chunk), rows_seen, total,
            )

            side_effect_ids = [row["id"] for row in chunk]
            joined_rows = _fetch_side_effect_rows(client, side_effect_ids)

            task_lookup_keys = _extract_task_lookup_keys(joined_rows)
            unique_task_count = sum(len(keys) for keys in task_lookup_keys.values())
            task_context = _resolve_task_context(client, task_lookup_keys)
            _attach_task_context(joined_rows, task_context)
            log.info(
                "side_effect chunk %d: resolved task bridge for %d/%d unique reference(s)",
                chunk_num, len(task_context), unique_task_count,
            )

            pb_lookup_keys = _extract_project_beneficiary_lookup_keys(joined_rows)
            pb_context = _resolve_project_beneficiary_context(client, pb_lookup_keys)
            _attach_project_beneficiary_context(joined_rows, pb_context)

            ind_lookup_keys = _extract_individual_lookup_keys(joined_rows)
            ind_context = _resolve_individual_context(client, ind_lookup_keys)
            _attach_individual_context(joined_rows, ind_context)
            log.info(
                "side_effect chunk %d: resolved project-beneficiary/individual bridge for %d rows",
                chunk_num, len(ind_context),
            )

            staff_lookup_keys = _extract_staff_lookup_keys(joined_rows)
            unique_staff_count = sum(len(user_ids) for user_ids in staff_lookup_keys.values())
            user_project_context = _resolve_user_project_context(client, staff_lookup_keys)
            _attach_project_context(joined_rows, user_project_context)
            log.info(
                "side_effect chunk %d: resolved project-staff bridge for %d/%d unique user(s)",
                chunk_num, len(user_project_context), unique_staff_count,
            )

            lookup_keys = extract_boundary_lookup_keys(joined_rows, _get_boundary_lookup_key)
            resolved_levels = resolve_boundary_levels(lookup_keys)
            attach_boundary_levels(joined_rows, resolved_levels, _get_boundary_lookup_key)
            log.info(
                "side_effect chunk %d: attached boundary hierarchy levels to %d rows",
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
                "side_effect chunk %d: attached user info to %d rows (%d unique user(s))",
                chunk_num, len(joined_rows), len(user_lookup_keys),
            )

            silver_rows = _build_silver_rows(joined_rows)
            _write_silver_chunk(client, silver_rows)
            log.info(
                "side_effect chunk %d: wrote %d/%d rows to %s",
                chunk_num, len(silver_rows), len(joined_rows), SILVER_TABLE,
            )

    transform_bronze_to_silver(parse_time_window())


side_effect_transformation()
