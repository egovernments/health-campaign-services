"""
stock_test_transformation.py

TEST twin of stock_transformation.py for the `stock` entity: the flatten +
write runs inside ClickHouse as one INSERT ... SELECT per slice (see
sql_flatten_common.py for how a run works). Writes to
<database>.stock_entity_sql_test, never to stock_entity.
Entity name `stock_test` (dag_id stock_test_transformation).

The held facility is the receiver for RECEIVED transactions and the sender
otherwise (the transacting facility is the other party). A facility whose
derived type is STAFF is a user, not a stg_facility row: its name comes from
the user-service and its boundary from the user's project (the staff bridge);
any other facility resolves through stg_facility -> stg_address, falling back
to the referenced project's boundary for PROJECT references.

Known differences from stock_transformation.py (all deliberate)
    - stg_stock is read with FINAL (the Python DAG reads every version).
    - The staff bridge takes the newest version of each staff row and drops it
      if deleted, then the lowest staff row id per user; stg_project_address is
      one boundary per project (14's prj_by_project).
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
    boundary_lookup,
    build_sql_test_dag,
    cycle_index_expressions,
    json_double_or_null_sql,
    json_int_or_zero_sql,
    json_object_sql,
    json_value_sql,
    project_address_join_sql,
    project_join_sql,
    staff_bridge_join_sql,
    staff_projects_subquery_sql,
    table,
    user_lookup,
)

# Sources, in the order they appear in the query:
#
#   source                     alias                  keyed / joined on                                        feeds
#   -------------------------  ---------------------  -------------------------------------------------------  --------------------------------------
#   stg_stock (FINAL)          stock                  tenant_id + id range of the slice                        every plain column; facility_id /
#                                                                                                              transacting_facility_id derived from
#                                                                                                              transaction_type (receiver vs sender)
#   stg_facility (FINAL)       facility               facility.id = the held facility id AND tenant,           facility_name/type/level/target, the
#                                                     deleted facilities excluded                              address key
#   stg_address (FINAL)        address                address.id = facility.address_id AND tenant              boundary code (first tier, non-STAFF)
#   stg_facility (FINAL)       transacting_facility   id = the transacting facility id AND tenant              transacting_facility_name/type/level
#   stg_product_variant(FINAL) variant                variant.id = stock.product_variant_id AND tenant         product_name (sku)
#   stg_project (FINAL)        project                project.id = stock.reference_id AND tenant               project_* columns, hierarchy_type,
#                                                                                                              cycles (cycleIndex on created_time)
#   stg_project_address        project_address        project_id = project.id AND tenant (14's prj_by_project) boundary code fallback for PROJECT refs
#   stg_project_staff          staff                  staff.staff_id = the held facility id AND tenant, ONLY   the STAFF facility's project ...
#                                                     when its derived type is STAFF (17's prj_by_staff)
#   stg_project (FINAL)        staff_project          id = staff.first_project_id AND tenant                   ... hierarchy_type for STAFF facilities
#   stg_project_address        staff_project_address  project_id = staff_project.id AND tenant                 ... boundary code for STAFF facilities
#   boundary-service response  levels                 (tenant_id, hierarchy_type, boundary_code)               level_one_code .. level_nine_code
#   user-service response      users                  (tenant_id, client_created_by)                           user_name, name_of_user, role, user_address
#   user-service response      facility_users         (tenant_id, held facility id) for STAFF facilities       facility_name (the user's USERNAME)
#   user-service response      transacting_users      (tenant_id, transacting facility id) for STAFF           transacting_facility_name

DOUBLE_FIELDS = ("lat", "lng")

FACILITY_ID_SQL = "if(lower(stock.transaction_type) = 'received', stock.receiver_id, stock.sender_id)"
FACILITY_TYPE_SQL = "if(lower(stock.transaction_type) = 'received', stock.receiver_type, stock.sender_type)"
TRANSACTING_ID_SQL = "if(lower(stock.transaction_type) = 'received', stock.sender_id, stock.receiver_id)"
TRANSACTING_TYPE_SQL = "if(lower(stock.transaction_type) = 'received', stock.sender_type, stock.receiver_type)"
IS_STAFF_SQL = f"upper({FACILITY_TYPE_SQL}) = 'STAFF'"


class StockSliceFilter(SliceFilter):
    driving_alias = "stock"

    def facility_ids(self) -> str:
        """Both parties' ids over the slice (raw superset)."""
        return (
            f"(SELECT DISTINCT fid FROM (SELECT arrayJoin([sender_id, receiver_id]) AS fid FROM {table('stg_stock')} "
            f"WHERE tenant_id = {self.tenant} AND {self.id_range('id')} AND id IN {self.in_window_ids}) WHERE fid != '')"
        )

    def facilities(self) -> str:
        return self.own_key_in("id", self.facility_ids())

    def addresses(self) -> str:
        return self.own_key_in(
            "id", f"(SELECT DISTINCT address_id FROM {table('stg_facility')} WHERE {self.facilities()} AND address_id != '')")

    def product_variants(self) -> str:
        return self.own_key_in("id", self.slice_column("product_variant_id"))

    def projects(self) -> str:
        return self.own_key_in("id", self.slice_column("reference_id"))

    def project_addresses(self) -> str:
        return self.own_key_in("project_id", self.slice_column("reference_id"))

    def staff(self) -> str:
        return self.own_key_in("staff_id", self.facility_ids())

    def staff_projects(self) -> str:
        return self.own_key_in("id", staff_projects_subquery_sql(self.staff()))

    def staff_project_addresses(self) -> str:
        return self.own_key_in("project_id", staff_projects_subquery_sql(self.staff()))


