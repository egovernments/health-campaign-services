"""
device_token_test_transformation.py

TEST twin of device_token_transformation.py for the `device_token` entity: the
flatten + write runs inside ClickHouse as one INSERT ... SELECT per slice
(see sql_flatten_common.py for how a run works). Writes to
<database>.device_token_entity_sql_test, never to device_token_entity.
Entity name `device_token_test` (dag_id device_token_test_transformation).

Known differences from device_token_transformation.py (all deliberate)
    - stg_device_tokens is read with FINAL (the Python DAG reads every version).
      Its sort key is (id) alone, so the slice's tenant filter does not prune;
      the table is small.
    - The user -> project bridge takes the newest version of each staff row and
      drops it if deleted, then the lowest staff row id per user; stg_project is
      read with FINAL and stg_project_address as one boundary per project
      (the Python DAG's bridge joins both without FINAL).
    - device_token_entity's own `_ingested_at` column keeps its DEFAULT on both
      sides and is not part of the comparison.
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
#   source                     alias            keyed / joined on                                     feeds
#   -------------------------  ---------------  ----------------------------------------------------  --------------------------------------------
#   stg_device_tokens (FINAL)  token            tenant_id + id range of the slice                     every plain column
#   stg_project_staff          staff            staff.staff_id = token.user_id AND tenant             the project id of the user's lowest-id
#                                               (17's prj_by_staff)                                   non-deleted staff row
#   stg_project (FINAL)        project          project.id = staff.first_project_id AND tenant        project_* columns, campaign_number,
#                                                                                                     hierarchy_type
#   stg_project_address        project_address  project_address.project_id = project.id AND tenant    the boundary lookup code
#                                               (14's prj_by_project)
#   boundary-service response  levels           (tenant_id, hierarchy_type, project boundary)         level_one_code .. level_nine_code
#   user-service response      users            (tenant_id, user_id) -- the same user as the bridge   user_name, role


class DeviceTokenSliceFilter(SliceFilter):
    driving_alias = "token"

    def staff(self) -> str:
        return self.own_key_in("staff_id", self.slice_column("user_id"))

    def projects(self) -> str:
        return self.own_key_in("id", staff_projects_subquery_sql(self.staff()))

    def project_addresses(self) -> str:
        return self.own_key_in("project_id", staff_projects_subquery_sql(self.staff()))


def _joins(f: DeviceTokenSliceFilter) -> str:
    return (
        staff_bridge_join_sql(f.staff(), "token.user_id", "token.tenant_id")
        + project_join_sql(f.projects(), "staff.first_project_id", "token.tenant_id")
        + project_address_join_sql(f.project_addresses(), "project.id", "token.tenant_id")
    )


def joined_rows_sql(f: DeviceTokenSliceFilter) -> str:
    """One row per device token for the slice, aliased `joined` by the caller."""
    return f"""
    SELECT
        token.id                        AS id,
        token.user_id                   AS user_id,
        token.device_type               AS device_type,
        token.tenant_id                 AS tenant_id,
        token.created_by                AS created_by,
        token.last_modified_by          AS last_modified_by,
        token.created_time              AS created_time,
        token.last_modified_time        AS last_modified_time,
        token.facility_id               AS facility_id,
        token.user_roles                AS user_roles,
        project.id                      AS project_id,
        project.project_type            AS project_type,
        project.project_type_id         AS project_type_id,
        project.name                    AS project_name,
        project.reference_id            AS campaign_number,
        JSONExtractString(project.additional_details, 'hierarchyType') AS hierarchy_type,
        project_address.latest_boundary AS boundary_code
    FROM {table('stg_device_tokens')} AS token FINAL{_joins(f)}
    WHERE {f.driving()}"""


def boundary_keys_sql(f: DeviceTokenSliceFilter) -> str:
    return f"""
SELECT DISTINCT tenant_id, hierarchy_type, boundary_code
FROM
(
    SELECT
        token.tenant_id                                                AS tenant_id,
        JSONExtractString(project.additional_details, 'hierarchyType') AS hierarchy_type,
        project_address.latest_boundary                                AS boundary_code
    FROM {table('stg_device_tokens')} AS token FINAL{_joins(f)}
    WHERE {f.driving()}
)
WHERE hierarchy_type != '' AND boundary_code != ''
"""


def user_keys_sql(f: DeviceTokenSliceFilter) -> str:
    return f"""
SELECT DISTINCT token.tenant_id AS tenant_id, token.user_id AS user_id
FROM {table('stg_device_tokens')} AS token FINAL
WHERE {f.driving()} AND token.user_id != ''
"""


def helper_expressions(f: DeviceTokenSliceFilter) -> list[tuple[str, str]]:
    return []


# (silver column, SQL expression) in device_token_entity's column order
# (its leading `_ingested_at` column is not written; it keeps its DEFAULT).
SILVER_COLUMNS: list[tuple[str, str]] = [
    ("id", "joined.id"),
    ("user_id", "joined.user_id"),
    ("device_type", "joined.device_type"),
    ("tenant_id", "joined.tenant_id"),
    ("created_by", "joined.created_by"),
    ("last_modified_by", "joined.last_modified_by"),
    ("created_time", "joined.created_time"),
    ("last_modified_time", "joined.last_modified_time"),
    ("facility_id", "joined.facility_id"),
    ("user_roles", "joined.user_roles"),
    ("user_name", "users.user_name"),
    ("role", "users.role"),
    *[(column, f"levels.{column}") for column in LEVEL_COLUMNS],
    ("hierarchy_type", "if(joined.boundary_code != '', joined.hierarchy_type, '')"),
    # both dates from the SERVER last_modified_time (no client audit trail on this entity)
    ("task_dates", "toDate32(fromUnixTimestamp64Milli(joined.last_modified_time, 'UTC'))"),
    ("synced_date", "toDate32(fromUnixTimestamp64Milli(joined.last_modified_time, 'UTC'))"),
    ("project_id", "joined.project_id"),
    ("project_type", "joined.project_type"),
    ("project_type_id", "joined.project_type_id"),
    ("project_name", "joined.project_name"),
    ("campaign_number", "joined.campaign_number"),
    ("campaign_id", "''"),
]


SPEC = EntitySpec(
    entity="device_token_test",
    python_entity="device_token",
    driving_table="stg_device_tokens",
    slice_filter_class=DeviceTokenSliceFilter,
    target=SilverTarget(
        silver_table="device_token_entity",
        joined_rows_sql=joined_rows_sql,
        helper_expressions=helper_expressions,
        silver_columns=SILVER_COLUMNS,
        lookups=(
            boundary_lookup(boundary_keys_sql, code_expr="joined.boundary_code"),
            user_lookup(user_keys_sql, user_expr="joined.user_id"),
        ),
    ),
)

# The Airflow DAG object for this entity, built from SPEC by sql_flatten_common
# (airflow safe-mode discovery needs the words "airflow" and "dag" in this file).
dag = build_sql_test_dag(SPEC)
