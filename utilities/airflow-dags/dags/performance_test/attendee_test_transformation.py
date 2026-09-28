"""
attendee_test_transformation.py

TEST twin of attendee_transformation.py for the `attendee` entity: the
flatten + write runs inside ClickHouse as one INSERT ... SELECT per slice
(see sql_flatten_common.py for how a run works). Writes to
<database>.attendee_entity_sql_test, never to attendee_entity.
Entity name `attendee_test` (dag_id attendee_test_transformation).

Known differences from attendee_transformation.py (all deliberate)
    - stg_attendance_attendee is read with FINAL (the Python DAG reads every version).
    - The user -> project bridge takes the newest version of each staff row and
      drops it if deleted, then the lowest staff row id per user; stg_project is
      read with FINAL and stg_project_address as one boundary per project.
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
    own_key_join_sql,
    parse_boundary_code_sql,
    project_address_join_sql,
    project_join_sql,
    staff_bridge_join_sql,
    staff_projects_subquery_sql,
    table,
    user_lookup,
)

# Sources, in the order they appear in the query:
#
#   source                          alias            keyed / joined on                                      feeds
#   ------------------------------  ---------------  -----------------------------------------------------  ----------------------------------------
#   stg_attendance_attendee (FINAL) attendee         tenant_id + id range of the slice                      every plain column; its additional_details
#                                                                                                           `boundaryCode` is the FIRST boundary code
#   stg_individual (FINAL)          individual       individual.id = attendee.individual_id AND tenant      user_uuid: the key of the user lookup AND
#                                                    (own PK), deleted individuals excluded                 of the project bridge
#   stg_attendance_register (FINAL) register         register.id = attendee.register_id AND tenant (own PK) register_service_code, register_name,
#                                                                                                           register_number
#   stg_project_staff               staff            staff.staff_id = individual.user_uuid AND tenant       the project id of the user's lowest-id
#                                                    (17's prj_by_staff)                                    non-deleted staff row
#   stg_project (FINAL)             project          project.id = staff.first_project_id AND tenant         project_* columns, campaign_number,
#                                                                                                           hierarchy_type
#   stg_project_address             project_address  project_address.project_id = project.id AND tenant     the boundary code FALLBACK
#                                                    (14's prj_by_project)
#   boundary-service response       levels           (tenant_id, hierarchy_type, lookup_boundary_code)      level_one_code .. level_nine_code
#   user-service response           users            (tenant_id, individual.user_uuid)                      user_name, name_of_user, role


class AttendeeSliceFilter(SliceFilter):
    driving_alias = "attendee"

    def individuals(self) -> str:
        return self.own_key_in("id", self.slice_column("individual_id"))

    def registers(self) -> str:
        return self.own_key_in("id", self.slice_column("register_id"))

    def user_uuids(self) -> str:
        return (
            f"(SELECT DISTINCT user_uuid FROM {table('stg_individual')} "
            f"WHERE {self.individuals()} AND user_uuid != '')"
        )

    def staff(self) -> str:
        return self.own_key_in("staff_id", self.user_uuids())

    def projects(self) -> str:
        return self.own_key_in("id", staff_projects_subquery_sql(self.staff()))

    def project_addresses(self) -> str:
        return self.own_key_in("project_id", staff_projects_subquery_sql(self.staff()))


LOOKUP_BOUNDARY_CODE_SQL = (
    f"if({parse_boundary_code_sql('attendee.additional_details')} != '', "
    f"{parse_boundary_code_sql('attendee.additional_details')}, project_address.latest_boundary)"
)


def _individual_join(f: AttendeeSliceFilter) -> str:
    return own_key_join_sql("stg_individual", f"{f.individuals()} AND is_deleted = false", "individual",
                            "id, tenant_id, user_uuid",
                            [("id", "attendee.individual_id"), ("tenant_id", "attendee.tenant_id")])


def _joins(f: AttendeeSliceFilter) -> str:
    return (
        _individual_join(f)
        + own_key_join_sql("stg_attendance_register", f.registers(), "register",
                           "id, tenant_id, name, service_code, register_number",
                           [("id", "attendee.register_id"), ("tenant_id", "attendee.tenant_id")])
        + staff_bridge_join_sql(f.staff(), "individual.user_uuid", "attendee.tenant_id")
        + project_join_sql(f.projects(), "staff.first_project_id", "attendee.tenant_id")
        + project_address_join_sql(f.project_addresses(), "project.id", "attendee.tenant_id")
    )


def joined_rows_sql(f: AttendeeSliceFilter) -> str:
    """One row per attendee for the slice, aliased `joined` by the caller."""
    return f"""
    SELECT
        attendee.id                       AS id,
        attendee.individual_id            AS individual_id,
        attendee.register_id              AS register_id,
        attendee.enrollment_date          AS enrollment_date,
        attendee.deenrollment_date        AS deenrollment_date,
        attendee.additional_details       AS additional_details,
        attendee.created_by               AS created_by,
        attendee.last_modified_by         AS last_modified_by,
        attendee.created_time             AS created_time,
        attendee.last_modified_time       AS last_modified_time,
        attendee.tenant_id                AS tenant_id,
        individual.user_uuid              AS user_uuid,
        register.name                     AS register_name,
        register.service_code             AS register_service_code,
        register.register_number          AS register_number,
        project.id                        AS project_id,
        project.project_type              AS project_type,
        project.project_type_id           AS project_type_id,
        project.name                      AS project_name,
        project.reference_id              AS campaign_number,
        JSONExtractString(project.additional_details, 'hierarchyType') AS hierarchy_type,
        {LOOKUP_BOUNDARY_CODE_SQL}        AS lookup_boundary_code
    FROM {table('stg_attendance_attendee')} AS attendee FINAL{_joins(f)}
    WHERE {f.driving()}"""


def boundary_keys_sql(f: AttendeeSliceFilter) -> str:
    return f"""
