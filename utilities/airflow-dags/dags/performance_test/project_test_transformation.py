"""
project_test_transformation.py

TEST twin of project_transformation.py for the `project` entity: the flatten
+ write runs inside ClickHouse as one INSERT ... SELECT per slice (see
sql_flatten_common.py for how a run works). Writes to
<database>.project_entity_sql_test, never to project_entity.
Entity name `project_test` (dag_id project_test_transformation).

Measurements and design rationale: airflow_dags/PROJECTION_COMPARISON_REPORT.md,
section 7 (this was the first SQL-side twin; 35 min -> 12 min on unified-dev).

Known differences from project_transformation.py (all deliberate)
    - stg_project_address and stg_project_target are read WITHOUT FINAL through
      14's prj_by_project projections and deduplicated with argMax on their own
      version columns, which yields exactly the rows FINAL would.
    - now() is evaluated once per statement; a row that fails to build aborts
      the slice instead of being skipped with a log line.
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sql_flatten_common import (  # noqa: E402
    LEVEL_COLUMNS,
    EntitySpec,
    SilverTarget,
    SliceFilter,
    boundary_lookup,
    build_sql_test_dag,
    json_string_array_sql,
    project_cycle_dose_expressions,
    project_dates_expressions,
    table,
)

# Sources, in the order they appear in the query:
#
#   source                         alias     keyed / joined on                                   feeds
#   -----------------------------  --------  --------------------------------------------------  -------------------------------------------
#   stg_project (FINAL)            project   tenant_id + id range of the slice                   every plain column, the JSON fields
#   stg_project_address            address   address.project_id = project.id                     boundary_code
#                                            AND address.tenant_id = project.tenant_id
#                                            (14's prj_by_project)
#   stg_project_target             target    target.project_id = project.id                      id, target_type, overall_target
#                                            (this table has no tenant_id column; 14's prj_by_project)
#   stg_product_variant (FINAL)    -- none   NOT a join: a Map(variant id -> sku) built once per   product_name
#                                            slice for its tenant; each project's list of
#                                            additional_details.projectType.resources[].productVariantId
#                                            is looked up in it (see product_sku_map_sql)
#   boundary-service response      levels    (tenant_id, hierarchy_type, boundary_code)          level_one_code .. level_nine_code


class ProjectSliceFilter(SliceFilter):
    driving_alias = "project"

    def addresses(self) -> str:
        """WHERE for stg_project_address (has tenant_id; keyed by project_id)."""
        return self.keyed_by_driving_id("project_id")

    def targets(self) -> str:
        """WHERE for stg_project_target (no tenant_id column; keyed by project_id)."""
        return f"{self.id_range('project_id')} AND project_id IN {self.in_window_ids}"


def address_join_sql(join_type: str, f: ProjectSliceFilter) -> str:
    """JOIN stg_project_address AS address ON (project_id, tenant_id). Latest
    boundary per address row via argMax on _ingested_at, the table's
    ReplacingMergeTree version column."""
    return f"""
    {join_type} JOIN
    (
        SELECT tenant_id, project_id, id, argMax(boundary, _ingested_at) AS latest_boundary
        FROM {table('stg_project_address')}
        WHERE {f.addresses()}
        GROUP BY tenant_id, project_id, id
    ) AS address
        ON address.project_id = project.id AND address.tenant_id = project.tenant_id"""


def target_join_sql(f: ProjectSliceFilter) -> str:
    """LEFT JOIN stg_project_target AS target ON project_id. Latest version of
    each target via argMax on last_modified_time; a target whose newest version
    is deleted is dropped (HAVING after argMax). A project with several targets
    yields several silver rows; a project with none yields one placeholder row."""
    return f"""
    LEFT JOIN
    (
        SELECT
            project_id,
            id,
            argMax(beneficiary_type, last_modified_time) AS latest_beneficiary_type,
            argMax(target_no,        last_modified_time) AS latest_target_no,
            argMax(is_deleted,       last_modified_time) AS latest_is_deleted
        FROM {table('stg_project_target')}
        WHERE {f.targets()}
        GROUP BY project_id, id
        HAVING latest_is_deleted = false
    ) AS target
        ON target.project_id = project.id"""


def joined_rows_sql(f: ProjectSliceFilter) -> str:
    """One row per (project, target) for the slice, aliased `joined` by the
    caller: the project's own columns plus address_boundary and the target
    columns (empty strings / 0 when the project has no address or target)."""
    return f"""
    SELECT
        project.id                   AS id,
        project.tenant_id            AS tenant_id,
        project.additional_details   AS additional_details,
        project.project_number       AS project_number,
        project.reference_id         AS reference_id,
        project.created_by           AS created_by,
        project.created_time         AS created_time,
        project.last_modified_time   AS last_modified_time,
        project.project_sub_type     AS project_sub_type,
        project.start_date           AS start_date,
        project.end_date             AS end_date,
        project.project_type         AS project_type,
        project.project_type_id      AS project_type_id,
        project.name                 AS name,
        JSONExtractString(project.additional_details, 'hierarchyType') AS hierarchy_type,
        address.latest_boundary          AS address_boundary,
        target.id                        AS target_id,
        target.latest_beneficiary_type   AS target_beneficiary_type,
        target.latest_target_no          AS target_target_no
    FROM {table('stg_project')} AS project FINAL{address_join_sql("LEFT", f)}{target_join_sql(f)}
    WHERE {f.driving()}"""


def product_sku_map_sql(tenant_literal: str) -> str:
    """Product names. KEY: stg_product_variant.id (the variant id), scoped to the
    slice's tenant. Built once per slice as a Map(variant id -> sku) from
    stg_product_variant FINAL; each project's list of variant ids is mapped
    through it, keeping the list order. A variant id with no match falls back
    to the id itself, as the Python DAG does."""
    return (
        f"(SELECT mapFromArrays(groupArray(id), groupArray(sku)) "
        f"FROM {table('stg_product_variant')} FINAL WHERE tenant_id = {tenant_literal})"
    )


def boundary_keys_sql(f: ProjectSliceFilter) -> str:
    """A key exists only when the project's additional_details has a
    hierarchyType AND the project has an address boundary
    (= project_transformation._get_boundary_lookup_key). INNER join: a project
    without an address cannot have a key."""
    return f"""
