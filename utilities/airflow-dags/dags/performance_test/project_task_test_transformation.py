"""
project_task_test_transformation.py

TEST twin of project_task_transformation.py for the `project_task` entity:
the flatten + write runs inside ClickHouse as one INSERT ... SELECT per slice
(see sql_flatten_common.py for how a run works). Writes to
<database>.project_task_entity_sql_test, never to project_task_entity.
Entity name `project_task_test` (dag_id project_task_test_transformation).

Measurements: airflow_dags/PROJECTION_COMPARISON_REPORT.md sections 8-8c (at
~1 M tasks the Python DAG is killed by the ClickHouse pod's memory limit; this
twin finishes in 7 minutes with 127 MiB p50 per insert).

household AND individual are both joined in this one statement although a
project is either HOUSEHOLD or INDIVIDUAL. CLAUDE.md's "no unconditional
joins for mutually-exclusive lookups" rule targets joining whole branch
tables onto every row; here each right side is a projection point lookup
bounded to the slice's own beneficiary refs, and the branch is chosen per
row in the column expressions.

Known differences from project_task_transformation.py (all deliberate)
    - The bridge, household and individual sides are ONE row per
      client_reference_id (newest version, ties by id); the Python DAG's
      "last writer wins" in undefined result order.
    - stg_project_address is one boundary per project (14's prj_by_project).
    - age / date_of_birth / gender are blank when the individual is not found;
      the Python DAG writes age 680 there since stg_individual.date_of_birth
      became a non-nullable Date32 (§8c).
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
    one_row_per_key_sql,
    project_address_join_sql,
    project_join_sql,
    python_float_text_sql,
    table,
    user_lookup,
)

# Sources, in the order they appear in the query:
#
#   source                        alias            keyed / joined on                                        feeds
#   ----------------------------  ---------------  -------------------------------------------------------  ---------------------------------------------
#   stg_project_task (FINAL)      task             tenant_id + id range of the slice                        task_id, status, client audit columns,
#                                                                                                           additional_details, synced_*, task_dates
#   stg_task_resource             resource         resource.task_id = task.id AND tenant                    id, client_reference_id, product_variant,
#                                                  (16's prj_by_task, argMax, is_deleted after argMax)      quantity, is_delivered, delivery_comments
#                                                  ONE SILVER ROW PER RESOURCE; placeholder row when none
#   stg_address (FINAL)           address          address.id = task.address_id AND tenant (own PK)         latitude, longitude, location_accuracy,
#                                                                                                           boundary_code, geo_point, boundary lookup
#   stg_project (FINAL)           project          project.id = task.project_id AND tenant (own PK)         hierarchy_type, beneficiaryType (delivered_to
#                                                                                                           + branch), cycles, project_* columns
#   stg_product_variant (FINAL)   variant          variant.id = resource.latest_product_variant_id          product_name (sku)
#                                                  AND tenant (own PK)
#   stg_project_address           project_address  project_address.project_id = project.id AND tenant       boundary lookup FALLBACK only
#                                                  (14's prj_by_project, one boundary per project)
#   stg_project_beneficiary       beneficiary      beneficiary.client_reference_id =                        household_id / individual_id, and the key
#                                                  task.project_beneficiary_client_reference_id AND tenant  into household / individual
#                                                  (16's prj_by_client_ref, ONE row per task-side ref)
#   stg_household                 household        household.client_reference_id = beneficiary ref          member_count   (HOUSEHOLD projects only)
#                                                  AND tenant (prj_by_client_ref, one row per ref)
#   stg_individual                individual       individual.client_reference_id = beneficiary ref         date_of_birth, gender, age (INDIVIDUAL only)
#                                                  AND tenant (prj_by_client_ref, one row per ref)
#   boundary-service response     levels           (tenant_id, hierarchy_type, lookup boundary code)        level_one_code .. level_nine_code
#   user-service response         users            (tenant_id, client_created_by)                           user_name, name_of_user, role, user_address


class ProjectTaskSliceFilter(SliceFilter):
    driving_alias = "task"

    def resources(self) -> str:
        """For stg_task_resource via prj_by_task: prefix range on (tenant_id, task_id)."""
        return self.keyed_by_driving_id("task_id")

    def addresses(self) -> str:
        return self.own_key_in("id", self.slice_column("address_id"))

    def projects(self) -> str:
        return self.own_key_in("id", self.slice_column("project_id"))

    def project_addresses(self) -> str:
        return self.own_key_in("project_id", self.slice_column("project_id"))

    def product_variants(self) -> str:
        """For stg_product_variant FINAL: the tenant's whole catalogue (hundreds of rows)."""
        return f"tenant_id = {self.tenant}"

    def beneficiaries(self) -> str:
        return self.own_key_in("client_reference_id", self.slice_column("project_beneficiary_client_reference_id"))

    def beneficiary_targets(self) -> str:
        """For stg_household / stg_individual via prj_by_client_ref: the refs the
        slice's bridge rows point at (a raw superset; the join itself dedups)."""
        return self.own_key_in(
            "client_reference_id",
            f"(SELECT DISTINCT beneficiary_client_reference_id FROM {table('stg_project_beneficiary')} "
            f"WHERE {self.beneficiaries()} AND beneficiary_client_reference_id != '')")


