"""
referral_test_transformation.py

TEST twin of referral_transformation.py for the `referral` entity: the flatten
+ write runs inside ClickHouse as one INSERT ... SELECT per slice (see
sql_flatten_common.py for how a run works). Writes to
<database>.referral_entity_sql_test, never to referral_entity.
Entity name `referral_test` (dag_id referral_test_transformation).

The Python DAG resolves the beneficiary -> project and the individual -> address
chains as per-tenant lookup rounds; here they are slice-bounded joins in the one
statement, each collapsed to one row per key the way the Python DAG's
`ORDER BY id ASC LIMIT 1 BY <key>` does.

Known differences from referral_transformation.py (all deliberate)
    - stg_referral is read with FINAL (the Python DAG reads every version).
    - The beneficiary and individual sides take each row's newest version before
      picking the lowest id per reference (no is_deleted filter, as in the
      Python DAG); the side effect matched by client_reference_id is one row per
      reference (newest), where the Python DAG's FINAL join fans out.
    - stg_project is read with FINAL and stg_project_address as one boundary per
      project (14's prj_by_project).
    - now() is evaluated once per statement; a row that fails to build aborts the
      slice instead of being skipped with a log line.
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
    cycle_index_expressions,
    json_int_or_null_sql,
    json_object_sql,
    json_string_sql,
    json_value_sql,
    one_row_per_key_sql,
    own_key_join_sql,
    project_address_join_sql,
    project_join_sql,
    table,
    user_lookup,
)

# Sources, in the order they appear in the query:
#
#   source                     alias                keyed / joined on                                      feeds
#   -------------------------  -------------------  -----------------------------------------------------  --------------------------------------
#   stg_referral (FINAL)       referral             tenant_id + id range of the slice                      every plain column, additional_fields
#   stg_facility (FINAL)       facility             facility.id = referral.recipient_id AND tenant         facility_name (FACILITY recipients)
#   stg_side_effect (FINAL)    side_effect_by_id    side_effect_by_id.id = referral.side_effect_id         side_effect JSON, preferred
#   stg_side_effect            side_effect_by_ref   client_reference_id =                                  side_effect JSON, fallback
#                                                   referral.side_effect_client_reference_id AND tenant
#                                                   (18's prj_by_client_ref, one row per reference)
#   stg_project_beneficiary    beneficiary          beneficiary.client_reference_id =                      the individual's reference, the
#                                                   referral.project_beneficiary_client_reference_id       beneficiary's project id
#                                                   AND tenant (16's prj_by_client_ref, lowest id)
#   stg_project (FINAL)        project              project.id = beneficiary.latest_project_id AND tenant  project_* columns, campaign_number,
#                                                                                                          hierarchy_type, cycles (cycleIndex on
#                                                                                                          the SERVER created_time)
#   stg_project_address        project_address      project_id = project.id AND tenant (14's prj_by_project) boundary code fallback
#   stg_individual             individual           individual.client_reference_id = beneficiary ref      date_of_birth, age, gender,
#                                                   AND tenant (16's prj_by_client_ref, lowest id)         height / disabilityType, the address key
#   stg_individual_address     individual_address   individual_address.individual_id = individual.latest_id boundary code (first tier), via the
#   + stg_address (FINAL)                           (own PK prefix; oldest non-deleted address row)         address's locality_code
#   boundary-service response  levels               (tenant_id, hierarchy_type, boundary_code)             level_one_code .. level_nine_code
#   user-service response      users                (tenant_id, created_by)                                user_name, name_of_user, role, user_address

DEFAULT_FACILITY_NAME = "APS"


class ReferralSliceFilter(SliceFilter):
    driving_alias = "referral"

    def facilities(self) -> str:
        return self.own_key_in("id", self.slice_column("recipient_id"))

    def side_effects_by_id(self) -> str:
        return self.own_key_in("id", self.slice_column("side_effect_id"))

    def side_effects_by_ref(self) -> str:
        return self.own_key_in("client_reference_id", self.slice_column("side_effect_client_reference_id"))

    def beneficiaries(self) -> str:
        return self.own_key_in("client_reference_id", self.slice_column("project_beneficiary_client_reference_id"))

    def beneficiary_project_ids(self) -> str:
        return (f"(SELECT DISTINCT project_id FROM {table('stg_project_beneficiary')} "
                f"WHERE {self.beneficiaries()} AND project_id != '')")

    def projects(self) -> str:
        return self.own_key_in("id", self.beneficiary_project_ids())

    def project_addresses(self) -> str:
        return self.own_key_in("project_id", self.beneficiary_project_ids())

    def individual_refs(self) -> str:
        return (f"(SELECT DISTINCT beneficiary_client_reference_id FROM {table('stg_project_beneficiary')} "
                f"WHERE {self.beneficiaries()} AND beneficiary_client_reference_id != '')")

    def individuals(self) -> str:
        return self.own_key_in("client_reference_id", self.individual_refs())

    def individual_ids(self) -> str:
        return f"(SELECT DISTINCT id FROM {table('stg_individual')} WHERE {self.individuals()})"

    def individual_addresses(self) -> str:
        return f"individual_id IN {self.individual_ids()}"

    def addresses(self) -> str:
        return self.own_key_in(
            "id", f"(SELECT DISTINCT address_id FROM {table('stg_individual_address')} WHERE {self.individual_addresses()})")


def side_effect_by_ref_join_sql(f: ReferralSliceFilter) -> str:
    inner = one_row_per_key_sql(
        "stg_side_effect", f.side_effects_by_ref(), "client_reference_id",
        ["id", "task_id", "symptoms", "additional_details"], has_is_deleted=False,
    )
    return f"""
    LEFT JOIN
    ({inner}
    ) AS side_effect_by_ref
        ON side_effect_by_ref.client_reference_id = referral.side_effect_client_reference_id
       AND side_effect_by_ref.tenant_id = referral.tenant_id"""


def beneficiary_join_sql(f: ReferralSliceFilter) -> str:
    inner = one_row_per_key_sql(
        "stg_project_beneficiary", f.beneficiaries(), "client_reference_id",
        ["beneficiary_client_reference_id", "project_id"], has_is_deleted=False, pick="lowest_id",
    )
    return f"""
    LEFT JOIN
    ({inner}
    ) AS beneficiary
        ON beneficiary.client_reference_id = referral.project_beneficiary_client_reference_id
       AND beneficiary.tenant_id = referral.tenant_id"""


def individual_join_sql(f: ReferralSliceFilter) -> str:
    inner = one_row_per_key_sql(
        "stg_individual", f.individuals(), "client_reference_id",
        ["id", "date_of_birth", "gender", "additional_details"], has_is_deleted=False, pick="lowest_id",
    )
    return f"""
    LEFT JOIN
    ({inner}
    ) AS individual
        ON individual.client_reference_id = beneficiary.latest_beneficiary_client_reference_id
       AND individual.tenant_id = referral.tenant_id"""


def individual_address_join_sql(f: ReferralSliceFilter) -> str:
    """LEFT JOIN the individual's OLDEST non-deleted address (= ORDER BY created_time
    LIMIT 1 BY individual_id) and that address's locality_code."""
    return f"""
    LEFT JOIN
    (
        SELECT
            individual_address.individual_id AS individual_id,
            address.locality_code            AS locality_code
        FROM
        (
            SELECT individual_id, argMin(latest_address_id, created_time) AS address_id
            FROM
            (
                SELECT
                    individual_id,
                    address_id,
                    argMax(address_id, last_modified_time)  AS latest_address_id,
                    argMax(is_deleted, last_modified_time)  AS latest_is_deleted,
                    min(created_time)                       AS created_time
                FROM {table('stg_individual_address')}
                WHERE {f.individual_addresses()}
                GROUP BY individual_id, address_id
                HAVING latest_is_deleted = false
            )
            GROUP BY individual_id
        ) AS individual_address
        LEFT JOIN
        (
            SELECT id, locality_code
            FROM {table('stg_address')} FINAL
            WHERE {f.addresses()}
        ) AS address
            ON address.id = individual_address.address_id
    ) AS individual_address
        ON individual_address.individual_id = individual.latest_id"""


