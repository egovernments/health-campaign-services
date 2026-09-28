"""
muster_roll_test_transformation.py

TEST twin of muster_roll_transformation.py for the `muster_roll` entity: the
flatten + write runs inside ClickHouse as one INSERT ... SELECT per slice
(see sql_flatten_common.py for how a run works). Writes to
<database>.muster_roll_entity_sql_test, never to muster_roll_entity.
Entity name `muster_roll_test` (dag_id muster_roll_test_transformation).

Known differences from muster_roll_transformation.py (all deliberate)
    - stg_muster_roll is read with FINAL (the Python DAG reads every version and
      relies on the silver table's own ReplacingMergeTree to collapse them).
    - The two user -> project bridges take, per staff row, the NEWEST version and
      drop it if deleted, then the lowest staff row id per user; the Python DAG's
      inline `LIMIT 1 BY staff_id` subqueries apply is_deleted = false to every
      version and scan the whole table.
    - stg_project_address is read through 14's prj_by_project as one boundary per
      project; the Python DAG's FINAL join fans out on several address rows.
    - now() is evaluated once per statement; a row that fails to build aborts the
      slice instead of being skipped with a log line.
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
    project_address_join_sql,
    project_join_sql,
    staff_bridge_join_sql,
    staff_projects_subquery_sql,
    table,
    user_lookup,
)

# Sources, in the order they appear in the query:
#
#   source                     alias                keyed / joined on                                         feeds
#   -------------------------  -------------------  --------------------------------------------------------  --------------------------------------
#   stg_muster_roll (FINAL)    muster_roll          tenant_id + id range of the slice                         every plain column, edited
#   stg_attendance_summary     summary              summary.muster_roll_id = muster_roll.id                   individual_entry_id, individual_id,
#                                                   (18's prj_by_muster_roll; this table has no tenant_id;    actual_total_attendance
#                                                   ONE SILVER ROW PER SUMMARY ENTRY, one row when none)
#   stg_project_staff          created_by_staff     staff_id = muster_roll.created_by AND tenant              the created-by user's project ...
#                                                   (17's prj_by_staff, lowest staff row id per user)
#   stg_project (FINAL)        created_by_project   id = created_by_staff.first_project_id AND tenant         ... whose hierarchyType and boundary
#   stg_project_address        created_by_address   project_id = created_by_project.id AND tenant             drive the boundary lookup ONLY
#                                                   (14's prj_by_project)
#   stg_project_staff          modified_by_staff    staff_id = muster_roll.last_modified_by AND tenant        the last-modifying user's project ...
#   stg_project (FINAL)        modified_by_project  id = modified_by_staff.first_project_id AND tenant        ... feeds project_* and campaign_number
#   boundary-service response  levels               (tenant_id, created-by project hierarchyType,             level_one_code .. level_nine_code
#                                                   created-by project boundary)
#   user-service response      users                (tenant_id, last_modified_by)                             user_name, name_of_user, role


class MusterRollSliceFilter(SliceFilter):
    driving_alias = "muster_roll"

    def summaries(self) -> str:
        """For stg_attendance_summary via prj_by_muster_roll (no tenant_id column)."""
        return f"{self.id_range('muster_roll_id')} AND muster_roll_id IN {self.in_window_ids}"

    def created_by_staff(self) -> str:
        return self.own_key_in("staff_id", self.slice_column("created_by"))

    def modified_by_staff(self) -> str:
        return self.own_key_in("staff_id", self.slice_column("last_modified_by"))

    def created_by_projects(self) -> str:
        return self.own_key_in("id", staff_projects_subquery_sql(self.created_by_staff()))

    def created_by_project_addresses(self) -> str:
        return self.own_key_in("project_id", staff_projects_subquery_sql(self.created_by_staff()))

    def modified_by_projects(self) -> str:
        return self.own_key_in("id", staff_projects_subquery_sql(self.modified_by_staff()))


def summary_join_sql(f: MusterRollSliceFilter) -> str:
    """LEFT JOIN stg_attendance_summary AS summary ON muster_roll_id: newest
    version of each summary row (argMax on last_modified_time); every entry
    of a muster roll yields one silver row."""
    return f"""
    LEFT JOIN
    (
        SELECT
            muster_roll_id,
            id,
            argMax(individual_id,            last_modified_time) AS latest_individual_id,
            argMax(actual_total_attendance,  last_modified_time) AS latest_actual_total_attendance
        FROM {table('stg_attendance_summary')}
        WHERE {f.summaries()}
        GROUP BY muster_roll_id, id
    ) AS summary
        ON summary.muster_roll_id = muster_roll.id"""


def _joins(f: MusterRollSliceFilter) -> str:
    return (
        summary_join_sql(f)
        + staff_bridge_join_sql(f.created_by_staff(), "muster_roll.created_by", "muster_roll.tenant_id", alias="created_by_staff")
        + project_join_sql(f.created_by_projects(), "created_by_staff.first_project_id", "muster_roll.tenant_id",
                           alias="created_by_project", columns="id, tenant_id, additional_details")
        + project_address_join_sql(f.created_by_project_addresses(), "created_by_project.id", "muster_roll.tenant_id",
                                   alias="created_by_address")
        + staff_bridge_join_sql(f.modified_by_staff(), "muster_roll.last_modified_by", "muster_roll.tenant_id", alias="modified_by_staff")
        + project_join_sql(f.modified_by_projects(), "modified_by_staff.first_project_id", "muster_roll.tenant_id",
                           alias="modified_by_project")
    )


def joined_rows_sql(f: MusterRollSliceFilter) -> str:
    """One row per (muster roll, attendance summary entry), aliased `joined` by the caller."""
    return f"""
    SELECT
        muster_roll.id                          AS id,
        muster_roll.tenant_id                   AS tenant_id,
        muster_roll.musterroll_number           AS musterroll_number,
        muster_roll.attendance_register_id      AS attendance_register_id,
        muster_roll.start_date                  AS start_date,
        muster_roll.end_date                    AS end_date,
        muster_roll.musterroll_status           AS musterroll_status,
        muster_roll.status                      AS status,
        muster_roll.additional_details          AS additional_details,
        muster_roll.created_by                  AS created_by,
        muster_roll.last_modified_by            AS last_modified_by,
        muster_roll.created_time                AS created_time,
        muster_roll.last_modified_time          AS last_modified_time,
        muster_roll.reference_id                AS reference_id,
        muster_roll.service_code                AS service_code,
        muster_roll.billing_period_id           AS billing_period_id,
        summary.id                              AS individual_entry_id,
        summary.latest_individual_id            AS individual_id,
        summary.latest_actual_total_attendance  AS actual_total_attendance,
        JSONExtractString(created_by_project.additional_details, 'hierarchyType') AS hierarchy_type,
        created_by_address.latest_boundary      AS boundary_code,
        modified_by_project.id                  AS project_id,
        modified_by_project.project_type        AS project_type,
        modified_by_project.project_type_id     AS project_type_id,
        modified_by_project.name                AS project_name,
        modified_by_project.reference_id        AS campaign_number
    FROM {table('stg_muster_roll')} AS muster_roll FINAL{_joins(f)}
    WHERE {f.driving()}"""


def boundary_keys_sql(f: MusterRollSliceFilter) -> str:
    """(tenant, created-by project's hierarchyType, created-by project's boundary)."""
    return f"""
