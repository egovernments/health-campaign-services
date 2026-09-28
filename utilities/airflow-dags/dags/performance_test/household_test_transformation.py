"""
household_test_transformation.py

TEST twin of household_transformation.py for the `household` entity: the
flatten + write runs inside ClickHouse as one INSERT ... SELECT per slice
(see sql_flatten_common.py for how a run works). Writes to
<database>.household_entity_sql_test, never to household_entity.
Entity name `household_test` (dag_id household_test_transformation).

Known differences from household_transformation.py (all deliberate)
    - stg_household is read with FINAL (the Python DAG reads every version and
      relies on the silver table's own ReplacingMergeTree to collapse them).
    - The user -> project bridge takes, per (tenant_id, staff row id), the
      NEWEST version and drops it if that version is deleted, then the lowest
      staff row id per user. The Python DAG applies is_deleted = false to every
      version without FINAL, so a row deleted in its newest version could still
      be picked there through an older version.
    - stg_project is read with FINAL; the Python DAG's LEFT JOIN without FINAL
      returns an arbitrary version of a multi-version project.
    - now() is evaluated once per statement, not once per row; a row that
      fails to build aborts the slice instead of being skipped with a log line.
    - Python's json.dumps escapes non-ASCII text as \\uXXXX; ClickHouse writes
      it as UTF-8. Same JSON, different bytes, for the rare row with such text.
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
    boundary_lookup,
    build_sql_test_dag,
    cycle_index_expressions,
    json_int_or_zero_sql,
    json_object_sql,
    json_string_sql,
    json_value_sql,
    project_join_sql,
    staff_bridge_join_sql,
    staff_projects_subquery_sql,
    table,
    user_lookup,
)

# Sources, in the order they appear in the query:
#
#   source                     alias      keyed / joined on                                       feeds
#   -------------------------  ---------  ------------------------------------------------------  ------------------------------------------
#   stg_household (FINAL)      household  tenant_id + id range of the slice                       id, client_reference_id, member_count,
#                                                                                                 audit columns, raw additional_details,
#                                                                                                 derived additional_details, synced_*, task_dates
#   stg_address (FINAL)        address    address.id = household.address_id AND tenant (own PK)   address_*, geo_point_*, address_locality,
#                                                                                                 the boundary lookup code
#   stg_project_staff          staff      staff.staff_id = household.client_last_modified_by      the project id of the user's LOWEST-id
#                                         AND tenant (17's prj_by_staff, argMax per staff row,    non-deleted staff assignment
#                                         then argMin(project_id, id) per user)
#   stg_project (FINAL)        project    project.id = staff.first_project_id AND tenant (own PK)  project_* columns, campaign_number,
#                                                                                                 hierarchy_type and cycles (cycleIndex)
#   boundary-service response  levels     (tenant_id, hierarchy_type, address.locality_code)       level_one_code .. level_nine_code
#   user-service response      users      (tenant_id, created_by)  -- the SERVER audit's creator,  user_name, name_of_user, role, user_address
#                                         not the bridge's client_last_modified_by

# Mirrors HouseholdService.java's ADDITIONAL_DETAILS_INTEGER_FIELDS: these
# additional_details values are written as integers, everything else as-is.
HOUSEHOLD_INT_FIELDS = ("pregnantWomen", "children", "noOfRooms", "menCount", "womenCount")


class HouseholdSliceFilter(SliceFilter):
    driving_alias = "household"

    def addresses(self) -> str:
        return self.own_key_in("id", self.slice_column("address_id"))

    def staff(self) -> str:
        return self.own_key_in("staff_id", self.slice_column("client_last_modified_by"))

    def projects(self) -> str:
        return self.own_key_in("id", staff_projects_subquery_sql(self.staff()))


def _joins(f: HouseholdSliceFilter) -> str:
    return (
        address_join_sql(f.addresses(), "household.address_id", "household.tenant_id")
        + staff_bridge_join_sql(f.staff(), "household.client_last_modified_by", "household.tenant_id")
        + project_join_sql(f.projects(), "staff.first_project_id", "household.tenant_id")
    )


def joined_rows_sql(f: HouseholdSliceFilter) -> str:
    """One row per household for the slice, aliased `joined` by the caller.
    Unmatched sides are '' / 0 / false (join_use_nulls = 0)."""
    return f"""
    SELECT
        household.id                          AS id,
        household.tenant_id                   AS tenant_id,
        household.client_reference_id         AS client_reference_id,
        household.member_count                AS member_count,
        household.is_deleted                  AS is_deleted,
        household.row_version                 AS row_version,
        household.address_id                  AS address_id,
        household.additional_details          AS additional_details,
        household.created_by                  AS created_by,
        household.last_modified_by            AS last_modified_by,
        household.created_time                AS created_time,
        household.last_modified_time          AS last_modified_time,
        household.client_created_by           AS client_created_by,
        household.client_last_modified_by     AS client_last_modified_by,
        household.client_created_time         AS client_created_time,
        household.client_last_modified_time   AS client_last_modified_time,
        address.latitude                      AS address_latitude,
        address.longitude                     AS address_longitude,
        address.location_accuracy             AS address_location_accuracy,
        address.type                          AS address_type,
        address.locality_code                 AS address_locality_code,
        project.id                            AS project_id,
        project.additional_details            AS project_additional_details,
        project.project_type                  AS project_type,
        project.project_type_id               AS project_type_id,
        project.name                          AS project_name,
        project.reference_id                  AS campaign_number,
        JSONExtractString(project.additional_details, 'hierarchyType') AS hierarchy_type
    FROM {table('stg_household')} AS household FINAL{_joins(f)}
    WHERE {f.driving()}"""


def boundary_keys_sql(f: HouseholdSliceFilter) -> str:
    """Distinct (tenant_id, hierarchy_type, boundary_code) needing a boundary
    lookup: hierarchy_type from the BRIDGED PROJECT's additional_details, code =
    the household's OWN address locality (no project-boundary fallback); both
    must be non-empty (= household_transformation._get_boundary_lookup_key)."""
    return f"""
