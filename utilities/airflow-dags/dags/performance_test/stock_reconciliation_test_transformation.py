"""
stock_reconciliation_test_transformation.py

TEST twin of stock_reconciliation_transformation.py for the
`stock_reconciliation` entity: the flatten + write runs inside ClickHouse as
one INSERT ... SELECT per slice (see sql_flatten_common.py for how a run
works). Writes to <database>.stock_reconciliation_entity_sql_test, never to
stock_reconciliation_entity. Entity name `stock_reconciliation_test`.

Known differences from stock_reconciliation_transformation.py (all deliberate)
    - stg_stock_reconciliation is read with FINAL (the Python DAG reads every
      version and relies on the silver table's own ReplacingMergeTree).
    - stg_project_address is read through 14's prj_by_project as one boundary
      per project; the Python DAG's FINAL join fans out on several address rows.
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
    json_double_or_null_sql,
    json_int_or_zero_sql,
    json_object_sql,
    json_value_sql,
    project_address_join_sql,
    project_join_sql,
    table,
    user_lookup,
)

# Sources, in the order they appear in the query:
#
#   source                          alias            keyed / joined on                                          feeds
#   ------------------------------  ---------------  ---------------------------------------------------------  --------------------------------------
#   stg_stock_reconciliation(FINAL) reconciliation   tenant_id + id range of the slice                          every plain column, additional_fields,
#                                                                                                               the coerced additional_details
#   stg_facility (FINAL)            facility         facility.id = reconciliation.facility_id AND tenant        facility_name, facility_level,
#                                                    (own PK), deleted facilities excluded                      facility_target, the address key
#   stg_address (FINAL)             address          address.id = facility.address_id AND tenant (own PK)       boundary_code (first tier)
#   stg_product_variant (FINAL)     variant          variant.id = reconciliation.product_variant_id AND tenant  product_name (sku)
#   stg_project (FINAL)             project          project.id = reconciliation.reference_id AND tenant        project_* columns, campaign_number,
#                                                                                                               hierarchy_type
#   stg_project_address             project_address  project_address.project_id = project.id AND tenant         boundary_code second tier, only when
#                                                    (14's prj_by_project)                                      reference_id_type = PROJECT
#   boundary-service response       levels           (tenant_id, hierarchy_type, boundary_code)                 level_one_code .. level_nine_code
#   user-service response           users            (tenant_id, client_last_modified_by)                      user_name, name_of_user, role, user_address

# additional_details keys written as doubles (= ADDITIONAL_DETAILS_DOUBLE_FIELDS)
DOUBLE_FIELDS = ("received", "issued", "returned", "lost", "gained", "damaged", "inHand")


class StockReconciliationSliceFilter(SliceFilter):
    driving_alias = "reconciliation"

    def facilities(self) -> str:
        return self.own_key_in("id", self.slice_column("facility_id"))

    def addresses(self) -> str:
        """The facilities' address ids (raw superset; the join itself picks)."""
        return self.own_key_in(
            "id",
            f"(SELECT DISTINCT address_id FROM {table('stg_facility')} WHERE {self.facilities()} AND address_id != '')",
        )

    def product_variants(self) -> str:
        return self.own_key_in("id", self.slice_column("product_variant_id"))

    def projects(self) -> str:
        return self.own_key_in("id", self.slice_column("reference_id"))

    def project_addresses(self) -> str:
        return self.own_key_in("project_id", self.slice_column("reference_id"))


BOUNDARY_CODE_SQL = (
    "if(address.locality_code != '', address.locality_code, "
    "if(upper(reconciliation.reference_id_type) = 'PROJECT', project_address.latest_boundary, ''))"
)


def facility_join_sql(f: StockReconciliationSliceFilter) -> str:
    """LEFT JOIN stg_facility AS facility ON its own PK; a deleted facility does not match."""
    return f"""
    LEFT JOIN
    (
        SELECT id, tenant_id, name, usage, is_permanent, address_id, additional_details
        FROM {table('stg_facility')} FINAL
        WHERE {f.facilities()} AND is_deleted = false
    ) AS facility
        ON facility.id = reconciliation.facility_id AND facility.tenant_id = reconciliation.tenant_id"""


def variant_join_sql(f: StockReconciliationSliceFilter) -> str:
    return f"""
    LEFT JOIN
    (
        SELECT id, tenant_id, sku
        FROM {table('stg_product_variant')} FINAL
        WHERE {f.product_variants()}
    ) AS variant
        ON variant.id = reconciliation.product_variant_id AND variant.tenant_id = reconciliation.tenant_id"""


