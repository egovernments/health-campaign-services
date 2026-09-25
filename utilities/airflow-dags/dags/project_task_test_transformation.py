"""
project_task_test_transformation.py

TEST twin of project_task_transformation.py for the `project_task` entity.
Same bronze inputs, same boundary-service and user-service calls from Python,
same silver row semantics -- but the per-row flatten + write runs inside
ClickHouse as one INSERT ... SELECT per slice instead of in Python.

Writes to analytics.project_task_entity_sql_test (created AS
project_task_entity), never to project_task_entity, so the two can be diffed.
Triggered by the orchestrator when the entity name `project_task_test` is in
entity_transformation_order (dag_id = <entity>_transformation), or by hand.
Conf: {"start_time", "end_time"} as the orchestrator sends, plus optional
"truncate_target": true.

How a run works
    1. plan_slices      one query over stg_project_task's prj_ingested_at projection
                        splits the window into per-tenant id ranges of <= slice_size tasks
    2. per slice        a. fetch_boundary_keys  ClickHouse: distinct (tenant, hierarchy, code)
                        b. fetch_user_keys      ClickHouse: distinct (tenant, client_created_by)
                        c. resolve_boundary_levels + resolve_user_info  Python + APIs, unchanged
                        d. insert_slice  ClickHouse: bronze joins + flatten + write, with both API
                           results embedded as inline VALUES tables
    Nothing from a core service is stored in ClickHouse; the VALUES text lives
    only in that one query. Every join side is filtered to the slice, so RAM
    per query is bounded regardless of table size (the Python DAG's one
    6-table FINAL join reads 3.36 M rows and peaks at 2.43 GiB per chunk).

Sister DAG and design rationale: project_test_transformation.py and
airflow_dags/PROJECTION_COMPARISON_REPORT.md (section 7 onwards).

Conventions in this file (same as project_test_transformation.py)
    - Per-slice SQL is rendered fully in Python (tenant, id bounds, window and
      VALUES rows as explicit literals) and sent without driver-side parameter
      binding: clickhouse-connect 0.11 (the cluster image) does not bind
      %(name)s parameters in client.command(). Only plan_slices uses %(name)s
      binding, through client.query(), which both driver versions support.
    - Silver columns are declared once in SILVER_COLUMNS as (column, expression)
      pairs; both halves of the INSERT are generated from that list.
    - Projections used here are defined in ClickHouse_ddl/16_project_task_projections.sql
      (plus 14's prj_by_project on stg_project_address). A query with FINAL never
      uses a projection, so the re-keyed join sides are argMax subqueries.
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
    resolve_boundary_levels,
    resolve_user_info,
)

log = logging.getLogger(__name__)

DAG_ID = "project_task_test_transformation"

BRONZE_TASK_TABLE = "analytics.stg_project_task"
BRONZE_RESOURCE_TABLE = "analytics.stg_task_resource"
BRONZE_ADDRESS_TABLE = "analytics.stg_address"
BRONZE_PROJECT_TABLE = "analytics.stg_project"
BRONZE_PROJECT_ADDRESS_TABLE = "analytics.stg_project_address"
BRONZE_PRODUCT_VARIANT_TABLE = "analytics.stg_product_variant"
BRONZE_BENEFICIARY_TABLE = "analytics.stg_project_beneficiary"
BRONZE_HOUSEHOLD_TABLE = "analytics.stg_household"
BRONZE_INDIVIDUAL_TABLE = "analytics.stg_individual"
SILVER_TABLE = "analytics.project_task_entity"
TEST_SILVER_TABLE = "analytics.project_task_entity_sql_test"

SLICE_SIZE_VARIABLE = "project_task_sql_test_slice_size"
DEFAULT_SLICE_SIZE = 5000
# Two inline VALUES tables per statement (boundary levels, users). Guards keep
# the rendered statement under the 10 MB max_query_size set in clickhouse_utils.
MAX_INLINE_BOUNDARY_CODES = 15_000
MAX_INLINE_USERS = 15_000
MAX_INSERT_SQL_BYTES = 9_000_000

LEVEL_COLUMNS = [f"level_{ordinal}_code" for ordinal in BOUNDARY_LEVEL_ORDINALS]
BOUNDARY_LEVELS_COLUMNS = ["tenant_id", "hierarchy_type", "boundary_code", *LEVEL_COLUMNS]
USER_INFO_COLUMNS = ["tenant_id", "user_id", "user_name", "name_of_user", "role", "user_address"]

# Per-slice INSERT ... SELECT. All join sides are slice-filtered, so these are
# guards, not tuning: fail loudly rather than lean on the pod's memory limit.
# join_use_nulls = 0 is load-bearing: unmatched LEFT JOINs yield '' / 0 /
# false / 1970-01-01, which is exactly what the Python DAG's own join returned
# and what its _default_* helpers then passed through.
CLICKHOUSE_INSERT_SETTINGS = {
    "max_threads": 2,
    "max_insert_threads": 1,
    "max_block_size": 8192,
    "max_memory_usage": 3_000_000_000,
    "join_use_nulls": 0,
    "join_algorithm": "hash",
    "max_execution_time": 900,
}
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
    One unit of work: the in-window tasks of a single tenant whose id lies in
    [first_task_id, end_task_id). end_task_id is None for the last slice of a
    tenant. Single-tenant on purpose: every predicate is then a plain prefix
    range on the (tenant_id, ...) sort keys, and the API groupings are per
    tenant anyway.
    """
    tenant_id: str
    first_task_id: str
    end_task_id: str | None

    @property
    def label(self) -> str:
        end = self.end_task_id if self.end_task_id is not None else "end"
        return f"tenant={self.tenant_id} [{self.first_task_id}, {end})"


