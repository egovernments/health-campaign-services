"""
sql_flatten_common.py

Shared plumbing for the SQL-side flatten TEST twins in dags/sql_test/.

Every `<entity>_test_transformation.py` in this folder is a test twin of the
Python `<entity>_transformation.py` one level up: same bronze inputs, same
external API calls from Python, same silver row semantics -- but the per-row
flatten + write runs inside ClickHouse as one INSERT ... SELECT per slice. An
entity file declares WHAT its rows are (joins, helper expressions, silver
column expressions, which API lookups it needs); this module owns HOW a run
works, identically for every entity:

    1. plan_slices      one query over the driving table's prj_ingested_at projection splits
                        the window into per-tenant id ranges of <= slice_size rows
    2. per slice        a. one keys query per API lookup (ClickHouse)
                        b. the lookup's resolver (Python + eGov service, unchanged helpers
                           from egov_api_utils)
                        c. insert_slice: bronze joins + flatten + write, with every lookup's
                           result embedded as an inline VALUES table in that one statement

Nothing from a core service is stored in ClickHouse; the VALUES text lives only
in the one query. Every join side is filtered to the slice, so RAM per query is
bounded regardless of table size. Rationale and measurements:
airflow_dags/PROJECTION_COMPARISON_REPORT.md, sections 7-10.

Conventions (see the four original twins for the history)
    - Per-slice SQL is rendered fully in Python (tenant, id bounds, window and
      VALUES rows as explicit literals) and sent without driver-side parameter
      binding: clickhouse-connect 0.11 (the cluster image) does not bind
      %(name)s parameters in client.command(). Only plan_slices binds, through
      client.query(), which both driver versions support.
    - Silver columns are declared once per entity as (column, expression)
      pairs; both halves of the INSERT are generated from that list.
    - Tables are named WITHOUT a database; `table()` prefixes the database from
      the Airflow Variable `sql_test_database` (default `analytics`), so the
      same twins can run against a copy of the data in another database.
    - A query with FINAL never uses a projection: join sides that are looked up
      by a non-primary key are argMax subqueries over the projections in
      ClickHouse_ddl/14, 16, 17 and 18; sides looked up by their own primary
      key (tenant_id, id) use FINAL.
    - json.dumps is reproduced byte for byte (", " and ": " separators, dict
      insertion order) except that non-ASCII text is written as UTF-8, not
      \\uXXXX.
"""
from __future__ import annotations

import logging
import os
import sys
import time
from dataclasses import dataclass, field
from typing import Callable

import pendulum
from airflow.decorators import dag, task
from airflow.exceptions import AirflowFailException
from airflow.models import Variable

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)
sys.path.insert(0, os.path.dirname(_HERE))
from clickhouse_utils import get_clickhouse_client  # noqa: E402
from egov_api_utils import (  # noqa: E402
    BOUNDARY_LEVEL_ORDINALS,
    resolve_boundary_levels,
    resolve_user_info,
)

log = logging.getLogger(__name__)

DATABASE_VARIABLE = "sql_test_database"
DEFAULT_DATABASE = "analytics"
DEFAULT_SLICE_SIZE = 5000
# Inline VALUES tables per statement. Guards keep the rendered statement under
# the 10 MB max_query_size set in clickhouse_utils: lower the slice size, don't
# raise these.
MAX_INLINE_ROWS_PER_LOOKUP = 15_000
MAX_INSERT_SQL_BYTES = 9_000_000

LEVEL_COLUMNS = [f"level_{ordinal}_code" for ordinal in BOUNDARY_LEVEL_ORDINALS]
BOUNDARY_LEVELS_COLUMNS = ["tenant_id", "hierarchy_type", "boundary_code", *LEVEL_COLUMNS]
USER_INFO_COLUMNS = ["tenant_id", "user_id", "user_name", "name_of_user", "role", "user_address"]