def _joins(f: StockReconciliationSliceFilter) -> str:
    return (
        facility_join_sql(f)
        + address_join_sql(f.addresses(), "facility.address_id", "reconciliation.tenant_id",
                           columns="id, tenant_id, locality_code")
        + variant_join_sql(f)
        + project_join_sql(f.projects(), "reconciliation.reference_id", "reconciliation.tenant_id")
        + project_address_join_sql(f.project_addresses(), "project.id", "project.tenant_id")
    )


def joined_rows_sql(f: StockReconciliationSliceFilter) -> str:
    """One row per reconciliation for the slice, aliased `joined` by the caller."""
    return f"""
    SELECT
        reconciliation.id                          AS id,
        reconciliation.client_reference_id         AS client_reference_id,
        reconciliation.tenant_id                   AS tenant_id,
        reconciliation.facility_id                 AS facility_id,
        reconciliation.product_variant_id          AS product_variant_id,
        reconciliation.reference_id                AS reference_id,
        reconciliation.reference_id_type           AS reference_id_type,
        reconciliation.date_of_reconciliation      AS date_of_reconciliation,
        reconciliation.calculated_count            AS calculated_count,
        reconciliation.physical_recorded_count     AS physical_recorded_count,
        reconciliation.comments_on_reconciliation  AS comments_on_reconciliation,
        reconciliation.additional_details          AS additional_details,
        reconciliation.created_by                  AS created_by,
        reconciliation.created_time                AS created_time,
        reconciliation.last_modified_by            AS last_modified_by,
        reconciliation.last_modified_time          AS last_modified_time,
        reconciliation.client_created_time         AS client_created_time,
        reconciliation.client_last_modified_time   AS client_last_modified_time,
        reconciliation.client_created_by           AS client_created_by,
        reconciliation.client_last_modified_by     AS client_last_modified_by,
        reconciliation.row_version                 AS row_version,
        reconciliation.is_deleted                  AS is_deleted,
        facility.name                              AS facility_name,
        facility.usage                             AS facility_usage,
        facility.is_permanent                      AS facility_is_permanent,
        facility.additional_details                AS facility_additional_details,
        variant.sku                                AS product_sku,
        project.project_type                       AS project_type,
        project.project_type_id                    AS project_type_id,
        project.name                               AS project_name,
        project.reference_id                       AS campaign_number,
        JSONExtractString(project.additional_details, 'hierarchyType') AS hierarchy_type,
        {BOUNDARY_CODE_SQL}                        AS boundary_code
    FROM {table('stg_stock_reconciliation')} AS reconciliation FINAL{_joins(f)}
    WHERE {f.driving()}"""


def boundary_keys_sql(f: StockReconciliationSliceFilter) -> str:
    """(tenant, project hierarchyType, facility locality else -- for PROJECT
    references -- the project's boundary), both non-empty (= _get_boundary_lookup_key)."""
    return f"""
SELECT DISTINCT tenant_id, hierarchy_type, boundary_code
FROM
(
    SELECT
        reconciliation.tenant_id                                       AS tenant_id,
        JSONExtractString(project.additional_details, 'hierarchyType') AS hierarchy_type,
        {BOUNDARY_CODE_SQL}                                            AS boundary_code
    FROM {table('stg_stock_reconciliation')} AS reconciliation FINAL{_joins(f)}
    WHERE {f.driving()}
)
WHERE hierarchy_type != '' AND boundary_code != ''
"""


def user_keys_sql(f: StockReconciliationSliceFilter) -> str:
    """(tenant_id, client_last_modified_by), the CLIENT audit's last modifier."""
    return f"""
SELECT DISTINCT reconciliation.tenant_id AS tenant_id, reconciliation.client_last_modified_by AS user_id
FROM {table('stg_stock_reconciliation')} AS reconciliation FINAL
WHERE {f.driving()} AND reconciliation.client_last_modified_by != ''
"""


