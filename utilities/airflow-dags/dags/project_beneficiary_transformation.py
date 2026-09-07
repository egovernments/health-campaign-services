"""
project_beneficiary_transformation.py

Bronze -> silver transformation DAG for the `project_beneficiary` entity.
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
    parse_additional_fields,
    parse_hierarchy_type,
    resolve_boundary_levels,
    resolve_user_info,
)

log = logging.getLogger(__name__)

DAG_ID = "project_beneficiary_transformation"

BRONZE_TABLE = "analytics.stg_project_beneficiary"
PROJECT_TABLE = "analytics.stg_project"
PROJECT_ADDRESS_TABLE = "analytics.stg_project_address"
INDIVIDUAL_TABLE = "analytics.stg_individual"
CHUNK_SIZE_VARIABLE = "bronze_to_silver_chunk_size"
DEFAULT_CHUNK_SIZE = 5000

SILVER_TABLE = "project_beneficiary_entity"

# Mirrors Java's fieldsTypeMap: only these four additionalFields keys get
# type-coerced when flattened; every other key is stored as a raw string,
# even if blank.
FIELD_TYPE_COERCIONS = {"ageInMonths": int, "isGuestMember": bool, "isHeadOfHousehold": bool, "age": int}
# Mirrors Java's mandatoryFields set -- checkMandatoryFieldExists only ever
# backfills these two keys from a linked Individual lookup.
MANDATORY_FIELDS = ("ageInMonths", "gender")

_EPOCH_DATE = pendulum.Date(1970, 1, 1)
_EPOCH_DATETIME = pendulum.datetime(1970, 1, 1, tz="UTC")

SILVER_COLUMNS = [
    "id", "tenant_id", "project_id", "beneficiary_id", "beneficiary_client_reference_id",
    "client_reference_id", "date_of_registration", "tag", "is_deleted", "row_version",
    "beneficiary_additional_fields", "created_by", "last_modified_by", "created_time", "last_modified_time",
    "client_created_by", "client_last_modified_by", "client_created_time", "client_last_modified_time",
    "level_one_code", "level_two_code", "level_three_code", "level_four_code", "level_five_code",
    "level_six_code", "level_seven_code", "level_eight_code", "level_nine_code", "hierarchy_type",
    "user_name", "name_of_user", "role", "user_address",
    "task_dates", "synced_date", "synced_time_stamp", "additional_details",
    "project_type", "project_type_id", "project_name", "campaign_number", "campaign_id",
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


def _fetch_enriched_beneficiary_rows(client, beneficiary_ids: list[str]) -> list[dict]:
    """
    Left-joins this chunk's stg_project_beneficiary rows with their parent
    stg_project row and that project's stg_project_address row.

    stg_project_beneficiary and stg_project share several column names
    verbatim (id, tenant_id, additional_details, created_by, last_modified_by,
    created_time, last_modified_time, row_version, is_deleted) -- same
    collision risk project_staff_transformation.py's join already avoids,
    so this query explicitly selects and aliases only the columns actually
    needed rather than `pb.*`/`p.*`.

    No fan-out guard needed: stg_project_beneficiary is the driving
    (1-row-per-beneficiary) table, and both joins are single-row-per-project
    relationships -- a missing project/address row just leaves those
    columns empty, never drops or duplicates the beneficiary row.
    """
    result = client.query(
        f"""
        SELECT
            pb.id                              AS id,
            pb.tenant_id                        AS tenant_id,
            pb.project_id                       AS project_id,
            pb.beneficiary_id                   AS beneficiary_id,
            pb.client_reference_id              AS client_reference_id,
            pb.beneficiary_client_reference_id  AS beneficiary_client_reference_id,
            pb.date_of_registration              AS date_of_registration,
            pb.tag                              AS tag,
            pb.is_deleted                       AS is_deleted,
            pb.row_version                      AS row_version,
            pb.additional_details               AS beneficiary_additional_details_raw,
            pb.created_by                       AS created_by,
            pb.last_modified_by                 AS last_modified_by,
            pb.created_time                     AS created_time,
            pb.last_modified_time               AS last_modified_time,
            pb.client_created_by                AS client_created_by,
            pb.client_last_modified_by          AS client_last_modified_by,
            pb.client_created_time              AS client_created_time,
            pb.client_last_modified_time        AS client_last_modified_time,
            p.additional_details                AS project_additional_details,
            p.project_type                      AS project_type,
            p.project_type_id                   AS project_type_id,
            p.name                              AS project_name,
            p.reference_id                      AS campaign_number,
            paddr.boundary                       AS project_address_boundary
        FROM {BRONZE_TABLE} AS pb
        LEFT JOIN {PROJECT_TABLE} AS p
            ON p.id = pb.project_id AND p.tenant_id = pb.tenant_id
        LEFT JOIN {PROJECT_ADDRESS_TABLE} AS paddr
            ON paddr.project_id = p.id AND paddr.tenant_id = p.tenant_id
        WHERE pb.id IN %(beneficiary_ids)s
        """,
        parameters={"beneficiary_ids": beneficiary_ids},
    )
    return list(result.named_results())


def _coerce_field_value(value, target_type):
    """
    Mirrors Java's putValueBasedOnType: a blank/None value becomes None; on
    a successful parse, the coerced value is returned; on a parse failure,
    the RAW STRING is kept as-is (matches Java's catch-and-fallback, which
    stores the original string on a NumberFormatException, not a null).
    """
    if value is None or (isinstance(value, str) and value.strip() == ""):
        return None
    try:
        if target_type is int:
            return int(value)
        if target_type is bool:
            return str(value).strip().lower() == "true"
    except (TypeError, ValueError):
        return value
    return value


def _build_beneficiary_additional_details(raw_additional_details) -> dict:
    """
    Mirrors additionalFieldsToDetails: flattens the beneficiary's own
    additionalFields blob into a plain dict, coercing only the four known
    keys (FIELD_TYPE_COERCIONS) and leaving every other key as a raw
    string verbatim -- matches Java's fieldsTypeMap/putValueBasedOnType
    exactly, including "unknown key -> stored as-is, even if blank".
    """
    fields = parse_additional_fields(raw_additional_details)
    return {
        key: (_coerce_field_value(value, FIELD_TYPE_COERCIONS[key]) if key in FIELD_TYPE_COERCIONS else value)
        for key, value in fields.items()
    }


def _extract_individual_backfill_keys(joined_rows: list[dict], details_by_id: dict) -> dict[str, set[str]]:
    """
    Groups rows still missing ageInMonths and/or gender (after the base
    additionalDetails dict is built) by tenant, keyed on their own
    beneficiary_client_reference_id -- mirrors checkMandatoryFieldExists,
    gating the individual lookup to only the rows that actually need it
    (most won't, since this data is usually already tagged on the
    beneficiary itself).
    """
    lookup_keys: dict[str, set[str]] = {}
    for row in joined_rows:
        details = details_by_id[row["id"]]
        if details.get("ageInMonths") is None or details.get("gender") is None:
            ref = row.get("beneficiary_client_reference_id")
            if ref:
                lookup_keys.setdefault(row["tenant_id"], set()).add(ref)
    return lookup_keys


def _resolve_individual_backfill(client, lookup_keys: dict[str, set[str]]) -> dict[tuple[str, str], dict]:
    """
    One query per tenant in lookup_keys (zero queries if empty), keyed
    directly on beneficiary_client_reference_id -- no bridge-through-
    stg_project_beneficiary needed here (unlike project_task's household/
    individual resolution), since this row already IS the
    project_beneficiary row.
    """
    resolved: dict[tuple[str, str], dict] = {}
    for tenant_id, refs in lookup_keys.items():
        result = client.query(
            f"""
            SELECT client_reference_id, date_of_birth, gender
            FROM {INDIVIDUAL_TABLE}
            WHERE tenant_id = %(tenant_id)s
                AND client_reference_id IN %(refs)s
                AND is_deleted = false
            """,
            parameters={"tenant_id": tenant_id, "refs": list(refs)},
        )
        for r in result.named_results():
            resolved[(tenant_id, r["client_reference_id"])] = {
                "age_in_months": calculate_age_in_months(r["date_of_birth"]),
                "gender": r["gender"] or None,
            }
    return resolved


def _attach_additional_details(joined_rows: list[dict], individual_backfill: dict) -> None:
    """
    Builds the base additionalDetails dict for every row, then backfills
    only the still-missing mandatory keys from the resolved individual
    lookup. Stores the finished dict on the row as `additional_details_dict`
    (distinct from the final JSON-string silver column) so boundary
    resolution can read a `locality` override off it too.
    """
    for row in joined_rows:
        details = _build_beneficiary_additional_details(row.get("beneficiary_additional_details_raw"))
        if details.get("ageInMonths") is None or details.get("gender") is None:
            backfill = individual_backfill.get((row["tenant_id"], row.get("beneficiary_client_reference_id")))
            if backfill:
                if details.get("ageInMonths") is None:
                    details["ageInMonths"] = backfill["age_in_months"]
                if details.get("gender") is None:
                    details["gender"] = backfill["gender"]
        row["additional_details_dict"] = details


def _get_boundary_lookup_key(row: dict) -> tuple[str, str, str] | None:
    """
    Two-tier fallback mirroring Java's isMissing(additionalDetails.get(LOCALITY))
    branch: prefers a `locality` code the beneficiary's own additionalFields
    explicitly carries, falling back to the linked project's own address
    boundary (same fallback shape as project_staff_transformation.py). Must
    run after _attach_additional_details (needs additional_details_dict).
    """
    hierarchy_type = parse_hierarchy_type(row.get("project_additional_details"))
    if not hierarchy_type:
        return None
    code = row["additional_details_dict"].get("locality") or row.get("project_address_boundary")
    if not code:
        return None
    return row["tenant_id"], hierarchy_type, code


def _get_user_lookup_key(row: dict) -> tuple[str, str] | None:
    """userInfo here is keyed on the CLIENT-side last-modifier, unlike
    every other entity so far (project_task uses client_created_by,
    project_staff uses its own staff_id)."""
    user_id = row.get("client_last_modified_by")
    if not user_id:
        return None
    return row["tenant_id"], user_id


def _default_str(value) -> str:
    return value if value is not None else ""


def _default_int(value) -> int:
    return 0 if value is None else int(round(value))


def _default_bool(value) -> bool:
    return bool(value) if value is not None else False


def _default_date(epoch_ms):
    return pendulum.from_timestamp(epoch_ms / 1000, tz="UTC").date() if epoch_ms else _EPOCH_DATE


def _default_datetime(epoch_ms):
    return pendulum.from_timestamp(epoch_ms / 1000, tz="UTC") if epoch_ms else _EPOCH_DATETIME


def _build_silver_row(row: dict) -> dict:
    """
    Maps one fully-enriched joined_rows dict onto project_beneficiary_entity's
    exact column set. `beneficiary_additional_fields` holds the beneficiary's
    own RAW additionalFields blob (mirrors Java embedding the whole original
    ProjectBeneficiary object) -- `additional_details` holds the separate,
    DERIVED/backfilled blob built by _attach_additional_details. task_dates
    uses the CLIENT audit's last_modified_time; synced_date/synced_time_stamp
    use the SERVER audit's -- same split already established for
    project_task_entity.
    """
    return {
        "id": row["id"],
        "tenant_id": _default_str(row.get("tenant_id")),
        "project_id": _default_str(row.get("project_id")),
        "beneficiary_id": _default_str(row.get("beneficiary_id")),
        "beneficiary_client_reference_id": _default_str(row.get("beneficiary_client_reference_id")),
        "client_reference_id": _default_str(row.get("client_reference_id")),
        "date_of_registration": _default_int(row.get("date_of_registration")),
        "tag": _default_str(row.get("tag")),
        "is_deleted": _default_bool(row.get("is_deleted")),
        "row_version": _default_int(row.get("row_version")),
        "beneficiary_additional_fields": _default_str(row.get("beneficiary_additional_details_raw")),
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
        "user_name": _default_str(row.get("user_name")),
        "name_of_user": _default_str(row.get("name_of_user")),
        "role": _default_str(row.get("role")),
        "user_address": _default_str(row.get("user_address")),
        "task_dates": _default_date(row.get("client_last_modified_time")),
        "synced_date": _default_date(row.get("last_modified_time")),
        "synced_time_stamp": _default_datetime(row.get("last_modified_time")),
        "additional_details": json.dumps(row["additional_details_dict"]),
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
                "project_beneficiary: failed to build silver row for beneficiary id=%s; skipping this row",
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
    description="Transforms project_beneficiary bronze events into the project_beneficiary_entity silver table.",
    schedule=None,
    start_date=pendulum.datetime(2024, 1, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    tags=["bronze-to-silver", "project_beneficiary"],
)
def project_beneficiary_transformation():

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
        Reads project_beneficiary bronze rows for this run's window in
        fixed-size chunks via keyset pagination, transforms, and writes each
        chunk to project_beneficiary_entity before moving to the next chunk.

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
            "project_beneficiary bronze records ingested in [%s, %s): %d (chunk_size=%d)",
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
                "project_beneficiary chunk %d: %d beneficiary rows (cumulative %d/%d)",
                chunk_num, len(chunk), rows_seen, total,
            )

            beneficiary_ids = [row["id"] for row in chunk]
            joined_rows = _fetch_enriched_beneficiary_rows(client, beneficiary_ids)
            log.info(
                "project_beneficiary chunk %d: %d beneficiary+project+address rows after LEFT JOIN",
                chunk_num, len(joined_rows),
            )

            details_by_id = {row["id"]: _build_beneficiary_additional_details(
                row.get("beneficiary_additional_details_raw")) for row in joined_rows}
            individual_lookup_keys = _extract_individual_backfill_keys(joined_rows, details_by_id)
            individual_backfill = _resolve_individual_backfill(client, individual_lookup_keys)
            _attach_additional_details(joined_rows, individual_backfill)
            log.info(
                "project_beneficiary chunk %d: attached additional_details to %d rows "
                "(%d individual backfill lookup(s))",
                chunk_num, len(joined_rows),
                sum(len(v) for v in individual_lookup_keys.values()),
            )

            lookup_keys = extract_boundary_lookup_keys(joined_rows, _get_boundary_lookup_key)
            resolved_levels = resolve_boundary_levels(lookup_keys)
            attach_boundary_levels(joined_rows, resolved_levels, _get_boundary_lookup_key)
            log.info(
                "project_beneficiary chunk %d: attached boundary hierarchy levels to %d rows",
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
                "project_beneficiary chunk %d: attached user info to %d rows (%d unique user(s))",
                chunk_num, len(joined_rows), len(user_lookup_keys),
            )

            silver_rows = _build_silver_rows(joined_rows)
            _write_silver_chunk(client, silver_rows)
            log.info(
                "project_beneficiary chunk %d: wrote %d/%d rows to %s",
                chunk_num, len(silver_rows), len(joined_rows), SILVER_TABLE,
            )

    transform_bronze_to_silver(parse_time_window())


project_beneficiary_transformation()
