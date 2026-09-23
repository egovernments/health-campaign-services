"""
project_transformation_sql_test.py

TEST twin of project_transformation.py for the `project` entity. Same bronze
inputs, same boundary-service calls from Python, same silver row semantics --
but the per-row flatten + write runs inside ClickHouse as one
INSERT ... SELECT per slice instead of in Python.

Writes to analytics.project_entity_sql_test (created AS project_entity), never
to project_entity, so the two can be diffed. Manual trigger only, not part of
entity_transformation_order. Conf: {"start_time", "end_time"} as the
orchestrator sends, plus optional "truncate_target": true.

How a run works
    1. plan_slices      one query over the prj_ingested_at projection splits the
                        window into per-tenant id ranges of <= slice_size projects
    2. per slice        a. fetch_boundary_keys  ClickHouse: distinct (tenant, hierarchy, code)
                        b. resolve_boundary_levels  Python + boundary-service, unchanged
                        c. insert_slice  ClickHouse: bronze joins + flatten + write, with the
                           slice's resolved levels embedded as an inline VALUES table
    Nothing from a core service is stored in ClickHouse; the VALUES text lives
    only in that one query. Every join side is filtered to the slice, so RAM
    per query is bounded regardless of table size.

Measurements and design rationale: airflow_dags/PROJECTION_COMPARISON_REPORT.md, section 7.

Conventions in this file
    - Table names and query structure are Python f-strings; VALUES are bound by
      clickhouse-connect with %(name)s placeholders (Python %-formatting), so SQL
      text must never contain a bare '%': use modulo(), and escape embedded
      literals with sql_string_literal().
    - Silver columns are declared once in SILVER_COLUMNS as (column, expression)
      pairs; both halves of the INSERT are generated from that list. To add a
      column, add one pair (and the column to project_entity's DDL).
"""
from __future__ import annotations

import logging
import os
import sys
import time
from dataclasses import dataclass

import pendulum
from airflow.decorators import dag, task
from airflow.exceptions import AirflowFailException
from airflow.models import Variable

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from clickhouse_utils import get_clickhouse_client  # noqa: E402
from egov_api_utils import (  # noqa: E402
    BOUNDARY_LEVEL_ORDINALS,
    DAY_MILLIS,
    MAX_TASK_DATES,
    resolve_boundary_levels,
)

log = logging.getLogger(__name__)

DAG_ID = "project_test_transformation"

BRONZE_PROJECT_TABLE = "analytics.stg_project"
BRONZE_ADDRESS_TABLE = "analytics.stg_project_address"
BRONZE_TARGET_TABLE = "analytics.stg_project_target"
BRONZE_PRODUCT_VARIANT_TABLE = "analytics.stg_product_variant"
SILVER_TABLE = "analytics.project_entity"
TEST_SILVER_TABLE = "analytics.project_entity_sql_test"

SLICE_SIZE_VARIABLE = "project_sql_test_slice_size"
DEFAULT_SLICE_SIZE = 5000
# One project has one boundary, so a slice resolves up to slice_size codes at
# ~200 bytes of VALUES text each. 15k codes is ~3 MB against the 10 MB
# max_query_size set in clickhouse_utils. Lower the slice size, don't raise this.
MAX_INLINE_BOUNDARY_CODES = 15_000

LEVEL_COLUMNS = [f"level_{ordinal}_code" for ordinal in BOUNDARY_LEVEL_ORDINALS]
BOUNDARY_LEVELS_COLUMNS = ["tenant_id", "hierarchy_type", "boundary_code", *LEVEL_COLUMNS]

