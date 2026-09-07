"""
project_transformation.py

Bronze -> silver transformation DAG for the `project` entity.
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
    DAY_MILLIS,
    attach_boundary_levels,
    build_project_additional_details,
    extract_boundary_lookup_keys,
    get_project_dates_list,
    parse_hierarchy_type,
    parse_project_beneficiary_type,
    resolve_boundary_levels,
)

log = logging.getLogger(__name__)

DAG_ID = "project_transformation"

BRONZE_TABLE = "analytics.stg_project"
PROJECT_ADDRESS_TABLE = "analytics.stg_project_address"
PROJECT_TARGET_TABLE = "analytics.stg_project_target"
PRODUCT_VARIANT_TABLE = "analytics.stg_product_variant"
CHUNK_SIZE_VARIABLE = "bronze_to_silver_chunk_size"
DEFAULT_CHUNK_SIZE = 5000
JOIN_ROW_COUNT_WARNING_MULTIPLIER = 3

SILVER_TABLE = "project_entity"

SILVER_COLUMNS = [
    "id", "tenant_id", "project_number", "reference_id", "created_by", "created_time",
    "last_modified_time", "project_beneficiary_type", "sub_project_type",
    "overall_target", "target_per_day", "campaign_duration_in_days", "start_date", "end_date",
    "product_variant", "product_name", "target_type", "boundary_code",
    "level_one_code", "level_two_code", "level_three_code", "level_four_code", "level_five_code",
    "level_six_code", "level_seven_code", "level_eight_code", "level_nine_code", "hierarchy_type",
    "task_dates", "additional_details",
    "project_id", "project_type", "project_type_id", "project_name", "campaign_number", "campaign_id",
]


def _parse_window_bound(iso_ts: str):
    return pendulum.parse(iso_ts)


def _count_bronze_records(client, start_dt, end_dt) -> int:
    """
    Deliberately no FINAL here (unlike the enrichment queries below):
    _ingested_at is not this table's ReplacingMergeTree version column
    (last_modified_time is), so FINAL could make the row that "wins" a
    given id's dedup carry a different _ingested_at than the duplicate that
    actually fell inside [start_dt, end_dt) -- undercounting or pagination
    drift against _iter_bronze_chunks below, not just a cost tradeoff.
    """
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


def _fetch_enriched_project_rows(client, project_ids: list[str]) -> list[dict]:
    """
    Left-joins this chunk's stg_project rows with their stg_project_address
    row (a project has zero or one address) and their stg_project_target
    rows (a project can have zero or many targets, one per beneficiary
    type). No LIMIT on either join -- every address/target for every
    project_id passed in is returned, so nothing is cut off mid-project by a
    page boundary.

    stg_project_address and stg_project_target share several column names
    with stg_project verbatim (tenant_id, id, created_by, created_time,
    last_modified_by, last_modified_time, is_deleted, _ingested_at) -- `p.*`
    would silently collide on all of those (ClickHouse renames p's copy to
    "p.<col>" in the result set whenever a same-named column exists
    *anywhere* in the joined tables, even if that joined column is never
    itself selected), so this query explicitly selects and aliases only the
    stg_project columns actually needed, rather than using p.*. paddr's/pt's
    own columns are aliased with address_/target_ prefixes for the same
    reason project_task_transformation.py aliases its resource join columns.
    """
    result = client.query(
        f"""
        SELECT
            p.id                   AS id,
            p.tenant_id            AS tenant_id,
            p.additional_details   AS additional_details,
            p.project_number       AS project_number,
            p.reference_id         AS reference_id,
            p.created_by           AS created_by,
            p.created_time         AS created_time,
            p.last_modified_time   AS last_modified_time,
            p.project_sub_type     AS project_sub_type,
            p.start_date           AS start_date,
            p.end_date             AS end_date,
            p.project_type         AS project_type,
            p.project_type_id      AS project_type_id,
            p.name                 AS name,
            paddr.boundary      AS address_boundary,
            pt.id               AS target_id,
            pt.beneficiary_type AS target_beneficiary_type,
            pt.target_no        AS target_target_no
        FROM {BRONZE_TABLE} AS p FINAL
        LEFT JOIN {PROJECT_ADDRESS_TABLE} AS paddr FINAL
            ON paddr.project_id = p.id AND paddr.tenant_id = p.tenant_id
        LEFT JOIN {PROJECT_TARGET_TABLE} AS pt FINAL
            ON pt.project_id = p.id AND pt.is_deleted = false
        WHERE p.id IN %(project_ids)s
        """,
        parameters={"project_ids": project_ids},
    )
    return list(result.named_results())


def _get_boundary_lookup_key(row: dict) -> tuple[str, str, str] | None:
    """
    Returns (tenant_id, hierarchy_type, code) for boundary hierarchy
    resolution, mirroring Java's localityCode derivation (project's own
    address boundary) -- there's no further fallback available here, since
    stg_project_address IS the boundary source for a project (unlike
    project_task, which can fall back from its own address to its parent
    project's).
    """
    hierarchy_type = parse_hierarchy_type(row.get("additional_details"))
    if not hierarchy_type:
        return None
    code = row.get("address_boundary")
    if not code:
        return None
    return row["tenant_id"], hierarchy_type, code


def _backfill_hierarchy_type(joined_rows: list[dict]) -> None:
    """
    attach_boundary_levels's lookup key is deliberately all-or-nothing (see
    its docstring in egov_api_utils.py): if _get_boundary_lookup_key returns
    None -- e.g. address_boundary is missing, or the boundary-service call
    itself fails/is unreachable -- hierarchy_type comes back empty too, even
    though it's a value already known locally from the project's own
    additional_details, independent of any address code or external call.
    Backfilled here (project_transformation-specific, not a change to the
    shared egov_api_utils.py contract other entities rely on) so that known
    data point isn't discarded just because level-code resolution couldn't
    complete. level_one_code..level_nine_code correctly stay empty in that
    case -- there's genuinely nothing to show without a resolved code.
    """
    for row in joined_rows:
        if not row.get("hierarchy_type"):
            row["hierarchy_type"] = parse_hierarchy_type(row.get("additional_details")) or ""


def _get_project_type_resource_ids(additional_details) -> list[str]:
    """
    Reads additionalDetails.projectType.resources[].productVariantId from a
    project's own additional_details blob -- same embedding convention this
    deployment already relies on for beneficiaryType/cycles, used here
    instead of Java's MDMS HCM-PROJECT-TYPES resource lookup (no bronze
    table models project-type-to-resource mappings, so there is nothing to
    join against for this data other than the project's own JSON).
    """
    if not additional_details:
        return []
    try:
        parsed = json.loads(additional_details)
    except (TypeError, ValueError):
        return []
    project_type = parsed.get("projectType") if isinstance(parsed, dict) else None
    resources = project_type.get("resources") if isinstance(project_type, dict) else None
    if not isinstance(resources, list):
        return []
    return [r["productVariantId"] for r in resources if isinstance(r, dict) and r.get("productVariantId")]


def _extract_product_lookup_keys(joined_rows: list[dict]) -> dict[str, set[str]]:
    """Groups product_variant_ids needing name resolution by tenant, so
    _resolve_product_names runs at most one query per tenant per chunk."""
    lookup_keys: dict[str, set[str]] = {}
    for row in joined_rows:
        variant_ids = _get_project_type_resource_ids(row.get("additional_details"))
        if variant_ids:
            lookup_keys.setdefault(row["tenant_id"], set()).update(variant_ids)
    return lookup_keys


def _resolve_product_names(client, tenant_variant_ids: dict[str, set[str]]) -> dict[tuple[str, str], str]:
    """Runs one query per tenant in tenant_variant_ids (zero queries if
    empty), resolving product_variant_id -> sku directly against bronze --
    no product-service HTTP call needed, unlike Java's
    fetchProductVariantNameFromService."""
    resolved: dict[tuple[str, str], str] = {}
    for tenant_id, variant_ids in tenant_variant_ids.items():
        result = client.query(
            f"SELECT id, sku FROM {PRODUCT_VARIANT_TABLE} FINAL "
            f"WHERE tenant_id = %(tenant_id)s AND id IN %(variant_ids)s",
            parameters={"tenant_id": tenant_id, "variant_ids": list(variant_ids)},
        )
        for r in result.named_results():
            resolved[(tenant_id, r["id"])] = r["sku"] or ""
    return resolved


def _attach_product_fields(joined_rows: list[dict], resolved_names: dict) -> None:
    """
    Sets product_variant (comma-joined productVariantIds) and product_name
    (comma-joined resolved skus, same order) on each row, mirroring Java's
    String.join(",", ...) on both lists. A variant id with no bronze match
    falls back to the raw id itself, matching
    fetchProductVariantNameFromService's own miss-fallback behavior.
    """
    for row in joined_rows:
        variant_ids = _get_project_type_resource_ids(row.get("additional_details"))
        if not variant_ids:
            row["product_variant"] = ""
            row["product_name"] = ""
            continue
        tenant_id = row["tenant_id"]
        names = [resolved_names.get((tenant_id, vid), vid) for vid in variant_ids]
        row["product_variant"] = ",".join(variant_ids)
        row["product_name"] = ",".join(names)


def _resolve_target_fields(row: dict) -> dict:
    """
    Real stg_project_target rows are used as-is. If a project chunk row has
    none (target_id empty after the LEFT JOIN), synthesize a single
    placeholder rather than dropping the project from silver entirely --
    Java's reference returns an empty list for a target-less project
    (constructTaskResourceIfNull has no equivalent here), but this
    deployment wants a safeguard against a project silently vanishing from
    project_entity, e.g. if its target hasn't landed in bronze yet. Expected
    to rarely/never trigger on healthy data.
    """
    if row.get("target_id"):
        return {
            "id": row["target_id"],
            "target_type": row.get("target_beneficiary_type") or "",
            "target_no": row.get("target_target_no"),
        }
    return {
        "id": f"{row['id']}-NO_TARGET",
        "target_type": "",
        "target_no": None,
    }


def _resolve_target_duration_fields(target_no, row: dict) -> dict:
    """
    Mirrors the OVERALL branch of Java's target-number-type switch (the only
    branch this deployment's reference config actually uses -- there's no
    Airflow Variable for a PER_DAY toggle, so that branch isn't built).
    """
    start_date, end_date = row.get("start_date"), row.get("end_date")
    campaign_duration_in_days = None
    target_per_day = None
    if start_date and end_date:
        campaign_duration_in_days = int((end_date - start_date) / DAY_MILLIS)
        if target_no is not None and campaign_duration_in_days > 0:
            target_per_day = int(target_no / campaign_duration_in_days)
    return {
        "target_per_day": target_per_day,
        "campaign_duration_in_days": campaign_duration_in_days,
    }


def _default_str(value) -> str:
    return value if value is not None else ""


def _default_int(value) -> int:
    return 0 if value is None else int(round(value))


def _build_silver_row(row: dict) -> dict:
    """
    Maps one fully-enriched joined_rows dict onto project_entity's exact
    column set. `id`/`project_id` and `reference_id`/`campaign_number` are
    deliberate, not accidental duplication -- see
    _resolve_target_fields/module docstring notes and the plan for why.
    """
    target = _resolve_target_fields(row)
    duration_fields = _resolve_target_duration_fields(target["target_no"], row)

    return {
        "id": target["id"],
        "tenant_id": _default_str(row.get("tenant_id")),
        "project_number": _default_str(row.get("project_number")),
        "reference_id": _default_str(row.get("reference_id")),
        "created_by": _default_str(row.get("created_by")),
        "created_time": _default_int(row.get("created_time")),
        "last_modified_time": _default_int(row.get("last_modified_time")),
        "project_beneficiary_type": _default_str(parse_project_beneficiary_type(row.get("additional_details"))),
        "sub_project_type": _default_str(row.get("project_sub_type")),
        "overall_target": _default_int(target["target_no"]),
        "target_per_day": _default_int(duration_fields["target_per_day"]),
        "campaign_duration_in_days": _default_int(duration_fields["campaign_duration_in_days"]),
        "start_date": _default_int(row.get("start_date")),
        "end_date": _default_int(row.get("end_date")),
        "product_variant": _default_str(row.get("product_variant")),
        "product_name": _default_str(row.get("product_name")),
        "target_type": target["target_type"],
        "boundary_code": _default_str(row.get("address_boundary")),
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
        "task_dates": json.dumps(get_project_dates_list(row.get("start_date"), row.get("end_date"))),
        "additional_details": build_project_additional_details(row.get("additional_details")),
        "project_id": row["id"],
        "project_type": _default_str(row.get("project_type")),
        "project_type_id": _default_str(row.get("project_type_id")),
        "project_name": _default_str(row.get("name")),
        "campaign_number": _default_str(row.get("reference_id")),
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
                "project: failed to build silver row for project id=%s; skipping this row",
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
    description="Transforms project bronze events into the project_entity silver table.",
    schedule=None,
    start_date=pendulum.datetime(2024, 1, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    tags=["bronze-to-silver", "project"],
)
def project_transformation():

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
        Reads project bronze rows for this run's window in fixed-size chunks
        via keyset pagination, transforms, and writes each chunk to
        project_entity before moving to the next chunk.

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
            "project bronze records ingested in [%s, %s): %d (chunk_size=%d)",
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
                "project chunk %d: %d project rows (cumulative %d/%d)",
                chunk_num, len(chunk), rows_seen, total,
            )

            project_ids = [row["id"] for row in chunk]
            joined_rows = _fetch_enriched_project_rows(client, project_ids)
            if len(joined_rows) > chunk_size * JOIN_ROW_COUNT_WARNING_MULTIPLIER:
                log.warning(
                    "project chunk %d: joined rows (%d) exceed %dx chunk_size (%d) "
                    "-- some projects in this chunk have unusually high target fan-out",
                    chunk_num, len(joined_rows), JOIN_ROW_COUNT_WARNING_MULTIPLIER, chunk_size,
                )
            no_target_count = sum(1 for row in joined_rows if not row.get("target_id"))
            if no_target_count:
                log.info(
                    "project chunk %d: %d/%d rows had no real target, using placeholder fallback",
                    chunk_num, no_target_count, len(joined_rows),
                )
            log.info(
                "project chunk %d: %d project+address+target rows after LEFT JOIN",
                chunk_num, len(joined_rows),
            )

            lookup_keys = extract_boundary_lookup_keys(joined_rows, _get_boundary_lookup_key)
            resolved_levels = resolve_boundary_levels(lookup_keys)
            attach_boundary_levels(joined_rows, resolved_levels, _get_boundary_lookup_key)
            _backfill_hierarchy_type(joined_rows)
            log.info(
                "project chunk %d: attached boundary hierarchy levels to %d rows",
                chunk_num, len(joined_rows),
            )

            product_lookup_keys = _extract_product_lookup_keys(joined_rows)
            resolved_product_names = _resolve_product_names(client, product_lookup_keys)
            _attach_product_fields(joined_rows, resolved_product_names)
            log.info(
                "project chunk %d: attached product_variant/product_name to %d rows",
                chunk_num, len(joined_rows),
            )

            silver_rows = _build_silver_rows(joined_rows)
            _write_silver_chunk(client, silver_rows)
            log.info(
                "project chunk %d: wrote %d/%d rows to %s",
                chunk_num, len(silver_rows), len(joined_rows), SILVER_TABLE,
            )

    transform_bronze_to_silver(parse_time_window())


project_transformation()
