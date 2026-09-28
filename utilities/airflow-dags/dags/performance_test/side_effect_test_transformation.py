"""
side_effect_test_transformation.py

TEST twin of side_effect_transformation.py for the `side_effect` entity: the
flatten + write runs inside ClickHouse as one INSERT ... SELECT per slice
(see sql_flatten_common.py for how a run works). Writes to
<database>.side_effect_entity_sql_test, never to side_effect_entity.
Entity name `side_effect_test` (dag_id side_effect_test_transformation).

The Python DAG resolves four dependent lookup rounds per chunk (task ->
project beneficiary -> individual, and the user -> project bridge); here they
are four slice-bounded joins in the one statement, each collapsed to one row
per key the way the Python DAG's `ORDER BY id ASC LIMIT 1 BY <key>` does.

Known differences from side_effect_transformation.py (all deliberate)
    - stg_side_effect is read with FINAL (the Python DAG reads every version).
    - The task, beneficiary and individual sides take each row's newest version
      before picking the lowest id per key (the Python DAG picks the lowest id
      among ALL versions, deleted ones included -- so does this twin: no
      is_deleted filter on those three sides, as in the Python DAG).
    - The user -> project bridge takes the newest version of each staff row and
      drops it if deleted, then the lowest staff row id per user.
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
    address_join_sql,
    age_expressions,
    boundary_lookup,
    build_sql_test_dag,
    field_pairs_sql,
    json_int_or_null_sql,
    json_object_sql,
    json_value_sql,
    one_row_per_key_sql,
    project_address_join_sql,
    project_join_sql,
    staff_bridge_join_sql,
    staff_projects_subquery_sql,
    table,
    user_lookup,
)

# Sources, in the order they appear in the query:
#
#   source                     alias            keyed / joined on                                        feeds
#   -------------------------  ---------------  -------------------------------------------------------  ---------------------------------------
#   stg_side_effect (FINAL)    side_effect      tenant_id + id range of the slice                        every plain column, raw_symptoms,
#                                                                                                        symptoms, additional_fields
#   stg_project_task           task             task.client_reference_id =                               the beneficiary reference, the task's
#                                               side_effect.task_client_reference_id AND tenant          address and project, cycleIndex
#                                               (18's prj_by_client_ref, lowest task id per reference)
#   stg_address (FINAL)        address          address.id = task.latest_address_id AND tenant           boundary code (first tier)
#   stg_project (FINAL)        task_project     task_project.id = task.latest_project_id AND tenant      hierarchy_type (ONLY from the task's
#                                                                                                        project, never the staff bridge's)
#   stg_project_address        task_address     project_id = task_project.id AND tenant                  boundary code fallback
#                                               (14's prj_by_project)
#   stg_project_beneficiary    beneficiary      beneficiary.client_reference_id = the TASK's             the individual's reference
#                                               project_beneficiary_client_reference_id AND tenant
#                                               (16's prj_by_client_ref, lowest id per reference)
#   stg_individual             individual       individual.client_reference_id = beneficiary ref         date_of_birth, age, gender,
#                                               AND tenant (16's prj_by_client_ref, lowest id)           height / disabilityType
#   stg_project_staff          staff            staff.staff_id = side_effect.client_last_modified_by     the project id of the user's lowest-id
#                                               AND tenant (17's prj_by_staff)                           non-deleted staff row -> project_* trailer
#   stg_project (FINAL)        project          project.id = staff.first_project_id AND tenant           project_* columns, campaign_number
#   boundary-service response  levels           (tenant_id, task project hierarchyType, boundary_code)   level_one_code .. level_nine_code
#   user-service response      users            (tenant_id, client_created_by)                           user_name, name_of_user, role, user_address


class SideEffectSliceFilter(SliceFilter):
    driving_alias = "side_effect"

    def tasks(self) -> str:
        return self.own_key_in("client_reference_id", self.slice_column("task_client_reference_id"))

    def task_addresses(self) -> str:
        return self.own_key_in(
            "id", f"(SELECT DISTINCT address_id FROM {table('stg_project_task')} WHERE {self.tasks()} AND address_id != '')")

    def task_project_ids(self) -> str:
        return f"(SELECT DISTINCT project_id FROM {table('stg_project_task')} WHERE {self.tasks()} AND project_id != '')"

    def task_projects(self) -> str:
        return self.own_key_in("id", self.task_project_ids())

    def task_project_addresses(self) -> str:
        return self.own_key_in("project_id", self.task_project_ids())

    def beneficiaries(self) -> str:
        return self.own_key_in(
            "client_reference_id",
            f"(SELECT DISTINCT project_beneficiary_client_reference_id FROM {table('stg_project_task')} "
            f"WHERE {self.tasks()} AND project_beneficiary_client_reference_id != '')")

    def individuals(self) -> str:
        return self.own_key_in(
            "client_reference_id",
            f"(SELECT DISTINCT beneficiary_client_reference_id FROM {table('stg_project_beneficiary')} "
            f"WHERE {self.beneficiaries()} AND beneficiary_client_reference_id != '')")

    def staff(self) -> str:
        return self.own_key_in("staff_id", self.slice_column("client_last_modified_by"))

    def projects(self) -> str:
        return self.own_key_in("id", staff_projects_subquery_sql(self.staff()))


def task_join_sql(f: SideEffectSliceFilter) -> str:
    inner = one_row_per_key_sql(
        "stg_project_task", f.tasks(), "client_reference_id",
        ["project_beneficiary_client_reference_id", "additional_details", "address_id", "project_id"],
        has_is_deleted=False, pick="lowest_id",
    )
    return f"""
    LEFT JOIN
    ({inner}
    ) AS task
        ON task.client_reference_id = side_effect.task_client_reference_id AND task.tenant_id = side_effect.tenant_id"""


def beneficiary_join_sql(f: SideEffectSliceFilter) -> str:
    inner = one_row_per_key_sql(
        "stg_project_beneficiary", f.beneficiaries(), "client_reference_id",
        ["beneficiary_client_reference_id"], has_is_deleted=False, pick="lowest_id",
    )
    return f"""
    LEFT JOIN
    ({inner}
    ) AS beneficiary
        ON beneficiary.client_reference_id = task.latest_project_beneficiary_client_reference_id
       AND beneficiary.tenant_id = side_effect.tenant_id"""


def individual_join_sql(f: SideEffectSliceFilter) -> str:
    inner = one_row_per_key_sql(
        "stg_individual", f.individuals(), "client_reference_id",
        ["date_of_birth", "gender", "additional_details"], has_is_deleted=False, pick="lowest_id",
    )
    return f"""
    LEFT JOIN
    ({inner}
    ) AS individual
        ON individual.client_reference_id = beneficiary.latest_beneficiary_client_reference_id
       AND individual.tenant_id = side_effect.tenant_id"""


BOUNDARY_CODE_SQL = "if(address.locality_code != '', address.locality_code, task_address.latest_boundary)"


def _boundary_joins(f: SideEffectSliceFilter) -> str:
    return (
        task_join_sql(f)
        + address_join_sql(f.task_addresses(), "task.latest_address_id", "side_effect.tenant_id",
                           columns="id, tenant_id, locality_code")
        + project_join_sql(f.task_projects(), "task.latest_project_id", "side_effect.tenant_id",
                           alias="task_project", columns="id, tenant_id, additional_details")
        + project_address_join_sql(f.task_project_addresses(), "task_project.id", "side_effect.tenant_id",
                                   alias="task_address")
    )


def _joins(f: SideEffectSliceFilter) -> str:
    return (
        _boundary_joins(f)
        + beneficiary_join_sql(f)
        + individual_join_sql(f)
        + staff_bridge_join_sql(f.staff(), "side_effect.client_last_modified_by", "side_effect.tenant_id")
        + project_join_sql(f.projects(), "staff.first_project_id", "side_effect.tenant_id")
    )


def joined_rows_sql(f: SideEffectSliceFilter) -> str:
    """One row per side effect for the slice, aliased `joined` by the caller."""
    return f"""
    SELECT
        side_effect.id                                       AS id,
        side_effect.client_reference_id                      AS client_reference_id,
        side_effect.tenant_id                                AS tenant_id,
        side_effect.task_id                                  AS task_id,
        side_effect.task_client_reference_id                 AS task_client_reference_id,
        side_effect.project_beneficiary_id                   AS project_beneficiary_id,
        side_effect.project_beneficiary_client_reference_id  AS project_beneficiary_client_reference_id,
        side_effect.symptoms                                 AS symptoms,
        side_effect.created_by                               AS created_by,
        side_effect.created_time                             AS created_time,
        side_effect.last_modified_by                         AS last_modified_by,
        side_effect.last_modified_time                       AS last_modified_time,
        side_effect.client_created_by                        AS client_created_by,
        side_effect.client_created_time                      AS client_created_time,
        side_effect.client_last_modified_by                  AS client_last_modified_by,
        side_effect.client_last_modified_time                AS client_last_modified_time,
        side_effect.row_version                              AS row_version,
        side_effect.is_deleted                               AS is_deleted,
        side_effect.additional_details                       AS additional_details,
        task.latest_additional_details                       AS task_additional_details,
        JSONExtractString(task_project.additional_details, 'hierarchyType') AS hierarchy_type,
        {BOUNDARY_CODE_SQL}                                  AS boundary_code,
        beneficiary.latest_beneficiary_client_reference_id   AS beneficiary_client_reference_id,
        individual.client_reference_id != ''                 AS individual_matched,
        individual.latest_date_of_birth                      AS individual_date_of_birth,
        individual.latest_gender                             AS individual_gender,
        individual.latest_additional_details                 AS individual_additional_details,
        project.id                                           AS project_id,
        project.project_type                                 AS project_type,
        project.project_type_id                              AS project_type_id,
        project.name                                         AS project_name,
        project.reference_id                                 AS campaign_number
    FROM {table('stg_side_effect')} AS side_effect FINAL{_joins(f)}
    WHERE {f.driving()}"""


def boundary_keys_sql(f: SideEffectSliceFilter) -> str:
    return f"""