def facility_join_sql(f: StockSliceFilter, alias: str, on_id_expr: str) -> str:
    return f"""
    LEFT JOIN
    (
        SELECT id, tenant_id, name, usage, is_permanent, address_id, additional_details
        FROM {table('stg_facility')} FINAL
        WHERE {f.facilities()} AND is_deleted = false
    ) AS {alias}
        ON {alias}.id = {on_id_expr} AND {alias}.tenant_id = stock.tenant_id"""


def variant_join_sql(f: StockSliceFilter) -> str:
    return f"""
    LEFT JOIN
    (
        SELECT id, tenant_id, sku
        FROM {table('stg_product_variant')} FINAL
        WHERE {f.product_variants()}
    ) AS variant
        ON variant.id = stock.product_variant_id AND variant.tenant_id = stock.tenant_id"""


HIERARCHY_TYPE_SQL = (
    f"if({IS_STAFF_SQL}, JSONExtractString(staff_project.additional_details, 'hierarchyType'), "
    "JSONExtractString(project.additional_details, 'hierarchyType'))"
)
BOUNDARY_CODE_SQL = (
    f"if({IS_STAFF_SQL}, staff_project_address.latest_boundary, "
    "if(address.locality_code != '', address.locality_code, "
    "if(upper(stock.reference_id_type) = 'PROJECT', project_address.latest_boundary, '')))"
)


def _joins(f: StockSliceFilter) -> str:
    return (
        facility_join_sql(f, "facility", FACILITY_ID_SQL)
        + address_join_sql(f.addresses(), "facility.address_id", "stock.tenant_id", columns="id, tenant_id, locality_code")
        + facility_join_sql(f, "transacting_facility", TRANSACTING_ID_SQL)
        + variant_join_sql(f)
        + project_join_sql(f.projects(), "stock.reference_id", "stock.tenant_id")
        + project_address_join_sql(f.project_addresses(), "project.id", "stock.tenant_id")
        + staff_bridge_join_sql(f.staff(), f"if({IS_STAFF_SQL}, {FACILITY_ID_SQL}, '')", "stock.tenant_id")
        + project_join_sql(f.staff_projects(), "staff.first_project_id", "stock.tenant_id",
                           alias="staff_project", columns="id, tenant_id, additional_details")
        + project_address_join_sql(f.staff_project_addresses(), "staff_project.id", "stock.tenant_id",
                                   alias="staff_project_address")
    )