SELECT DISTINCT tenant_id, hierarchy_type, boundary_code
FROM
(
    SELECT
        project.tenant_id                                               AS tenant_id,
        JSONExtractString(project.additional_details, 'hierarchyType') AS hierarchy_type,
        address.latest_boundary                                         AS boundary_code
    FROM {table('stg_project')} AS project FINAL{address_join_sql("INNER", f)}
    WHERE {f.driving()}
)
WHERE hierarchy_type != '' AND boundary_code != ''
"""


def helper_expressions(f: ProjectSliceFilter) -> list[tuple[str, str]]:
    """Port of project_transformation._build_silver_row and the egov_api_utils
    helpers it calls (get_project_dates_list, build_project_additional_details,
    _resolve_target_duration_fields)."""
    return [
        # product variants -> names (see product_sku_map_sql for the key)
        ("product_sku_by_variant_id", product_sku_map_sql(f.tenant)),
        ("product_variant_ids",
         "arrayFilter(v -> v != '', arrayMap(r -> JSONExtractString(r, 'productVariantId'), "
         "JSONExtractArrayRaw(joined.additional_details, 'projectType', 'resources')))"),
        # task dates (get_project_dates_list) and the cycle/dose blob (build_project_additional_details)
        *project_dates_expressions("joined.start_date", "joined.end_date"),
        *project_cycle_dose_expressions("joined.additional_details"),
        # campaign duration (_resolve_target_duration_fields): Python int() truncates toward zero
        ("duration_days",
         "if(joined.start_date != 0 AND joined.end_date != 0, "
         "toInt32(trunc((joined.end_date - joined.start_date) / day_ms)), toInt32(0))"),
    ]


# (silver column, SQL expression) in project_entity's column order.
SILVER_COLUMNS: list[tuple[str, str]] = [
    ("id", "if(joined.target_id != '', joined.target_id, concat(joined.id, '-NO_TARGET'))"),
    ("tenant_id", "joined.tenant_id"),
    ("project_number", "joined.project_number"),
    ("reference_id", "joined.reference_id"),
    ("created_by", "joined.created_by"),
    ("created_time", "joined.created_time"),
    ("last_modified_time", "joined.last_modified_time"),
    ("project_beneficiary_type", "JSONExtractString(joined.additional_details, 'projectType', 'beneficiaryType')"),
    ("sub_project_type", "joined.project_sub_type"),
    ("overall_target", "toInt32(joined.target_target_no)"),
    ("target_per_day",
     "if(joined.target_id != '' AND duration_days > 0, "
     "toInt32(trunc(joined.target_target_no / duration_days)), toInt32(0))"),
    ("campaign_duration_in_days", "duration_days"),
    ("start_date", "joined.start_date"),
    ("end_date", "joined.end_date"),
    ("product_variant", "arrayStringConcat(product_variant_ids, ',')"),
    ("product_name",
     "arrayStringConcat(arrayMap(v -> if(mapContains(product_sku_by_variant_id, v), product_sku_by_variant_id[v], v), "
     "product_variant_ids), ',')"),
    ("target_type", "joined.target_beneficiary_type"),
    ("boundary_code", "joined.address_boundary"),
    *[(column, f"levels.{column}") for column in LEVEL_COLUMNS],
    ("hierarchy_type", "joined.hierarchy_type"),
    ("task_dates", json_string_array_sql("task_date_list")),
    ("additional_details", "cycle_dose_json"),
    ("project_id", "joined.id"),
    ("project_type", "joined.project_type"),
    ("project_type_id", "joined.project_type_id"),
    ("project_name", "joined.name"),
    ("campaign_number", "joined.reference_id"),
    ("campaign_id", "''"),
]


SPEC = EntitySpec(
    entity="project_test",
    python_entity="project",
    driving_table="stg_project",
    slice_filter_class=ProjectSliceFilter,
    target=SilverTarget(
        silver_table="project_entity",
        joined_rows_sql=joined_rows_sql,
        helper_expressions=helper_expressions,
        silver_columns=SILVER_COLUMNS,
        lookups=(boundary_lookup(boundary_keys_sql, code_expr="joined.address_boundary"),),
    ),
)

# The Airflow DAG object for this entity, built from SPEC by sql_flatten_common
# (airflow safe-mode discovery needs the words "airflow" and "dag" in this file).
dag = build_sql_test_dag(SPEC)