SELECT DISTINCT tenant_id, hierarchy_type, boundary_code
FROM
(
    SELECT
        side_effect.tenant_id                                               AS tenant_id,
        JSONExtractString(task_project.additional_details, 'hierarchyType') AS hierarchy_type,
        {BOUNDARY_CODE_SQL}                                                 AS boundary_code
    FROM {table('stg_side_effect')} AS side_effect FINAL{_boundary_joins(f)}
    WHERE {f.driving()}
)
WHERE hierarchy_type != '' AND boundary_code != ''
"""


def user_keys_sql(f: SideEffectSliceFilter) -> str:
    return f"""
SELECT DISTINCT side_effect.tenant_id AS tenant_id, side_effect.client_created_by AS user_id
FROM {table('stg_side_effect')} AS side_effect FINAL
WHERE {f.driving()} AND side_effect.client_created_by != ''
"""


def helper_expressions(f: SideEffectSliceFilter) -> list[tuple[str, str]]:
    return [
        # (= _build_derived_additional_details) own fields, then the task's cycleIndex if the task has one,
        # then the individual's height (int) + disabilityType, only together and only if height parses
        ("own_pairs", field_pairs_sql("joined.additional_details")),
        ("task_fields",
         "arrayFilter(fld -> JSONHas(fld, 'key'), JSONExtractArrayRaw(joined.task_additional_details, 'fields'))"),
        ("task_cycle_index_field", "arrayLast(fld -> JSONExtractString(fld, 'key') = 'cycleIndex', task_fields)"),
        ("task_cycle_pairs",
         f"if(task_cycle_index_field != '', [('cycleIndex', {json_value_sql('task_cycle_index_field')})], [])"),
        ("individual_fields",
         "arrayFilter(fld -> JSONHas(fld, 'key'), JSONExtractArrayRaw(joined.individual_additional_details, 'fields'))"),
        ("height_field", "arrayLast(fld -> JSONExtractString(fld, 'key') = 'height', individual_fields)"),
        ("disability_field", "arrayLast(fld -> JSONExtractString(fld, 'key') = 'disabilityType', individual_fields)"),
        ("height_json", json_int_or_null_sql("height_field")),
        ("individual_extra_pairs",
         f"if(height_field != '' AND disability_field != '' AND height_json != 'null', "
         f"[('height', height_json), ('disabilityType', {json_value_sql('disability_field')})], [])"),
        ("additional_details_json",
         json_object_sql("arrayConcat(own_pairs, task_cycle_pairs, individual_extra_pairs)")),
        # symptoms (= _build_symptoms): the raw JSON array of strings, comma-joined
        ("symptoms_text",
         "if(JSONType(joined.symptoms) = 'Array', arrayStringConcat(JSONExtract(joined.symptoms, 'Array(String)'), ','), '')"),
        *age_expressions("joined.individual_date_of_birth"),
    ]


# (silver column, SQL expression) in side_effect_entity's column order.
SILVER_COLUMNS: list[tuple[str, str]] = [
    ("id", "joined.id"),
    ("client_reference_id", "joined.client_reference_id"),
    ("task_id", "joined.task_id"),
    ("task_client_reference_id", "joined.task_client_reference_id"),
    ("project_beneficiary_id", "joined.project_beneficiary_id"),
    ("project_beneficiary_client_reference_id", "joined.project_beneficiary_client_reference_id"),
    ("raw_symptoms", "joined.symptoms"),
    ("tenant_id", "joined.tenant_id"),
    ("is_deleted", "joined.is_deleted"),
    ("row_version", "toInt32(joined.row_version)"),
    ("created_by", "joined.created_by"),
    ("last_modified_by", "joined.last_modified_by"),
    ("created_time", "joined.created_time"),
    ("last_modified_time", "joined.last_modified_time"),
    ("client_created_by", "joined.client_created_by"),
    ("client_last_modified_by", "joined.client_last_modified_by"),
    ("client_created_time", "joined.client_created_time"),
    ("client_last_modified_time", "joined.client_last_modified_time"),
    ("additional_fields", "joined.additional_details"),
    ("date_of_birth", "if(joined.individual_matched, date_of_birth_ms, toInt64(0))"),
    ("age", "if(joined.individual_matched, toInt32(age_months), toInt32(0))"),
    *[(column, f"levels.{column}") for column in LEVEL_COLUMNS],
    ("hierarchy_type", "if(joined.boundary_code != '', joined.hierarchy_type, '')"),
    ("boundary_code", "joined.boundary_code"),
    ("individual_id", "joined.beneficiary_client_reference_id"),
    ("gender", "if(joined.individual_matched, joined.individual_gender, '')"),
    ("symptoms", "symptoms_text"),
    ("user_name", "users.user_name"),
    ("name_of_user", "users.name_of_user"),
    ("role", "users.role"),
    ("user_address", "users.user_address"),
    ("task_dates", "toDate32(fromUnixTimestamp64Milli(joined.client_last_modified_time, 'UTC'))"),
    ("synced_date", "toDate32(fromUnixTimestamp64Milli(joined.last_modified_time, 'UTC'))"),
    ("additional_details", "additional_details_json"),
    ("project_id", "joined.project_id"),
    ("project_type", "joined.project_type"),
    ("project_type_id", "joined.project_type_id"),
    ("project_name", "joined.project_name"),
    ("campaign_number", "joined.campaign_number"),
    ("campaign_id", "''"),
]


SPEC = EntitySpec(
    entity="side_effect_test",
    python_entity="side_effect",
    driving_table="stg_side_effect",
    slice_filter_class=SideEffectSliceFilter,
    target=SilverTarget(
        silver_table="side_effect_entity",
        joined_rows_sql=joined_rows_sql,
        helper_expressions=helper_expressions,
        silver_columns=SILVER_COLUMNS,
        lookups=(
            boundary_lookup(boundary_keys_sql, code_expr="joined.boundary_code"),
            user_lookup(user_keys_sql, user_expr="joined.client_created_by"),
        ),
    ),
)

# The Airflow DAG object for this entity, built from SPEC by sql_flatten_common
# (airflow safe-mode discovery needs the words "airflow" and "dag" in this file).
dag = build_sql_test_dag(SPEC)