@dataclass(frozen=True)
class SliceResult:
    boundary_codes_resolved: int
    users_resolved: int
    written_rows: int
    api_seconds: float
    insert_seconds: float
    total_seconds: float


# =============================================================================
# SQL: where every silver column comes from
# =============================================================================
#
# One INSERT ... SELECT per slice. Sources, in the order they appear in the query:
#
#   source                        alias            keyed / joined on                                        feeds
#   ----------------------------  ---------------  -------------------------------------------------------  ---------------------------------------------
#   stg_project_task (FINAL)      task             tenant_id + id range of the slice                        task_id, status, client audit columns,
#                                                                                                           additional_details, synced_*, task_dates
#   stg_task_resource             resource         resource.task_id = task.id AND tenant                    id, client_reference_id, product_variant,
#                                                  (prj_by_task, argMax, is_deleted after argMax)            quantity, is_delivered, delivery_comments
#                                                  ONE SILVER ROW PER RESOURCE; placeholder row when none
#   stg_address (FINAL)           address          address.id = task.address_id AND tenant (own PK)         latitude, longitude, location_accuracy,
#                                                                                                           boundary_code, geo_point, boundary lookup
#   stg_project (FINAL)           project          project.id = task.project_id AND tenant (own PK)         hierarchy_type, beneficiaryType (delivered_to
#                                                                                                           + branch), cycles, project_* columns
#   stg_product_variant (FINAL)   variant          variant.id = resource.latest_product_variant_id          product_name (sku)
#                                                  AND tenant (own PK)
#   stg_project_address           project_address  project_address.project_id = project.id AND tenant       boundary lookup FALLBACK only
#                                                  (14's prj_by_project, one boundary per project)
#   stg_project_beneficiary       beneficiary      beneficiary.client_reference_id =                        household_id / individual_id, and the key
#                                                  task.project_beneficiary_client_reference_id AND tenant  into household / individual
#                                                  (prj_by_client_ref, ONE row per task-side ref)
#   stg_household                 household        household.client_reference_id = beneficiary ref          member_count   (HOUSEHOLD projects only)
#                                                  AND tenant (prj_by_client_ref, one row per ref)
#   stg_individual                individual       individual.client_reference_id = beneficiary ref         date_of_birth, gender, age (INDIVIDUAL only)
#                                                  AND tenant (prj_by_client_ref, one row per ref)
#   boundary-service response     levels           (tenant_id, hierarchy_type, lookup boundary code)        level_one_code .. level_nine_code
#                                                  inline VALUES, see boundary_levels_values_sql
#   user-service response         users            (tenant_id, client_created_by)                           user_name, name_of_user, role, user_address
#                                                  inline VALUES, see user_info_values_sql
#
# household AND individual are both joined in this one statement although a
# project is either HOUSEHOLD or INDIVIDUAL. CLAUDE.md's "no unconditional
# joins for mutually-exclusive lookups" rule targets joining whole branch
# tables onto every row; here each right side is a projection point lookup
# bounded to the slice's own beneficiary refs (a HOUSEHOLD campaign's refs
# simply find nothing in stg_individual), and the branch is chosen per row in
# the column expressions.

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
    Renders the WHERE clause that restricts each bronze table to one slice,
    with the slice's tenant, id bounds and the run's window as literals.

    Tasks: id range (primary key) plus `IN in_window_task_ids` for exact window
    semantics. Every other table is restricted to the KEY VALUES of the slice's
    tasks (address ids, project ids, beneficiary refs), so each join side is a
    point lookup on its primary key or projection, never a scan.
    """

    def __init__(self, slice_: Slice, window: TimeWindow):
        self.tenant = sql_string_literal(slice_.tenant_id)
        self._first_id = sql_string_literal(slice_.first_task_id)
        self._end_id = sql_string_literal(slice_.end_task_id) if slice_.end_task_id is not None else None
        self._window_start = sql_datetime_literal(window.start)
        self._window_end = sql_datetime_literal(window.end)

    def _upper_bound(self, column: str) -> str:
        return f" AND {column} < {self._end_id}" if self._end_id is not None else ""

    @property
    def in_window_task_ids(self) -> str:
        """Subquery of this slice's task ids; touches only prj_ingested_at columns."""
        return (
            f"(SELECT id FROM {BRONZE_TASK_TABLE} "
            f"WHERE _ingested_at >= {self._window_start} AND _ingested_at < {self._window_end} "
            f"AND tenant_id = {self.tenant} AND id >= {self._first_id}{self._upper_bound('id')} "
            f"GROUP BY id)"
        )

    def tasks(self) -> str:
        """WHERE for stg_project_task, aliased `task`."""
        return (
            f"task.tenant_id = {self.tenant} AND task.id >= {self._first_id}{self._upper_bound('task.id')} "
            f"AND task.id IN {self.in_window_task_ids}"
        )

    def slice_task_column(self, column: str) -> str:
        """Distinct non-empty values of one stg_project_task column over the
        slice's tasks. No FINAL: a superset of key values is harmless in a
        filter, and the primary-key range keeps the read cheap."""
        return (
            f"(SELECT DISTINCT {column} FROM {BRONZE_TASK_TABLE} "
            f"WHERE tenant_id = {self.tenant} AND id >= {self._first_id}{self._upper_bound('id')} "
            f"AND id IN {self.in_window_task_ids} AND {column} != '')"
        )

    def resources(self) -> str:
        """For stg_task_resource via prj_by_task: prefix range on (tenant_id, task_id)."""
        return (
            f"tenant_id = {self.tenant} AND task_id >= {self._first_id}{self._upper_bound('task_id')} "
            f"AND task_id IN {self.in_window_task_ids}"
        )

    def addresses(self) -> str:
        """For stg_address FINAL: own primary key (tenant_id, id)."""
        return f"tenant_id = {self.tenant} AND id IN {self.slice_task_column('address_id')}"

    def projects(self) -> str:
        """For stg_project FINAL: own primary key (tenant_id, id)."""
        return f"tenant_id = {self.tenant} AND id IN {self.slice_task_column('project_id')}"

    def project_addresses(self) -> str:
        """For stg_project_address via 14's prj_by_project (tenant_id, project_id)."""
        return f"tenant_id = {self.tenant} AND project_id IN {self.slice_task_column('project_id')}"

    def product_variants(self) -> str:
        """For stg_product_variant FINAL: the tenant's whole catalogue (hundreds of rows)."""
        return f"tenant_id = {self.tenant}"

    def beneficiaries(self) -> str:
        """For stg_project_beneficiary via prj_by_client_ref (tenant_id, client_reference_id)."""
        return (
            f"tenant_id = {self.tenant} AND client_reference_id IN "
            f"{self.slice_task_column('project_beneficiary_client_reference_id')}"
        )

    def beneficiary_targets(self) -> str:
        """For stg_household / stg_individual via prj_by_client_ref: the refs the
        slice's bridge rows point at (a raw superset; the join itself dedups)."""
        return (
            f"tenant_id = {self.tenant} AND client_reference_id IN "
            f"(SELECT DISTINCT beneficiary_client_reference_id FROM {BRONZE_BENEFICIARY_TABLE} "
            f"WHERE {self.beneficiaries()} AND beneficiary_client_reference_id != '')"
        )