BOUNDARY_CODE_SQL = "if(individual_address.locality_code != '', individual_address.locality_code, project_address.latest_boundary)"


def _joins(f: ReferralSliceFilter) -> str:
    return (
        own_key_join_sql("stg_facility", f.facilities(), "facility", "id, tenant_id, name",
                         [("id", "referral.recipient_id"), ("tenant_id", "referral.tenant_id")])
        + own_key_join_sql("stg_side_effect", f.side_effects_by_id(), "side_effect_by_id",
                           "id, tenant_id, task_id, symptoms, additional_details",
                           [("id", "referral.side_effect_id"), ("tenant_id", "referral.tenant_id")])
        + side_effect_by_ref_join_sql(f)
        + beneficiary_join_sql(f)
        + project_join_sql(f.projects(), "beneficiary.latest_project_id", "referral.tenant_id")
        + project_address_join_sql(f.project_addresses(), "project.id", "referral.tenant_id")
        + individual_join_sql(f)
        + individual_address_join_sql(f)
    )


def joined_rows_sql(f: ReferralSliceFilter) -> str:
    """One row per referral for the slice, aliased `joined` by the caller."""
    return f"""
    SELECT
        referral.id                                       AS id,
        referral.client_reference_id                      AS client_reference_id,
        referral.tenant_id                                AS tenant_id,
        referral.project_beneficiary_id                   AS project_beneficiary_id,
        referral.project_beneficiary_client_reference_id  AS project_beneficiary_client_reference_id,
        referral.referrer_id                              AS referrer_id,
        referral.recipient_id                             AS recipient_id,
        referral.recipient_type                           AS recipient_type,
        referral.reasons                                  AS reasons,
        referral.created_by                               AS created_by,
        referral.created_time                             AS created_time,
        referral.last_modified_by                         AS last_modified_by,
        referral.last_modified_time                       AS last_modified_time,
        referral.client_created_by                        AS client_created_by,
        referral.client_created_time                      AS client_created_time,
        referral.client_last_modified_by                  AS client_last_modified_by,
        referral.client_last_modified_time                AS client_last_modified_time,
        referral.row_version                              AS row_version,
        referral.is_deleted                               AS is_deleted,
        referral.additional_details                       AS additional_details,
        referral.referral_code                            AS referral_code,
        facility.name                                     AS facility_name,
        side_effect_by_id.id                              AS se_by_id_id,
        side_effect_by_id.task_id                         AS se_by_id_task_id,
        side_effect_by_id.symptoms                        AS se_by_id_symptoms,
        side_effect_by_id.additional_details              AS se_by_id_additional_details,
        side_effect_by_ref.latest_id                      AS se_by_ref_id,
        side_effect_by_ref.latest_task_id                 AS se_by_ref_task_id,
        side_effect_by_ref.latest_symptoms                AS se_by_ref_symptoms,
        side_effect_by_ref.latest_additional_details      AS se_by_ref_additional_details,
        beneficiary.latest_beneficiary_client_reference_id AS beneficiary_client_reference_id,
        project.id                                        AS project_id,
        project.project_type                              AS project_type,
        project.project_type_id                           AS project_type_id,
        project.name                                      AS project_name,
        project.reference_id                              AS campaign_number,
        project.additional_details                        AS project_additional_details,
        JSONExtractString(project.additional_details, 'hierarchyType') AS hierarchy_type,
        {BOUNDARY_CODE_SQL}                               AS boundary_code,
        individual.client_reference_id != ''              AS individual_matched,
        individual.latest_date_of_birth                   AS individual_date_of_birth,
        individual.latest_gender                          AS individual_gender,
        individual.latest_additional_details              AS individual_additional_details
    FROM {table('stg_referral')} AS referral FINAL{_joins(f)}
    WHERE {f.driving()}"""


