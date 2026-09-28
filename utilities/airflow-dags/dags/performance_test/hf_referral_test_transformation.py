"""
hf_referral_test_transformation.py

TEST twin of hf_referral_transformation.py for the `hf_referral` entity: the
flatten + write runs inside ClickHouse as one INSERT ... SELECT per slice
(see sql_flatten_common.py for how a run works). Writes to
<database>.hf_referral_entity_sql_test, never to hf_referral_entity.
Entity name `hf_referral_test` (dag_id hf_referral_test_transformation).

Known differences from hf_referral_transformation.py (all deliberate)
    - stg_hf_referral is read with FINAL (the Python DAG reads every version and
      relies on the silver table's own ReplacingMergeTree to collapse them).
    - stg_project_address is read through 14's prj_by_project as one boundary
      per project (newest by _ingested_at); the Python DAG's FINAL join fans
      out if a project has several address rows.
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
    boundary_lookup,
    build_sql_test_dag,
    cycle_index_expressions,
    project_address_join_sql,
    project_join_sql,
    table,
    user_lookup,
)

# Sources, in the order they appear in the query:
#
#   source                     alias            keyed / joined on                                        feeds
#   -------------------------  ---------------  -------------------------------------------------------  ----------------------------------------
#   stg_hf_referral (FINAL)    hf_referral      tenant_id + id range of the slice                        every plain column, additional_fields,
#                                                                                                        cycleIndex (client_created_time)
#   stg_project (FINAL)        project          project.id = hf_referral.project_id AND tenant (own PK)  project_* columns, campaign_number,
#                                                                                                        hierarchy_type, cycles
#   stg_project_address        project_address  project_address.project_id = project.id AND tenant       boundary lookup FALLBACK (third tier)
#                                               (14's prj_by_project, one boundary per project)
#   boundary-service response  levels           (tenant_id, hierarchy_type, lookup_boundary_code)         level_one_code .. level_nine_code
#                                               where the code is, in order: hf_referral.locality_code,
#                                               the `boundaryCode` field of its additionalFields, the
#                                               project's boundary
#   user-service response      users            (tenant_id, client_created_by)                           user_name, role, user_address


class HfReferralSliceFilter(SliceFilter):
    driving_alias = "hf_referral"

    def projects(self) -> str:
        """For stg_project FINAL: own primary key (tenant_id, id)."""
        return self.own_key_in("id", self.slice_column("project_id"))

    def project_addresses(self) -> str:
        """For stg_project_address via 14's prj_by_project (tenant_id, project_id)."""
        return self.own_key_in("project_id", self.slice_column("project_id"))


# the three-tier boundary code (= hf_referral_transformation._get_boundary_lookup_key)
LOOKUP_BOUNDARY_CODE_SQL = (
    "multiIf(hf_referral.locality_code != '', hf_referral.locality_code, "
    "additional_fields_boundary_code != '', additional_fields_boundary_code, "
    "project_address.latest_boundary)"
)
ADDITIONAL_FIELDS_BOUNDARY_CODE_SQL = (
    "JSONExtractString(arrayLast(fld -> JSONExtractString(fld, 'key') = 'boundaryCode', "
    "arrayFilter(fld -> JSONHas(fld, 'key'), JSONExtractArrayRaw(hf_referral.additional_details, 'fields'))), 'value')"
)


def _joins(f: HfReferralSliceFilter) -> str:
    return (
        project_join_sql(f.projects(), "hf_referral.project_id", "hf_referral.tenant_id")
        + project_address_join_sql(f.project_addresses(), "project.id", "project.tenant_id")
    )