# --- joins onto stg_project_task (alias `task`) ---------------------------------

def resource_join_sql(slice_filter: SliceFilter) -> str:
    """LEFT JOIN stg_task_resource AS resource ON (task_id, tenant_id). Latest
    version of each resource via argMax on last_modified_time (its RMT version
    column); a resource whose newest version is deleted is dropped. A task with
    N resources yields N silver rows; a task with none yields one placeholder row."""
    return f"""
    LEFT JOIN
    (
        SELECT
            tenant_id,
            task_id,
            id,
            argMax(product_variant_id,      last_modified_time) AS latest_product_variant_id,
            argMax(quantity,                last_modified_time) AS latest_quantity,
            argMax(is_delivered,            last_modified_time) AS latest_is_delivered,
            argMax(reason_if_not_delivered, last_modified_time) AS latest_reason_if_not_delivered,
            argMax(client_reference_id,     last_modified_time) AS latest_client_reference_id,
            argMax(is_deleted,              last_modified_time) AS latest_is_deleted
        FROM {BRONZE_RESOURCE_TABLE}
        WHERE {slice_filter.resources()}
        GROUP BY tenant_id, task_id, id
        HAVING latest_is_deleted = false
    ) AS resource
        ON resource.task_id = task.id AND resource.tenant_id = task.tenant_id"""


def address_join_sql(slice_filter: SliceFilter) -> str:
    """LEFT JOIN stg_address AS address ON address.id = task.address_id.
    FINAL is fine here: the lookup is on the table's own primary key."""
    return f"""
    LEFT JOIN
    (
        SELECT id, tenant_id, latitude, longitude, location_accuracy, locality_code
        FROM {BRONZE_ADDRESS_TABLE} FINAL
        WHERE {slice_filter.addresses()}
    ) AS address
        ON address.id = task.address_id AND address.tenant_id = task.tenant_id"""


def project_join_sql(slice_filter: SliceFilter) -> str:
    """LEFT JOIN stg_project AS project ON project.id = task.project_id (own PK, FINAL)."""
    return f"""
    LEFT JOIN
    (
        SELECT id, tenant_id, additional_details, project_type, project_type_id, name, reference_id
        FROM {BRONZE_PROJECT_TABLE} FINAL
        WHERE {slice_filter.projects()}
    ) AS project
        ON project.id = task.project_id AND project.tenant_id = task.tenant_id"""


def product_variant_join_sql(slice_filter: SliceFilter) -> str:
    """LEFT JOIN stg_product_variant AS variant ON the RESOURCE's variant id
    (a join, not a Map as in the project DAG: one variant per resource row).
    A placeholder row has no variant id, so product_name stays ''."""
    return f"""
    LEFT JOIN
    (
        SELECT id, tenant_id, sku
        FROM {BRONZE_PRODUCT_VARIANT_TABLE} FINAL
        WHERE {slice_filter.product_variants()}
    ) AS variant
        ON variant.id = resource.latest_product_variant_id AND variant.tenant_id = task.tenant_id"""


def project_address_join_sql(slice_filter: SliceFilter) -> str:
    """LEFT JOIN stg_project_address AS project_address ON project_id, used only
    as the boundary-lookup fallback. One boundary per project (newest by
    _ingested_at, its RMT version column, then id) -- the Python DAG's plain
    join would fan out if a project had several address rows; none do today."""
    return f"""
    LEFT JOIN
    (
        SELECT tenant_id, project_id, argMax(boundary, (_ingested_at, id)) AS latest_boundary
        FROM {BRONZE_PROJECT_ADDRESS_TABLE}
        WHERE {slice_filter.project_addresses()}
        GROUP BY tenant_id, project_id
    ) AS project_address
        ON project_address.project_id = project.id AND project_address.tenant_id = project.tenant_id"""