SELECT DISTINCT tenant_id, hierarchy_type, boundary_code
FROM
(
    SELECT
        household.tenant_id                                             AS tenant_id,
        JSONExtractString(project.additional_details, 'hierarchyType')  AS hierarchy_type,
        address.locality_code                                           AS boundary_code
    FROM {table('stg_household')} AS household FINAL{_joins(f)}
    WHERE {f.driving()}
)
WHERE hierarchy_type != '' AND boundary_code != ''
"""


def user_keys_sql(f: HouseholdSliceFilter) -> str:
    """Distinct (tenant_id, created_by) of the slice's households -- the
    SERVER audit's creator, for display (= household_transformation._get_user_lookup_key)."""
    return f"""
SELECT DISTINCT household.tenant_id AS tenant_id, household.created_by AS user_id
FROM {table('stg_household')} AS household FINAL
WHERE {f.driving()} AND household.created_by != ''
"""


def helper_expressions(f: HouseholdSliceFilter) -> list[tuple[str, str]]:
    """Port of household_transformation._build_household_additional_details,
    _resolve_household_cycle_index and the egov_api_utils helpers they call."""
    int_field_list = ", ".join(f"'{key}'" for key in HOUSEHOLD_INT_FIELDS)
    return [
        # the household's own additional_details: {"fields": [{"key": ..., "value": ...}, ...]}
        ("household_fields",
         "arrayFilter(f -> JSONHas(f, 'key'), JSONExtractArrayRaw(joined.additional_details, 'fields'))"),
        # (key, json value) per field; the five integer keys are coerced like Python's int(), 0 on failure
        ("field_pairs",
         f"arrayMap(f -> (JSONExtractString(f, 'key'), if(JSONExtractString(f, 'key') IN ({int_field_list}), "
         f"{json_int_or_zero_sql('f')}, {json_value_sql('f')})), household_fields)"),
        # FIRST occurrence of a repeated key wins here (HouseholdService.additionalFieldsToDetails)
        ("unique_field_pairs",
         "arrayMap(k -> (k, arrayFirst(p -> p.1 = k, field_pairs).2), arrayDistinct(arrayMap(p -> p.1, field_pairs)))"),
        ("pregnant_women", "toInt64OrZero(arrayFirst(p -> p.1 = 'pregnantWomen', unique_field_pairs).2)"),
        ("children",       "toInt64OrZero(arrayFirst(p -> p.1 = 'children', unique_field_pairs).2)"),
        ("is_vulnerable",  "pregnant_women > 0 OR children > 0"),
        # cycleIndex (= get_project_cycles, fetch_cycle_index on client_created_time, "%02d" or null)
        *cycle_index_expressions("joined.project_additional_details", "joined.client_created_time"),
        # the derived blob: coerced fields, then isVulnerable (only when true), then cycleIndex (always)
        ("additional_details_pairs",
         "arrayConcat(unique_field_pairs, if(is_vulnerable, [('isVulnerable', 'true')], []), "
         "[('cycleIndex', cycle_index_json)])"),
        ("additional_details_json", json_object_sql("additional_details_pairs")),
    ]