def joined_rows_sql(f: StockSliceFilter) -> str:
    """One row per stock transaction for the slice, aliased `joined` by the caller."""
    return f"""
    SELECT
        stock.id                                    AS id,
        stock.client_reference_id                   AS client_reference_id,
        stock.tenant_id                             AS tenant_id,
        stock.product_variant_id                    AS product_variant_id,
        stock.quantity                              AS quantity,
        stock.waybill_number                        AS waybill_number,
        stock.date_of_entry                         AS date_of_entry,
        stock.campaign_number                       AS campaign_number,
        stock.reference_id                          AS reference_id,
        stock.transaction_type                      AS transaction_type,
        stock.transaction_reason                    AS transaction_reason,
        stock.additional_details                    AS additional_details,
        stock.last_modified_time                    AS last_modified_time,
        stock.client_created_time                   AS client_created_time,
        stock.client_last_modified_time             AS client_last_modified_time,
        stock.client_created_by                     AS client_created_by,
        stock.client_last_modified_by               AS client_last_modified_by,
        stock.created_time                          AS created_time,
        {FACILITY_ID_SQL}                           AS facility_id,
        {FACILITY_TYPE_SQL}                         AS facility_type_derived,
        {TRANSACTING_ID_SQL}                        AS transacting_facility_id,
        {TRANSACTING_TYPE_SQL}                      AS transacting_facility_type_derived,
        {IS_STAFF_SQL}                              AS facility_is_staff,
        upper({TRANSACTING_TYPE_SQL}) = 'STAFF'     AS transacting_is_staff,
        facility.name                               AS facility_name,
        facility.usage                              AS facility_usage,
        facility.is_permanent                       AS facility_is_permanent,
        facility.additional_details                 AS facility_additional_details,
        transacting_facility.name                   AS transacting_facility_name,
        transacting_facility.usage                  AS transacting_facility_usage,
        transacting_facility.is_permanent           AS transacting_facility_is_permanent,
        transacting_facility.additional_details     AS transacting_facility_additional_details,
        variant.sku                                 AS product_sku,
        project.project_type                        AS project_type,
        project.project_type_id                     AS project_type_id,
        project.name                                AS project_name,
        project.additional_details                  AS project_additional_details,
        {HIERARCHY_TYPE_SQL}                        AS hierarchy_type,
        {BOUNDARY_CODE_SQL}                         AS boundary_code
    FROM {table('stg_stock')} AS stock FINAL{_joins(f)}
    WHERE {f.driving()}"""


def boundary_keys_sql(f: StockSliceFilter) -> str:
    return f"""
SELECT DISTINCT tenant_id, hierarchy_type, boundary_code
FROM
({joined_rows_sql(f)}
)
WHERE hierarchy_type != '' AND boundary_code != ''
"""


def user_keys_sql(f: StockSliceFilter) -> str:
    return f"""
SELECT DISTINCT stock.tenant_id AS tenant_id, stock.client_created_by AS user_id
FROM {table('stg_stock')} AS stock FINAL
WHERE {f.driving()} AND stock.client_created_by != ''
"""


def facility_user_keys_sql(f: StockSliceFilter) -> str:
    return f"""
SELECT DISTINCT stock.tenant_id AS tenant_id, {FACILITY_ID_SQL} AS user_id
FROM {table('stg_stock')} AS stock FINAL
WHERE {f.driving()} AND {IS_STAFF_SQL} AND {FACILITY_ID_SQL} != ''
"""


def transacting_user_keys_sql(f: StockSliceFilter) -> str:
    return f"""
SELECT DISTINCT stock.tenant_id AS tenant_id, {TRANSACTING_ID_SQL} AS user_id
FROM {table('stg_stock')} AS stock FINAL
WHERE {f.driving()} AND upper({TRANSACTING_TYPE_SQL}) = 'STAFF' AND {TRANSACTING_ID_SQL} != ''
"""


def _facility_helpers(prefix: str, staff_flag: str, users_alias: str, id_expr: str) -> list[tuple[str, str]]:
    """facility_* / transacting_facility_* derivations (= the Python DAG's per-side
    branch): a STAFF facility is named by its user and has no level/target."""
    fields = f"arrayFilter(fld -> JSONHas(fld, 'key'), JSONExtractArrayRaw(joined.{prefix}_additional_details, 'fields'))"
    type_field = f"arrayLast(fld -> JSONExtractString(fld, 'key') = 'type', {fields})"
    target_field = f"arrayLast(fld -> JSONExtractString(fld, 'key') = 'target', {fields})"
    return [
        (f"{prefix}_name_out",
         f"if({staff_flag}, if({users_alias}.user_name != '', {users_alias}.user_name, {id_expr}), "
         f"if(joined.{prefix}_name != '', joined.{prefix}_name, {id_expr}))"),
        (f"{prefix}_type_out",
         f"if({staff_flag}, joined.{prefix}_type_derived, "
         f"if(JSONExtractString({type_field}, 'value') != '', JSONExtractString({type_field}, 'value'), joined.{prefix}_type_derived))"),
        (f"{prefix}_level_out",
         f"if({staff_flag}, '', if(upper(joined.{prefix}_usage) = 'WAREHOUSE', "
         f"if(joined.{prefix}_is_permanent, 'DISTRICT_WAREHOUSE', 'SATELLITE_WAREHOUSE'), ''))"),
        (f"{prefix}_target_out", f"if({staff_flag}, toInt64(0), toInt64OrZero({json_int_or_zero_sql(target_field)}))"),
    ]