SELECT DISTINCT tenant_id, hierarchy_type, boundary_code
FROM
(
    SELECT
        attendee.tenant_id                                             AS tenant_id,
        JSONExtractString(project.additional_details, 'hierarchyType') AS hierarchy_type,
        {LOOKUP_BOUNDARY_CODE_SQL}                                     AS boundary_code
    FROM {table('stg_attendance_attendee')} AS attendee FINAL{_joins(f)}
    WHERE {f.driving()}
)
WHERE hierarchy_type != '' AND boundary_code != ''
"""


def user_keys_sql(f: AttendeeSliceFilter) -> str:
    return f"""
SELECT DISTINCT attendee.tenant_id AS tenant_id, individual.user_uuid AS user_id
FROM {table('stg_attendance_attendee')} AS attendee FINAL{_individual_join(f)}
WHERE {f.driving()} AND individual.user_uuid != ''
"""


def helper_expressions(f: AttendeeSliceFilter) -> list[tuple[str, str]]:
    return []


# (silver column, SQL expression) in attendee_entity's column order.
SILVER_COLUMNS: list[tuple[str, str]] = [
    ("id", "joined.id"),
    ("tenant_id", "joined.tenant_id"),
    ("register_id", "joined.register_id"),
    ("individual_id", "joined.individual_id"),
    ("enrollment_date", "toFloat64(joined.enrollment_date)"),
    ("denrollment_date", "toFloat64(joined.deenrollment_date)"),
    ("created_by", "joined.created_by"),
    ("last_modified_by", "joined.last_modified_by"),
    ("created_time", "joined.created_time"),
    ("last_modified_time", "joined.last_modified_time"),
    ("additional_details", "joined.additional_details"),
    ("user_name", "users.user_name"),
    ("name_of_user", "users.name_of_user"),
    ("role", "users.role"),
    ("register_service_code", "joined.register_service_code"),
    ("register_name", "joined.register_name"),
    ("register_number", "joined.register_number"),
    *[(column, f"levels.{column}") for column in LEVEL_COLUMNS],
    ("hierarchy_type", "if(joined.lookup_boundary_code != '', joined.hierarchy_type, '')"),
    ("project_id", "joined.project_id"),
    ("project_type", "joined.project_type"),
    ("project_type_id", "joined.project_type_id"),
    ("project_name", "joined.project_name"),
    ("campaign_number", "joined.campaign_number"),
    ("campaign_id", "''"),
]


SPEC = EntitySpec(
    entity="attendee_test",
    python_entity="attendee",
    driving_table="stg_attendance_attendee",
    slice_filter_class=AttendeeSliceFilter,
    target=SilverTarget(
        silver_table="attendee_entity",
        joined_rows_sql=joined_rows_sql,
        helper_expressions=helper_expressions,
        silver_columns=SILVER_COLUMNS,
        lookups=(
            boundary_lookup(boundary_keys_sql, code_expr="joined.lookup_boundary_code"),
            user_lookup(user_keys_sql, user_expr="joined.user_uuid"),
        ),
    ),
)

# The Airflow DAG object for this entity, built from SPEC by sql_flatten_common
# (airflow safe-mode discovery needs the words "airflow" and "dag" in this file).
dag = build_sql_test_dag(SPEC)
