"""
attendance_register_test_transformation.py

TEST twin of attendance_register_transformation.py for the
`attendance_register` entity: the flatten + write runs inside ClickHouse as one
INSERT ... SELECT per slice (see sql_flatten_common.py for how a run works).
Writes to <database>.attendance_register_entity_sql_test, never to
attendance_register_entity. Entity name `attendance_register_test`.

Known differences from attendance_register_transformation.py (all deliberate)
    - stg_attendance_register is read with FINAL (the Python DAG reads every version).
    - attendees_count / staffs_count count each child row ONCE (newest version);
      the Python DAG counts every bronze row of the child tables, so a
      re-ingested attendee would count twice there. attendees_info lists the
      attendees' users in child-row id order.
    - stg_project is read with FINAL and stg_project_address through 14's
      prj_by_project as one boundary per project.
    - transformer_time_stamp is now() at statement time (once per slice) rather
      than once per run; it is not part of the comparison.
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
    json_string_sql,
    project_address_join_sql,
    project_join_sql,
    table,
    user_info_json_lookup,
)

# Sources, in the order they appear in the query:
#
#   source                          alias            keyed / joined on                                     feeds
#   ------------------------------  ---------------  ----------------------------------------------------  ---------------------------------------
#   stg_attendance_register (FINAL) register         tenant_id + id range of the slice                     every plain column, campaign_number
#                                                                                                          (a bronze column here)
#   stg_project (FINAL)             project          project.id = register.reference_id AND tenant         project_* columns, hierarchy_type
#   stg_project_address             project_address  project_address.project_id = project.id AND tenant    boundary lookup FALLBACK after
#                                                    (14's prj_by_project)                                 register.locality_code
#   stg_attendance_attendee         attendees        attendees.register_id = register.id AND tenant        attendees_count, and the attendees'
#                                                    (18's prj_by_register, grouped per register)          individuals -> user uuids
#   stg_individual (FINAL)          -- inside        individual.id = attendee.individual_id AND tenant     user_uuid per attendee
#                                   attendees        (own PK), deleted individuals excluded
#   stg_attendance_staff            staff            staff.register_id = register.id AND tenant            staffs_count
#                                                    (18's prj_by_register, grouped per register)
#   boundary-service response       levels           (tenant_id, hierarchy_type, lookup_boundary_code)     level_one_code .. level_nine_code
#   user-service response           attendee_users   Map(user_uuid -> user info JSON) for the tenant       attendees_info


class AttendanceRegisterSliceFilter(SliceFilter):
    driving_alias = "register"

    def projects(self) -> str:
        return self.own_key_in("id", self.slice_column("reference_id"))

    def project_addresses(self) -> str:
        return self.own_key_in("project_id", self.slice_column("reference_id"))

    def attendees(self) -> str:
        return f"tenant_id = {self.tenant} AND {self.id_range('register_id')} AND register_id IN {self.in_window_ids}"

    def staff(self) -> str:
        return f"tenant_id = {self.tenant} AND {self.id_range('register_id')} AND register_id IN {self.in_window_ids}"

    def attendee_individuals(self) -> str:
        """The slice's attendees' individual ids (raw superset)."""
        return self.own_key_in(
            "id",
            f"(SELECT DISTINCT individual_id FROM {table('stg_attendance_attendee')} "
            f"WHERE {self.attendees()} AND individual_id != '')",
        )


LOOKUP_BOUNDARY_CODE_SQL = "if(register.locality_code != '', register.locality_code, project_address.latest_boundary)"


def attendee_rows_sql(f: AttendanceRegisterSliceFilter) -> str:
    """One row per attendee child row (newest version) with its individual's user uuid."""
    return f"""
        SELECT
            attendee.tenant_id   AS tenant_id,
            attendee.register_id AS register_id,
            attendee.id          AS id,
            individual.user_uuid AS user_uuid
        FROM
        (
            SELECT tenant_id, register_id, id, argMax(individual_id, last_modified_time) AS latest_individual_id
            FROM {table('stg_attendance_attendee')}
            WHERE {f.attendees()}
            GROUP BY tenant_id, register_id, id
        ) AS attendee
        LEFT JOIN
        (
            SELECT id, tenant_id, user_uuid
            FROM {table('stg_individual')} FINAL
            WHERE {f.attendee_individuals()} AND is_deleted = false
        ) AS individual
            ON individual.id = attendee.latest_individual_id AND individual.tenant_id = attendee.tenant_id"""


def attendees_join_sql(f: AttendanceRegisterSliceFilter) -> str:
    """LEFT JOIN the per-register attendee rollup AS attendees: the count, and
    the distinct non-empty user uuids in child-row id order."""
    return f"""
    LEFT JOIN
    (
        SELECT
            tenant_id,
            register_id,
            count() AS attendee_count,
            arrayDistinct(arrayFilter(u -> u != '', arrayMap(t -> t.2, arraySort(t -> t.1, groupArray((id, user_uuid)))))) AS user_uuids
        FROM
        ({attendee_rows_sql(f)}
        )
        GROUP BY tenant_id, register_id
    ) AS attendees
        ON attendees.register_id = register.id AND attendees.tenant_id = register.tenant_id"""


def staff_join_sql(f: AttendanceRegisterSliceFilter) -> str:
    """LEFT JOIN the per-register staff count AS staff (one per child row, newest version)."""
    return f"""
    LEFT JOIN
    (
        SELECT tenant_id, register_id, count() AS staff_count
        FROM
        (
            SELECT tenant_id, register_id, id
            FROM {table('stg_attendance_staff')}
            WHERE {f.staff()}
            GROUP BY tenant_id, register_id, id
        )
        GROUP BY tenant_id, register_id
    ) AS staff
        ON staff.register_id = register.id AND staff.tenant_id = register.tenant_id"""