# Per-slice INSERT ... SELECT. All join sides are slice-filtered, so these are
# guards, not tuning: fail loudly rather than lean on the pod's memory limit.
# max_threads=2 because the ClickHouse pod has a 1.5-CPU limit.
CLICKHOUSE_INSERT_SETTINGS = {
    "max_threads": 2,
    "max_insert_threads": 1,
    "max_block_size": 8192,
    "max_memory_usage": 3_000_000_000,
    "join_use_nulls": 0,
    "join_algorithm": "hash",
    "max_execution_time": 900,
}
# The one query per run that touches every id in the window (three narrow
# columns from the projection); let it spill instead of failing on huge windows.
CLICKHOUSE_PLAN_SETTINGS = {
    "max_threads": 2,
    "max_bytes_before_external_group_by": 1_073_741_824,
    "max_bytes_before_external_sort": 1_073_741_824,
}
CLICKHOUSE_LOOKUP_SETTINGS = {"max_threads": 2}


# =============================================================================
# Domain objects
# =============================================================================

@dataclass(frozen=True)
class TimeWindow:
    """The bronze ingestion window [start, end) this run processes."""
    start: pendulum.DateTime
    end: pendulum.DateTime
    truncate_target: bool

    @classmethod
    def from_task_output(cls, payload: dict) -> "TimeWindow":
        return cls(
            start=pendulum.parse(payload["start_time"]),
            end=pendulum.parse(payload["end_time"]),
            truncate_target=bool(payload.get("truncate_target", False)),
        )


@dataclass(frozen=True)
class Slice:
    """
    One unit of work: the in-window projects of a single tenant whose id lies
    in [first_project_id, end_project_id). end_project_id is None for the last
    slice of a tenant (open-ended). Single-tenant on purpose: every predicate is
    then a plain prefix range on the (tenant_id, id) sort keys, and the product
    lookup and boundary API grouping are per tenant anyway.
    """
    tenant_id: str
    first_project_id: str
    end_project_id: str | None

    @property
    def label(self) -> str:
        end = self.end_project_id if self.end_project_id is not None else "end"
        return f"tenant={self.tenant_id} [{self.first_project_id}, {end})"

    def query_parameters(self, window: TimeWindow) -> dict:
        return {
            "start_dt": window.start,
            "end_dt": window.end,
            "tenant_id": self.tenant_id,
            "first_project_id": self.first_project_id,
            "end_project_id": self.end_project_id,
        }


@dataclass(frozen=True)
class SliceResult:
    boundary_codes_resolved: int
    written_rows: int
    api_seconds: float
    insert_seconds: float
    total_seconds: float


# =============================================================================
# SQL building blocks
# =============================================================================

class SliceFilter:
    """
    Renders the WHERE clause that restricts each bronze table to one slice.
    Every fragment binds the same parameters (see Slice.query_parameters).

    The id range gives primary-key / projection pruning; the extra
    `IN in_window_project_ids` keeps exact window semantics (only ids ingested
    in-window, as the existing DAG's page -> join handoff does) and bounds the
    join hash tables to in-window projects even when the range is sparse.
    """

    def __init__(self, slice_: Slice):
        self._has_upper_bound = slice_.end_project_id is not None

    def _upper_bound(self, column: str) -> str:
        return f" AND {column} < %(end_project_id)s" if self._has_upper_bound else ""

    @property
    def in_window_project_ids(self) -> str:
        """Subquery of this slice's project ids; touches only prj_ingested_at columns."""
        return (
            f"(SELECT id FROM {BRONZE_PROJECT_TABLE} "
            f"WHERE _ingested_at >= %(start_dt)s AND _ingested_at < %(end_dt)s "
            f"AND tenant_id = %(tenant_id)s AND id >= %(first_project_id)s{self._upper_bound('id')} "
            f"GROUP BY id)"
        )

    def projects(self) -> str:
        """For stg_project aliased as p."""
        return (
            f"p.tenant_id = %(tenant_id)s AND p.id >= %(first_project_id)s{self._upper_bound('p.id')} "
            f"AND p.id IN {self.in_window_project_ids}"
        )

    def addresses(self) -> str:
        """For stg_project_address (has tenant_id; keyed by project_id)."""
        return (
            f"tenant_id = %(tenant_id)s AND project_id >= %(first_project_id)s{self._upper_bound('project_id')} "
            f"AND project_id IN {self.in_window_project_ids}"
        )

    def targets(self) -> str:
        """For stg_project_target (no tenant_id column; keyed by project_id)."""
        return (
            f"project_id >= %(first_project_id)s{self._upper_bound('project_id')} "
            f"AND project_id IN {self.in_window_project_ids}"
        )