def _one_row_per_client_ref_sql(table: str, where: str, value_column: str, alias: str) -> str:
    """
    Subquery shape shared by the bridge, household and individual sides: per
    RMT key (tenant_id, id) take the newest version (argMax on
    last_modified_time, drop rows whose newest version is deleted), then
    collapse to ONE row per client_reference_id (newest version, ties by id).
    The Python DAG resolves duplicate refs as "last writer wins" in undefined
    result order; this is the deterministic equivalent.
    """
    return f"""
        SELECT
            tenant_id,
            client_reference_id,
            argMax(latest_{value_column}, (version, id)) AS {alias}
        FROM
        (
            SELECT
                tenant_id,
                client_reference_id,
                id,
                argMax({value_column}, last_modified_time) AS latest_{value_column},
                argMax(is_deleted,     last_modified_time) AS latest_is_deleted,
                max(last_modified_time)                    AS version
            FROM {table}
            WHERE {where}
            GROUP BY tenant_id, client_reference_id, id
            HAVING latest_is_deleted = false
        )
        GROUP BY tenant_id, client_reference_id"""


def beneficiary_join_sql(slice_filter: SliceFilter) -> str:
    """LEFT JOIN the bridge AS beneficiary ON its client_reference_id =
    task.project_beneficiary_client_reference_id; yields the household's or
    individual's client reference."""
    inner = _one_row_per_client_ref_sql(
        BRONZE_BENEFICIARY_TABLE, slice_filter.beneficiaries(),
        "beneficiary_client_reference_id", "latest_beneficiary_client_reference_id",
    )
    return f"""
    LEFT JOIN
    ({inner}
    ) AS beneficiary
        ON beneficiary.client_reference_id = task.project_beneficiary_client_reference_id
       AND beneficiary.tenant_id = task.tenant_id"""


def household_join_sql(slice_filter: SliceFilter) -> str:
    """LEFT JOIN stg_household AS household ON the beneficiary's reference."""
    inner = _one_row_per_client_ref_sql(
        BRONZE_HOUSEHOLD_TABLE, slice_filter.beneficiary_targets(), "member_count", "latest_member_count",
    )
    return f"""
    LEFT JOIN
    ({inner}
    ) AS household
        ON household.client_reference_id = beneficiary.latest_beneficiary_client_reference_id
       AND household.tenant_id = task.tenant_id"""


def individual_join_sql(slice_filter: SliceFilter) -> str:
    """LEFT JOIN stg_individual AS individual ON the beneficiary's reference
    (date_of_birth and gender, each collapsed to one row per reference)."""
    where = slice_filter.beneficiary_targets()
    return f"""
    LEFT JOIN
    (
        SELECT
            tenant_id,
            client_reference_id,
            argMax(latest_date_of_birth, (version, id)) AS latest_date_of_birth,
            argMax(latest_gender,        (version, id)) AS latest_gender
        FROM
        (
            SELECT
                tenant_id,
                client_reference_id,
                id,
                argMax(date_of_birth, last_modified_time) AS latest_date_of_birth,
                argMax(gender,        last_modified_time) AS latest_gender,
                argMax(is_deleted,    last_modified_time) AS latest_is_deleted,
                max(last_modified_time)                   AS version
            FROM {BRONZE_INDIVIDUAL_TABLE}
            WHERE {where}
            GROUP BY tenant_id, client_reference_id, id
            HAVING latest_is_deleted = false
        )
        GROUP BY tenant_id, client_reference_id
    ) AS individual
        ON individual.client_reference_id = beneficiary.latest_beneficiary_client_reference_id
       AND individual.tenant_id = task.tenant_id"""


def joined_bronze_rows_sql(slice_filter: SliceFilter) -> str:
    """One row per (task, resource) for the slice, aliased `joined` by the
    caller. Unmatched sides are '' / 0 / false (join_use_nulls = 0)."""
    return f"""
    SELECT
        task.id                                       AS id,
        task.tenant_id                                AS tenant_id,
        task.project_id                               AS project_id,
        task.project_beneficiary_client_reference_id  AS project_beneficiary_client_reference_id,
        task.additional_details                       AS additional_details,
        task.created_time                             AS created_time,
        task.last_modified_time                       AS last_modified_time,
        task.client_created_time                      AS client_created_time,
        task.client_last_modified_time                AS client_last_modified_time,
        task.client_created_by                        AS client_created_by,
        task.client_last_modified_by                  AS client_last_modified_by,
        task.client_reference_id                      AS client_reference_id,
        task.status                                   AS status,
        resource.id                                   AS resource_id,
        resource.latest_product_variant_id            AS resource_product_variant_id,
        resource.latest_quantity                      AS resource_quantity,
        resource.latest_is_delivered                  AS resource_is_delivered,
        resource.latest_reason_if_not_delivered       AS resource_reason_if_not_delivered,
        resource.latest_client_reference_id           AS resource_client_reference_id,
        address.latitude                              AS address_latitude,
        address.longitude                             AS address_longitude,
        address.location_accuracy                     AS address_location_accuracy,
        address.locality_code                         AS address_locality_code,
        project.additional_details                    AS project_additional_details,
        project.project_type                          AS project_type,
        project.project_type_id                       AS project_type_id,
        project.name                                  AS project_name,
        project.reference_id                          AS campaign_number,
        JSONExtractString(project.additional_details, 'hierarchyType')                  AS hierarchy_type,
        JSONExtractString(project.additional_details, 'projectType', 'beneficiaryType') AS beneficiary_type,
        variant.sku                                   AS product_name,
        if(address.locality_code != '', address.locality_code, project_address.latest_boundary) AS lookup_boundary_code,
        beneficiary.client_reference_id != ''         AS beneficiary_matched,
        beneficiary.latest_beneficiary_client_reference_id AS beneficiary_ref,
        household.latest_member_count                 AS household_member_count,
        individual.client_reference_id != ''          AS individual_matched,
        individual.latest_date_of_birth               AS individual_date_of_birth,
        individual.latest_gender                      AS individual_gender
    FROM {BRONZE_TASK_TABLE} AS task FINAL{resource_join_sql(slice_filter)}{address_join_sql(slice_filter)}{project_join_sql(slice_filter)}{product_variant_join_sql(slice_filter)}{project_address_join_sql(slice_filter)}{beneficiary_join_sql(slice_filter)}{household_join_sql(slice_filter)}{individual_join_sql(slice_filter)}
    WHERE {slice_filter.tasks()}"""