def resource_join_sql(f: ProjectTaskSliceFilter) -> str:
    """LEFT JOIN stg_task_resource AS resource ON (task_id, tenant_id). Latest
    version of each resource via argMax on last_modified_time; a resource whose
    newest version is deleted is dropped. A task with N resources yields N
    silver rows; a task with none yields one placeholder row."""
    return f"""
    LEFT JOIN
    (
        SELECT
            tenant_id,
            task_id,
            id,
            argMax(product_variant_id,      last_modified_time) AS latest_product_variant_id,
            argMax(quantity,                last_modified_time) AS latest_quantity,
            argMax(is_delivered,            last_modified_time) AS latest_is_delivered,
            argMax(reason_if_not_delivered, last_modified_time) AS latest_reason_if_not_delivered,
            argMax(client_reference_id,     last_modified_time) AS latest_client_reference_id,
            argMax(is_deleted,              last_modified_time) AS latest_is_deleted
        FROM {table('stg_task_resource')}
        WHERE {f.resources()}
        GROUP BY tenant_id, task_id, id
        HAVING latest_is_deleted = false
    ) AS resource
        ON resource.task_id = task.id AND resource.tenant_id = task.tenant_id"""


def product_variant_join_sql(f: ProjectTaskSliceFilter) -> str:
    """LEFT JOIN stg_product_variant AS variant ON the RESOURCE's variant id
    (a join, not a Map as in the project twin: one variant per resource row)."""
    return f"""
    LEFT JOIN
    (
        SELECT id, tenant_id, sku
        FROM {table('stg_product_variant')} FINAL
        WHERE {f.product_variants()}
    ) AS variant
        ON variant.id = resource.latest_product_variant_id AND variant.tenant_id = task.tenant_id"""


def beneficiary_join_sql(f: ProjectTaskSliceFilter) -> str:
    inner = one_row_per_key_sql("stg_project_beneficiary", f.beneficiaries(), "client_reference_id",
                                ["beneficiary_client_reference_id"])
    return f"""
    LEFT JOIN
    ({inner}
    ) AS beneficiary
        ON beneficiary.client_reference_id = task.project_beneficiary_client_reference_id
       AND beneficiary.tenant_id = task.tenant_id"""


def household_join_sql(f: ProjectTaskSliceFilter) -> str:
    inner = one_row_per_key_sql("stg_household", f.beneficiary_targets(), "client_reference_id", ["member_count"])
    return f"""
    LEFT JOIN
    ({inner}
    ) AS household
        ON household.client_reference_id = beneficiary.latest_beneficiary_client_reference_id
       AND household.tenant_id = task.tenant_id"""


def individual_join_sql(f: ProjectTaskSliceFilter) -> str:
    inner = one_row_per_key_sql("stg_individual", f.beneficiary_targets(), "client_reference_id",
                                ["date_of_birth", "gender"])
    return f"""
    LEFT JOIN
    ({inner}
    ) AS individual
        ON individual.client_reference_id = beneficiary.latest_beneficiary_client_reference_id
       AND individual.tenant_id = task.tenant_id"""


def _boundary_joins(f: ProjectTaskSliceFilter) -> str:
    return (
        address_join_sql(f.addresses(), "task.address_id", "task.tenant_id",
                         columns="id, tenant_id, latitude, longitude, location_accuracy, locality_code")
        + project_join_sql(f.projects(), "task.project_id", "task.tenant_id")
        + project_address_join_sql(f.project_addresses(), "project.id", "project.tenant_id")
    )


LOOKUP_BOUNDARY_CODE_SQL = "if(address.locality_code != '', address.locality_code, project_address.latest_boundary)"