# Per-slice INSERT ... SELECT. All join sides are slice-filtered, so these are
# guards, not tuning: fail loudly rather than lean on the pod's memory limit.
# join_use_nulls = 0 is load-bearing: unmatched LEFT JOINs yield '' / 0 /
# false / 1970-01-01, which is exactly what the Python DAGs' own joins returned
# and what their _default_* helpers then passed through. max_threads = 2
# because the ClickHouse pod has a 1.5-CPU limit.
CLICKHOUSE_INSERT_SETTINGS = {
    "max_threads": 2,
    "max_insert_threads": 1,
    "max_block_size": 8192,
    "max_memory_usage": 3_000_000_000,
    "join_use_nulls": 0,
    "join_algorithm": "hash",
    "max_execution_time": 900,
    # exact float parsing, so a "14.6978646" read out of JSON prints back as Python does
    "precise_float_parsing": 1,
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
# Database prefix
# =============================================================================

_database = DEFAULT_DATABASE


def set_database(name: str) -> None:
    """Called once per task run with the `sql_test_database` Variable."""
    global _database
    _database = name


def table(name: str) -> str:
    """`<database>.<name>` for an unqualified bronze/silver table name."""
    return f"{_database}.{name}"


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
    One unit of work: the in-window rows of the driving bronze table for a
    single tenant whose id lies in [first_id, end_id). end_id is None for the
    last slice of a tenant. Single-tenant on purpose: every predicate is then a
    plain prefix range on the (tenant_id, ...) sort keys, and the API
    groupings are per tenant anyway.
    """
    tenant_id: str
    first_id: str
    end_id: str | None

    @property
    def label(self) -> str:
        end = self.end_id if self.end_id is not None else "end"
        return f"tenant={self.tenant_id} [{self.first_id}, {end})"


@dataclass(frozen=True)
class SliceResult:
    resolved: dict[str, int]      # lookup alias -> rows embedded
    written_rows: int
    api_seconds: float
    insert_seconds: float
    total_seconds: float


# =============================================================================
# Literals and the slice filter
# =============================================================================

def sql_string_literal(value: str) -> str:
    """Single-quoted ClickHouse string literal."""
    escaped = str(value).replace("\\", "\\\\").replace("'", "\\'")
    return f"'{escaped}'"


def sql_datetime_literal(moment: pendulum.DateTime) -> str:
    """DateTime64 literal pinned to UTC, so the window means the same thing
    whatever the server's timezone is."""
    return f"toDateTime64('{moment.in_timezone('UTC').format('YYYY-MM-DD HH:mm:ss.SSSSSS')}', 6, 'UTC')"


class SliceFilter:
    """
    Renders the WHERE clauses that restrict each bronze table to one slice,
    with the slice's tenant, id bounds and the run's window as literals.

    The driving table: id range (primary key) plus `IN in_window_ids` for exact
    window semantics. Every other table is restricted to the KEY VALUES of the
    slice's driving rows (via `slice_column`) or to the driving id range itself
    (via `keyed_by_driving_id`, for tables re-keyed by the driving id through a
    projection), so each join side is a point lookup on its primary key or
    projection, never a scan. Entity files subclass this and add one method per
    join side, named after the table it filters.
    """

    driving_alias = "row"   # entity subclasses set the alias their joined_rows_sql uses

    def __init__(self, driving_table: str, slice_: Slice, window: TimeWindow):
        self.driving_table = table(driving_table)
        self.tenant_id = slice_.tenant_id
        self.tenant = sql_string_literal(slice_.tenant_id)
        self._first_id = sql_string_literal(slice_.first_id)
        self._end_id = sql_string_literal(slice_.end_id) if slice_.end_id is not None else None
        self._window_start = sql_datetime_literal(window.start)
        self._window_end = sql_datetime_literal(window.end)

    def _upper_bound(self, column: str) -> str:
        return f" AND {column} < {self._end_id}" if self._end_id is not None else ""

    def id_range(self, column: str) -> str:
        """`<column> >= first AND <column> < end` (open-ended for the last slice)."""
        return f"{column} >= {self._first_id}{self._upper_bound(column)}"

    @property
    def in_window_ids(self) -> str:
        """Subquery of this slice's driving ids; touches only prj_ingested_at columns."""
        return (
            f"(SELECT id FROM {self.driving_table} "
            f"WHERE _ingested_at >= {self._window_start} AND _ingested_at < {self._window_end} "
            f"AND tenant_id = {self.tenant} AND {self.id_range('id')} "
            f"GROUP BY id)"
        )

    def driving(self) -> str:
        """WHERE for the driving table under `driving_alias`."""
        a = self.driving_alias
        return (
            f"{a}.tenant_id = {self.tenant} AND {self.id_range(f'{a}.id')} "
            f"AND {a}.id IN {self.in_window_ids}"
        )

    def slice_column(self, column: str) -> str:
        """Distinct non-empty values of one driving-table column over the
        slice's rows. No FINAL: a superset of key values is harmless in a
        filter, and the primary-key range keeps the read cheap."""
        return (
            f"(SELECT DISTINCT {column} FROM {self.driving_table} "
            f"WHERE tenant_id = {self.tenant} AND {self.id_range('id')} "
            f"AND id IN {self.in_window_ids} AND {column} != '')"
        )

    def keyed_by_driving_id(self, key_column: str) -> str:
        """For a table re-keyed by the driving id through a projection
        (e.g. stg_task_resource.task_id): prefix range on (tenant_id, key)."""
        return (
            f"tenant_id = {self.tenant} AND {self.id_range(key_column)} "
            f"AND {key_column} IN {self.in_window_ids}"
        )

    def own_key_in(self, key_column: str, values_subquery: str) -> str:
        """`tenant_id = T AND <key_column> IN (<subquery>)`: a point lookup on
        a table's own primary key or on a projection's key."""
        return f"tenant_id = {self.tenant} AND {key_column} IN {values_subquery}"


# =============================================================================
# Reusable join shapes
# =============================================================================

def one_row_per_key_sql(
    bronze_table: str, where: str, key_column: str, value_columns: list[str],
    version_column: str = "last_modified_time", has_is_deleted: bool = True, pick: str = "newest",
) -> str:
    """
    Subquery shape for a join side looked up by a NON-primary key through a
    projection (client_reference_id, register_id, parent_id, ...): per RMT key
    (tenant_id, id) take the newest version (argMax on the version column;
    with has_is_deleted, drop rows whose newest version is deleted), then
    collapse to ONE row per <key_column>, carrying each value column as
    latest_<column>. pick = "newest": the newest version wins (ties by id) --
    the deterministic equivalent of the Python DAGs' FINAL joins, which fan out
    when a key has several rows. pick = "lowest_id": the lowest row id wins --
    the Python DAGs' `ORDER BY id ASC LIMIT 1 BY <key>` bridges.
    """
    winner = "argMin(latest_{column}, id)" if pick == "lowest_id" else "argMax(latest_{column}, (version, id))"
    outer_values = ",\n            ".join(
        f"{winner.format(column=column)} AS latest_{column}" for column in value_columns
    )
    inner_values = ",\n                ".join(
        f"argMax({column}, {version_column}) AS latest_{column}" for column in value_columns
    )
    deleted_agg = f"argMax(is_deleted, {version_column}) AS latest_is_deleted," if has_is_deleted else ""
    having = "\n            HAVING latest_is_deleted = false" if has_is_deleted else ""
    return f"""
        SELECT
            tenant_id,
            {key_column},
            {outer_values}
        FROM
        (
            SELECT
                tenant_id,
                {key_column},
                id,
                {inner_values},
                {deleted_agg}
                max({version_column})                AS version
            FROM {table(bronze_table)}
            WHERE {where}
            GROUP BY tenant_id, {key_column}, id{having}
        )
        GROUP BY tenant_id, {key_column}"""


def staff_bridge_join_sql(where: str, on_user_expr: str, on_tenant_expr: str, alias: str = "staff") -> str:
    """
    LEFT JOIN stg_project_staff AS <alias> ON <alias>.staff_id = <on_user_expr>:
    the user -> project bridge. Per staff row (tenant_id, staff_id, id) take
    the newest version (argMax on last_modified_time) and drop it if deleted;
    then per user keep the project of the LOWEST staff row id -- the Python
    DAGs' `ORDER BY ps.id ASC LIMIT 1 BY ps.staff_id`, itself
    ProjectStaffRepository's default ordering. Reads through 17's prj_by_staff
    (`where` must be `tenant_id = T AND staff_id IN (...)`).
    """
    return f"""
    LEFT JOIN
    (
        SELECT
            tenant_id,
            staff_id,
            argMin(latest_project_id, id) AS first_project_id
        FROM
        (
            SELECT
                tenant_id,
                staff_id,
                id,
                argMax(project_id, last_modified_time) AS latest_project_id,
                argMax(is_deleted, last_modified_time) AS latest_is_deleted
            FROM {table('stg_project_staff')}
            WHERE {where}
            GROUP BY tenant_id, staff_id, id
            HAVING latest_is_deleted = false
        )
        GROUP BY tenant_id, staff_id
    ) AS {alias}
        ON {alias}.staff_id = {on_user_expr} AND {alias}.tenant_id = {on_tenant_expr}"""


def staff_projects_subquery_sql(staff_where: str) -> str:
    """The project ids a slice's staff rows point at (raw superset; the join
    itself picks) -- the filter for the stg_project FINAL side of a bridge."""
    return (
        f"(SELECT DISTINCT project_id FROM {table('stg_project_staff')} "
        f"WHERE {staff_where} AND project_id != '')"
    )


PROJECT_COLUMNS = "id, tenant_id, additional_details, project_type, project_type_id, name, reference_id"


def project_join_sql(where: str, on_id_expr: str, on_tenant_expr: str, alias: str = "project",
                     columns: str = PROJECT_COLUMNS) -> str:
    """LEFT JOIN stg_project AS <alias> ON <alias>.id = <on_id_expr> (own PK, FINAL)."""
    return f"""
    LEFT JOIN
    (
        SELECT {columns}
        FROM {table('stg_project')} FINAL
        WHERE {where}
    ) AS {alias}
        ON {alias}.id = {on_id_expr} AND {alias}.tenant_id = {on_tenant_expr}"""


def project_address_join_sql(where: str, on_project_expr: str, on_tenant_expr: str,
                             alias: str = "project_address") -> str:
    """LEFT JOIN stg_project_address AS <alias> ON project_id, one boundary per
    project (newest by _ingested_at, its RMT version column, then id), through
    14's prj_by_project (`where` = `tenant_id = T AND project_id IN (...)`)."""
    return f"""
    LEFT JOIN
    (
        SELECT tenant_id, project_id, argMax(boundary, (_ingested_at, id)) AS latest_boundary
        FROM {table('stg_project_address')}
        WHERE {where}
        GROUP BY tenant_id, project_id
    ) AS {alias}
        ON {alias}.project_id = {on_project_expr} AND {alias}.tenant_id = {on_tenant_expr}"""


def own_key_join_sql(bronze_table: str, where: str, alias: str, columns: str, on: list[tuple[str, str]]) -> str:
    """LEFT JOIN <bronze_table> FINAL AS <alias> on its OWN primary key
    (`on` pairs the alias's columns with the driving expressions). FINAL is
    fine here: the lookup is on the table's own (tenant_id, id)."""
    conditions = " AND ".join(f"{alias}.{column} = {expression}" for column, expression in on)
    return f"""
    LEFT JOIN
    (
        SELECT {columns}
        FROM {table(bronze_table)} FINAL
        WHERE {where}
    ) AS {alias}
        ON {conditions}"""


def parse_boundary_code_sql(json_expr: str) -> str:
    """= egov_api_utils.parse_boundary_code: an object's `boundaryCode` value,
    a bare JSON string itself, else ''."""
    return (
        f"multiIf(JSONType({json_expr}) = 'Object', JSONExtractString({json_expr}, 'boundaryCode'), "
        f"JSONType({json_expr}) = 'String', JSONExtractString({json_expr}), '')"
    )


def address_join_sql(where: str, on_id_expr: str, on_tenant_expr: str, alias: str = "address",
                     columns: str = "id, tenant_id, latitude, longitude, location_accuracy, type, locality_code") -> str:
    """LEFT JOIN stg_address AS <alias> ON <alias>.id = <on_id_expr> (own PK, FINAL)."""
    return f"""
    LEFT JOIN
    (
        SELECT {columns}
        FROM {table('stg_address')} FINAL
        WHERE {where}
    ) AS {alias}
        ON {alias}.id = {on_id_expr} AND {alias}.tenant_id = {on_tenant_expr}"""


# =============================================================================
# JSON text builders (reproduce json.dumps)
# =============================================================================

def json_string_sql(text_expr: str) -> str:
    """JSON string token for a String expression: quotes and escapes `\\`, `"`
    and the control characters json.dumps escapes by short name."""
    escaped = text_expr
    for raw, escape in (("\\", "\\\\"), ('"', '\\"'), ("\n", "\\n"), ("\r", "\\r"),
                        ("\t", "\\t"), ("\b", "\\b"), ("\f", "\\f")):
        escaped = f"replaceAll({escaped}, {sql_string_literal(raw)}, {sql_string_literal(escape)})"
    return f"concat('\"', {escaped}, '\"')"


def json_value_sql(field_expr: str) -> str:
    """JSON token for one `{"key": ..., "value": ...}` field object's value, as
    Python would re-serialise it: a missing value is null, a string is
    re-escaped, anything else (number, bool, null, object, array) keeps its
    source text (= parse_additional_fields' verbatim copy)."""
    string_token = json_string_sql(f"JSONExtractString({field_expr}, 'value')")
    return (
        f"multiIf(NOT JSONHas({field_expr}, 'value'), 'null', "
        f"JSONType({field_expr}, 'value') = 'String', {string_token}, "
        f"JSONExtractRaw({field_expr}, 'value'))"
    )


def json_int_or_zero_sql(field_expr: str) -> str:
    """Field value coerced like Python's int(value) with a 0 fallback: strings
    must be whole numbers, floats truncate, bools become 1/0, else 0."""
    return (
        f"multiIf(NOT JSONHas({field_expr}, 'value'), '0', "
        f"JSONType({field_expr}, 'value') = 'String', toString(toInt64OrZero(JSONExtractString({field_expr}, 'value'))), "
        f"JSONType({field_expr}, 'value') IN ('Int64', 'UInt64', 'Double'), "
        f"toString(toInt64(trunc(JSONExtractFloat({field_expr}, 'value')))), "
        f"JSONType({field_expr}, 'value') = 'Bool', if(JSONExtractBool({field_expr}, 'value'), '1', '0'), "
        f"'0')"
    )


def json_int_or_null_sql(field_expr: str) -> str:
    """Field value coerced like Python's int(value) with a None fallback."""
    return (
        f"multiIf(NOT JSONHas({field_expr}, 'value'), 'null', "
        f"JSONType({field_expr}, 'value') = 'String', "
        f"ifNull(toString(toInt64OrNull(JSONExtractString({field_expr}, 'value'))), 'null'), "
        f"JSONType({field_expr}, 'value') IN ('Int64', 'UInt64', 'Double'), "
        f"toString(toInt64(trunc(JSONExtractFloat({field_expr}, 'value')))), "
        f"JSONType({field_expr}, 'value') = 'Bool', if(JSONExtractBool({field_expr}, 'value'), '1', '0'), "
        f"'null')"
    )


def json_double_or_null_sql(field_expr: str) -> str:
    """Field value coerced like Python's float(value) with a None fallback,
    printed as Python's float repr (stock / stock_reconciliation quantity keys)."""
    s = f"JSONExtractString({field_expr}, 'value')"
    return (
        f"multiIf(NOT JSONHas({field_expr}, 'value'), 'null', "
        f"JSONType({field_expr}, 'value') = 'String', ifNull({python_float_text_sql(f'toFloat64OrNull({s})')}, 'null'), "
        f"JSONType({field_expr}, 'value') IN ('Int64', 'UInt64', 'Double'), "
        f"{python_float_text_sql(f'JSONExtractFloat({field_expr}, ' + chr(39) + 'value' + chr(39) + ')')}, "
        f"JSONType({field_expr}, 'value') = 'Bool', if(JSONExtractBool({field_expr}, 'value'), '1.0', '0.0'), "
        f"'null')"
    )


def python_float_text_sql(float_expr: str) -> str:
    """repr() of a Python float for a Float64 expression: integral values get
    a trailing `.0` (ClickHouse prints them without)."""
    return (
        f"if({float_expr} = trunc({float_expr}) AND abs({float_expr}) < 1e16, "
        f"concat(toString(toInt64({float_expr})), '.0'), toString({float_expr}))"
    )


def field_pairs_sql(blob_expr: str) -> str:
    """Array of (key, json value) for a `{"fields": [{"key", "value"}, ...]}`
    blob, in array order, one entry per field (= parse_additional_fields
    before its dict collapses repeated keys)."""
    return (
        f"arrayMap(f -> (JSONExtractString(f, 'key'), {json_value_sql('f')}), "
        f"arrayFilter(f -> JSONHas(f, 'key'), JSONExtractArrayRaw({blob_expr}, 'fields')))"
    )


def json_object_sql(pairs_expr: str, drop_null_values: bool = False) -> str:
    """Renders an Array(Tuple(key String, json_value String)) with Python dict
    semantics: keys in first-occurrence order, the LAST value for a repeated
    key wins (dict insert / update), `{"k": v, ...}` text. With
    drop_null_values, keys whose (winning) value is null are left out
    (= `{k: v for k, v in d.items() if v is not None}`)."""
    keys = f"arrayDistinct(arrayMap(p -> p.1, {pairs_expr}))"
    if drop_null_values:
        keys = f"arrayFilter(k -> arrayLast(p -> p.1 = k, {pairs_expr}).2 != 'null', {keys})"
    return (
        f"concat('{{', arrayStringConcat(arrayMap(k -> concat({json_string_sql('k')}, ': ', "
        f"arrayLast(p -> p.1 = k, {pairs_expr}).2), {keys}), ', '), '}}')"
    )


def json_loads_or_string_sql(field_expr: str) -> str:
    """
    JSON token for a field value after Python's `try: json.loads(value)
    except: value` (UserActionTransformationService.convertToJsonNode and
    kin): a string holding a JSON scalar becomes that scalar (numbers keep
    Python's float repr, so "0.0" -> 0.0 and "1e2" -> 100.0), a string
    holding a JSON object/array is kept as its source text (Python would
    re-serialise it; only whitespace can differ), any other string stays a
    string, and a non-string value is passed through as-is.
    """
    s = f"JSONExtractString({field_expr}, 'value')"
    return (
        f"multiIf(NOT JSONHas({field_expr}, 'value'), 'null', "
        f"JSONType({field_expr}, 'value') != 'String', JSONExtractRaw({field_expr}, 'value'), "
        f"{s} IN ('true', 'false', 'null'), {s}, "
        f"match({s}, '^-?(0|[1-9][0-9]*)$'), {s}, "
        f"match({s}, '^-?(0|[1-9][0-9]*)(\\\\.[0-9]+)?([eE][+-]?[0-9]+)?$'), {python_float_text_sql(f'toFloat64OrZero({s})')}, "
        f"(startsWith({s}, '{{') OR startsWith({s}, '[') OR startsWith({s}, '\"')) AND isValidJSON({s}), {s}, "
        f"{json_string_sql(s)})"
    )


def json_string_array_sql(array_expr: str) -> str:
    """`["a", "b"]` for an Array(String) expression (json.dumps of a list of str)."""
    return f"concat('[', arrayStringConcat(arrayMap(x -> concat('\"', x, '\"'), {array_expr}), ', '), ']')"


# =============================================================================
# Reusable helper-expression groups (WITH clause entries)
# =============================================================================

def cycle_index_expressions(project_details_expr: str, event_ms_expr: str) -> list[tuple[str, str]]:
    """
    cycleIndex as "%02d" text or null (= get_project_cycles + fetch_cycle_index
    + the household/hf_referral/referral/service_task formatting). Yields the
    WITH entries project_cycles, event_ms, cycle_hit, cycle_index_json.
    """
    return [
        ("project_cycles",
         "arrayMap(c -> (assumeNotNull(c.1), assumeNotNull(c.2), assumeNotNull(c.3)), "
         "arrayFilter(c -> c.1 IS NOT NULL AND c.2 IS NOT NULL AND c.3 IS NOT NULL, "
         "arrayMap(c -> (toInt64OrNull(JSONExtractString(c, 'id')), toInt64OrNull(JSONExtractString(c, 'startDate')), "
         "toInt64OrNull(JSONExtractString(c, 'endDate'))), "
         f"JSONExtractArrayRaw({project_details_expr}, 'projectType', 'cycles'))))"),
        ("event_ms", event_ms_expr),
        # index of the cycle containing the time, or the cycle whose gap before the next one contains it
        ("cycle_hit",
         "if(event_ms = 0 OR empty(project_cycles), toUInt64(0), arrayFirstIndex(i -> "
         "(project_cycles[i].2 <= event_ms AND event_ms <= project_cycles[i].3) OR "
         "(i < length(project_cycles) AND project_cycles[i].3 < event_ms AND event_ms < project_cycles[i + 1].2), "
         "arrayEnumerate(project_cycles)))"),
        ("cycle_index_json",
         "if(cycle_hit > 0, concat('\"', leftPad(toString(project_cycles[cycle_hit].1), 2, '0'), '\"'), 'null')"),
    ]


def age_expressions(dob_expr: str) -> list[tuple[str, str]]:
    """Age in whole months (= calculate_age_in_months) and the Date32 -> epoch
    ms conversion (= _date_to_epoch_ms); now() once per statement. A NULL or
    join-default date must be gated by the caller on the individual matching."""
    return [
        ("today_utc", "toDate(now('UTC'))"),
        ("age_months",
         f"ifNull((toYear(today_utc) - toYear({dob_expr})) * 12 "
         f"+ (toMonth(today_utc) - toMonth({dob_expr})), toInt64(0))"),
        ("date_of_birth_ms", f"toInt64(ifNull(toInt32({dob_expr}), toInt32(0))) * 86400000"),
    ]


# =============================================================================
# API lookups: keys query -> Python resolver -> inline VALUES table -> LEFT JOIN
# =============================================================================

@dataclass(frozen=True)
class Lookup:
    """
    One external enrichment embedded per slice. `keys_sql(filter)` returns the
    distinct key tuples needing resolution (columns = key_columns, in order);
    `resolve(key_rows)` calls the eGov helper and returns VALUES rows with
    columns `columns`; `join_on` pairs each VALUES column used as a join key
    with the `joined.` expression it matches. An unresolved key row is simply
    absent, which the LEFT JOIN turns into '' (= the Python DAGs' defaults).
    """
    alias: str
    columns: list[str]
    keys_sql: Callable[[SliceFilter], str]
    resolve: Callable[[list[tuple]], list[list[str]]]
    join_on: list[tuple[str, str]]


def _resolve_boundary_rows(key_rows: list[tuple]) -> list[list[str]]:
    lookup_keys: dict[tuple[str, str], set[str]] = {}
    for tenant_id, hierarchy_type, boundary_code in key_rows:
        lookup_keys.setdefault((tenant_id, hierarchy_type), set()).add(boundary_code)
    resolved = resolve_boundary_levels(lookup_keys)  # unchanged boundary-service path
    # A code the API could not resolve carries nine empty levels, the same as an unmatched LEFT JOIN.
    return [
        [tenant_id, hierarchy_type, boundary_code, *[levels.get(column, "") for column in LEVEL_COLUMNS]]
        for (tenant_id, hierarchy_type, boundary_code), levels in resolved.items()
    ]


def boundary_lookup(keys_sql: Callable[[SliceFilter], str], code_expr: str,
                    hierarchy_expr: str = "joined.hierarchy_type", alias: str = "levels") -> Lookup:
    """Boundary levels keyed (tenant_id, hierarchy_type, boundary_code); `keys_sql`
    must select exactly those three columns, non-empty (= the entity's
    _get_boundary_lookup_key)."""
    return Lookup(
        alias=alias, columns=BOUNDARY_LEVELS_COLUMNS, keys_sql=keys_sql, resolve=_resolve_boundary_rows,
        join_on=[("tenant_id", "joined.tenant_id"), ("hierarchy_type", hierarchy_expr), ("boundary_code", code_expr)],
    )


def _resolve_user_rows(key_rows: list[tuple]) -> list[list[str]]:
    resolved = resolve_user_info({(tenant_id, user_id) for tenant_id, user_id in key_rows})  # user-service (+MDMS)
    # None -> '' exactly as the Python DAGs' `info.get(...) or ""`; a not-found
    # user already carries USERNAME = its uuid (egov_api_utils._not_found_user_info).
    return [
        [tenant_id, user_id, str(info.get("USERNAME") or ""), str(info.get("NAME") or ""),
         str(info.get("ROLE") or ""), str(info.get("CITY") or "")]
        for (tenant_id, user_id), info in resolved.items()
    ]


def user_lookup(keys_sql: Callable[[SliceFilter], str], user_expr: str, alias: str = "users") -> Lookup:
    """User display info keyed (tenant_id, user_id); `keys_sql` selects those two
    columns, non-empty (= the entity's _get_user_lookup_key). An entity with
    several user columns declares one lookup per column (distinct aliases)."""
    return Lookup(
        alias=alias, columns=USER_INFO_COLUMNS, keys_sql=keys_sql, resolve=_resolve_user_rows,
        join_on=[("tenant_id", "joined.tenant_id"), ("user_id", user_expr)],
    )


WORKFLOW_COLUMNS = ["tenant_id", "business_id", "wf_status", "process_instance", "wf_status_info"]


def _resolve_workflow_rows(key_rows: list[tuple]) -> list[list[str]]:
    from egov_api_utils import resolve_workflow_summaries  # workflow-service; bill / bill_detail only
    import json
    resolved = resolve_workflow_summaries({(tenant_id, business_id) for tenant_id, business_id in key_rows})
    rows = []
    for (tenant_id, business_id), summary in resolved.items():
        if not summary:
            continue  # {} -> the LEFT JOIN misses: '' / '' / '', as the Python DAGs' defaults
        latest_instance = summary.get("_latestInstance")
        without_instance = {k: v for k, v in summary.items() if k != "_latestInstance"}
        rows.append([
            tenant_id, business_id, str(summary.get("currentStatus") or ""),
            json.dumps(latest_instance) if latest_instance else "", json.dumps(without_instance),
        ])
    return rows


def workflow_lookup(keys_sql: Callable[[SliceFilter], str], business_id_expr: str, alias: str = "workflow") -> Lookup:
    """Workflow summary keyed (tenant_id, business_id) -- the JSON columns are
    rendered in Python exactly as the Python DAGs do (json.dumps) and embedded
    as strings."""
    return Lookup(
        alias=alias, columns=WORKFLOW_COLUMNS, keys_sql=keys_sql, resolve=_resolve_workflow_rows,
        join_on=[("tenant_id", "joined.tenant_id"), ("business_id", business_id_expr)],
    )


def values_table_sql(columns: list[str], rows: list[list[str]]) -> str:
    """Inline VALUES table with the given String columns; a zero-row SELECT
    with the same columns when there is nothing to embed."""
    if not rows:
        empty_row = ", ".join(f"'' AS {column}" for column in columns)
        return f"SELECT {empty_row} WHERE 0"
    structure = ", ".join(f"{column} String" for column in columns)
    rendered = ", ".join("(" + ", ".join(sql_string_literal(v) for v in row) + ")" for row in rows)
    return f"SELECT * FROM VALUES('{structure}', {rendered})"


def lookup_map_sql(lookup: Lookup, values_sql: str, tenant_literal: str) -> str:
    """A 3-column lookup (tenant_id, key, value) as a Map(key -> value) scalar
    for the slice's tenant, for entities that need per-array-element lookups
    (attendance_register's attendees_info)."""
    key_column, value_column = lookup.columns[1], lookup.columns[2]
    return (
        f"(SELECT mapFromArrays(groupArray({key_column}), groupArray({value_column})) "
        f"FROM ({values_sql}) WHERE tenant_id = {tenant_literal})"
    )


def _resolve_user_info_json_rows(key_rows: list[tuple]) -> list[list[str]]:
    import json
    resolved = resolve_user_info({(tenant_id, user_id) for tenant_id, user_id in key_rows})
    # the whole info dict as json.dumps text (USERNAME, NAME, ROLE, ID, CITY; nulls kept)
    return [[tenant_id, user_id, json.dumps(info)] for (tenant_id, user_id), info in resolved.items()]


def user_info_json_lookup(keys_sql: Callable[[SliceFilter], str], alias: str) -> Lookup:
    """User info keyed (tenant_id, user_id) with the whole info dict as JSON text,
    exposed as a Map alias (see lookup_map_sql) rather than a join."""
    return Lookup(
        alias=alias, columns=["tenant_id", "user_id", "info_json"], keys_sql=keys_sql,
        resolve=_resolve_user_info_json_rows, join_on=[],
    )


def project_dates_expressions(start_ms_expr: str, end_ms_expr: str) -> list[tuple[str, str]]:
    """= get_project_dates_list: one 'YYYY-MM-DD' per day from start_date while
    ts <= end_date + DAY (the inclusive off-by-one kept), empty if either bound
    is 0, capped at MAX_TASK_DATES. Yields day_ms, max_task_dates,
    task_date_count, task_date_list."""
    from egov_api_utils import DAY_MILLIS, MAX_TASK_DATES
    return [
        ("day_ms", f"toInt64({DAY_MILLIS})"),
        ("max_task_dates", f"toInt64({MAX_TASK_DATES})"),
        ("task_date_count",
         f"if({start_ms_expr} = 0 OR {end_ms_expr} = 0 OR {end_ms_expr} + day_ms < {start_ms_expr}, toInt64(0), "
         f"least(intDiv({end_ms_expr} + day_ms - {start_ms_expr}, day_ms) + 1, max_task_dates))"),
        ("task_date_list",
         f"arrayMap(i -> toString(toDate32(fromUnixTimestamp64Milli({start_ms_expr} + toInt64(i) * day_ms, 'UTC'))), "
         "range(toUInt32(task_date_count)))"),
    ]


def project_cycle_dose_expressions(project_details_expr: str) -> list[tuple[str, str]]:
    """= build_project_additional_details: every cycle id and the FIRST cycle's
    delivery ids, each prefixed with a literal '0'. Yields cycles, cycle_index,
    dose_index, cycle_dose_json ('' when the project has no cycles)."""
    return [
        ("cycles", f"JSONExtractArrayRaw({project_details_expr}, 'projectType', 'cycles')"),
        ("cycle_index",
         "arrayMap(c -> concat('0', JSONExtractString(c, 'id')), arrayFilter(c -> JSONHas(c, 'id'), cycles))"),
        ("dose_index",
         "arrayMap(d -> concat('0', JSONExtractString(d, 'id')), "
         "arrayFilter(d -> JSONHas(d, 'id'), JSONExtractArrayRaw(cycles[1], 'deliveries')))"),
        ("cycle_dose_json",
         f"if(length(cycles) = 0, '', concat('{{\"doseIndex\": ', {json_string_array_sql('dose_index')}, "
         f"', \"cycleIndex\": ', {json_string_array_sql('cycle_index')}, '}}'))"),
    ]


def lookup_join_sql(lookup: Lookup, values_sql: str) -> str:
    """LEFT JOIN the lookup's VALUES table AS <alias> ON its join_on pairs."""
    conditions = "\n   AND ".join(f"{lookup.alias}.{column} = {expression}" for column, expression in lookup.join_on)
    return f"""
LEFT JOIN
(
    {values_sql}
) AS {lookup.alias}
    ON {conditions}"""


# =============================================================================
# The entity contract
# =============================================================================

@dataclass(frozen=True)
class SilverTarget:
    """One INSERT ... SELECT per slice into `<silver_table>_sql_test`."""
    silver_table: str                                            # unqualified, e.g. "household_entity"
    joined_rows_sql: Callable[[SliceFilter], str]                # one row per output row, aliased `joined`
    helper_expressions: Callable[[SliceFilter], list[tuple[str, str]]]  # WITH clause entries
    silver_columns: list[tuple[str, str]]                        # (silver column, expression), table order
    lookups: tuple[Lookup, ...] = ()                             # joined after `joined`, in this order

    @property
    def test_table(self) -> str:
        return f"{self.silver_table}_sql_test"


@dataclass(frozen=True)
class EntitySpec:
    """What an entity file declares. `entity` is the orchestrator's entity
    name (dag_id = <entity>_transformation), e.g. `household_test`."""
    entity: str
    python_entity: str                 # the Python DAG's entity name, e.g. `household` (tags, Variable name)
    driving_table: str                 # unqualified bronze table the slices are planned over
    slice_filter_class: type
    target: SilverTarget
    extra_targets: tuple[SilverTarget, ...] = ()   # a second silver table written per slice (service_task)
    default_slice_size: int = DEFAULT_SLICE_SIZE

    @property
    def dag_id(self) -> str:
        return f"{self.entity}_transformation"

    @property
    def slice_size_variable(self) -> str:
        return f"{self.python_entity}_sql_test_slice_size"

    @property
    def targets(self) -> tuple[SilverTarget, ...]:
        return (self.target, *self.extra_targets)


# =============================================================================
# Rendering
# =============================================================================

def insert_slice_sql(spec: EntitySpec, target: SilverTarget, slice_filter: SliceFilter,
                     lookup_values: dict[str, str]) -> str:
    """The whole per-slice statement: INSERT INTO test table <-
    flatten(joined bronze rows LEFT JOIN each lookup's VALUES table)."""
    insert_columns = ",\n    ".join(column for column, _ in target.silver_columns)
    # map-style lookups (no join_on) become WITH entries: Map(key -> value) for the slice's tenant
    map_entries = [
        (lookup.alias, lookup_map_sql(lookup, lookup_values[lookup.alias], slice_filter.tenant))
        for lookup in target.lookups if not lookup.join_on
    ]
    helpers = map_entries + target.helper_expressions(slice_filter)
    with_clause = ("WITH\n    " + ",\n    ".join(f"{expression} AS {name}" for name, expression in helpers) + "\n") if helpers else ""
    # Output columns are positional (INSERT maps by position); the comment line
    # above each expression names the silver column it feeds.
    select_list = ",\n".join(f"    -- {column}\n    {expression}" for column, expression in target.silver_columns)
    lookup_joins = "".join(
        lookup_join_sql(lookup, lookup_values[lookup.alias]) for lookup in target.lookups if lookup.join_on
    )
    sql = f"""
INSERT INTO {table(target.test_table)}
(
    {insert_columns}
)
{with_clause}SELECT
{select_list}
FROM
({target.joined_rows_sql(slice_filter)}
) AS joined{lookup_joins}
"""
    size = len(sql.encode())
    if size > MAX_INSERT_SQL_BYTES:
        raise AirflowFailException(
            f"{spec.dag_id}: rendered slice statement is {size} bytes, above MAX_INSERT_SQL_BYTES="
            f"{MAX_INSERT_SQL_BYTES}; lower the {spec.slice_size_variable} Variable."
        )
    return sql


def render_lookup_values(spec: EntitySpec, lookup: Lookup, rows: list[list[str]]) -> str:
    if len(rows) > MAX_INLINE_ROWS_PER_LOOKUP:
        raise AirflowFailException(
            f"{spec.dag_id}: slice resolved {len(rows)} `{lookup.alias}` rows, above "
            f"MAX_INLINE_ROWS_PER_LOOKUP={MAX_INLINE_ROWS_PER_LOOKUP}; lower the {spec.slice_size_variable} Variable."
        )
    return values_table_sql(lookup.columns, rows)


# =============================================================================
# ClickHouse calls
# =============================================================================

def create_test_tables(spec: EntitySpec, client, truncate: bool) -> None:
    """Test targets mirror their silver tables (columns, engine, ORDER BY,
    indexes) so the two can be diffed."""
    for target in spec.targets:
        client.command(f"CREATE TABLE IF NOT EXISTS {table(target.test_table)} AS {table(target.silver_table)}")
        if truncate:
            client.command(f"TRUNCATE TABLE {table(target.test_table)}")
            log.info("%s: truncated %s", spec.dag_id, table(target.test_table))


def plan_slices(spec: EntitySpec, client, window: TimeWindow, slice_size: int) -> list[Slice]:
    """Splits the window into per-tenant id ranges of <= slice_size distinct
    driving rows. One query over the prj_ingested_at projection; modulo()
    instead of the percent operator because this is the one %-bound query."""
    result = client.query(
        f"""
        SELECT
            tenant_id,
            arraySort(groupArrayIf(id, modulo(rn - 1, %(slice_size)s) = 0)) AS slice_starts,
            count() AS row_count
        FROM
        (
            SELECT tenant_id, id, row_number() OVER (PARTITION BY tenant_id ORDER BY id) AS rn
            FROM
            (
                SELECT tenant_id, id
                FROM {table(spec.driving_table)}
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
        for position, first_id in enumerate(starts):
            next_start = starts[position + 1] if position + 1 < len(starts) else None
            slices.append(Slice(row["tenant_id"], first_id, next_start))
        log.info("%s: tenant %s has %d %s rows in window -> %d slices",
                 spec.dag_id, row["tenant_id"], row["row_count"], spec.driving_table, len(starts))
    return slices


def fetch_lookup_keys(client, lookup: Lookup, slice_filter: SliceFilter) -> list[tuple]:
    result = client.query(lookup.keys_sql(slice_filter), settings=CLICKHOUSE_LOOKUP_SETTINGS)
    return [tuple(row) for row in result.result_rows]


def count_rows(client, qualified_table: str) -> int:
    return client.query(f"SELECT count() FROM {qualified_table}").result_rows[0][0]


def process_slice(spec: EntitySpec, client, window: TimeWindow, slice_: Slice) -> SliceResult:
    """keys (ClickHouse) -> each lookup's resolver (Python + API) -> one insert per target (ClickHouse)."""
    started = time.monotonic()
    slice_filter = spec.slice_filter_class(spec.driving_table, slice_, window)

    # Every lookup of every target, resolved once per alias.
    lookups: dict[str, Lookup] = {}
    for target in spec.targets:
        for lookup in target.lookups:
            lookups.setdefault(lookup.alias, lookup)
    key_rows = {alias: fetch_lookup_keys(client, lookup, slice_filter) for alias, lookup in lookups.items()}

    api_started = time.monotonic()
    value_rows = {alias: lookup.resolve(key_rows[alias]) for alias, lookup in lookups.items()}
    api_seconds = time.monotonic() - api_started
    lookup_values = {alias: render_lookup_values(spec, lookups[alias], rows) for alias, rows in value_rows.items()}

    insert_started = time.monotonic()
    written_rows = 0
    for target in spec.targets:
        # rows written = the test table's row-count delta (this DAG is the table's only
        # writer, and the delta works on every clickhouse-connect version, unlike QuerySummary)
        rows_before = count_rows(client, table(target.test_table))
        client.command(insert_slice_sql(spec, target, slice_filter, lookup_values), settings=CLICKHOUSE_INSERT_SETTINGS)
        written_rows += count_rows(client, table(target.test_table)) - rows_before
    insert_seconds = time.monotonic() - insert_started

    return SliceResult(
        resolved={alias: len(rows) for alias, rows in value_rows.items()},
        written_rows=written_rows,
        api_seconds=api_seconds,
        insert_seconds=insert_seconds,
        total_seconds=time.monotonic() - started,
    )


# =============================================================================
# DAG factory
# =============================================================================

def build_sql_test_dag(spec: EntitySpec):
    """Builds the two-task TaskFlow DAG (parse_time_window -> transform_bronze_to_silver_sql)
    for an EntitySpec; the entity module assigns the result to a global."""

    @dag(
        dag_id=spec.dag_id,
        description=(f"TEST: {spec.python_entity} bronze -> {spec.target.test_table} "
                     f"with the flatten done in ClickHouse."),
        schedule=None,
        start_date=pendulum.datetime(2024, 1, 1, tz="UTC"),
        catchup=False,
        max_active_runs=1,
        tags=["bronze-to-silver", spec.python_entity, "sql-test"],
    )
    def sql_test_dag():

        @task
        def parse_time_window(**context) -> dict:
            """Validates start_time/end_time (orchestrator conf shape) and the
            optional truncate_target flag."""
            conf = context["dag_run"].conf or {}
            start_time_raw = conf.get("start_time")
            end_time_raw = conf.get("end_time")

            if not start_time_raw or not end_time_raw:
                raise AirflowFailException(
                    f"{spec.dag_id} requires 'start_time' and 'end_time' in dag_run.conf; got conf={conf!r}. "
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
            slice_size = int(Variable.get(spec.slice_size_variable, default_var=spec.default_slice_size))
            set_database(Variable.get(DATABASE_VARIABLE, default_var=DEFAULT_DATABASE))

            client = get_clickhouse_client()
            create_test_tables(spec, client, window.truncate_target)

            planning_started = time.monotonic()
            slices = plan_slices(spec, client, window, slice_size)
            log.info(
                "%s: %d slices (slice_size=%d) for [%s, %s) in database %s, planned in %.2fs",
                spec.dag_id, len(slices), slice_size, window.start, window.end, _database,
                time.monotonic() - planning_started,
            )
            if not slices:
                return

            total_written = 0
            total_insert_seconds = 0.0
            total_api_seconds = 0.0
            for number, slice_ in enumerate(slices, start=1):
                result = process_slice(spec, client, window, slice_)
                total_written += result.written_rows
                total_insert_seconds += result.insert_seconds
                total_api_seconds += result.api_seconds
                resolved = ", ".join(f"{alias} {count}" for alias, count in result.resolved.items()) or "no lookups"
                log.info(
                    "%s slice %d/%d %s: resolved %s (api %.2fs), written_rows=%d (insert %.2fs), slice %.2fs",
                    spec.dag_id, number, len(slices), slice_.label, resolved,
                    result.api_seconds, result.written_rows, result.insert_seconds, result.total_seconds,
                )

            counts = "; ".join(
                f"{table(target.test_table)} row count (no FINAL)={count_rows(client, table(target.test_table))}"
                for target in spec.targets
            )
            log.info(
                "%s: done. written_rows=%d over %d slices; insert total %.1fs, api total %.1fs; %s",
                spec.dag_id, total_written, len(slices), total_insert_seconds, total_api_seconds, counts,
            )

        transform_bronze_to_silver_sql(parse_time_window())

    return sql_test_dag()