# --- API keys and the inline VALUES hand-off ----------------------------------

def boundary_keys_sql(slice_filter: SliceFilter) -> str:
    """Distinct (tenant_id, hierarchy_type, boundary_code) needing a boundary
    lookup: hierarchy_type from the PROJECT's additional_details, code = the
    task's address locality, else the project's boundary; both must be
    non-empty (= project_task_transformation._get_boundary_lookup_key)."""
    return f"""
SELECT DISTINCT tenant_id, hierarchy_type, boundary_code
FROM
(
    SELECT
        task.tenant_id                                                                          AS tenant_id,
        JSONExtractString(project.additional_details, 'hierarchyType')                          AS hierarchy_type,
        if(address.locality_code != '', address.locality_code, project_address.latest_boundary) AS boundary_code
    FROM {BRONZE_TASK_TABLE} AS task FINAL{address_join_sql(slice_filter)}{project_join_sql(slice_filter)}{project_address_join_sql(slice_filter)}
    WHERE {slice_filter.tasks()}
)
WHERE hierarchy_type != '' AND boundary_code != ''
"""


def user_keys_sql(slice_filter: SliceFilter) -> str:
    """Distinct (tenant_id, client_created_by) of the slice's tasks
    (= project_task_transformation._get_user_lookup_key)."""
    return f"""
SELECT DISTINCT task.tenant_id AS tenant_id, task.client_created_by AS user_id
FROM {BRONZE_TASK_TABLE} AS task FINAL
WHERE {slice_filter.tasks()} AND task.client_created_by != ''
"""


def _values_table_sql(columns: list[str], rows: list[list[str]]) -> str:
    """Inline VALUES table with the given String columns; a zero-row SELECT
    with the same columns when there is nothing to embed."""
    if not rows:
        empty_row = ", ".join(f"'' AS {column}" for column in columns)
        return f"SELECT {empty_row} WHERE 0"
    structure = ", ".join(f"{column} String" for column in columns)
    rendered = ", ".join("(" + ", ".join(sql_string_literal(v) for v in row) + ")" for row in rows)
    return f"SELECT * FROM VALUES('{structure}', {rendered})"


def boundary_levels_values_sql(resolved_levels: dict) -> str:
    """API-resolved levels keyed (tenant_id, hierarchy_type, boundary_code) as
    an inline VALUES table with BOUNDARY_LEVELS_COLUMNS. An unresolved code
    carries nine empty levels, the same as an unmatched LEFT JOIN."""
    if len(resolved_levels) > MAX_INLINE_BOUNDARY_CODES:
        raise AirflowFailException(
            f"{DAG_ID}: slice resolved {len(resolved_levels)} boundary codes, above "
            f"MAX_INLINE_BOUNDARY_CODES={MAX_INLINE_BOUNDARY_CODES}; lower the {SLICE_SIZE_VARIABLE} Variable."
        )
    rows = [
        [tenant_id, hierarchy_type, boundary_code, *[levels.get(column, "") for column in LEVEL_COLUMNS]]
        for (tenant_id, hierarchy_type, boundary_code), levels in resolved_levels.items()
    ]
    return _values_table_sql(BOUNDARY_LEVELS_COLUMNS, rows)


def user_info_values_sql(resolved_users: dict) -> str:
    """API-resolved users keyed (tenant_id, user_id) -> {USERNAME, NAME, ROLE,
    CITY} as an inline VALUES table with USER_INFO_COLUMNS. None -> '' exactly
    as the Python DAG's `info.get(...) or ""`; a not-found user already carries
    USERNAME = its uuid (egov_api_utils._not_found_user_info)."""
    if len(resolved_users) > MAX_INLINE_USERS:
        raise AirflowFailException(
            f"{DAG_ID}: slice resolved {len(resolved_users)} users, above "
            f"MAX_INLINE_USERS={MAX_INLINE_USERS}; lower the {SLICE_SIZE_VARIABLE} Variable."
        )
    rows = [
        [tenant_id, user_id, str(info.get("USERNAME") or ""), str(info.get("NAME") or ""),
         str(info.get("ROLE") or ""), str(info.get("CITY") or "")]
        for (tenant_id, user_id), info in resolved_users.items()
    ]
    return _values_table_sql(USER_INFO_COLUMNS, rows)


def boundary_levels_join_sql(boundary_levels_values: str) -> str:
    """LEFT JOIN the slice's resolved levels AS levels ON
    (tenant_id, hierarchy_type, lookup boundary code) of the joined row."""
    return f"""
LEFT JOIN
(
    {boundary_levels_values}
) AS levels
    ON levels.tenant_id = joined.tenant_id
   AND levels.hierarchy_type = joined.hierarchy_type
   AND levels.boundary_code = joined.lookup_boundary_code"""


def user_info_join_sql(user_info_values: str) -> str:
    """LEFT JOIN the slice's resolved users AS users ON (tenant_id, client_created_by)."""
    return f"""
LEFT JOIN
(
    {user_info_values}
) AS users
    ON users.tenant_id = joined.tenant_id
   AND users.user_id = joined.client_created_by"""


# --- the flatten ---------------------------------------------------------------
#
# Port of project_task_transformation._build_silver_row, _resolve_resource_fields,
# _attach_task_core_fields, _attach_beneficiary_details, _attach_cycle_dose_delivery
# and the egov_api_utils helpers they call. `joined` is one row from
# joined_bronze_rows_sql; `levels` / `users` are the API VALUES tables.