SELECT DISTINCT tenant_id, hierarchy_type, boundary_code
FROM
(
    SELECT
        muster_roll.tenant_id                                                     AS tenant_id,
        JSONExtractString(created_by_project.additional_details, 'hierarchyType') AS hierarchy_type,
        created_by_address.latest_boundary                                        AS boundary_code
    FROM {table('stg_muster_roll')} AS muster_roll FINAL{staff_bridge_join_sql(f.created_by_staff(), 'muster_roll.created_by', 'muster_roll.tenant_id', alias='created_by_staff')}{project_join_sql(f.created_by_projects(), 'created_by_staff.first_project_id', 'muster_roll.tenant_id', alias='created_by_project', columns='id, tenant_id, additional_details')}{project_address_join_sql(f.created_by_project_addresses(), 'created_by_project.id', 'muster_roll.tenant_id', alias='created_by_address')}
    WHERE {f.driving()}
)
WHERE hierarchy_type != '' AND boundary_code != ''
"""


def user_keys_sql(f: MusterRollSliceFilter) -> str:
    return f"""
SELECT DISTINCT muster_roll.tenant_id AS tenant_id, muster_roll.last_modified_by AS user_id
FROM {table('stg_muster_roll')} AS muster_roll FINAL
WHERE {f.driving()} AND muster_roll.last_modified_by != ''
"""


def helper_expressions(f: MusterRollSliceFilter) -> list[tuple[str, str]]:
    return [
        # edited (= _get_edit_timestamp is not None): additionalDetails.editInfo.attendanceUpdatedAtEpochMs present and not null
        ("edited",
         "JSONType(joined.additional_details, 'editInfo') = 'Object' "
         "AND JSONHas(joined.additional_details, 'editInfo', 'attendanceUpdatedAtEpochMs') "
         "AND JSONType(joined.additional_details, 'editInfo', 'attendanceUpdatedAtEpochMs') != 'Null'"),
    ]


# (silver column, SQL expression) in muster_roll_entity's column order.
SILVER_COLUMNS: list[tuple[str, str]] = [
    ("id", "joined.id"),
    ("tenant_id", "joined.tenant_id"),
    ("muster_roll_number", "joined.musterroll_number"),
    ("register_id", "joined.attendance_register_id"),
    ("status", "joined.status"),
    ("muster_roll_status", "joined.musterroll_status"),
    ("start_date", "joined.start_date"),
    ("end_date", "joined.end_date"),
    ("individual_entry_id", "joined.individual_entry_id"),
    ("individual_id", "joined.individual_id"),
    ("actual_total_attendance", "CAST(joined.actual_total_attendance AS Decimal(4, 2))"),
    ("reference_id", "joined.reference_id"),
    ("service_code", "joined.service_code"),
    ("billing_period_id", "joined.billing_period_id"),
    ("additional_details", "joined.additional_details"),
    ("created_by", "joined.created_by"),
    ("last_modified_by", "joined.last_modified_by"),
    ("created_time", "joined.created_time"),
    ("last_modified_time", "joined.last_modified_time"),
    ("edited", "edited"),
    ("user_name", "users.user_name"),
    ("name_of_user", "users.name_of_user"),
    ("role", "users.role"),
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
    entity="muster_roll_test",
    python_entity="muster_roll",
    driving_table="stg_muster_roll",
    slice_filter_class=MusterRollSliceFilter,
    target=SilverTarget(
        silver_table="muster_roll_entity",
        joined_rows_sql=joined_rows_sql,
        helper_expressions=helper_expressions,
        silver_columns=SILVER_COLUMNS,
        lookups=(
            boundary_lookup(boundary_keys_sql, code_expr="joined.boundary_code"),
            user_lookup(user_keys_sql, user_expr="joined.last_modified_by"),
        ),
    ),
)

# The Airflow DAG object for this entity, built from SPEC by sql_flatten_common
# (airflow safe-mode discovery needs the words "airflow" and "dag" in this file).
dag = build_sql_test_dag(SPEC)