def address_join_sql(join_type: str, slice_filter: SliceFilter) -> str:
    """Latest address per project, deduplicated with argMax on the table's own
    ReplacingMergeTree version column (same as project_transformation's Step 2:
    no FINAL, so the prj_by_project projection is used)."""
    return f"""
    {join_type} JOIN
    (
        SELECT tenant_id, project_id, id, argMax(boundary, _ingested_at) AS latest_boundary
        FROM {BRONZE_ADDRESS_TABLE}
        WHERE {slice_filter.addresses()}
        GROUP BY tenant_id, project_id, id
    ) AS paddr
        ON paddr.project_id = p.id AND paddr.tenant_id = p.tenant_id"""


def target_join_sql(slice_filter: SliceFilter) -> str:
    """Latest version of each target per project; a target whose newest
    version is deleted is dropped (HAVING after argMax)."""
    return f"""
    LEFT JOIN
    (
        SELECT
            project_id,
            id,
            argMax(beneficiary_type, last_modified_time) AS latest_beneficiary_type,
            argMax(target_no,        last_modified_time) AS latest_target_no,
            argMax(is_deleted,       last_modified_time) AS latest_is_deleted
        FROM {BRONZE_TARGET_TABLE}
        WHERE {slice_filter.targets()}
        GROUP BY project_id, id
        HAVING latest_is_deleted = false
    ) AS pt
        ON pt.project_id = p.id"""


def boundary_keys_sql(slice_filter: SliceFilter) -> str:
    """Distinct (tenant_id, hierarchy_type, boundary_code) needing a boundary
    lookup in this slice: a key exists only when the project's additional_details
    has a hierarchyType AND the project has an address boundary
    (= project_transformation._get_boundary_lookup_key)."""
    return f"""
SELECT DISTINCT tenant_id, hierarchy_type, boundary_code
FROM
(
    SELECT
        p.tenant_id                                               AS tenant_id,
        JSONExtractString(p.additional_details, 'hierarchyType') AS hierarchy_type,
        paddr.latest_boundary                                     AS boundary_code
    FROM {BRONZE_PROJECT_TABLE} AS p FINAL{address_join_sql("INNER", slice_filter)}
    WHERE {slice_filter.projects()}
)
WHERE hierarchy_type != '' AND boundary_code != ''
"""


def sql_string_literal(value: str) -> str:
    """Single-quoted ClickHouse literal, escaped for the literal and for the
    %-formatting clickhouse-connect applies afterwards."""
    escaped = str(value).replace("\\", "\\\\").replace("'", "\\'").replace("%", "%%")
    return f"'{escaped}'"