def silver_helper_expressions() -> list[tuple[str, str]]:
    """Named intermediate expressions, rendered as the query's WITH clause and
    referenced from SILVER_COLUMNS."""
    return [
        # the task's own additional_details: {"fields": [{"key": ..., "value": ...}, ...]}
        # (= parse_additional_fields; a later duplicate key wins, hence arrayLast)
        ("task_fields",
         "arrayMap(f -> (JSONExtractString(f, 'key'), JSONExtractString(f, 'value')), "
         "arrayFilter(f -> JSONHas(f, 'key'), JSONExtractArrayRaw(joined.additional_details, 'fields')))"),
        ("field_delivery_strategy",  "arrayLast(t -> t.1 = 'deliveryStrategy', task_fields).2"),
        ("field_cycle_index",        "arrayLast(t -> t.1 = 'cycleIndex', task_fields).2"),
        ("field_dose_index",         "arrayLast(t -> t.1 = 'doseIndex', task_fields).2"),
        ("field_product_variant_id", "arrayLast(t -> t.1 = 'productVariantId', task_fields).2"),
        ("field_task_status",        "arrayLast(t -> t.1 = 'taskStatus', task_fields).2"),

        # real resource row vs placeholder (= _resolve_resource_fields / Java constructTaskResourceIfNull)
        ("has_resource", "joined.resource_id != ''"),
        ("placeholder_referred",
         "upper(field_task_status) = 'BENEFICIARY_REFERRED' AND field_product_variant_id != ''"),

        # beneficiary branch (= _attach_beneficiary_details): chosen by the PROJECT's beneficiaryType
        ("is_household_project",  "upper(joined.beneficiary_type) = 'HOUSEHOLD'"),
        ("is_individual_project", "upper(joined.beneficiary_type) = 'INDIVIDUAL'"),
        ("beneficiary_found",     "joined.project_beneficiary_client_reference_id != '' AND joined.beneficiary_matched"),
        # the individual row itself may be missing from bronze although the bridge points at it; the Python
        # DAG then has None for date_of_birth/gender -> 1970-01-01 / '' / age 0 (the join default for a
        # missing Date32 would be 1900-01-01, so the match flag, not the value, must decide)
        ("individual_known",      "is_individual_project AND beneficiary_found AND joined.individual_matched"),
        # age in whole months (= calculate_age_in_months); now() once per statement rather than per row
        ("today_utc", "toDate(now('UTC'))"),
        # stg_individual.date_of_birth is Nullable(Date32) on the cluster (the repo DDL says Date32);
        # a NULL must land as the Python None defaults (age 0, date_of_birth 1970-01-01), not as the
        # Date32 default 1900-01-01 that a NULL cast to a non-nullable column would give
        ("age_months",
         "ifNull((toYear(today_utc) - toYear(joined.individual_date_of_birth)) * 12 "
         "+ (toMonth(today_utc) - toMonth(joined.individual_date_of_birth)), toInt64(0))"),

        # cycleIndex / doseIndex (= _parse_uint8, get_project_cycles, fetch_cycle_index)
        # -1 means "did not parse" and falls through to the next rule
        ("parsed_cycle_index", "ifNull(toInt64OrNull(field_cycle_index), toInt64(-1))"),
        ("parsed_dose_index",  "ifNull(toInt64OrNull(field_dose_index), toInt64(-1))"),
        ("project_cycles",
         "arrayMap(c -> (assumeNotNull(c.1), assumeNotNull(c.2), assumeNotNull(c.3)), "
         "arrayFilter(c -> c.1 IS NOT NULL AND c.2 IS NOT NULL AND c.3 IS NOT NULL, "
         "arrayMap(c -> (toInt64OrNull(JSONExtractString(c, 'id')), toInt64OrNull(JSONExtractString(c, 'startDate')), "
         "toInt64OrNull(JSONExtractString(c, 'endDate'))), "
         "JSONExtractArrayRaw(joined.project_additional_details, 'projectType', 'cycles'))))"),
        ("task_ms", "joined.client_created_time"),
        # index of the cycle containing the task time, or the cycle whose gap before the next one contains it
        ("cycle_hit",
         "if(task_ms = 0 OR empty(project_cycles), toUInt64(0), arrayFirstIndex(i -> "
         "(project_cycles[i].2 <= task_ms AND task_ms <= project_cycles[i].3) OR "
         "(i < length(project_cycles) AND project_cycles[i].3 < task_ms AND task_ms < project_cycles[i + 1].2), "
         "arrayEnumerate(project_cycles)))"),
        ("computed_cycle_index", "if(cycle_hit > 0, project_cycles[cycle_hit].1, toInt64(-1))"),
        ("cycle_index_value",
         "multiIf(NOT is_individual_project, toInt64(0), "
         "parsed_cycle_index BETWEEN 0 AND 255, parsed_cycle_index, "
         "computed_cycle_index BETWEEN 0 AND 255, computed_cycle_index, toInt64(0))"),
        ("dose_index_value",
         "if(is_individual_project AND parsed_dose_index BETWEEN 0 AND 255, parsed_dose_index, toInt64(0))"),

        # geo_point = json.dumps([longitude, latitude]); Python prints integral floats as 12.0, ClickHouse as 12
        ("lon_json",
         "if(joined.address_longitude = trunc(joined.address_longitude) AND abs(joined.address_longitude) < 1e16, "
         "concat(toString(toInt64(joined.address_longitude)), '.0'), toString(joined.address_longitude))"),
        ("lat_json",
         "if(joined.address_latitude = trunc(joined.address_latitude) AND abs(joined.address_latitude) < 1e16, "
         "concat(toString(toInt64(joined.address_latitude)), '.0'), toString(joined.address_latitude))"),
    ]


