"""
household_member_test_transformation.py

TEST twin of household_member_transformation.py for the `household_member`
entity: the flatten + write runs inside ClickHouse as one INSERT ... SELECT
per slice (see sql_flatten_common.py for how a run works). Writes to
<database>.household_member_entity_sql_test, never to household_member_entity.
Entity name `household_member_test` (dag_id household_member_test_transformation).

Known differences from household_member_transformation.py (all deliberate)
    - stg_household_member is read with FINAL (the Python DAG reads every
      version and relies on the silver table's own ReplacingMergeTree).
    - The household and the individual are collapsed to ONE row per
      client_reference_id (newest version, ties by id); the Python DAG's join
      fans out when a reference has several bronze rows.
    - The user -> project bridge takes, per (tenant_id, staff row id), the
      NEWEST version and drops it if that version is deleted, then the lowest
      staff row id per user. The Python DAG applies is_deleted = false to every
      version without FINAL. stg_project is read with FINAL (Python: no FINAL).
    - date_of_birth, age and gender are blank (0 / 0 / '') when the individual
      is not found. The Python DAG currently writes date_of_birth 0 and age 680
      for such rows: stg_individual.date_of_birth became a non-nullable Date32,
      so its LEFT JOIN default is 1970-01-01 (see PROJECTION_COMPARISON_REPORT §8c).
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
    age_expressions,
    boundary_lookup,
    build_sql_test_dag,
    cycle_index_expressions,
    field_pairs_sql,
    json_int_or_null_sql,
    json_object_sql,
    json_value_sql,
    one_row_per_key_sql,
    project_join_sql,
    staff_bridge_join_sql,
    staff_projects_subquery_sql,
    table,
    user_lookup,
)

# Sources, in the order they appear in the query:
#
#   source                         alias       keyed / joined on                                        feeds
#   -----------------------------  ----------  -------------------------------------------------------  --------------------------------------------
#   stg_household_member (FINAL)   member      tenant_id + id range of the slice                        id, household/individual references,
#                                                                                                       is_head_of_household, audit columns,
#                                                                                                       raw additional_details, synced_*, task_dates
#   stg_household                  household   household.client_reference_id =                          the household's address_id and its
#                                              member.household_client_reference_id AND tenant           additional_details (merged into the member's)
#                                              (17's prj_by_client_ref, ONE row per reference)
#   stg_address (FINAL)            address     address.id = household.latest_address_id AND tenant      geo_point_*, boundary_code, the boundary
#                                              (own PK) -- the member has no address of its own          lookup code
#   stg_individual                 individual  individual.client_reference_id =                         date_of_birth, age, gender, and the
#                                              member.individual_client_reference_id AND tenant          height / disabilityType extras
#                                              (17's prj_by_client_ref, ONE row per reference)
#   stg_project_staff              staff       staff.staff_id = member.client_last_modified_by          the project id of the user's LOWEST-id
#                                              AND tenant (17's prj_by_staff, argMax per staff row,     non-deleted staff assignment
#                                              then argMin(project_id, id) per user)
#   stg_project (FINAL)            project     project.id = staff.first_project_id AND tenant (own PK)  project_* columns, campaign_number,
#                                                                                                       hierarchy_type and cycles (cycleIndex)
#   boundary-service response      levels      (tenant_id, hierarchy_type, address.locality_code)       level_one_code .. level_nine_code
#   user-service response          users       (tenant_id, client_last_modified_by) -- the same user     user_name, name_of_user, role, user_address
#                                              as the bridge, unlike household's created_by


class HouseholdMemberSliceFilter(SliceFilter):
    driving_alias = "member"

    def households(self) -> str:
        return self.own_key_in("client_reference_id", self.slice_column("household_client_reference_id"))

    def addresses(self) -> str:
        """The address ids of the slice's households (a raw superset; the join itself picks)."""
        return self.own_key_in(
            "id", f"(SELECT DISTINCT address_id FROM {table('stg_household')} WHERE {self.households()} AND address_id != '')")

    def individuals(self) -> str:
        return self.own_key_in("client_reference_id", self.slice_column("individual_client_reference_id"))

    def staff(self) -> str:
        return self.own_key_in("staff_id", self.slice_column("client_last_modified_by"))

    def projects(self) -> str:
        return self.own_key_in("id", staff_projects_subquery_sql(self.staff()))


def household_join_sql(f: HouseholdMemberSliceFilter) -> str:
    """LEFT JOIN stg_household AS household ON household.client_reference_id =
    member.household_client_reference_id (the client-reference path Java uses,
    NOT household_id): the household's address id and additional_details."""
    inner = one_row_per_key_sql("stg_household", f.households(), "client_reference_id", ["address_id", "additional_details"])
    return f"""
    LEFT JOIN
    ({inner}
    ) AS household
        ON household.client_reference_id = member.household_client_reference_id
       AND household.tenant_id = member.tenant_id"""


def individual_join_sql(f: HouseholdMemberSliceFilter) -> str:
    inner = one_row_per_key_sql("stg_individual", f.individuals(), "client_reference_id",
                                ["date_of_birth", "gender", "additional_details"])
    return f"""
    LEFT JOIN
    ({inner}
    ) AS individual
        ON individual.client_reference_id = member.individual_client_reference_id
       AND individual.tenant_id = member.tenant_id"""