def joined_rows_sql(f: ProjectTaskSliceFilter) -> str:
    """One row per (task, resource) for the slice, aliased `joined` by the
    caller. Unmatched sides are '' / 0 / false (join_use_nulls = 0)."""
    return f"""
    SELECT
        task.id                                       AS id,
        task.tenant_id                                AS tenant_id,
        task.project_id                               AS project_id,
        task.project_beneficiary_client_reference_id  AS project_beneficiary_client_reference_id,
        task.additional_details                       AS additional_details,
        task.created_time                             AS created_time,
        task.last_modified_time                       AS last_modified_time,
        task.client_created_time                      AS client_created_time,
        task.client_last_modified_time                AS client_last_modified_time,
        task.client_created_by                        AS client_created_by,
        task.client_last_modified_by                  AS client_last_modified_by,
        task.client_reference_id                      AS client_reference_id,
        task.status                                   AS status,
        resource.id                                   AS resource_id,
        resource.latest_product_variant_id            AS resource_product_variant_id,
        resource.latest_quantity                      AS resource_quantity,
        resource.latest_is_delivered                  AS resource_is_delivered,
        resource.latest_reason_if_not_delivered       AS resource_reason_if_not_delivered,
        resource.latest_client_reference_id           AS resource_client_reference_id,
        address.latitude                              AS address_latitude,
        address.longitude                             AS address_longitude,
        address.location_accuracy                     AS address_location_accuracy,
        address.locality_code                         AS address_locality_code,
        project.additional_details                    AS project_additional_details,
        project.project_type                          AS project_type,
        project.project_type_id                       AS project_type_id,
        project.name                                  AS project_name,
        project.reference_id                          AS campaign_number,
        JSONExtractString(project.additional_details, 'hierarchyType')                  AS hierarchy_type,
        JSONExtractString(project.additional_details, 'projectType', 'beneficiaryType') AS beneficiary_type,
        variant.sku                                   AS product_name,
        {LOOKUP_BOUNDARY_CODE_SQL}                    AS lookup_boundary_code,
        beneficiary.client_reference_id != ''         AS beneficiary_matched,
        beneficiary.latest_beneficiary_client_reference_id AS beneficiary_ref,
        household.latest_member_count                 AS household_member_count,
        individual.client_reference_id != ''          AS individual_matched,
        individual.latest_date_of_birth               AS individual_date_of_birth,
        individual.latest_gender                      AS individual_gender
    FROM {table('stg_project_task')} AS task FINAL{resource_join_sql(f)}{_boundary_joins(f)}{product_variant_join_sql(f)}{beneficiary_join_sql(f)}{household_join_sql(f)}{individual_join_sql(f)}
    WHERE {f.driving()}"""


def boundary_keys_sql(f: ProjectTaskSliceFilter) -> str:
    """hierarchy_type from the PROJECT's additional_details, code = the task's
    address locality, else the project's boundary; both non-empty
    (= project_task_transformation._get_boundary_lookup_key)."""
    return f"""
SELECT DISTINCT tenant_id, hierarchy_type, boundary_code
FROM
(
    SELECT
        task.tenant_id                                                 AS tenant_id,
        JSONExtractString(project.additional_details, 'hierarchyType') AS hierarchy_type,
        {LOOKUP_BOUNDARY_CODE_SQL}                                     AS boundary_code
    FROM {table('stg_project_task')} AS task FINAL{_boundary_joins(f)}
    WHERE {f.driving()}
)
WHERE hierarchy_type != '' AND boundary_code != ''
"""


def user_keys_sql(f: ProjectTaskSliceFilter) -> str:
    return f"""
SELECT DISTINCT task.tenant_id AS tenant_id, task.client_created_by AS user_id
FROM {table('stg_project_task')} AS task FINAL
WHERE {f.driving()} AND task.client_created_by != ''
"""