# (silver column, SQL expression) in project_task_entity's column order. Both the
# INSERT column list and the SELECT list are generated from this. `joined.*`
# columns come from joined_bronze_rows_sql, `levels.*` / `users.*` from the API
# VALUES tables, bare names from silver_helper_expressions.
SILVER_COLUMNS: list[tuple[str, str]] = [
    ("id", "if(has_resource, joined.resource_id, concat(joined.status, '-', joined.id))"),
    ("task_id", "joined.id"),
    ("task_type", "'DELIVERY'"),
    ("status", "joined.status"),
    ("tenant_id", "joined.tenant_id"),
    ("administration_status", "joined.status"),
    ("client_reference_id",
     "if(has_resource, joined.resource_client_reference_id, concat(joined.status, '-', joined.client_reference_id))"),
    ("task_client_reference_id", "joined.client_reference_id"),
    ("project_beneficiary_client_reference_id", "joined.project_beneficiary_client_reference_id"),
    # audit columns come from the CLIENT audit trail, not the server one
    ("created_by", "joined.client_created_by"),
    ("last_modified_by", "joined.client_last_modified_by"),
    ("created_time", "joined.client_created_time"),
    ("last_modified_time", "joined.client_last_modified_time"),
    ("product_variant", "if(has_resource, joined.resource_product_variant_id, field_product_variant_id)"),
    ("product_name", "joined.product_name"),
    ("quantity",
     "if(has_resource, toInt64(round(joined.resource_quantity)), if(placeholder_referred, toInt64(2), toInt64(0)))"),
    ("delivered_to", "joined.beneficiary_type"),
    ("is_delivered", "if(has_resource, joined.resource_is_delivered, false)"),
    ("delivery_comments",
     "if(has_resource, joined.resource_reason_if_not_delivered, "
     "if(placeholder_referred, 'ADMINISTRATION_NOT_SUCCESSFUL', ''))"),
    ("household_id", "if(is_household_project AND beneficiary_found, joined.beneficiary_ref, '')"),
    ("member_count", "if(is_household_project AND beneficiary_found, joined.household_member_count, toInt32(0))"),
    ("individual_id", "if(is_individual_project AND beneficiary_found, joined.beneficiary_ref, '')"),
    ("user_name", "users.user_name"),
    ("name_of_user", "users.name_of_user"),
    ("role", "users.role"),
    ("user_address", "users.user_address"),
    ("latitude", "joined.address_latitude"),
    ("longitude", "joined.address_longitude"),
    ("location_accuracy", "toFloat64(joined.address_location_accuracy)"),
    # the task's own locality only -- the project-boundary fallback applies to the LOOKUP key, not here
    ("boundary_code", "joined.address_locality_code"),
    ("geo_point", "concat('[', lon_json, ', ', lat_json, ']')"),
    *[(column, f"levels.{column}") for column in LEVEL_COLUMNS],
    # all-or-nothing with the levels: blank unless a lookup key existed (= attach_boundary_levels)
    ("hierarchy_type", "if(joined.lookup_boundary_code != '', joined.hierarchy_type, '')"),
    ("age", "if(individual_known, toUInt32(greatest(toInt64(0), age_months)), toUInt32(0))"),
    ("gender", "if(individual_known, joined.individual_gender, '')"),
    # via Int32 days: a plain if() between a Date32 join column and a Date32 constant hits a
    # ClickHouse LOGICAL_ERROR ("Cannot get native value for column with type Date32") on some blocks
    ("date_of_birth",
     "toDate32(if(individual_known, ifNull(toInt32(joined.individual_date_of_birth), toInt32(0)), toInt32(0)))"),
    ("cycleIndex", "toUInt8(cycle_index_value)"),
    ("doseIndex", "toUInt8(dose_index_value)"),
    ("delivery_strategy", "field_delivery_strategy"),
    # server timestamps for the synced_* columns, client timestamp for task_dates; 0 -> epoch defaults
    ("synced_time_stamp", "fromUnixTimestamp64Milli(joined.created_time, 'UTC')"),
    ("synced_date", "toDate32(fromUnixTimestamp64Milli(joined.last_modified_time, 'UTC'))"),
    ("synced_time", "joined.last_modified_time"),
    ("task_dates", "toDate32(fromUnixTimestamp64Milli(joined.client_last_modified_time, 'UTC'))"),
    ("additional_details", "joined.additional_details"),
    ("project_id", "joined.project_id"),
    ("project_type", "joined.project_type"),
    ("project_type_id", "joined.project_type_id"),
    ("project_name", "joined.project_name"),
    ("campaign_number", "joined.campaign_number"),
    ("campaign_id", "''"),
]


def insert_slice_sql(slice_filter: SliceFilter, boundary_levels_values: str, user_info_values: str) -> str:
    """The whole per-slice statement: INSERT INTO test table <-
    flatten(joined bronze rows LEFT JOIN levels LEFT JOIN users)."""
    insert_columns = ",\n    ".join(column for column, _ in SILVER_COLUMNS)
    with_clause = ",\n    ".join(f"{expression} AS {name}" for name, expression in silver_helper_expressions())
    # Output columns are positional (INSERT maps by position); the comment line
    # above each expression names the silver column it feeds.
    select_list = ",\n".join(f"    -- {column}\n    {expression}" for column, expression in SILVER_COLUMNS)
    sql = f"""
INSERT INTO {TEST_SILVER_TABLE}
(
    {insert_columns}
)
WITH
    {with_clause}
SELECT
{select_list}
FROM
({joined_bronze_rows_sql(slice_filter)}
) AS joined{boundary_levels_join_sql(boundary_levels_values)}{user_info_join_sql(user_info_values)}
"""
    size = len(sql.encode())
    if size > MAX_INSERT_SQL_BYTES:
        raise AirflowFailException(
            f"{DAG_ID}: rendered slice statement is {size} bytes, above MAX_INSERT_SQL_BYTES="
            f"{MAX_INSERT_SQL_BYTES}; lower the {SLICE_SIZE_VARIABLE} Variable."
        )
    return sql