def boundary_levels_values_sql(resolved_levels: dict) -> str:
    """
    The Python -> ClickHouse hand-off for one slice: the API-resolved levels,
    keyed (tenant_id, hierarchy_type, boundary_code), rendered as an inline
    VALUES table with columns BOUNDARY_LEVELS_COLUMNS. A code the API could not
    resolve carries nine empty levels, the same as an unmatched LEFT JOIN.
    """
    if len(resolved_levels) > MAX_INLINE_BOUNDARY_CODES:
        raise AirflowFailException(
            f"{DAG_ID}: slice resolved {len(resolved_levels)} boundary codes, above "
            f"MAX_INLINE_BOUNDARY_CODES={MAX_INLINE_BOUNDARY_CODES}; lower the "
            f"{SLICE_SIZE_VARIABLE} Airflow Variable."
        )
    if not resolved_levels:
        empty_row = ", ".join(f"'' AS {column}" for column in BOUNDARY_LEVELS_COLUMNS)
        return f"SELECT {empty_row} WHERE 0"

    structure = ", ".join(f"{column} String" for column in BOUNDARY_LEVELS_COLUMNS)
    rows = []
    for (tenant_id, hierarchy_type, boundary_code), levels in resolved_levels.items():
        values = [tenant_id, hierarchy_type, boundary_code, *[levels.get(column, "") for column in LEVEL_COLUMNS]]
        rows.append("(" + ", ".join(sql_string_literal(v) for v in values) + ")")
    return f"SELECT * FROM VALUES('{structure}', {', '.join(rows)})"


# =============================================================================
# The flatten, in SQL
# =============================================================================
#
# Port of project_transformation._build_silver_row and the egov_api_utils
# helpers it calls. `j` is one joined bronze row (project + latest address +
# one target row, or an empty target when the project has none); `bl` is the
# boundary levels VALUES table. JSON output reproduces json.dumps byte-for-byte
# (", " between items, ": " after keys, doseIndex before cycleIndex).

# Reusable expressions, referenced by name from SILVER_COLUMNS. Rendered as the
# query's WITH clause.
SILVER_HELPER_EXPRESSIONS: list[tuple[str, str]] = [
    ("day_ms", f"toInt64({DAY_MILLIS})"),
    ("max_task_dates", f"toInt64({MAX_TASK_DATES})"),
    # _resolve_product_names: bronze-only sku lookup for the slice's tenant, one map per query
    ("sku_by_variant_id",
     f"(SELECT mapFromArrays(groupArray(id), groupArray(sku)) FROM {BRONZE_PRODUCT_VARIANT_TABLE} FINAL "
     f"WHERE tenant_id = %(tenant_id)s)"),
    # _get_project_type_resource_ids
    ("resource_ids",
     "arrayFilter(v -> v != '', arrayMap(r -> JSONExtractString(r, 'productVariantId'), "
     "JSONExtractArrayRaw(j.additional_details, 'projectType', 'resources')))"),
    # build_project_additional_details: every cycle id, and the FIRST cycle's delivery ids, each '0'-prefixed
    ("cycles", "JSONExtractArrayRaw(j.additional_details, 'projectType', 'cycles')"),
    ("cycle_index", "arrayMap(c -> concat('0', JSONExtractString(c, 'id')), arrayFilter(c -> JSONHas(c, 'id'), cycles))"),
    ("dose_index",
     "arrayMap(d -> concat('0', JSONExtractString(d, 'id')), "
     "arrayFilter(d -> JSONHas(d, 'id'), JSONExtractArrayRaw(cycles[1], 'deliveries')))"),
    # get_project_dates_list: start_date while ts <= end_date + DAY (inclusive off-by-one kept),
    # empty if either bound is 0, capped at MAX_TASK_DATES
    ("task_date_count",
     "if(j.start_date = 0 OR j.end_date = 0 OR j.end_date + day_ms < j.start_date, toInt64(0), "
     "least(intDiv(j.end_date + day_ms - j.start_date, day_ms) + 1, max_task_dates))"),
    ("task_date_list",
     "arrayMap(i -> toString(toDate32(fromUnixTimestamp64Milli(j.start_date + toInt64(i) * day_ms, 'UTC'))), "
     "range(toUInt32(task_date_count)))"),
    # _resolve_target_duration_fields: Python int() truncates toward zero
    ("duration_days",
     "if(j.start_date != 0 AND j.end_date != 0, toInt32(trunc((j.end_date - j.start_date) / day_ms)), toInt32(0))"),
]

