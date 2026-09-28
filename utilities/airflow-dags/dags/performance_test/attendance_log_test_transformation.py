"""
attendance_log_test_transformation.py

TEST twin of attendance_log_transformation.py for the `attendance_log`
entity: the flatten + write runs inside ClickHouse as one INSERT ... SELECT
per slice (see sql_flatten_common.py for how a run works). Writes to
<database>.attendance_log_entity_sql_test, never to attendance_log_entity.
Entity name `attendance_log_test`.

Known differences from attendance_log_transformation.py (all deliberate)
    - stg_attendance_log is read with FINAL (the Python DAG reads every version).
    - The two user -> project bridges (created_by for the boundary, last_modified_by
      for the project trailer) take the newest version of each staff row and drop
      it if deleted, then the lowest staff row id per user; stg_project is read
      with FINAL and stg_project_address as one boundary per project.
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
#   source                          alias               keyed / joined on                                    feeds
#   ------------------------------  ------------------  ---------------------------------------------------  --------------------------------------
#   stg_attendance_log (FINAL)      log                 tenant_id + id range of the slice                    every plain column, attendance_time
#   stg_individual (FINAL)          individual          individual.id = log.individual_id AND tenant         user_uuid: the attendee user lookup key
#                                                       (own PK), deleted individuals excluded
#   stg_attendance_register (FINAL) register            register.id = log.register_id AND tenant (own PK)    register_service_code/name/number
#   stg_project_staff               created_by_staff    staff_id = log.created_by AND tenant                 the attendance taker's project ...
#   stg_project (FINAL)             created_by_project  id = created_by_staff.first_project_id AND tenant    ... whose hierarchyType and boundary
#   stg_project_address             created_by_address  project_id = created_by_project.id AND tenant        drive the boundary lookup (fallback code)
#   stg_project_staff               modified_by_staff   staff_id = log.last_modified_by AND tenant           the last modifier's project ...
#   stg_project (FINAL)             modified_by_project id = modified_by_staff.first_project_id AND tenant   ... feeds project_* and campaign_number
#   boundary-service response       levels              (tenant_id, taker project hierarchyType,             level_one_code .. level_nine_code
#                                                       log additional_details boundaryCode else the taker
#                                                       project's boundary)
#   user-service response           users               (tenant_id, individual.user_uuid)                    user_name, name_of_user (the attendee)
#   user-service response           taker_users         (tenant_id, log.created_by)                          attendance_taker_user_name, ..._name_of_user,
#                                                                                                            role (the taker's role, never the attendee's)


class AttendanceLogSliceFilter(SliceFilter):
    driving_alias = "log"

    def individuals(self) -> str:
        return self.own_key_in("id", self.slice_column("individual_id"))

    def registers(self) -> str:
        return self.own_key_in("id", self.slice_column("register_id"))

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


LOOKUP_BOUNDARY_CODE_SQL = (
    f"if({parse_boundary_code_sql('log.additional_details')} != '', "
    f"{parse_boundary_code_sql('log.additional_details')}, created_by_address.latest_boundary)"
)


def _individual_join(f: AttendanceLogSliceFilter) -> str:
    return own_key_join_sql("stg_individual", f"{f.individuals()} AND is_deleted = false", "individual",
                            "id, tenant_id, user_uuid", [("id", "log.individual_id"), ("tenant_id", "log.tenant_id")])


def _boundary_joins(f: AttendanceLogSliceFilter) -> str:
    return (
        staff_bridge_join_sql(f.created_by_staff(), "log.created_by", "log.tenant_id", alias="created_by_staff")
        + project_join_sql(f.created_by_projects(), "created_by_staff.first_project_id", "log.tenant_id",
                           alias="created_by_project", columns="id, tenant_id, additional_details")
        + project_address_join_sql(f.created_by_project_addresses(), "created_by_project.id", "log.tenant_id",
                                   alias="created_by_address")
    )


def _joins(f: AttendanceLogSliceFilter) -> str:
    return (
        _individual_join(f)
        + own_key_join_sql("stg_attendance_register", f.registers(), "register",
                           "id, tenant_id, name, service_code, register_number",
                           [("id", "log.register_id"), ("tenant_id", "log.tenant_id")])
        + _boundary_joins(f)
        + staff_bridge_join_sql(f.modified_by_staff(), "log.last_modified_by", "log.tenant_id", alias="modified_by_staff")
        + project_join_sql(f.modified_by_projects(), "modified_by_staff.first_project_id", "log.tenant_id",
                           alias="modified_by_project")
    )


def joined_rows_sql(f: AttendanceLogSliceFilter) -> str:
    """One row per attendance log for the slice, aliased `joined` by the caller."""
    return f"""
    SELECT
        log.id                              AS id,
        log.individual_id                   AS individual_id,
        log.register_id                     AS register_id,
        log.status                          AS status,
        log.time                            AS time,
        log.event_type                      AS event_type,
        log.additional_details              AS additional_details,
        log.created_by                      AS created_by,
        log.last_modified_by                AS last_modified_by,
        log.created_time                    AS created_time,
        log.last_modified_time              AS last_modified_time,
        log.tenant_id                       AS tenant_id,
        individual.user_uuid                AS user_uuid,
        register.name                       AS register_name,
        register.service_code               AS register_service_code,
        register.register_number            AS register_number,
        JSONExtractString(created_by_project.additional_details, 'hierarchyType') AS hierarchy_type,
        {LOOKUP_BOUNDARY_CODE_SQL}          AS lookup_boundary_code,
        modified_by_project.id              AS project_id,
        modified_by_project.project_type    AS project_type,
        modified_by_project.project_type_id AS project_type_id,
        modified_by_project.name            AS project_name,
        modified_by_project.reference_id    AS campaign_number
    FROM {table('stg_attendance_log')} AS log FINAL{_joins(f)}
    WHERE {f.driving()}"""


def boundary_keys_sql(f: AttendanceLogSliceFilter) -> str:
    return f"""