def helper_expressions(f: ProjectTaskSliceFilter) -> list[tuple[str, str]]:
    """Port of project_task_transformation._build_silver_row, _resolve_resource_fields,
    _attach_task_core_fields, _attach_beneficiary_details, _attach_cycle_dose_delivery
    and the egov_api_utils helpers they call."""
    return [
        # the task's own additional_details: {"fields": [{"key": ..., "value": ...}, ...]}
        # (= parse_additional_fields; a later duplicate key wins, hence arrayLast)
        ("task_fields",
         "arrayMap(f -> (JSONExtractString(f, 'key'), JSONExtractString(f, 'value')), "
         "arrayFilter(f -> JSONHas(f, 'key'), JSONExtractArrayRaw(joined.additional_details, 'fields')))"),
        ("field_delivery_strategy",  "arrayLast(t -> t.1 = 'deliveryStrategy', task_fields).2"),
        ("field_cycle_index",        "arrayLast(t -> t.1 = 'cycleIndex', task_fields).2"),
        ("field_dose_index",         "arrayLast(t -> t.1 = 'doseIndex', task_fields).2"),
        ("field_product_variant_id", "arrayLast(t -> t.1 = 'productVariantId', task_fields).2"),
        ("field_task_status",        "arrayLast(t -> t.1 = 'taskStatus', task_fields).2"),

        # real resource row vs placeholder (= _resolve_resource_fields / Java constructTaskResourceIfNull)
        ("has_resource", "joined.resource_id != ''"),
        ("placeholder_referred",
         "upper(field_task_status) = 'BENEFICIARY_REFERRED' AND field_product_variant_id != ''"),

        # beneficiary branch (= _attach_beneficiary_details): chosen by the PROJECT's beneficiaryType
        ("is_household_project",  "upper(joined.beneficiary_type) = 'HOUSEHOLD'"),
        ("is_individual_project", "upper(joined.beneficiary_type) = 'INDIVIDUAL'"),
        ("beneficiary_found",     "joined.project_beneficiary_client_reference_id != '' AND joined.beneficiary_matched"),
        # the individual row itself may be missing from bronze although the bridge points at it; the match
        # flag, not the value, must decide (the join default for a missing Date32 would be 1900-01-01)
        ("individual_known",      "is_individual_project AND beneficiary_found AND joined.individual_matched"),
        # age in whole months (= calculate_age_in_months); now() once per statement rather than per row
        ("today_utc", "toDate(now('UTC'))"),
        ("age_months",
         "ifNull((toYear(today_utc) - toYear(joined.individual_date_of_birth)) * 12 "
         "+ (toMonth(today_utc) - toMonth(joined.individual_date_of_birth)), toInt64(0))"),

        # cycleIndex / doseIndex (= _parse_uint8, get_project_cycles, fetch_cycle_index)
        # -1 means "did not parse" and falls through to the next rule
        ("parsed_cycle_index", "ifNull(toInt64OrNull(field_cycle_index), toInt64(-1))"),
        ("parsed_dose_index",  "ifNull(toInt64OrNull(field_dose_index), toInt64(-1))"),
        ("project_cycles",
         "arrayMap(c -> (assumeNotNull(c.1), assumeNotNull(c.2), assumeNotNull(c.3)), "
         "arrayFilter(c -> c.1 IS NOT NULL AND c.2 IS NOT NULL AND c.3 IS NOT NULL, "
         "arrayMap(c -> (toInt64OrNull(JSONExtractString(c, 'id')), toInt64OrNull(JSONExtractString(c, 'startDate')), "
         "toInt64OrNull(JSONExtractString(c, 'endDate'))), "
         "JSONExtractArrayRaw(joined.project_additional_details, 'projectType', 'cycles'))))"),
        ("task_ms", "joined.client_created_time"),
        # index of the cycle containing the task time, or the cycle whose gap before the next one contains it
        ("cycle_hit",
         "if(task_ms = 0 OR empty(project_cycles), toUInt64(0), arrayFirstIndex(i -> "
         "(project_cycles[i].2 <= task_ms AND task_ms <= project_cycles[i].3) OR "
         "(i < length(project_cycles) AND project_cycles[i].3 < task_ms AND task_ms < project_cycles[i + 1].2), "
         "arrayEnumerate(project_cycles)))"),
        ("computed_cycle_index", "if(cycle_hit > 0, project_cycles[cycle_hit].1, toInt64(-1))"),
        ("cycle_index_value",
         "multiIf(NOT is_individual_project, toInt64(0), "
         "parsed_cycle_index BETWEEN 0 AND 255, parsed_cycle_index, "
         "computed_cycle_index BETWEEN 0 AND 255, computed_cycle_index, toInt64(0))"),
        ("dose_index_value",
         "if(is_individual_project AND parsed_dose_index BETWEEN 0 AND 255, parsed_dose_index, toInt64(0))"),

        # geo_point = json.dumps([longitude, latitude]); Python prints integral floats as 12.0
        ("lon_json", python_float_text_sql("joined.address_longitude")),
        ("lat_json", python_float_text_sql("joined.address_latitude")),
    ]