# (silver column, SQL expression) in project_entity's column order. Both the
# INSERT column list and the SELECT list are generated from this.
SILVER_COLUMNS: list[tuple[str, str]] = [
    ("id", "if(j.target_id != '', j.target_id, concat(j.id, '-NO_TARGET'))"),
    ("tenant_id", "j.tenant_id"),
    ("project_number", "j.project_number"),
    ("reference_id", "j.reference_id"),
    ("created_by", "j.created_by"),
    ("created_time", "j.created_time"),
    ("last_modified_time", "j.last_modified_time"),
    ("project_beneficiary_type", "JSONExtractString(j.additional_details, 'projectType', 'beneficiaryType')"),
    ("sub_project_type", "j.project_sub_type"),
    ("overall_target", "toInt32(j.target_target_no)"),
    ("target_per_day",
     "if(j.target_id != '' AND duration_days > 0, toInt32(trunc(j.target_target_no / duration_days)), toInt32(0))"),
    ("campaign_duration_in_days", "duration_days"),
    ("start_date", "j.start_date"),
    ("end_date", "j.end_date"),
    ("product_variant", "arrayStringConcat(resource_ids, ',')"),
    ("product_name",
     "arrayStringConcat(arrayMap(v -> if(mapContains(sku_by_variant_id, v), sku_by_variant_id[v], v), resource_ids), ',')"),
    ("target_type", "j.target_beneficiary_type"),
    ("boundary_code", "j.address_boundary"),
    *[(column, f"bl.{column}") for column in LEVEL_COLUMNS],
    ("hierarchy_type", "j.hierarchy_type"),
    ("task_dates", "concat('[', arrayStringConcat(arrayMap(d -> concat('\"', d, '\"'), task_date_list), ', '), ']')"),
    ("additional_details",
     "if(length(cycles) = 0, '', concat("
     "'{\"doseIndex\": [', arrayStringConcat(arrayMap(x -> concat('\"', x, '\"'), dose_index), ', '), "
     "'], \"cycleIndex\": [', arrayStringConcat(arrayMap(x -> concat('\"', x, '\"'), cycle_index), ', '), ']}'))"),
    ("project_id", "j.id"),
    ("project_type", "j.project_type"),
    ("project_type_id", "j.project_type_id"),
    ("project_name", "j.name"),
    ("campaign_number", "j.reference_id"),
    ("campaign_id", "''"),
]


def insert_slice_sql(slice_filter: SliceFilter, boundary_levels_values: str) -> str:
    """The whole per-slice INSERT ... SELECT: bronze joins -> flatten -> write."""
    insert_columns = ",\n    ".join(column for column, _ in SILVER_COLUMNS)
    with_clause = ",\n    ".join(f"{expression} AS {name}" for name, expression in SILVER_HELPER_EXPRESSIONS)
    # Output columns are positional (INSERT maps by position); the comment line
    # above each expression names the silver column it feeds.
    select_list = ",\n".join(f"    -- {column}\n    {expression}" for column, expression in SILVER_COLUMNS)
    return f"""
INSERT INTO {TEST_SILVER_TABLE}
(
    {insert_columns}
)
WITH
    {with_clause}
SELECT
{select_list}
FROM
(
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
        JSONExtractString(p.additional_details, 'hierarchyType') AS hierarchy_type,
        paddr.latest_boundary      AS address_boundary,
        pt.id                      AS target_id,
        pt.latest_beneficiary_type AS target_beneficiary_type,
        pt.latest_target_no        AS target_target_no
    FROM {BRONZE_PROJECT_TABLE} AS p FINAL{address_join_sql("LEFT", slice_filter)}{target_join_sql(slice_filter)}
    WHERE {slice_filter.projects()}
) AS j
LEFT JOIN
(
    {boundary_levels_values}
) AS bl
    ON bl.tenant_id = j.tenant_id
   AND bl.hierarchy_type = j.hierarchy_type
   AND bl.boundary_code = j.address_boundary
"""


# =============================================================================
# ClickHouse calls
# =============================================================================