# (silver column, SQL expression) in household_entity's column order.
SILVER_COLUMNS: list[tuple[str, str]] = [
    ("id", "joined.id"),
    ("tenant_id", "joined.tenant_id"),
    ("client_reference_id", "joined.client_reference_id"),
    ("member_count", "joined.member_count"),
    ("is_deleted", "joined.is_deleted"),
    ("row_version", "toInt32(joined.row_version)"),
    ("address_id", "joined.address_id"),
    ("address_latitude", "joined.address_latitude"),
    ("address_longitude", "joined.address_longitude"),
    ("address_location_accuracy", "toFloat64(joined.address_location_accuracy)"),
    ("address_type", "joined.address_type"),
    # bronze only carries the locality CODE, so this is the minimal {"code": ...} object (or '')
    ("address_locality",
     f"if(joined.address_locality_code != '', concat('{{\"code\": ', {json_string_sql('joined.address_locality_code')}, '}}'), '')"),
    # the RAW bronze blob; the DERIVED one is `additional_details` below
    ("household_additional_fields", "joined.additional_details"),
    # server audit trail, then client audit trail
    ("created_by", "joined.created_by"),
    ("last_modified_by", "joined.last_modified_by"),
    ("created_time", "joined.created_time"),
    ("last_modified_time", "joined.last_modified_time"),
    ("client_created_by", "joined.client_created_by"),
    ("client_last_modified_by", "joined.client_last_modified_by"),
    ("client_created_time", "joined.client_created_time"),
    ("client_last_modified_time", "joined.client_last_modified_time"),
    ("user_name", "users.user_name"),
    ("name_of_user", "users.name_of_user"),
    ("role", "users.role"),
    ("user_address", "users.user_address"),
    # client timestamp for task_dates, server timestamps for the synced_* columns; 0 -> epoch defaults
    ("task_dates", "toDate32(fromUnixTimestamp64Milli(joined.client_last_modified_time, 'UTC'))"),
    ("synced_date", "toDate32(fromUnixTimestamp64Milli(joined.last_modified_time, 'UTC'))"),
    ("synced_time_stamp", "fromUnixTimestamp64Milli(joined.last_modified_time, 'UTC')"),
    *[(column, f"levels.{column}") for column in LEVEL_COLUMNS],
    # all-or-nothing with the levels: blank unless a lookup key existed (= attach_boundary_levels)
    ("hierarchy_type", "if(joined.address_locality_code != '', joined.hierarchy_type, '')"),
    ("geo_point_lat", "joined.address_latitude"),
    ("geo_point_lon", "joined.address_longitude"),
    ("additional_details", "additional_details_json"),
    ("project_id", "joined.project_id"),
    ("project_type", "joined.project_type"),
    ("project_type_id", "joined.project_type_id"),
    ("project_name", "joined.project_name"),
    ("campaign_number", "joined.campaign_number"),
    ("campaign_id", "''"),
]


SPEC = EntitySpec(
    entity="household_test",
    python_entity="household",
    driving_table="stg_household",
    slice_filter_class=HouseholdSliceFilter,
    target=SilverTarget(
        silver_table="household_entity",
        joined_rows_sql=joined_rows_sql,
        helper_expressions=helper_expressions,
        silver_columns=SILVER_COLUMNS,
        lookups=(
            boundary_lookup(boundary_keys_sql, code_expr="joined.address_locality_code"),
            user_lookup(user_keys_sql, user_expr="joined.created_by"),
        ),
    ),
)

# The Airflow DAG object for this entity, built from SPEC by sql_flatten_common
# (airflow safe-mode discovery needs the words "airflow" and "dag" in this file).
dag = build_sql_test_dag(SPEC)