def boundary_keys_sql(f: ReferralSliceFilter) -> str:
    return f"""
SELECT DISTINCT tenant_id, hierarchy_type, boundary_code
FROM
(
    SELECT
        referral.tenant_id                                             AS tenant_id,
        JSONExtractString(project.additional_details, 'hierarchyType') AS hierarchy_type,
        {BOUNDARY_CODE_SQL}                                            AS boundary_code
    FROM {table('stg_referral')} AS referral FINAL{beneficiary_join_sql(f)}{project_join_sql(f.projects(), 'beneficiary.latest_project_id', 'referral.tenant_id')}{project_address_join_sql(f.project_addresses(), 'project.id', 'referral.tenant_id')}{individual_join_sql(f)}{individual_address_join_sql(f)}
    WHERE {f.driving()}
)
WHERE hierarchy_type != '' AND boundary_code != ''
"""


def user_keys_sql(f: ReferralSliceFilter) -> str:
    return f"""
SELECT DISTINCT referral.tenant_id AS tenant_id, referral.created_by AS user_id
FROM {table('stg_referral')} AS referral FINAL
WHERE {f.driving()} AND referral.created_by != ''
"""


def helper_expressions(f: ReferralSliceFilter) -> list[tuple[str, str]]:
    def side_effect_json(prefix: str) -> str:
        return (
            f"concat('{{\"id\": ', {json_string_sql(f'joined.{prefix}_id')}, "
            f"', \"taskId\": ', {json_string_sql(f'joined.{prefix}_task_id')}, "
            f"', \"symptoms\": ', {json_string_sql(f'joined.{prefix}_symptoms')}, "
            f"', \"additionalDetails\": ', {json_string_sql(f'joined.{prefix}_additional_details')}, '}}')"
        )
    return [
        # side_effect (= _build_side_effect_json): the id-matched row, else the reference-matched one, else ''
        ("side_effect_json",
         f"multiIf(joined.se_by_id_id != '', {side_effect_json('se_by_id')}, "
         f"joined.se_by_ref_id != '', {side_effect_json('se_by_ref')}, '')"),
        # facility_name: the facility's name for FACILITY recipients, else the default
        ("facility_name",
         f"if(upper(joined.recipient_type) = 'FACILITY' AND joined.facility_name != '', joined.facility_name, "
         f"'{DEFAULT_FACILITY_NAME}')"),
        # cycleIndex on the SERVER created_time (= _format_cycle_index)
        *cycle_index_expressions("joined.project_additional_details", "joined.created_time"),
        # height (int) + disabilityType, only together and only if height parses (= _get_individual_extra_fields)
        ("individual_fields",
         "arrayFilter(fld -> JSONHas(fld, 'key'), JSONExtractArrayRaw(joined.individual_additional_details, 'fields'))"),
        ("height_field", "arrayLast(fld -> JSONExtractString(fld, 'key') = 'height', individual_fields)"),
        ("disability_field", "arrayLast(fld -> JSONExtractString(fld, 'key') = 'disabilityType', individual_fields)"),
        ("height_json", json_int_or_null_sql("height_field")),
        ("individual_extra_pairs",
         f"if(height_field != '' AND disability_field != '' AND height_json != 'null', "
         f"[('height', height_json), ('disabilityType', {json_value_sql('disability_field')})], [])"),
        ("additional_details_json",
         json_object_sql("arrayConcat([('cycleIndex', cycle_index_json)], individual_extra_pairs)")),
        *age_expressions("joined.individual_date_of_birth"),
    ]