def _joins(f: HouseholdMemberSliceFilter) -> str:
    return (
        household_join_sql(f)
        + address_join_sql(f.addresses(), "household.latest_address_id", "member.tenant_id",
                           columns="id, tenant_id, latitude, longitude, locality_code")
        + individual_join_sql(f)
        + staff_bridge_join_sql(f.staff(), "member.client_last_modified_by", "member.tenant_id")
        + project_join_sql(f.projects(), "staff.first_project_id", "member.tenant_id")
    )


def joined_rows_sql(f: HouseholdMemberSliceFilter) -> str:
    """One row per household member for the slice, aliased `joined` by the
    caller. Unmatched sides are '' / 0 / false / 1970-01-01 (join_use_nulls = 0)."""
    return f"""
    SELECT
        member.id                               AS id,
        member.tenant_id                        AS tenant_id,
        member.client_reference_id              AS client_reference_id,
        member.household_id                     AS household_id,
        member.household_client_reference_id    AS household_client_reference_id,
        member.individual_id                    AS individual_id,
        member.individual_client_reference_id   AS individual_client_reference_id,
        member.is_head_of_household             AS is_head_of_household,
        member.is_deleted                       AS is_deleted,
        member.row_version                      AS row_version,
        member.additional_details               AS additional_details,
        member.created_by                       AS created_by,
        member.last_modified_by                 AS last_modified_by,
        member.created_time                     AS created_time,
        member.last_modified_time               AS last_modified_time,
        member.client_created_by                AS client_created_by,
        member.client_last_modified_by          AS client_last_modified_by,
        member.client_created_time              AS client_created_time,
        member.client_last_modified_time        AS client_last_modified_time,
        household.latest_additional_details     AS household_additional_details,
        address.latitude                        AS address_latitude,
        address.longitude                       AS address_longitude,
        address.locality_code                   AS address_locality_code,
        individual.client_reference_id != ''    AS individual_matched,
        individual.latest_date_of_birth         AS individual_date_of_birth,
        individual.latest_gender                AS individual_gender,
        individual.latest_additional_details    AS individual_additional_details,
        project.id                              AS project_id,
        project.additional_details              AS project_additional_details,
        project.project_type                    AS project_type,
        project.project_type_id                 AS project_type_id,
        project.name                            AS project_name,
        project.reference_id                    AS campaign_number,
        JSONExtractString(project.additional_details, 'hierarchyType') AS hierarchy_type
    FROM {table('stg_household_member')} AS member FINAL{_joins(f)}
    WHERE {f.driving()}"""


def boundary_keys_sql(f: HouseholdMemberSliceFilter) -> str:
    """(tenant, project hierarchyType, the PARENT HOUSEHOLD's address locality), both non-empty."""
    return f"""
SELECT DISTINCT tenant_id, hierarchy_type, boundary_code
FROM
(
    SELECT
        member.tenant_id                                                AS tenant_id,
        JSONExtractString(project.additional_details, 'hierarchyType')  AS hierarchy_type,
        address.locality_code                                           AS boundary_code
    FROM {table('stg_household_member')} AS member FINAL{household_join_sql(f)}{address_join_sql(f.addresses(), 'household.latest_address_id', 'member.tenant_id', columns='id, tenant_id, latitude, longitude, locality_code')}{staff_bridge_join_sql(f.staff(), 'member.client_last_modified_by', 'member.tenant_id')}{project_join_sql(f.projects(), 'staff.first_project_id', 'member.tenant_id')}
    WHERE {f.driving()}
)
WHERE hierarchy_type != '' AND boundary_code != ''
"""


def user_keys_sql(f: HouseholdMemberSliceFilter) -> str:
    """(tenant_id, client_last_modified_by): the same user as the project bridge."""
    return f"""
SELECT DISTINCT member.tenant_id AS tenant_id, member.client_last_modified_by AS user_id
FROM {table('stg_household_member')} AS member FINAL
WHERE {f.driving()} AND member.client_last_modified_by != ''
"""