def helper_expressions(f: StockReconciliationSliceFilter) -> list[tuple[str, str]]:
    double_keys = ", ".join(f"'{key}'" for key in DOUBLE_FIELDS)
    return [
        # facility_level (= _resolve_facility_level): WAREHOUSE -> DISTRICT_/SATELLITE_WAREHOUSE by is_permanent
        ("facility_level",
         "if(upper(joined.facility_usage) = 'WAREHOUSE', "
         "if(joined.facility_is_permanent, 'DISTRICT_WAREHOUSE', 'SATELLITE_WAREHOUSE'), '')"),
        # facility_target (= _resolve_facility_target): int() of the LAST `target` field, 0 when absent or unparsable
        ("facility_fields",
         "arrayFilter(fld -> JSONHas(fld, 'key'), JSONExtractArrayRaw(joined.facility_additional_details, 'fields'))"),
        ("target_field", "arrayLast(fld -> JSONExtractString(fld, 'key') = 'target', facility_fields)"),
        ("facility_target", f"toInt64OrZero({json_int_or_zero_sql('target_field')})"),
        # additional_details (= _build_stock_reconciliation_additional_details): quantity keys as doubles or null
        ("field_pairs",
         f"arrayMap(fld -> (JSONExtractString(fld, 'key'), if(JSONExtractString(fld, 'key') IN ({double_keys}), "
         f"{json_double_or_null_sql('fld')}, {json_value_sql('fld')})), "
         "arrayFilter(fld -> JSONHas(fld, 'key'), JSONExtractArrayRaw(joined.additional_details, 'fields')))"),
        ("additional_details_json", json_object_sql("field_pairs")),
    ]


# (silver column, SQL expression) in stock_reconciliation_entity's column order.
SILVER_COLUMNS: list[tuple[str, str]] = [
    ("id", "joined.id"),
    ("client_reference_id", "joined.client_reference_id"),
    ("tenant_id", "joined.tenant_id"),
    ("facility_id", "joined.facility_id"),
    ("product_variant_id", "joined.product_variant_id"),
    ("reference_id", "joined.reference_id"),
    ("reference_id_type", "joined.reference_id_type"),
    ("physical_count", "joined.physical_recorded_count"),
    ("calculated_count", "joined.calculated_count"),
    ("comments_on_reconciliation", "joined.comments_on_reconciliation"),
    ("date_of_reconciliation", "joined.date_of_reconciliation"),
    ("additional_fields", "joined.additional_details"),
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
    # name / sku fall back to the id when the lookup misses
    ("facility_name", "if(joined.facility_name != '', joined.facility_name, joined.facility_id)"),
    ("facility_target", "facility_target"),
    ("facility_level", "facility_level"),
    ("product_name", "if(joined.product_sku != '', joined.product_sku, joined.product_variant_id)"),
    ("user_name", "users.user_name"),
    ("name_of_user", "users.name_of_user"),
    ("role", "users.role"),
    ("user_address", "users.user_address"),
    ("synced_time_stamp", "fromUnixTimestamp64Milli(joined.last_modified_time, 'UTC')"),
    ("synced_time", "joined.last_modified_time"),
    ("task_dates", "toDate32(fromUnixTimestamp64Milli(joined.client_last_modified_time, 'UTC'))"),
    ("synced_date", "toDate32(fromUnixTimestamp64Milli(joined.last_modified_time, 'UTC'))"),
    *[(column, f"levels.{column}") for column in LEVEL_COLUMNS],
    ("hierarchy_type", "if(joined.boundary_code != '', joined.hierarchy_type, '')"),
    ("boundary_code", "joined.boundary_code"),
    ("additional_details", "additional_details_json"),
    ("project_id", "joined.reference_id"),
    ("project_type", "joined.project_type"),
    ("project_type_id", "joined.project_type_id"),
    ("project_name", "joined.project_name"),
    ("campaign_number", "joined.campaign_number"),
    ("campaign_id", "''"),
]


SPEC = EntitySpec(
    entity="stock_reconciliation_test",
    python_entity="stock_reconciliation",
    driving_table="stg_stock_reconciliation",
    slice_filter_class=StockReconciliationSliceFilter,
    target=SilverTarget(
        silver_table="stock_reconciliation_entity",
        joined_rows_sql=joined_rows_sql,
        helper_expressions=helper_expressions,
        silver_columns=SILVER_COLUMNS,
        lookups=(
            boundary_lookup(boundary_keys_sql, code_expr="joined.boundary_code"),
            user_lookup(user_keys_sql, user_expr="joined.client_last_modified_by"),
        ),
    ),
)

# The Airflow DAG object for this entity, built from SPEC by sql_flatten_common
# (airflow safe-mode discovery needs the words "airflow" and "dag" in this file).
dag = build_sql_test_dag(SPEC)
