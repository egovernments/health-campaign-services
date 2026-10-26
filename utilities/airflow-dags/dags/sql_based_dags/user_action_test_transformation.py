"""
user_action_test_transformation.py

TEST twin of user_action_transformation.py for the `user_action` entity: the
flatten + write runs inside ClickHouse as one INSERT ... SELECT per slice
(see sql_flatten_common.py for how a run works). Writes to
<database>.user_action_entity_sql_test, never to user_action_entity.
Entity name `user_action_test` (dag_id user_action_test_transformation).

Known differences from user_action_transformation.py (all deliberate)
    - stg_user_action is read with FINAL (the Python DAG reads every version and
      relies on the silver table's own ReplacingMergeTree to collapse them).
    - now() is evaluated once per statement; a row that fails to build aborts
      the slice instead of being skipped with a log line.
    - additional_details values that hold a JSON object/array keep their source
      text (Python re-serialises them; only whitespace can differ); non-ASCII
      text is written as UTF-8, not \\uXXXX.
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
    json_loads_or_string_sql,
    json_object_sql,
    project_join_sql,
    table,
    user_lookup,
)

# Sources, in the order they appear in the query:
#
#   source                     alias        keyed / joined on                                     feeds
#   -------------------------  -----------  ----------------------------------------------------  --------------------------------------------
#   stg_user_action (FINAL)    user_action  tenant_id + id range of the slice                     every plain column, additional_fields,
#                                                                                                 the derived additional_details
#   stg_project (FINAL)        project      project.id = user_action.project_id AND tenant        project_* columns, campaign_number,
#                                           (own PK)                                              hierarchy_type
#   boundary-service response  levels       (tenant_id, hierarchy_type, user_action.boundary_code)  level_one_code .. level_nine_code
#                                           -- the entity's own boundary_code, no fallback
#   user-service response      users        (tenant_id, client_created_by)                        user_name, name_of_user, role


class UserActionSliceFilter(SliceFilter):
    driving_alias = "user_action"

    def projects(self) -> str:
        """For stg_project FINAL: own primary key (tenant_id, id)."""
        return self.own_key_in("id", self.slice_column("project_id"))


def joined_rows_sql(f: UserActionSliceFilter) -> str:
    """One row per user action for the slice, aliased `joined` by the caller.
    Unmatched project columns are '' (join_use_nulls = 0)."""
    return f"""
    SELECT
        user_action.id                        AS id,
        user_action.client_reference_id       AS client_reference_id,
        user_action.tenant_id                 AS tenant_id,
        user_action.project_id                AS project_id,
        user_action.latitude                  AS latitude,
        user_action.longitude                 AS longitude,
        user_action.location_accuracy         AS location_accuracy,
        user_action.boundary_code             AS boundary_code,
        user_action.action                    AS action,
        user_action.beneficiary_tag           AS beneficiary_tag,
        user_action.resource_tag              AS resource_tag,
        user_action.additional_details        AS additional_details,
        user_action.created_by                AS created_by,
        user_action.created_time              AS created_time,
        user_action.last_modified_by          AS last_modified_by,
        user_action.last_modified_time        AS last_modified_time,
        user_action.client_created_time       AS client_created_time,
        user_action.client_last_modified_time AS client_last_modified_time,
        user_action.client_created_by         AS client_created_by,
        user_action.client_last_modified_by   AS client_last_modified_by,
        project.project_type                  AS project_type,
        project.project_type_id               AS project_type_id,
        project.name                          AS project_name,
        project.reference_id                  AS campaign_number,
        JSONExtractString(project.additional_details, 'hierarchyType') AS hierarchy_type
    FROM {table('stg_user_action')} AS user_action FINAL{project_join_sql(f.projects(), 'user_action.project_id', 'user_action.tenant_id')}
    WHERE {f.driving()}"""


def boundary_keys_sql(f: UserActionSliceFilter) -> str:
    """(tenant_id, hierarchyType of the project, the action's own boundary_code),
    both non-empty (= user_action_transformation._get_boundary_lookup_key)."""
    return f"""
SELECT DISTINCT tenant_id, hierarchy_type, boundary_code
FROM
(
    SELECT
        user_action.tenant_id                                          AS tenant_id,
        JSONExtractString(project.additional_details, 'hierarchyType') AS hierarchy_type,
        user_action.boundary_code                                      AS boundary_code
    FROM {table('stg_user_action')} AS user_action FINAL{project_join_sql(f.projects(), 'user_action.project_id', 'user_action.tenant_id')}
    WHERE {f.driving()}
)
WHERE hierarchy_type != '' AND boundary_code != ''
"""


def user_keys_sql(f: UserActionSliceFilter) -> str:
    """(tenant_id, client_created_by), the CLIENT audit's creator."""
    return f"""
SELECT DISTINCT user_action.tenant_id AS tenant_id, user_action.client_created_by AS user_id
FROM {table('stg_user_action')} AS user_action FINAL
WHERE {f.driving()} AND user_action.client_created_by != ''
"""


def helper_expressions(f: UserActionSliceFilter) -> list[tuple[str, str]]:
    return [
        # (= parse_additional_fields + _convert_to_json_value, keys with a null value dropped)
        ("field_pairs",
         f"arrayMap(fld -> (JSONExtractString(fld, 'key'), {json_loads_or_string_sql('fld')}), "
         "arrayFilter(fld -> JSONHas(fld, 'key'), JSONExtractArrayRaw(joined.additional_details, 'fields')))"),
        ("additional_details_json", json_object_sql("field_pairs", drop_null_values=True)),
    ]


# (silver column, SQL expression) in user_action_entity's column order.
SILVER_COLUMNS: list[tuple[str, str]] = [
    ("id", "joined.id"),
    ("tenant_id", "joined.tenant_id"),
    ("client_reference_id", "joined.client_reference_id"),
    ("latitude", "joined.latitude"),
    ("longitude", "joined.longitude"),
    ("location_accuracy", "toFloat64(joined.location_accuracy)"),
    ("boundary_code", "joined.boundary_code"),
    ("action", "joined.action"),
    ("beneficiary_tag", "joined.beneficiary_tag"),
    ("resource_tag", "joined.resource_tag"),
    ("is_deleted", "false"),  # no source anywhere (bronze, Postgres, Java) -- same as the Python DAG
    ("additional_fields", "joined.additional_details"),
    ("created_by", "joined.created_by"),
    ("last_modified_by", "joined.last_modified_by"),
    ("created_time", "joined.created_time"),
    ("last_modified_time", "joined.last_modified_time"),
    ("client_created_by", "joined.client_created_by"),
    ("client_last_modified_by", "joined.client_last_modified_by"),
    ("client_created_time", "joined.client_created_time"),
    ("client_last_modified_time", "joined.client_last_modified_time"),
    ("project_id", "joined.project_id"),
    ("project_type", "joined.project_type"),
    ("project_type_id", "joined.project_type_id"),
    ("user_name", "users.user_name"),
    ("name_of_user", "users.name_of_user"),
    ("role", "users.role"),
    # server timestamp for synced_*, client timestamp for task_dates; 0 -> epoch defaults
    ("synced_time_stamp", "fromUnixTimestamp64Milli(joined.last_modified_time, 'UTC')"),
    ("synced_time", "joined.last_modified_time"),
    ("task_dates", "toDate32(fromUnixTimestamp64Milli(joined.client_last_modified_time, 'UTC'))"),
    ("synced_date", "toDate32(fromUnixTimestamp64Milli(joined.last_modified_time, 'UTC'))"),
    ("geo_latitude", "joined.latitude"),
    ("geo_longitude", "joined.longitude"),
    *[(column, f"levels.{column}") for column in LEVEL_COLUMNS],
    # all-or-nothing with the levels: blank unless a lookup key existed (= attach_boundary_levels)
    ("hierarchy_type", "if(joined.boundary_code != '', joined.hierarchy_type, '')"),
    ("additional_details", "additional_details_json"),
    ("project_name", "joined.project_name"),
    ("campaign_number", "joined.campaign_number"),
    ("campaign_id", "''"),
]


SPEC = EntitySpec(
    entity="user_action_test",
    python_entity="user_action",
    driving_table="stg_user_action",
    slice_filter_class=UserActionSliceFilter,
    target=SilverTarget(
        silver_table="user_action_entity",
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