def helper_expressions(f: HouseholdMemberSliceFilter) -> list[tuple[str, str]]:
    """Port of household_member_transformation._build_household_member_additional_details,
    _resolve_individual_extra_fields, _resolve_cycle_index_str, _date_to_epoch_ms."""
    return [
        # the three fields[] blobs as (key, json value) arrays
        ("household_pairs",  field_pairs_sql("joined.household_additional_details")),
        ("member_pairs",     field_pairs_sql("joined.additional_details")),
        ("individual_fields",
         "arrayFilter(f -> JSONHas(f, 'key'), JSONExtractArrayRaw(joined.individual_additional_details, 'fields'))"),
        # height / disabilityType are copied only when the individual has BOTH keys (Java's containsKey
        # pair); the LAST occurrence of each is what parse_additional_fields' dict holds
        ("individual_keys", "arrayMap(f -> JSONExtractString(f, 'key'), individual_fields)"),
        ("has_individual_extras", "has(individual_keys, 'height') AND has(individual_keys, 'disabilityType')"),
        ("height_field",          "arrayLast(f -> JSONExtractString(f, 'key') = 'height', individual_fields)"),
        ("disability_type_field", "arrayLast(f -> JSONExtractString(f, 'key') = 'disabilityType', individual_fields)"),
        ("individual_extra_pairs",
         f"if(has_individual_extras, [('height', {json_int_or_null_sql('height_field')}), "
         f"('disabilityType', {json_value_sql('disability_type_field')})], [])"),
        # cycleIndex (= get_project_cycles, fetch_cycle_index on client_created_time, "%02d" or null)
        *cycle_index_expressions("joined.project_additional_details", "joined.client_created_time"),
        # the derived blob: household fields, then member fields (member wins), then the individual
        # extras, then cycleIndex -- Python dict semantics applied once over the whole sequence
        ("additional_details_pairs",
         "arrayConcat(household_pairs, member_pairs, individual_extra_pairs, [('cycleIndex', cycle_index_json)])"),
        ("additional_details_json", json_object_sql("additional_details_pairs")),
        # date_of_birth / age / gender only when the individual row was found (see the module docstring)
        ("individual_known", "joined.individual_matched"),
        *age_expressions("joined.individual_date_of_birth"),
    ]


# (silver column, SQL expression) in household_member_entity's column order.
SILVER_COLUMNS: list[tuple[str, str]] = [
    ("id", "joined.id"),
    ("tenant_id", "joined.tenant_id"),
    ("client_reference_id", "joined.client_reference_id"),
    ("household_id", "joined.household_id"),
    ("household_client_reference_id", "joined.household_client_reference_id"),
    ("individual_id", "joined.individual_id"),
    ("individual_client_reference_id", "joined.individual_client_reference_id"),
    ("is_head_of_household", "joined.is_head_of_household"),
    ("is_deleted", "joined.is_deleted"),
    ("row_version", "toInt32(joined.row_version)"),
    # the RAW bronze blob; the DERIVED one is `additional_details` below
    ("member_additional_fields", "joined.additional_details"),
    # server audit trail, then client audit trail
    ("created_by", "joined.created_by"),
    ("last_modified_by", "joined.last_modified_by"),
    ("created_time", "joined.created_time"),
    ("last_modified_time", "joined.last_modified_time"),
    ("client_created_by", "joined.client_created_by"),
    ("client_last_modified_by", "joined.client_last_modified_by"),
    ("client_created_time", "joined.client_created_time"),
    ("client_last_modified_time", "joined.client_last_modified_time"),
    *[(column, f"levels.{column}") for column in LEVEL_COLUMNS],
    # all-or-nothing with the levels: blank unless a lookup key existed (= attach_boundary_levels)
    ("hierarchy_type", "if(joined.address_locality_code != '', joined.hierarchy_type, '')"),
    # epoch ms (Int64), not a calendar date, unlike every other date-of-birth column
    ("date_of_birth", "if(individual_known, date_of_birth_ms, toInt64(0))"),
    ("age", "if(individual_known, toInt32(age_months), toInt32(0))"),
    ("gender", "if(individual_known, joined.individual_gender, '')"),
    ("user_name", "users.user_name"),
    ("name_of_user", "users.name_of_user"),
    ("role", "users.role"),
    ("user_address", "users.user_address"),
    # client timestamp for task_dates, server timestamps for the synced_* columns; 0 -> epoch defaults
    ("task_dates", "toDate32(fromUnixTimestamp64Milli(joined.client_last_modified_time, 'UTC'))"),
    ("synced_date", "toDate32(fromUnixTimestamp64Milli(joined.last_modified_time, 'UTC'))"),
    ("synced_time_stamp", "fromUnixTimestamp64Milli(joined.last_modified_time, 'UTC')"),
    # the parent household's address
    ("geo_point_lat", "joined.address_latitude"),
    ("geo_point_lon", "joined.address_longitude"),
    ("boundary_code", "joined.address_locality_code"),
    ("additional_details", "additional_details_json"),
    ("project_id", "joined.project_id"),
    ("project_type", "joined.project_type"),
    ("project_type_id", "joined.project_type_id"),
    ("project_name", "joined.project_name"),
    ("campaign_number", "joined.campaign_number"),
    ("campaign_id", "''"),
]


SPEC = EntitySpec(
    entity="household_member_test",
    python_entity="household_member",
    driving_table="stg_household_member",
    slice_filter_class=HouseholdMemberSliceFilter,
    target=SilverTarget(
        silver_table="household_member_entity",
        joined_rows_sql=joined_rows_sql,
        helper_expressions=helper_expressions,
        silver_columns=SILVER_COLUMNS,
        lookups=(
            boundary_lookup(boundary_keys_sql, code_expr="joined.address_locality_code"),
            user_lookup(user_keys_sql, user_expr="joined.client_last_modified_by"),
        ),
    ),
)

# The Airflow DAG object for this entity, built from SPEC by sql_flatten_common
# (airflow safe-mode discovery needs the words "airflow" and "dag" in this file).
dag = build_sql_test_dag(SPEC)