def joined_rows_sql(f: HfReferralSliceFilter) -> str:
    """One row per HF referral for the slice, aliased `joined` by the caller."""
    return f"""
    SELECT
        hf_referral.id                        AS id,
        hf_referral.client_reference_id       AS client_reference_id,
        hf_referral.tenant_id                 AS tenant_id,
        hf_referral.project_id                AS project_id,
        hf_referral.project_facility_id       AS project_facility_id,
        hf_referral.symptom                   AS symptom,
        hf_referral.symptom_survey_id         AS symptom_survey_id,
        hf_referral.beneficiary_id            AS beneficiary_id,
        hf_referral.referral_code             AS referral_code,
        hf_referral.national_level_id         AS national_level_id,
        hf_referral.created_by                AS created_by,
        hf_referral.created_time              AS created_time,
        hf_referral.last_modified_by          AS last_modified_by,
        hf_referral.last_modified_time        AS last_modified_time,
        hf_referral.client_created_by         AS client_created_by,
        hf_referral.client_created_time       AS client_created_time,
        hf_referral.client_last_modified_by   AS client_last_modified_by,
        hf_referral.client_last_modified_time AS client_last_modified_time,
        hf_referral.row_version               AS row_version,
        hf_referral.is_deleted                AS is_deleted,
        hf_referral.additional_details        AS additional_details,
        project.project_type                  AS project_type,
        project.project_type_id               AS project_type_id,
        project.name                          AS project_name,
        project.reference_id                  AS campaign_number,
        project.additional_details            AS project_additional_details,
        JSONExtractString(project.additional_details, 'hierarchyType') AS hierarchy_type,
        {ADDITIONAL_FIELDS_BOUNDARY_CODE_SQL} AS additional_fields_boundary_code,
        {LOOKUP_BOUNDARY_CODE_SQL}            AS lookup_boundary_code
    FROM {table('stg_hf_referral')} AS hf_referral FINAL{_joins(f)}
    WHERE {f.driving()}"""


def boundary_keys_sql(f: HfReferralSliceFilter) -> str:
    return f"""
SELECT DISTINCT tenant_id, hierarchy_type, boundary_code
FROM
(
    SELECT
        hf_referral.tenant_id                                          AS tenant_id,
        JSONExtractString(project.additional_details, 'hierarchyType') AS hierarchy_type,
        {ADDITIONAL_FIELDS_BOUNDARY_CODE_SQL} AS additional_fields_boundary_code,
        {LOOKUP_BOUNDARY_CODE_SQL}            AS boundary_code
    FROM {table('stg_hf_referral')} AS hf_referral FINAL{_joins(f)}
    WHERE {f.driving()}
)
WHERE hierarchy_type != '' AND boundary_code != ''
"""


def user_keys_sql(f: HfReferralSliceFilter) -> str:
    """(tenant_id, client_created_by), the CLIENT audit's creator."""
    return f"""
SELECT DISTINCT hf_referral.tenant_id AS tenant_id, hf_referral.client_created_by AS user_id
FROM {table('stg_hf_referral')} AS hf_referral FINAL
WHERE {f.driving()} AND hf_referral.client_created_by != ''
"""


def helper_expressions(f: HfReferralSliceFilter) -> list[tuple[str, str]]:
    # cycleIndex matched against the CLIENT audit's created_time (= _format_cycle_index)
    return cycle_index_expressions("joined.project_additional_details", "joined.client_created_time")


# (silver column, SQL expression) in hf_referral_entity's column order.
SILVER_COLUMNS: list[tuple[str, str]] = [
    ("id", "joined.id"),
    ("client_reference_id", "joined.client_reference_id"),
    ("tenant_id", "joined.tenant_id"),
    ("project_id", "joined.project_id"),
    ("project_facility_id", "joined.project_facility_id"),
    ("symptom", "joined.symptom"),
    ("symptom_survey_id", "joined.symptom_survey_id"),
    ("beneficiary_id", "joined.beneficiary_id"),
    ("referral_code", "joined.referral_code"),
    ("national_level_id", "joined.national_level_id"),
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
    ("user_name", "users.user_name"),
    ("role", "users.role"),
    ("user_address", "users.user_address"),
    *[(column, f"levels.{column}") for column in LEVEL_COLUMNS],
    ("hierarchy_type", "if(joined.lookup_boundary_code != '', joined.hierarchy_type, '')"),
    ("task_dates", "toDate32(fromUnixTimestamp64Milli(joined.client_last_modified_time, 'UTC'))"),
    ("synced_date", "toDate32(fromUnixTimestamp64Milli(joined.last_modified_time, 'UTC'))"),
    ("additional_details", "concat('{\"cycleIndex\": ', cycle_index_json, '}')"),
    ("project_type", "joined.project_type"),
    ("project_type_id", "joined.project_type_id"),
    ("project_name", "joined.project_name"),
    ("campaign_number", "joined.campaign_number"),
    ("campaign_id", "''"),
]


SPEC = EntitySpec(
    entity="hf_referral_test",
    python_entity="hf_referral",
    driving_table="stg_hf_referral",
    slice_filter_class=HfReferralSliceFilter,
    target=SilverTarget(
        silver_table="hf_referral_entity",
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