def helper_expressions(f: StockSliceFilter) -> list[tuple[str, str]]:
    double_keys = ", ".join(f"'{key}'" for key in DOUBLE_FIELDS)
    return [
        *_facility_helpers("facility", "joined.facility_is_staff", "facility_users", "joined.facility_id"),
        *_facility_helpers("transacting_facility", "joined.transacting_is_staff", "transacting_users",
                           "joined.transacting_facility_id"),
        # cycleIndex on the SERVER created_time; additional_details = fields (lat/lng as doubles) + cycleIndex
        *cycle_index_expressions("joined.project_additional_details", "joined.created_time"),
        ("field_pairs",
         f"arrayMap(fld -> (JSONExtractString(fld, 'key'), if(JSONExtractString(fld, 'key') IN ({double_keys}), "
         f"{json_double_or_null_sql('fld')}, {json_value_sql('fld')})), "
         "arrayFilter(fld -> JSONHas(fld, 'key'), JSONExtractArrayRaw(joined.additional_details, 'fields')))"),
        ("additional_details_json", json_object_sql("arrayConcat(field_pairs, [('cycleIndex', cycle_index_json)])")),
    ]


# (silver column, SQL expression) in stock_entity's column order.
SILVER_COLUMNS: list[tuple[str, str]] = [
    ("id", "joined.id"),
    ("facility_id", "joined.facility_id"),
    ("transacting_facility_id", "joined.transacting_facility_id"),
    ("facility_name", "facility_name_out"),
    ("transacting_facility_name", "transacting_facility_name_out"),
    ("product_variant", "joined.product_variant_id"),
    ("product_name", "if(joined.product_sku != '', joined.product_sku, joined.product_variant_id)"),
    ("physical_count", "toInt32(joined.quantity)"),
    ("event_type", "joined.transaction_type"),
    ("reason", "joined.transaction_reason"),
    ("user_name", "users.user_name"),
    ("name_of_user", "users.name_of_user"),
    ("role", "users.role"),
    ("user_address", "users.user_address"),
    ("date_of_entry", "if(joined.date_of_entry != 0, joined.date_of_entry, joined.last_modified_time)"),
    *[(column, f"levels.{column}") for column in LEVEL_COLUMNS],
    ("hierarchy_type", "if(joined.boundary_code != '', joined.hierarchy_type, '')"),
    # the CLIENT audit trail feeds created_*/last_modified_* on this entity (inverted vs the others)
    ("created_by", "joined.client_created_by"),
    ("last_modified_by", "joined.client_last_modified_by"),
    ("created_time", "joined.client_created_time"),
    ("last_modified_time", "joined.client_last_modified_time"),
    ("synced_time_stamp", "fromUnixTimestamp64Milli(joined.last_modified_time, 'UTC')"),
    ("synced_time", "joined.last_modified_time"),
    ("additional_fields", "joined.additional_details"),
    ("client_reference_id", "joined.client_reference_id"),
    ("tenant_id", "joined.tenant_id"),
    ("facility_type", "facility_type_out"),
    ("transacting_facility_type", "transacting_facility_type_out"),
    ("facility_level", "facility_level_out"),
    ("transacting_facility_level", "transacting_facility_level_out"),
    ("facility_target", "facility_target_out"),
    ("task_dates", "toDate32(fromUnixTimestamp64Milli(joined.client_last_modified_time, 'UTC'))"),
    ("synced_date", "toDate32(fromUnixTimestamp64Milli(joined.last_modified_time, 'UTC'))"),
    ("additional_details", "additional_details_json"),
    ("waybill_number", "joined.waybill_number"),
    ("project_id", "joined.reference_id"),
    ("project_type", "joined.project_type"),
    ("project_type_id", "joined.project_type_id"),
    ("project_name", "joined.project_name"),
    ("campaign_number", "joined.campaign_number"),
    ("campaign_id", "''"),
]


SPEC = EntitySpec(
    entity="stock_test",
    python_entity="stock",
    driving_table="stg_stock",
    slice_filter_class=StockSliceFilter,
    target=SilverTarget(
        silver_table="stock_entity",
        joined_rows_sql=joined_rows_sql,
        helper_expressions=helper_expressions,
        silver_columns=SILVER_COLUMNS,
        lookups=(
            boundary_lookup(boundary_keys_sql, code_expr="joined.boundary_code"),
            user_lookup(user_keys_sql, user_expr="joined.client_created_by"),
            user_lookup(facility_user_keys_sql, user_expr="joined.facility_id", alias="facility_users"),
            user_lookup(transacting_user_keys_sql, user_expr="joined.transacting_facility_id", alias="transacting_users"),
        ),
    ),
)

# The Airflow DAG object for this entity, built from SPEC by sql_flatten_common
# (airflow safe-mode discovery needs the words "airflow" and "dag" in this file).
dag = build_sql_test_dag(SPEC)
