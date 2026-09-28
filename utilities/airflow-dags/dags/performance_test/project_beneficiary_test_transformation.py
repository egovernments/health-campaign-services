"""
project_beneficiary_test_transformation.py

TEST twin of project_beneficiary_transformation.py for the `project_beneficiary`
entity: the flatten + write runs inside ClickHouse as one INSERT ... SELECT per
slice (see sql_flatten_common.py for how a run works). Writes to
<database>.project_beneficiary_entity_sql_test, never to project_beneficiary_entity.
Entity name `project_beneficiary_test`.

Known differences from project_beneficiary_transformation.py (all deliberate)
    - stg_project_beneficiary is read with FINAL (the Python DAG reads every version).
    - stg_project is read with FINAL and stg_project_address through 14's
      prj_by_project as one boundary per project (the Python DAG joins both
      without FINAL).
    - The individual used to backfill ageInMonths / gender is ONE row per
      client_reference_id (newest version, ties by id); the Python DAG's
      per-tenant query lets the last returned row win.
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
    age_expressions,
    boundary_lookup,
    build_sql_test_dag,
    json_object_sql,
    json_string_sql,
    json_value_sql,
    one_row_per_key_sql,
    project_address_join_sql,
    project_join_sql,
    table,
    user_lookup,
)

# Sources, in the order they appear in the query:
#
#   source                          alias            keyed / joined on                                      feeds
#   ------------------------------  ---------------  -----------------------------------------------------  --------------------------------------
#   stg_project_beneficiary (FINAL) beneficiary      tenant_id + id range of the slice                      every plain column, the raw and the
#                                                                                                           derived additional_details
#   stg_project (FINAL)             project          project.id = beneficiary.project_id AND tenant         project_* columns, campaign_number,
#                                                    (own PK)                                               hierarchy_type
#   stg_project_address             project_address  project_address.project_id = project.id AND tenant     boundary lookup FALLBACK (after the
#                                                    (14's prj_by_project)                                  beneficiary's own `locality` field)
#   stg_individual                  individual       individual.client_reference_id =                       ageInMonths / gender BACKFILL when the
#                                                    beneficiary.beneficiary_client_reference_id AND tenant beneficiary's own fields lack them
#                                                    (16's prj_by_client_ref, one row per reference)
#   boundary-service response       levels           (tenant_id, hierarchy_type, lookup_boundary_code)      level_one_code .. level_nine_code
#   user-service response           users            (tenant_id, client_last_modified_by)                   user_name, name_of_user, role, user_address

INT_FIELDS = ("ageInMonths", "age")
BOOL_FIELDS = ("isGuestMember", "isHeadOfHousehold")


class ProjectBeneficiarySliceFilter(SliceFilter):
    driving_alias = "beneficiary"

    def projects(self) -> str:
        return self.own_key_in("id", self.slice_column("project_id"))

    def project_addresses(self) -> str:
        return self.own_key_in("project_id", self.slice_column("project_id"))

    def individuals(self) -> str:
        return self.own_key_in("client_reference_id", self.slice_column("beneficiary_client_reference_id"))


def individual_join_sql(f: ProjectBeneficiarySliceFilter) -> str:
    inner = one_row_per_key_sql("stg_individual", f.individuals(), "client_reference_id", ["date_of_birth", "gender"])
    return f"""
    LEFT JOIN
    ({inner}
    ) AS individual
        ON individual.client_reference_id = beneficiary.beneficiary_client_reference_id
       AND individual.tenant_id = beneficiary.tenant_id"""


def _joins(f: ProjectBeneficiarySliceFilter) -> str:
    return (
        project_join_sql(f.projects(), "beneficiary.project_id", "beneficiary.tenant_id")
        + project_address_join_sql(f.project_addresses(), "project.id", "beneficiary.tenant_id")
        + individual_join_sql(f)
    )


# the beneficiary's own `locality` field (last occurrence, as parse_additional_fields' dict holds it)
LOCALITY_FIELD_SQL = (
    "JSONExtractString(arrayLast(fld -> JSONExtractString(fld, 'key') = 'locality', "
    "arrayFilter(fld -> JSONHas(fld, 'key'), JSONExtractArrayRaw(beneficiary.additional_details, 'fields'))), 'value')"
)
LOOKUP_BOUNDARY_CODE_SQL = f"if({LOCALITY_FIELD_SQL} != '', {LOCALITY_FIELD_SQL}, project_address.latest_boundary)"


def joined_rows_sql(f: ProjectBeneficiarySliceFilter) -> str:
    """One row per project beneficiary for the slice, aliased `joined` by the caller."""
    return f"""
    SELECT
        beneficiary.id                               AS id,
        beneficiary.tenant_id                        AS tenant_id,
        beneficiary.project_id                       AS project_id,
        beneficiary.beneficiary_id                   AS beneficiary_id,
        beneficiary.client_reference_id              AS client_reference_id,
        beneficiary.beneficiary_client_reference_id  AS beneficiary_client_reference_id,
        beneficiary.date_of_registration             AS date_of_registration,
        beneficiary.tag                              AS tag,
        beneficiary.is_deleted                       AS is_deleted,
        beneficiary.row_version                      AS row_version,
        beneficiary.additional_details               AS additional_details,
        beneficiary.created_by                       AS created_by,
        beneficiary.last_modified_by                 AS last_modified_by,
        beneficiary.created_time                     AS created_time,
        beneficiary.last_modified_time               AS last_modified_time,
        beneficiary.client_created_by                AS client_created_by,
        beneficiary.client_last_modified_by          AS client_last_modified_by,
        beneficiary.client_created_time              AS client_created_time,
        beneficiary.client_last_modified_time        AS client_last_modified_time,
        project.project_type                         AS project_type,
        project.project_type_id                      AS project_type_id,
        project.name                                 AS project_name,
        project.reference_id                         AS campaign_number,
        JSONExtractString(project.additional_details, 'hierarchyType') AS hierarchy_type,
        {LOOKUP_BOUNDARY_CODE_SQL}                   AS lookup_boundary_code,
        individual.client_reference_id != ''         AS individual_matched,
        individual.latest_date_of_birth              AS individual_date_of_birth,
        individual.latest_gender                     AS individual_gender
    FROM {table('stg_project_beneficiary')} AS beneficiary FINAL{_joins(f)}
    WHERE {f.driving()}"""


def boundary_keys_sql(f: ProjectBeneficiarySliceFilter) -> str:
    """(tenant, project hierarchyType, the beneficiary's own `locality` field else the project's boundary)."""
    return f"""