# (silver column, SQL expression) in project_task_entity's column order.
SILVER_COLUMNS: list[tuple[str, str]] = [
    ("id", "if(has_resource, joined.resource_id, concat(joined.status, '-', joined.id))"),
    ("task_id", "joined.id"),
    ("task_type", "'DELIVERY'"),
    ("status", "joined.status"),
    ("tenant_id", "joined.tenant_id"),
    ("administration_status", "joined.status"),
    ("client_reference_id",
     "if(has_resource, joined.resource_client_reference_id, concat(joined.status, '-', joined.client_reference_id))"),
    ("task_client_reference_id", "joined.client_reference_id"),
    ("project_beneficiary_client_reference_id", "joined.project_beneficiary_client_reference_id"),
    # audit columns come from the CLIENT audit trail, not the server one
    ("created_by", "joined.client_created_by"),
    ("last_modified_by", "joined.client_last_modified_by"),
    ("created_time", "joined.client_created_time"),
    ("last_modified_time", "joined.client_last_modified_time"),
    ("product_variant", "if(has_resource, joined.resource_product_variant_id, field_product_variant_id)"),
    ("product_name", "joined.product_name"),
    ("quantity",
     "if(has_resource, toInt64(round(joined.resource_quantity)), if(placeholder_referred, toInt64(2), toInt64(0)))"),
    ("delivered_to", "joined.beneficiary_type"),
    ("is_delivered", "if(has_resource, joined.resource_is_delivered, false)"),
    ("delivery_comments",
     "if(has_resource, joined.resource_reason_if_not_delivered, "
     "if(placeholder_referred, 'ADMINISTRATION_NOT_SUCCESSFUL', ''))"),
    ("household_id", "if(is_household_project AND beneficiary_found, joined.beneficiary_ref, '')"),
    ("member_count", "if(is_household_project AND beneficiary_found, joined.household_member_count, toInt32(0))"),
    ("individual_id", "if(is_individual_project AND beneficiary_found, joined.beneficiary_ref, '')"),
    ("user_name", "users.user_name"),
    ("name_of_user", "users.name_of_user"),
    ("role", "users.role"),
    ("user_address", "users.user_address"),
    ("latitude", "joined.address_latitude"),
    ("longitude", "joined.address_longitude"),
    ("location_accuracy", "toFloat64(joined.address_location_accuracy)"),
    # the task's own locality only -- the project-boundary fallback applies to the LOOKUP key, not here
    ("boundary_code", "joined.address_locality_code"),
    ("geo_point", "concat('[', lon_json, ', ', lat_json, ']')"),
    *[(column, f"levels.{column}") for column in LEVEL_COLUMNS],
    # all-or-nothing with the levels: blank unless a lookup key existed (= attach_boundary_levels)
    ("hierarchy_type", "if(joined.lookup_boundary_code != '', joined.hierarchy_type, '')"),
    ("age", "if(individual_known, toUInt32(greatest(toInt64(0), age_months)), toUInt32(0))"),
    ("gender", "if(individual_known, joined.individual_gender, '')"),
    # via Int32 days: a plain if() between a Date32 join column and a Date32 constant hits a
    # ClickHouse LOGICAL_ERROR ("Cannot get native value for column with type Date32") on some blocks
    ("date_of_birth",
     "toDate32(if(individual_known, ifNull(toInt32(joined.individual_date_of_birth), toInt32(0)), toInt32(0)))"),
    ("cycleIndex", "toUInt8(cycle_index_value)"),
    ("doseIndex", "toUInt8(dose_index_value)"),
    ("delivery_strategy", "field_delivery_strategy"),
    # server timestamps for the synced_* columns, client timestamp for task_dates; 0 -> epoch defaults
    ("synced_time_stamp", "fromUnixTimestamp64Milli(joined.created_time, 'UTC')"),
    ("synced_date", "toDate32(fromUnixTimestamp64Milli(joined.last_modified_time, 'UTC'))"),
    ("synced_time", "joined.last_modified_time"),
    ("task_dates", "toDate32(fromUnixTimestamp64Milli(joined.client_last_modified_time, 'UTC'))"),
    ("additional_details", "joined.additional_details"),
    ("project_id", "joined.project_id"),
    ("project_type", "joined.project_type"),
    ("project_type_id", "joined.project_type_id"),
    ("project_name", "joined.project_name"),
    ("campaign_number", "joined.campaign_number"),
    ("campaign_id", "''"),
]


SPEC = EntitySpec(
    entity="project_task_test",
    python_entity="project_task",
    driving_table="stg_project_task",
    slice_filter_class=ProjectTaskSliceFilter,
    target=SilverTarget(
        silver_table="project_task_entity",
        joined_rows_sql=joined_rows_sql,
        helper_expressions=helper_expressions,
        silver_columns=SILVER_COLUMNS,
        lookups=(
            boundary_lookup(boundary_keys_sql, code_expr="joined.lookup_boundary_code"),
            user_lookup(user_keys_sql, user_expr="joined.client_created_by"),
        ),
    ),
)

# The Airflow DAG object for this entity, built from SPEC by sql_flatten_common
# (airflow safe-mode discovery needs the words "airflow" and "dag" in this file).
dag = build_sql_test_dag(SPEC)