def create_test_table(client, truncate: bool) -> None:
    """Test target mirrors project_entity (columns, engine, ORDER BY, index)
    so the two can be EXCEPT-diffed."""
    client.command(f"CREATE TABLE IF NOT EXISTS {TEST_SILVER_TABLE} AS {SILVER_TABLE}")
    if truncate:
        client.command(f"TRUNCATE TABLE {TEST_SILVER_TABLE}")
        log.info("%s: truncated %s", DAG_ID, TEST_SILVER_TABLE)


def plan_slices(client, window: TimeWindow, slice_size: int) -> list[Slice]:
    """
    Splits the window into per-tenant id ranges of <= slice_size distinct
    projects. One query over the prj_ingested_at projection (three narrow
    columns, no FINAL); GROUP BY tenant_id, id collapses re-ingested versions so
    boundaries fall on distinct projects. modulo() instead of the percent
    operator because of %-binding (see module docstring).
    """
    result = client.query(
        f"""
        SELECT
            tenant_id,
            arraySort(groupArrayIf(id, modulo(rn - 1, %(slice_size)s) = 0)) AS slice_starts,
            count() AS project_count
        FROM
        (
            SELECT tenant_id, id, row_number() OVER (PARTITION BY tenant_id ORDER BY id) AS rn
            FROM
            (
                SELECT tenant_id, id
                FROM {BRONZE_PROJECT_TABLE}
                WHERE _ingested_at >= %(start_dt)s AND _ingested_at < %(end_dt)s
                GROUP BY tenant_id, id
            )
        )
        GROUP BY tenant_id
        ORDER BY tenant_id
        """,
        parameters={"start_dt": window.start, "end_dt": window.end, "slice_size": slice_size},
        settings=CLICKHOUSE_PLAN_SETTINGS,
    )
    slices: list[Slice] = []
    for row in result.named_results():
        starts: list[str] = row["slice_starts"]
        for position, first_project_id in enumerate(starts):
            next_start = starts[position + 1] if position + 1 < len(starts) else None
            slices.append(Slice(row["tenant_id"], first_project_id, next_start))
        log.info(
            "%s: tenant %s has %d projects in window -> %d slices",
            DAG_ID, row["tenant_id"], row["project_count"], len(starts),
        )
    return slices


def fetch_boundary_keys(client, window: TimeWindow, slice_: Slice) -> dict[tuple[str, str], set[str]]:
    """Returns {(tenant_id, hierarchy_type): {boundary_codes}}, the input shape
    egov_api_utils.resolve_boundary_levels expects."""
    result = client.query(
        boundary_keys_sql(SliceFilter(slice_)),
        parameters=slice_.query_parameters(window),
        settings=CLICKHOUSE_LOOKUP_SETTINGS,
    )
    lookup_keys: dict[tuple[str, str], set[str]] = {}
    for row in result.named_results():
        lookup_keys.setdefault((row["tenant_id"], row["hierarchy_type"]), set()).add(row["boundary_code"])
    return lookup_keys


def insert_slice(client, window: TimeWindow, slice_: Slice, resolved_levels: dict) -> int:
    """Runs the slice's INSERT ... SELECT; returns rows written."""
    sql = insert_slice_sql(SliceFilter(slice_), boundary_levels_values_sql(resolved_levels))
    summary = client.command(sql, parameters=slice_.query_parameters(window), settings=CLICKHOUSE_INSERT_SETTINGS)
    return int(getattr(summary, "written_rows", 0) or 0)


def count_test_table_rows(client) -> int:
    return client.query(f"SELECT count() FROM {TEST_SILVER_TABLE}").result_rows[0][0]