SELECT DISTINCT tenant_id, hierarchy_type, boundary_code
FROM
(
    SELECT
        beneficiary.tenant_id                                          AS tenant_id,
        JSONExtractString(project.additional_details, 'hierarchyType') AS hierarchy_type,
        {LOOKUP_BOUNDARY_CODE_SQL}                                     AS boundary_code
    FROM {table('stg_project_beneficiary')} AS beneficiary FINAL{project_join_sql(f.projects(), 'beneficiary.project_id', 'beneficiary.tenant_id')}{project_address_join_sql(f.project_addresses(), 'project.id', 'beneficiary.tenant_id')}
    WHERE {f.driving()}
)
WHERE hierarchy_type != '' AND boundary_code != ''
"""


def user_keys_sql(f: ProjectBeneficiarySliceFilter) -> str:
    return f"""
SELECT DISTINCT beneficiary.tenant_id AS tenant_id, beneficiary.client_last_modified_by AS user_id
FROM {table('stg_project_beneficiary')} AS beneficiary FINAL
WHERE {f.driving()} AND beneficiary.client_last_modified_by != ''
"""


def _coerced_value_sql(field: str) -> str:
    """= _coerce_field_value: blank -> null; int keys -> the integer or the RAW
    STRING when it does not parse; bool keys -> `str(value).strip().lower() == "true"`;
    other keys verbatim (json_value_sql)."""
    s = f"JSONExtractString({field}, 'value')"
    int_keys = ", ".join(f"'{k}'" for k in INT_FIELDS)
    bool_keys = ", ".join(f"'{k}'" for k in BOOL_FIELDS)
    blank = f"(NOT JSONHas({field}, 'value') OR JSONType({field}, 'value') = 'Null' OR (JSONType({field}, 'value') = 'String' AND trim({s}) = ''))"
    as_int = (
        f"multiIf(JSONType({field}, 'value') = 'String', "
        f"ifNull(toString(toInt64OrNull({s})), {json_string_sql(s)}), "
        f"JSONType({field}, 'value') IN ('Int64', 'UInt64', 'Double'), toString(toInt64(trunc(JSONExtractFloat({field}, 'value')))), "
        f"JSONType({field}, 'value') = 'Bool', if(JSONExtractBool({field}, 'value'), '1', '0'), "
        f"JSONExtractRaw({field}, 'value'))"
    )
    as_bool = f"if(lower(trim(if(JSONType({field}, 'value') = 'String', {s}, JSONExtractRaw({field}, 'value')))) = 'true', 'true', 'false')"
    return (
        f"multiIf(JSONExtractString({field}, 'key') IN ({int_keys}), if({blank}, 'null', {as_int}), "
        f"JSONExtractString({field}, 'key') IN ({bool_keys}), if({blank}, 'null', {as_bool}), "
        f"{json_value_sql(field)})"
    )


def helper_expressions(f: ProjectBeneficiarySliceFilter) -> list[tuple[str, str]]:
    return [
        # (= _build_beneficiary_additional_details) the beneficiary's own fields, coerced per key
        ("field_pairs",
         f"arrayMap(fld -> (JSONExtractString(fld, 'key'), {_coerced_value_sql('fld')}), "
         "arrayFilter(fld -> JSONHas(fld, 'key'), JSONExtractArrayRaw(joined.additional_details, 'fields')))"),
        # mandatory-field check (= checkMandatoryFieldExists): the dict's value is missing or null
        ("age_missing",    "arrayLast(p -> p.1 = 'ageInMonths', field_pairs).2 IN ('', 'null')"),
        ("gender_missing", "arrayLast(p -> p.1 = 'gender', field_pairs).2 IN ('', 'null')"),
        *age_expressions("joined.individual_date_of_birth"),
        # backfilled values (= _resolve_individual_backfill): age in months; gender or null
        ("backfill_age_json", "toString(age_months)"),
        ("backfill_gender_json", f"if(joined.individual_gender != '', {json_string_sql('joined.individual_gender')}, 'null')"),
        ("additional_details_pairs",
         "arrayConcat(field_pairs, "
         "if(joined.individual_matched AND age_missing, [('ageInMonths', backfill_age_json)], []), "
         "if(joined.individual_matched AND gender_missing, [('gender', backfill_gender_json)], []))"),
        ("additional_details_json", json_object_sql("additional_details_pairs")),
    ]


# (silver column, SQL expression) in project_beneficiary_entity's column order.
SILVER_COLUMNS: list[tuple[str, str]] = [
    ("id", "joined.id"),
    ("tenant_id", "joined.tenant_id"),
    ("project_id", "joined.project_id"),
    ("beneficiary_id", "joined.beneficiary_id"),
    ("beneficiary_client_reference_id", "joined.beneficiary_client_reference_id"),
    ("client_reference_id", "joined.client_reference_id"),
    ("date_of_registration", "joined.date_of_registration"),
    ("tag", "joined.tag"),
    ("is_deleted", "joined.is_deleted"),
    ("row_version", "toInt32(joined.row_version)"),
    ("beneficiary_additional_fields", "joined.additional_details"),
    ("created_by", "joined.created_by"),
    ("last_modified_by", "joined.last_modified_by"),
    ("created_time", "joined.created_time"),
    ("last_modified_time", "joined.last_modified_time"),
    ("client_created_by", "joined.client_created_by"),
    ("client_last_modified_by", "joined.client_last_modified_by"),
    ("client_created_time", "joined.client_created_time"),
    ("client_last_modified_time", "joined.client_last_modified_time"),
    *[(column, f"levels.{column}") for column in LEVEL_COLUMNS],
    ("hierarchy_type", "if(joined.lookup_boundary_code != '', joined.hierarchy_type, '')"),
    ("user_name", "users.user_name"),
    ("name_of_user", "users.name_of_user"),
    ("role", "users.role"),
    ("user_address", "users.user_address"),
    ("task_dates", "toDate32(fromUnixTimestamp64Milli(joined.client_last_modified_time, 'UTC'))"),
    ("synced_date", "toDate32(fromUnixTimestamp64Milli(joined.last_modified_time, 'UTC'))"),
    ("synced_time_stamp", "fromUnixTimestamp64Milli(joined.last_modified_time, 'UTC')"),
    ("additional_details", "additional_details_json"),
    ("project_type", "joined.project_type"),
    ("project_type_id", "joined.project_type_id"),
    ("project_name", "joined.project_name"),
    ("campaign_number", "joined.campaign_number"),
    ("campaign_id", "''"),
]


SPEC = EntitySpec(
    entity="project_beneficiary_test",
    python_entity="project_beneficiary",
    driving_table="stg_project_beneficiary",
    slice_filter_class=ProjectBeneficiarySliceFilter,
    target=SilverTarget(
        silver_table="project_beneficiary_entity",
        joined_rows_sql=joined_rows_sql,
        helper_expressions=helper_expressions,
        silver_columns=SILVER_COLUMNS,
        lookups=(
            boundary_lookup(boundary_keys_sql, code_expr="joined.lookup_boundary_code"),
            user_lookup(user_keys_sql, user_expr="joined.client_last_modified_by"),
        ),
    ),
)

# The Airflow DAG object for this entity, built from SPEC by sql_flatten_common
# (airflow safe-mode discovery needs the words "airflow" and "dag" in this file).
dag = build_sql_test_dag(SPEC)