def _project_joins(f: AttendanceRegisterSliceFilter) -> str:
    return (
        project_join_sql(f.projects(), "register.reference_id", "register.tenant_id")
        + project_address_join_sql(f.project_addresses(), "project.id", "register.tenant_id")
    )


def joined_rows_sql(f: AttendanceRegisterSliceFilter) -> str:
    """One row per attendance register for the slice, aliased `joined` by the caller."""
    return f"""
    SELECT
        register.id                     AS id,
        register.tenant_id              AS tenant_id,
        register.register_number        AS register_number,
        register.name                   AS name,
        register.start_date             AS start_date,
        register.end_date               AS end_date,
        register.status                 AS status,
        register.additional_details     AS additional_details,
        register.created_by             AS created_by,
        register.last_modified_by       AS last_modified_by,
        register.created_time           AS created_time,
        register.last_modified_time     AS last_modified_time,
        register.reference_id           AS reference_id,
        register.service_code           AS service_code,
        register.campaign_number        AS campaign_number,
        project.project_type            AS project_type,
        project.project_type_id         AS project_type_id,
        project.name                    AS project_name,
        JSONExtractString(project.additional_details, 'hierarchyType') AS hierarchy_type,
        {LOOKUP_BOUNDARY_CODE_SQL}      AS lookup_boundary_code,
        attendees.attendee_count        AS attendees_count,
        attendees.user_uuids            AS attendee_user_uuids,
        staff.staff_count               AS staffs_count
    FROM {table('stg_attendance_register')} AS register FINAL{_project_joins(f)}{attendees_join_sql(f)}{staff_join_sql(f)}
    WHERE {f.driving()}"""


def boundary_keys_sql(f: AttendanceRegisterSliceFilter) -> str:
    return f"""
SELECT DISTINCT tenant_id, hierarchy_type, boundary_code
FROM
(
    SELECT
        register.tenant_id                                             AS tenant_id,
        JSONExtractString(project.additional_details, 'hierarchyType') AS hierarchy_type,
        {LOOKUP_BOUNDARY_CODE_SQL}                                     AS boundary_code
    FROM {table('stg_attendance_register')} AS register FINAL{_project_joins(f)}
    WHERE {f.driving()}
)
WHERE hierarchy_type != '' AND boundary_code != ''
"""


def attendee_user_keys_sql(f: AttendanceRegisterSliceFilter) -> str:
    """(tenant_id, user_uuid) of every attendee of the slice's registers."""
    return f"""
SELECT DISTINCT tenant_id, user_uuid AS user_id
FROM
({attendee_rows_sql(f)}
)
WHERE user_uuid != ''
"""


def helper_expressions(f: AttendanceRegisterSliceFilter) -> list[tuple[str, str]]:
    return [
        # attendees_info (= _build_register_rollups): {user_uuid: user info dict} for the register's attendees
        ("attendees_info_json",
         "concat('{', arrayStringConcat(arrayMap(u -> concat(" + json_string_sql("u") + ", ': ', attendee_users[u]), "
         "arrayFilter(u -> mapContains(attendee_users, u), joined.attendee_user_uuids)), ', '), '}')"),
    ]


# (silver column, SQL expression) in attendance_register_entity's column order.
SILVER_COLUMNS: list[tuple[str, str]] = [
    ("id", "joined.id"),
    ("tenant_id", "joined.tenant_id"),
    ("register_number", "joined.register_number"),
    ("name", "joined.name"),
    ("reference_id", "joined.reference_id"),
    ("service_code", "joined.service_code"),
    ("start_date", "joined.start_date"),
    ("end_date", "joined.end_date"),
    ("status", "joined.status"),
    ("register_additional_details", "joined.additional_details"),
    ("created_by", "joined.created_by"),
    ("last_modified_by", "joined.last_modified_by"),
    ("created_time", "joined.created_time"),
    ("last_modified_time", "joined.last_modified_time"),
    ("attendees_info", "attendees_info_json"),
    ("transformer_time_stamp", "now64(3, 'UTC')"),
    ("staffs_count", "toInt64(joined.staffs_count)"),
    ("attendees_count", "toInt64(joined.attendees_count)"),
    *[(column, f"levels.{column}") for column in LEVEL_COLUMNS],
    ("hierarchy_type", "if(joined.lookup_boundary_code != '', joined.hierarchy_type, '')"),
    ("project_id", "joined.reference_id"),
    ("project_type", "joined.project_type"),
    ("project_type_id", "joined.project_type_id"),
    ("project_name", "joined.project_name"),
    ("campaign_number", "joined.campaign_number"),
    ("campaign_id", "''"),
]


SPEC = EntitySpec(
    entity="attendance_register_test",
    python_entity="attendance_register",
    driving_table="stg_attendance_register",
    slice_filter_class=AttendanceRegisterSliceFilter,
    target=SilverTarget(
        silver_table="attendance_register_entity",
        joined_rows_sql=joined_rows_sql,
        helper_expressions=helper_expressions,
        silver_columns=SILVER_COLUMNS,
        lookups=(
            boundary_lookup(boundary_keys_sql, code_expr="joined.lookup_boundary_code"),
            user_info_json_lookup(attendee_user_keys_sql, alias="attendee_users"),
        ),
    ),
)

# The Airflow DAG object for this entity, built from SPEC by sql_flatten_common
# (airflow safe-mode discovery needs the words "airflow" and "dag" in this file).
dag = build_sql_test_dag(SPEC)