def process_slice(client, window: TimeWindow, slice_: Slice) -> SliceResult:
    """keys (ClickHouse) -> boundary levels (Python + API) -> insert (ClickHouse)."""
    started = time.monotonic()

    lookup_keys = fetch_boundary_keys(client, window, slice_)

    api_started = time.monotonic()
    resolved_levels = resolve_boundary_levels(lookup_keys)  # unchanged boundary-service path
    api_seconds = time.monotonic() - api_started

    insert_started = time.monotonic()
    written_rows = insert_slice(client, window, slice_, resolved_levels)
    insert_seconds = time.monotonic() - insert_started

    return SliceResult(
        boundary_codes_resolved=len(resolved_levels),
        written_rows=written_rows,
        api_seconds=api_seconds,
        insert_seconds=insert_seconds,
        total_seconds=time.monotonic() - started,
    )


# =============================================================================
# DAG
# =============================================================================

@dag(
    dag_id=DAG_ID,
    description="TEST: project bronze -> project_entity_sql_test with the flatten done in ClickHouse.",
    schedule=None,
    start_date=pendulum.datetime(2024, 1, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    tags=["bronze-to-silver", "project", "sql-test"],
)
def project_transformation_sql_test():

    @task
    def parse_time_window(**context) -> dict:
        """Validates start_time/end_time (orchestrator conf shape) and the
        optional truncate_target flag."""
        conf = context["dag_run"].conf or {}
        start_time_raw = conf.get("start_time")
        end_time_raw = conf.get("end_time")

        if not start_time_raw or not end_time_raw:
            raise AirflowFailException(
                f"{DAG_ID} requires 'start_time' and 'end_time' in dag_run.conf; got conf={conf!r}. "
                f'Example: {{"start_time": "2026-09-09T00:00:00+00:00", '
                f'"end_time": "2026-09-23T00:00:00+00:00", "truncate_target": true}}'
            )

        return {
            "start_time": pendulum.parse(start_time_raw).to_iso8601_string(),
            "end_time": pendulum.parse(end_time_raw).to_iso8601_string(),
            "truncate_target": bool(conf.get("truncate_target", False)),
        }

    @task
    def transform_bronze_to_silver_sql(time_window: dict) -> None:
        """Plans the slices, then runs process_slice for each and logs timings.
        Filtered on _ingested_at, not last_modified_time -- see
        airflow_dags/CLAUDE.md "Bronze read window column"."""
        window = TimeWindow.from_task_output(time_window)
        slice_size = int(Variable.get(SLICE_SIZE_VARIABLE, default_var=DEFAULT_SLICE_SIZE))

        client = get_clickhouse_client()
        create_test_table(client, window.truncate_target)

        planning_started = time.monotonic()
        slices = plan_slices(client, window, slice_size)
        log.info(
            "%s: %d slices (slice_size=%d) for [%s, %s), planned in %.2fs",
            DAG_ID, len(slices), slice_size, window.start, window.end, time.monotonic() - planning_started,
        )
        if not slices:
            return

        total_written = 0
        total_insert_seconds = 0.0
        total_api_seconds = 0.0
        for number, slice_ in enumerate(slices, start=1):
            result = process_slice(client, window, slice_)
            total_written += result.written_rows
            total_insert_seconds += result.insert_seconds
            total_api_seconds += result.api_seconds
            log.info(
                "%s slice %d/%d %s: %d boundary codes resolved (api %.2fs), "
                "written_rows=%d (insert %.2fs), slice %.2fs",
                DAG_ID, number, len(slices), slice_.label, result.boundary_codes_resolved,
                result.api_seconds, result.written_rows, result.insert_seconds, result.total_seconds,
            )

        log.info(
            "%s: done. written_rows=%d over %d slices; insert total %.1fs, boundary api total %.1fs; "
            "%s row count (no FINAL)=%d",
            DAG_ID, total_written, len(slices), total_insert_seconds, total_api_seconds,
            TEST_SILVER_TABLE, count_test_table_rows(client),
        )

    transform_bronze_to_silver_sql(parse_time_window())


project_transformation_sql_test()