# (silver column, SQL expression) in referral_entity's column order.
SILVER_COLUMNS: list[tuple[str, str]] = [
    ("id", "joined.id"),
    ("client_reference_id", "joined.client_reference_id"),
    ("project_beneficiary_id", "joined.project_beneficiary_id"),
    ("project_beneficiary_client_reference_id", "joined.project_beneficiary_client_reference_id"),
    ("referrer_id", "joined.referrer_id"),
    ("recipient_type", "joined.recipient_type"),
    ("recipient_id", "joined.recipient_id"),
    ("reasons", "joined.reasons"),
    ("side_effect", "side_effect_json"),
    ("referral_code", "joined.referral_code"),
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
    ("user_name", "users.user_name"),
    ("name_of_user", "users.name_of_user"),
    ("role", "users.role"),
    ("user_address", "users.user_address"),
    ("age", "if(joined.individual_matched, toInt32(age_months), toInt32(0))"),
    *[(column, f"levels.{column}") for column in LEVEL_COLUMNS],
    ("hierarchy_type", "if(joined.boundary_code != '', joined.hierarchy_type, '')"),
    ("facility_name", "facility_name"),
    ("individual_id", "joined.beneficiary_client_reference_id"),
    ("gender", "if(joined.individual_matched, joined.individual_gender, '')"),
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
    entity="referral_test",
    python_entity="referral",
    driving_table="stg_referral",
    slice_filter_class=ReferralSliceFilter,
    target=SilverTarget(
        silver_table="referral_entity",
        joined_rows_sql=joined_rows_sql,
        helper_expressions=helper_expressions,
        silver_columns=SILVER_COLUMNS,
        lookups=(
            boundary_lookup(boundary_keys_sql, code_expr="joined.boundary_code"),
            user_lookup(user_keys_sql, user_expr="joined.created_by"),
        ),
    ),
)

# The Airflow DAG object for this entity, built from SPEC by sql_flatten_common
# (airflow safe-mode discovery needs the words "airflow" and "dag" in this file).
dag = build_sql_test_dag(SPEC)
