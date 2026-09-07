"""
referral_transformation.py

Bronze -> silver transformation DAG for the `referral` entity (Referral ->
ReferralIndexV1 in the Java reference). Triggered exclusively by
bronze_to_silver_orchestrator with conf={"start_time": ..., "end_time": ...};
not scheduled on its own.

Referral's own project link is a direct project_beneficiary -> project
chain (no project_staff bridge involved, unlike most other entities), and
its own individual link is resolved via stg_individual.client_reference_id
(not id). Both chains are resolved as their own extract/resolve/attach
steps against deduped keys (see _resolve_project_beneficiary_context /
_resolve_individual_context below), NOT folded into the main per-chunk
join -- an 8-way join against every referral row in a chunk is expensive;
resolving each chain once against its own deduped key set and attaching in
Python is the same shape already used for the project-staff bridge
elsewhere in this codebase, just applied to a longer chain.
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
    fetch_cycle_index,
    get_project_cycles,
    parse_additional_fields,
    parse_hierarchy_type,
    resolve_boundary_levels,
    resolve_user_info,
)

log = logging.getLogger(__name__)

DAG_ID = "referral_transformation"

BRONZE_TABLE = "analytics.stg_referral"
FACILITY_TABLE = "analytics.stg_facility"
SIDE_EFFECT_TABLE = "analytics.stg_side_effect"
PROJECT_BENEFICIARY_TABLE = "analytics.stg_project_beneficiary"
PROJECT_TABLE = "analytics.stg_project"
PROJECT_ADDRESS_TABLE = "analytics.stg_project_address"
INDIVIDUAL_TABLE = "analytics.stg_individual"
INDIVIDUAL_ADDRESS_TABLE = "analytics.stg_individual_address"
ADDRESS_TABLE = "analytics.stg_address"
CHUNK_SIZE_VARIABLE = "bronze_to_silver_chunk_size"
DEFAULT_CHUNK_SIZE = 5000

SILVER_TABLE = "referral_entity"

_EPOCH_DATE = pendulum.Date(1970, 1, 1)

SILVER_COLUMNS = [
    "id", "client_reference_id", "project_beneficiary_id", "project_beneficiary_client_reference_id",
    "referrer_id", "recipient_type", "recipient_id", "reasons", "side_effect", "referral_code",
    "tenant_id", "is_deleted", "row_version", "created_by", "last_modified_by", "created_time",
    "last_modified_time", "client_created_by", "client_last_modified_by", "client_created_time",
    "client_last_modified_time", "additional_fields", "date_of_birth", "user_name", "name_of_user",
    "role", "user_address", "age",
    "level_one_code", "level_two_code", "level_three_code", "level_four_code", "level_five_code",
    "level_six_code", "level_seven_code", "level_eight_code", "level_nine_code", "hierarchy_type",
    "facility_name", "individual_id", "gender", "task_dates", "synced_date", "additional_details",
    "project_id", "project_type", "project_type_id", "project_name", "campaign_number", "campaign_id",
]

DEFAULT_FACILITY_NAME = "APS"


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


def _fetch_enriched_referral_rows(client, referral_ids: list[str]) -> list[dict]:
    """
    Deliberately cheap: just the two DIRECT, single-hop lookups off the
    referral row itself (facility by id, side-effect by id/client-ref). The
    project/individual chain is resolved separately below (see module
    docstring) -- no fan-out risk here, every target table is matched by
    its own primary/unique key. r's own columns are individually aliased
    rather than `r.*` -- with 3 joined tables, ClickHouse silently
    qualifies any r column whose bare name collides with a column in
    fac/se_by_id/se_by_cref as `r.<col>` in the result set, breaking
    downstream lookups expecting bare names. FINAL is used on all three
    joined tables to avoid row versions from un-merged ReplacingMergeTree
    duplicates.
    """
    result = client.query(
        f"""
        SELECT
            r.id                                      AS id,
            r.client_reference_id                      AS client_reference_id,
            r.tenant_id                                AS tenant_id,
            r.project_beneficiary_id                   AS project_beneficiary_id,
            r.project_beneficiary_client_reference_id  AS project_beneficiary_client_reference_id,
            r.referrer_id                              AS referrer_id,
            r.recipient_id                              AS recipient_id,
            r.recipient_type                            AS recipient_type,
            r.reasons                                   AS reasons,
            r.side_effect_id                            AS side_effect_id,
            r.side_effect_client_reference_id           AS side_effect_client_reference_id,
            r.created_by                                AS created_by,
            r.created_time                              AS created_time,
            r.last_modified_by                          AS last_modified_by,
            r.last_modified_time                        AS last_modified_time,
            r.client_created_by                         AS client_created_by,
            r.client_created_time                       AS client_created_time,
            r.client_last_modified_by                   AS client_last_modified_by,
            r.client_last_modified_time                 AS client_last_modified_time,
            r.row_version                               AS row_version,
            r.is_deleted                                AS is_deleted,
            r.additional_details                        AS additional_details,
            r.referral_code                             AS referral_code,
            r.project_id                                AS project_id,
            fac.name                     AS facility_name_raw,
            se_by_id.id                  AS se_by_id_id,
            se_by_id.symptoms            AS se_by_id_symptoms,
            se_by_id.task_id             AS se_by_id_task_id,
            se_by_id.additional_details  AS se_by_id_additional_details,
            se_by_cref.id                AS se_by_cref_id,
            se_by_cref.symptoms          AS se_by_cref_symptoms,
            se_by_cref.task_id           AS se_by_cref_task_id,
            se_by_cref.additional_details AS se_by_cref_additional_details
        FROM {BRONZE_TABLE} AS r
        LEFT JOIN {FACILITY_TABLE} AS fac FINAL
            ON fac.id = r.recipient_id AND fac.tenant_id = r.tenant_id
        LEFT JOIN {SIDE_EFFECT_TABLE} AS se_by_id FINAL
            ON se_by_id.id = r.side_effect_id AND se_by_id.tenant_id = r.tenant_id
        LEFT JOIN {SIDE_EFFECT_TABLE} AS se_by_cref FINAL
            ON se_by_cref.client_reference_id = r.side_effect_client_reference_id
                AND se_by_cref.tenant_id = r.tenant_id
        WHERE r.id IN %(referral_ids)s
        """,
        parameters={"referral_ids": referral_ids},
    )
    return list(result.named_results())


def _extract_project_beneficiary_lookup_keys(rows: list[dict]) -> dict[str, set[str]]:
    lookup_keys: dict[str, set[str]] = {}
    for row in rows:
        key = row.get("project_beneficiary_client_reference_id")
        if key:
            lookup_keys.setdefault(row["tenant_id"], set()).add(key)
    return lookup_keys


def _resolve_project_beneficiary_context(client, lookup_keys: dict[str, set[str]]) -> dict[tuple[str, str], dict]:
    """
    stg_project_beneficiary -> stg_project -> stg_project_address, LIMIT 1
    BY pb.client_reference_id (same defensive tie-break shape as every
    other bridge in this codebase). Resolved once per unique
    project_beneficiary_client_reference_id, not joined onto every referral
    row -- see module docstring. Returns project fields PLUS
    beneficiary_client_reference_id, which _resolve_individual_context
    below needs as ITS OWN starting id.
    """
    resolved: dict[tuple[str, str], dict] = {}
    for tenant_id, keys in lookup_keys.items():
        result = client.query(
            f"""
            SELECT
                pb.client_reference_id             AS pb_client_reference_id,
                pb.beneficiary_client_reference_id AS beneficiary_client_reference_id,
                p.id                  AS project_id,
                p.project_type        AS project_type,
                p.project_type_id     AS project_type_id,
                p.name                AS project_name,
                p.reference_id        AS campaign_number,
                p.additional_details  AS project_additional_details,
                paddr.boundary        AS project_boundary_code
            FROM {PROJECT_BENEFICIARY_TABLE} AS pb
            LEFT JOIN {PROJECT_TABLE} AS p
                ON p.id = pb.project_id AND p.tenant_id = pb.tenant_id
            LEFT JOIN {PROJECT_ADDRESS_TABLE} AS paddr
                ON paddr.project_id = p.id AND paddr.tenant_id = p.tenant_id
            WHERE pb.tenant_id = %(tenant_id)s AND pb.client_reference_id IN %(keys)s
            ORDER BY pb.id ASC
            LIMIT 1 BY pb.client_reference_id
            """,
            parameters={"tenant_id": tenant_id, "keys": list(keys)},
        )
        for r in result.named_results():
            resolved[(tenant_id, r["pb_client_reference_id"])] = {
                "beneficiary_client_reference_id": r["beneficiary_client_reference_id"],
                "project_id": r["project_id"] or "",
                "project_type": r["project_type"] or "",
                "project_type_id": r["project_type_id"] or "",
                "project_name": r["project_name"] or "",
                "campaign_number": r["campaign_number"] or "",
                "project_additional_details": r["project_additional_details"],
                "project_boundary_code": r["project_boundary_code"],
            }
    return resolved


def _attach_project_beneficiary_context(rows: list[dict], resolved: dict) -> None:
    for row in rows:
        row["beneficiary_client_reference_id"] = None
        row["project_id"] = ""
        row["project_type"] = ""
        row["project_type_id"] = ""
        row["project_name"] = ""
        row["campaign_number"] = ""
        row["project_additional_details"] = None
        row["project_boundary_code"] = None
        details = resolved.get((row["tenant_id"], row.get("project_beneficiary_client_reference_id")))
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
    stg_individual -> stg_individual_address -> stg_address, keyed on the
    individual's own client_reference_id. The address sub-select picks one
    deterministically-chosen address per individual (earliest-linked,
    non-deleted) -- Individual.getAddress().get(0)'s equivalent; Java's own
    "first" is whatever order the Individual API returned, so earliest
    created_time is a documented best-effort substitute, not a confirmed
    exact match.
    """
    resolved: dict[tuple[str, str], dict] = {}
    for tenant_id, keys in lookup_keys.items():
        result = client.query(
            f"""
            SELECT
                ind.client_reference_id AS ind_client_reference_id,
                ind.date_of_birth       AS date_of_birth,
                ind.gender              AS gender,
                ind.additional_details  AS individual_additional_details,
                ind_addr.individual_locality_code AS individual_locality_code
            FROM {INDIVIDUAL_TABLE} AS ind
            LEFT JOIN (
                SELECT
                    ia.individual_id AS individual_id,
                    addr.locality_code AS individual_locality_code
                FROM {INDIVIDUAL_ADDRESS_TABLE} AS ia
                LEFT JOIN {ADDRESS_TABLE} AS addr ON addr.id = ia.address_id
                WHERE ia.is_deleted = false
                ORDER BY ia.created_time ASC
                LIMIT 1 BY ia.individual_id
            ) AS ind_addr
                ON ind_addr.individual_id = ind.id
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
                "individual_locality_code": r["individual_locality_code"],
            }
    return resolved


def _attach_individual_context(rows: list[dict], resolved: dict) -> None:
    for row in rows:
        row["date_of_birth"] = None
        row["gender"] = None
        row["individual_additional_details"] = None
        row["individual_locality_code"] = None
        details = resolved.get((row["tenant_id"], row.get("beneficiary_client_reference_id")))
        if details:
            row.update(details)


def _get_boundary_lookup_key(row: dict) -> tuple[str, str, str] | None:
    """
    Locality-code-first (the individual's own linked address), falling back
    to the project's own boundary address -- restores Java's real
    branching now that bronze supports it via stg_individual_address.
    hierarchy_type comes from the project bridge either way (this repo's
    established per-project convention; there's no per-address hierarchy
    type), so a project resolution is still required for the locality-code
    path too, not just the fallback.
    """
    hierarchy_type = parse_hierarchy_type(row.get("project_additional_details"))
    if not hierarchy_type:
        return None
    code = row.get("individual_locality_code") or row.get("project_boundary_code")
    if not code:
        return None
    return row["tenant_id"], hierarchy_type, code


def _get_user_lookup_key(row: dict) -> tuple[str, str] | None:
    user_id = row.get("created_by")
    if not user_id:
        return None
    return row["tenant_id"], user_id


def _format_cycle_index(row: dict) -> str | None:
    """Mirrors CommonUtils.fetchCycleIndex's OWN formatting ("%02d") --
    distinct from build_project_additional_details's "0"+id convention used
    by project_task/household."""
    cycles = get_project_cycles(row.get("project_additional_details"))
    if not cycles:
        return None
    matched_id = fetch_cycle_index(cycles, row.get("created_time"))
    return f"{matched_id:02d}" if matched_id is not None else None


def _get_individual_extra_fields(row: dict) -> dict:
    """HEIGHT (int-coerced) + DISABILITY_TYPE (string) from the individual's
    own additionalFields -- ONLY included together, matching Java's
    `if (individualDetails.containsKey(HEIGHT) && ...containsKey(DISABILITY_TYPE))`."""
    fields = parse_additional_fields(row.get("individual_additional_details"))
    if "height" not in fields or "disabilityType" not in fields:
        return {}
    try:
        height = int(fields["height"])
    except (TypeError, ValueError):
        return {}
    return {"height": height, "disabilityType": fields["disabilityType"]}


def _build_side_effect_json(row: dict) -> str:
    """Prefers the id-matched stg_side_effect row over the
    client_reference_id-matched one."""
    if row.get("se_by_id_id"):
        prefix = "se_by_id_"
    elif row.get("se_by_cref_id"):
        prefix = "se_by_cref_"
    else:
        return ""
    return json.dumps({
        "id": row.get(f"{prefix}id"),
        "taskId": row.get(f"{prefix}task_id"),
        "symptoms": row.get(f"{prefix}symptoms"),
        "additionalDetails": row.get(f"{prefix}additional_details"),
    })


def _default_str(value) -> str:
    return value if value is not None else ""


def _default_int(value) -> int:
    return 0 if value is None else int(round(value))


def _date_to_epoch_ms(value) -> int | None:
    """stg_individual.date_of_birth comes back as a Python date (Date32);
    referral_entity.date_of_birth is Int64 (epoch ms, matching Java's
    Date.getTime()) -- convert back rather than passing the date through
    directly (which would crash _default_int, which expects a number)."""
    if not value:
        return None
    return int(pendulum.datetime(value.year, value.month, value.day, tz="UTC").timestamp() * 1000)


def _default_date(epoch_ms):
    return pendulum.from_timestamp(epoch_ms / 1000, tz="UTC").date() if epoch_ms else _EPOCH_DATE


def _build_silver_row(row: dict) -> dict:
    """
    Maps one fully-enriched joined row onto referral_entity's exact column
    set. additional_fields is bronze's raw additional_details passthrough;
    additional_details (derived) is a fresh blob Java builds itself
    (cycleIndex always, height/disabilityType only when both are present on
    the individual) -- the established raw/derived pairing convention, just
    with additional_fields as the raw-column name since additional_details
    is taken here.
    """
    recipient_type = row.get("recipient_type") or ""
    facility_name = (
        row.get("facility_name_raw")
        if recipient_type.upper() == "FACILITY" and row.get("facility_name_raw")
        else DEFAULT_FACILITY_NAME
    )
    derived_additional_details = {"cycleIndex": _format_cycle_index(row)}
    derived_additional_details.update(_get_individual_extra_fields(row))

    return {
        "id": row["id"],
        "client_reference_id": _default_str(row.get("client_reference_id")),
        "project_beneficiary_id": _default_str(row.get("project_beneficiary_id")),
        "project_beneficiary_client_reference_id": _default_str(row.get("project_beneficiary_client_reference_id")),
        "referrer_id": _default_str(row.get("referrer_id")),
        "recipient_type": recipient_type,
        "recipient_id": _default_str(row.get("recipient_id")),
        "reasons": _default_str(row.get("reasons")),
        "side_effect": _build_side_effect_json(row),
        "referral_code": _default_str(row.get("referral_code")),
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
        "user_name": _default_str(row.get("user_name")),
        "name_of_user": _default_str(row.get("name_of_user")),
        "role": _default_str(row.get("role")),
        "user_address": _default_str(row.get("user_address")),
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
        "facility_name": facility_name,
        "individual_id": _default_str(row.get("beneficiary_client_reference_id")),
        "gender": _default_str(row.get("gender")),
        "task_dates": _default_date(row.get("client_last_modified_time")),
        "synced_date": _default_date(row.get("last_modified_time")),
        "additional_details": json.dumps(derived_additional_details),
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
                "referral: failed to build silver row for referral id=%s; skipping this row",
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
    description="Transforms referral bronze events into the referral_entity silver table.",
    schedule=None,
    start_date=pendulum.datetime(2024, 1, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    tags=["bronze-to-silver", "referral"],
)
def referral_transformation():

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
        Reads referral bronze rows for this run's window in fixed-size
        chunks via keyset pagination, transforms, and writes each chunk to
        referral_entity before moving to the next chunk.

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
            "referral bronze records ingested in [%s, %s): %d (chunk_size=%d)",
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
                "referral chunk %d: %d referral rows (cumulative %d/%d)",
                chunk_num, len(chunk), rows_seen, total,
            )

            referral_ids = [row["id"] for row in chunk]
            joined_rows = _fetch_enriched_referral_rows(client, referral_ids)
            log.info(
                "referral chunk %d: %d rows after LEFT JOIN with facility/side-effect",
                chunk_num, len(joined_rows),
            )

            pb_lookup_keys = _extract_project_beneficiary_lookup_keys(joined_rows)
            unique_pb_count = sum(len(keys) for keys in pb_lookup_keys.values())
            pb_context = _resolve_project_beneficiary_context(client, pb_lookup_keys)
            _attach_project_beneficiary_context(joined_rows, pb_context)
            log.info(
                "referral chunk %d: resolved project-beneficiary bridge for %d/%d unique reference(s)",
                chunk_num, len(pb_context), unique_pb_count,
            )

            ind_lookup_keys = _extract_individual_lookup_keys(joined_rows)
            unique_ind_count = sum(len(keys) for keys in ind_lookup_keys.values())
            ind_context = _resolve_individual_context(client, ind_lookup_keys)
            _attach_individual_context(joined_rows, ind_context)
            log.info(
                "referral chunk %d: resolved individual bridge for %d/%d unique reference(s)",
                chunk_num, len(ind_context), unique_ind_count,
            )

            lookup_keys = extract_boundary_lookup_keys(joined_rows, _get_boundary_lookup_key)
            resolved_levels = resolve_boundary_levels(lookup_keys)
            attach_boundary_levels(joined_rows, resolved_levels, _get_boundary_lookup_key)
            log.info(
                "referral chunk %d: attached boundary hierarchy levels to %d rows",
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
                "referral chunk %d: attached user info to %d rows (%d unique user(s))",
                chunk_num, len(joined_rows), len(user_lookup_keys),
            )

            silver_rows = _build_silver_rows(joined_rows)
            _write_silver_chunk(client, silver_rows)
            log.info(
                "referral chunk %d: wrote %d/%d rows to %s",
                chunk_num, len(silver_rows), len(joined_rows), SILVER_TABLE,
            )

    transform_bronze_to_silver(parse_time_window())


referral_transformation()