# =============================================================================
# ClickHouse calls
# =============================================================================

def create_test_table(client, truncate: bool) -> None:
    """Test target mirrors project_task_entity (columns, engine, ORDER BY,
    indexes) so the two can be diffed."""
    client.command(f"CREATE TABLE IF NOT EXISTS {TEST_SILVER_TABLE} AS {SILVER_TABLE}")
    if truncate:
        client.command(f"TRUNCATE TABLE {TEST_SILVER_TABLE}")
        log.info("%s: truncated %s", DAG_ID, TEST_SILVER_TABLE)


def plan_slices(client, window: TimeWindow, slice_size: int) -> list[Slice]:
    """Splits the window into per-tenant id ranges of <= slice_size distinct
    tasks. One query over the prj_ingested_at projection; modulo() instead of
    the percent operator because this is the one %-bound query."""
    result = client.query(
        f"""
        SELECT
            tenant_id,
            arraySort(groupArrayIf(id, modulo(rn - 1, %(slice_size)s) = 0)) AS slice_starts,
            count() AS task_count
        FROM
        (
            SELECT tenant_id, id, row_number() OVER (PARTITION BY tenant_id ORDER BY id) AS rn
            FROM
            (
                SELECT tenant_id, id
                FROM {BRONZE_TASK_TABLE}
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
        for position, first_task_id in enumerate(starts):
            next_start = starts[position + 1] if position + 1 < len(starts) else None
            slices.append(Slice(row["tenant_id"], first_task_id, next_start))
        log.info("%s: tenant %s has %d tasks in window -> %d slices", DAG_ID, row["tenant_id"], row["task_count"], len(starts))
    return slices


def fetch_boundary_keys(client, window: TimeWindow, slice_: Slice) -> dict[tuple[str, str], set[str]]:
    """{(tenant_id, hierarchy_type): {boundary_codes}}, the input shape
    egov_api_utils.resolve_boundary_levels expects."""
    result = client.query(boundary_keys_sql(SliceFilter(slice_, window)), settings=CLICKHOUSE_LOOKUP_SETTINGS)
    lookup_keys: dict[tuple[str, str], set[str]] = {}
    for row in result.named_results():
        lookup_keys.setdefault((row["tenant_id"], row["hierarchy_type"]), set()).add(row["boundary_code"])
    return lookup_keys


def fetch_user_keys(client, window: TimeWindow, slice_: Slice) -> set[tuple[str, str]]:
    """{(tenant_id, user_id)}, the input shape egov_api_utils.resolve_user_info expects."""
    result = client.query(user_keys_sql(SliceFilter(slice_, window)), settings=CLICKHOUSE_LOOKUP_SETTINGS)
    return {(row["tenant_id"], row["user_id"]) for row in result.named_results()}


def count_test_table_rows(client) -> int:
    return client.query(f"SELECT count() FROM {TEST_SILVER_TABLE}").result_rows[0][0]


def insert_slice(client, window: TimeWindow, slice_: Slice, resolved_levels: dict, resolved_users: dict) -> int:
    """Runs the slice's INSERT ... SELECT; returns rows written, measured as the
    test table's row-count delta (this DAG is the table's only writer, and the
    delta works on every clickhouse-connect version, unlike QuerySummary)."""
    rows_before = count_test_table_rows(client)
    sql = insert_slice_sql(
        SliceFilter(slice_, window),
        boundary_levels_values_sql(resolved_levels),
        user_info_values_sql(resolved_users),
    )
    client.command(sql, settings=CLICKHOUSE_INSERT_SETTINGS)
    return count_test_table_rows(client) - rows_before


def process_slice(client, window: TimeWindow, slice_: Slice) -> SliceResult:
    """keys (ClickHouse) -> boundary levels + users (Python + APIs) -> insert (ClickHouse)."""
    started = time.monotonic()

    boundary_keys = fetch_boundary_keys(client, window, slice_)
    user_keys = fetch_user_keys(client, window, slice_)

    api_started = time.monotonic()
    resolved_levels = resolve_boundary_levels(boundary_keys)  # unchanged boundary-service path
    resolved_users = resolve_user_info(user_keys)             # unchanged user-service (+MDMS) path
    api_seconds = time.monotonic() - api_started

    insert_started = time.monotonic()
    written_rows = insert_slice(client, window, slice_, resolved_levels, resolved_users)
    insert_seconds = time.monotonic() - insert_started

    return SliceResult(
        boundary_codes_resolved=len(resolved_levels),
        users_resolved=len(resolved_users),
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
    description="TEST: project_task bronze -> project_task_entity_sql_test with the flatten done in ClickHouse.",
    schedule=None,
    start_date=pendulum.datetime(2024, 1, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    tags=["bronze-to-silver", "project_task", "sql-test"],
)
def project_task_test_transformation():

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
                "%s slice %d/%d %s: %d boundary codes + %d users resolved (api %.2fs), "
                "written_rows=%d (insert %.2fs), slice %.2fs",
                DAG_ID, number, len(slices), slice_.label, result.boundary_codes_resolved, result.users_resolved,
                result.api_seconds, result.written_rows, result.insert_seconds, result.total_seconds,
            )

        log.info(
            "%s: done. written_rows=%d over %d slices; insert total %.1fs, api total %.1fs; "
            "%s row count (no FINAL)=%d",
            DAG_ID, total_written, len(slices), total_insert_seconds, total_api_seconds,
            TEST_SILVER_TABLE, count_test_table_rows(client),
        )

    transform_bronze_to_silver_sql(parse_time_window())


project_task_test_transformation()