SELECT DISTINCT tenant_id, hierarchy_type, boundary_code
FROM
(
    SELECT
        log.tenant_id                                                             AS tenant_id,
        JSONExtractString(created_by_project.additional_details, 'hierarchyType') AS hierarchy_type,
        {LOOKUP_BOUNDARY_CODE_SQL}                                                AS boundary_code
    FROM {table('stg_attendance_log')} AS log FINAL{_boundary_joins(f)}
    WHERE {f.driving()}
)
WHERE hierarchy_type != '' AND boundary_code != ''
"""


def attendee_user_keys_sql(f: AttendanceLogSliceFilter) -> str:
    return f"""
SELECT DISTINCT log.tenant_id AS tenant_id, individual.user_uuid AS user_id
FROM {table('stg_attendance_log')} AS log FINAL{_individual_join(f)}
WHERE {f.driving()} AND individual.user_uuid != ''
"""


def taker_user_keys_sql(f: AttendanceLogSliceFilter) -> str:
    return f"""
SELECT DISTINCT log.tenant_id AS tenant_id, log.created_by AS user_id
FROM {table('stg_attendance_log')} AS log FINAL
WHERE {f.driving()} AND log.created_by != ''
"""


def helper_expressions(f: AttendanceLogSliceFilter) -> list[tuple[str, str]]:
    return [
        # 'yyyy-MM-ddTHH:mm:ss.SSSZ' with a literal Z, UTC (= _format_attendance_time); '' for time 0
        ("attendance_time",
         "if(joined.time = 0, '', concat(formatDateTime(fromUnixTimestamp64Milli(joined.time, 'UTC'), '%Y-%m-%dT%H:%i:%S.', 'UTC'), "
         "leftPad(toString(joined.time % 1000), 3, '0'), 'Z'))"),
    ]


# (silver column, SQL expression) in attendance_log_entity's column order.
SILVER_COLUMNS: list[tuple[str, str]] = [
    ("id", "joined.id"),
    ("tenant_id", "joined.tenant_id"),
    ("register_id", "joined.register_id"),
    ("individual_id", "joined.individual_id"),
    ("log_user_name", "''"),   # AttendanceLog.userName has no bronze source
    ("time", "joined.time"),
    ("type", "joined.event_type"),
    ("status", "joined.status"),
    ("document_ids", "''"),    # List<Document> not modelled in bronze
    ("log_additional_details", "joined.additional_details"),
    ("created_by", "joined.created_by"),
    ("last_modified_by", "joined.last_modified_by"),
    ("created_time", "joined.created_time"),
    ("last_modified_time", "joined.last_modified_time"),
    ("attendance_taker_user_name", "taker_users.user_name"),
    ("attendance_taker_name_of_user", "taker_users.name_of_user"),
    ("user_name", "users.user_name"),
    ("name_of_user", "users.name_of_user"),
    ("role", "taker_users.role"),   # the taker's role, never the attendee's (per Java)
    ("attendance_time", "attendance_time"),
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
    entity="attendance_log_test",
    python_entity="attendance_log",
    driving_table="stg_attendance_log",
    slice_filter_class=AttendanceLogSliceFilter,
    target=SilverTarget(
        silver_table="attendance_log_entity",
        joined_rows_sql=joined_rows_sql,
        helper_expressions=helper_expressions,
        silver_columns=SILVER_COLUMNS,
        lookups=(
            boundary_lookup(boundary_keys_sql, code_expr="joined.lookup_boundary_code"),
            user_lookup(attendee_user_keys_sql, user_expr="joined.user_uuid", alias="users"),
            user_lookup(taker_user_keys_sql, user_expr="joined.created_by", alias="taker_users"),
        ),
    ),
)

# The Airflow DAG object for this entity, built from SPEC by sql_flatten_common
# (airflow safe-mode discovery needs the words "airflow" and "dag" in this file).
dag = build_sql_test_dag(SPEC)
