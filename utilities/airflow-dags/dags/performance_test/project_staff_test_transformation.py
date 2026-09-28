"""
project_staff_test_transformation.py

TEST twin of project_staff_transformation.py for the `project_staff` entity:
the flatten + write runs inside ClickHouse as one INSERT ... SELECT per slice
(see sql_flatten_common.py for how a run works). Writes to
<database>.project_staff_entity_sql_test, never to project_staff_entity.
Entity name `project_staff_test` (dag_id project_staff_test_transformation).

Known differences from project_staff_transformation.py (all deliberate)
    - stg_project_staff is read with FINAL (the Python DAG reads every version).
    - stg_project is read with FINAL and stg_project_address through 14's
      prj_by_project as one boundary per project (the Python DAG joins both
      without FINAL, so a multi-version project fans out there).
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
    project_address_join_sql,
    project_cycle_dose_expressions,
    project_dates_expressions,
    project_join_sql,
    table,
    user_lookup,
)

# Sources, in the order they appear in the query:
#
#   source                     alias            keyed / joined on                                      feeds
#   -------------------------  ---------------  -----------------------------------------------------  ----------------------------------------
#   stg_project_staff (FINAL)  staff_row        tenant_id + id range of the slice                      id, user_id (= staff_id), is_deleted,
#                                                                                                      created_by, created_time
#   stg_project (FINAL)        project          project.id = staff_row.project_id AND tenant (own PK)  project_* columns, campaign_number,
#                                                                                                      hierarchy_type, task_dates (project
#                                                                                                      start/end), additional_details (cycles)
#   stg_project_address        project_address  project_address.project_id = project.id AND tenant     boundary_code and the boundary lookup
#                                               (14's prj_by_project)
#   boundary-service response  levels           (tenant_id, hierarchy_type, project boundary)          level_one_code .. level_nine_code
#   user-service response      users            (tenant_id, staff_id)                                  user_name, name_of_user, user_address, role


class ProjectStaffSliceFilter(SliceFilter):
    driving_alias = "staff_row"

    def projects(self) -> str:
        return self.own_key_in("id", self.slice_column("project_id"))

    def project_addresses(self) -> str:
        return self.own_key_in("project_id", self.slice_column("project_id"))


def _joins(f: ProjectStaffSliceFilter) -> str:
    return (
        project_join_sql(f.projects(), "staff_row.project_id", "staff_row.tenant_id",
                         columns="id, tenant_id, additional_details, project_type, project_type_id, name, reference_id, "
                                 "start_date, end_date")
        + project_address_join_sql(f.project_addresses(), "project.id", "staff_row.tenant_id")
    )


def joined_rows_sql(f: ProjectStaffSliceFilter) -> str:
    """One row per project staff assignment for the slice, aliased `joined` by the caller."""
    return f"""
    SELECT
        staff_row.id                    AS id,
        staff_row.tenant_id             AS tenant_id,
        staff_row.project_id            AS project_id,
        staff_row.staff_id              AS staff_id,
        staff_row.is_deleted            AS is_deleted,
        staff_row.created_by            AS created_by,
        staff_row.created_time          AS created_time,
        project.additional_details      AS project_additional_details,
        project.project_type            AS project_type,
        project.project_type_id         AS project_type_id,
        project.name                    AS project_name,
        project.reference_id            AS campaign_number,
        project.start_date              AS project_start_date,
        project.end_date                AS project_end_date,
        JSONExtractString(project.additional_details, 'hierarchyType') AS hierarchy_type,
        project_address.latest_boundary AS boundary_code
    FROM {table('stg_project_staff')} AS staff_row FINAL{_joins(f)}
    WHERE {f.driving()}"""


def boundary_keys_sql(f: ProjectStaffSliceFilter) -> str:
    return f"""
SELECT DISTINCT tenant_id, hierarchy_type, boundary_code
FROM
(
    SELECT
        staff_row.tenant_id                                            AS tenant_id,
        JSONExtractString(project.additional_details, 'hierarchyType') AS hierarchy_type,
        project_address.latest_boundary                                AS boundary_code
    FROM {table('stg_project_staff')} AS staff_row FINAL{_joins(f)}
    WHERE {f.driving()}
)
WHERE hierarchy_type != '' AND boundary_code != ''
"""


def user_keys_sql(f: ProjectStaffSliceFilter) -> str:
    """(tenant_id, staff_id): the staff row's own user id."""
    return f"""
SELECT DISTINCT staff_row.tenant_id AS tenant_id, staff_row.staff_id AS user_id
FROM {table('stg_project_staff')} AS staff_row FINAL
WHERE {f.driving()} AND staff_row.staff_id != ''
"""


def helper_expressions(f: ProjectStaffSliceFilter) -> list[tuple[str, str]]:
    return [
        *project_dates_expressions("joined.project_start_date", "joined.project_end_date"),
        *project_cycle_dose_expressions("joined.project_additional_details"),
    ]


# (silver column, SQL expression) in project_staff_entity's column order.
SILVER_COLUMNS: list[tuple[str, str]] = [
    ("id", "joined.id"),
    ("tenant_id", "joined.tenant_id"),
    ("user_id", "joined.staff_id"),
    ("user_name", "users.user_name"),
    ("name_of_user", "users.name_of_user"),
    ("user_address", "users.user_address"),
    ("role", "users.role"),
    ("boundary_code", "joined.boundary_code"),
    ("is_deleted", "joined.is_deleted"),
    ("created_by", "joined.created_by"),
    ("created_time", "joined.created_time"),
    # the PROJECT's day range and cycle/dose blob, as Java reads them off the project
    ("task_dates", json_string_array_sql("task_date_list")),
    ("additional_details", "cycle_dose_json"),
    *[(column, f"levels.{column}") for column in LEVEL_COLUMNS],
    ("hierarchy_type", "if(joined.boundary_code != '', joined.hierarchy_type, '')"),
    ("project_id", "joined.project_id"),
    ("project_type", "joined.project_type"),
    ("project_type_id", "joined.project_type_id"),
    ("project_name", "joined.project_name"),
    ("campaign_number", "joined.campaign_number"),
    ("campaign_id", "''"),
]


SPEC = EntitySpec(
    entity="project_staff_test",
    python_entity="project_staff",
    driving_table="stg_project_staff",
    slice_filter_class=ProjectStaffSliceFilter,
    target=SilverTarget(
        silver_table="project_staff_entity",
        joined_rows_sql=joined_rows_sql,
        helper_expressions=helper_expressions,
        silver_columns=SILVER_COLUMNS,
        lookups=(
            boundary_lookup(boundary_keys_sql, code_expr="joined.boundary_code"),
            user_lookup(user_keys_sql, user_expr="joined.staff_id"),
        ),
    ),
)

# The Airflow DAG object for this entity, built from SPEC by sql_flatten_common
# (airflow safe-mode discovery needs the words "airflow" and "dag" in this file).
dag = build_sql_test_dag(SPEC)
