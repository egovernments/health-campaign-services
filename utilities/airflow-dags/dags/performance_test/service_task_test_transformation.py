"""
service_task_test_transformation.py

TEST twin of service_task_transformation.py for the `service_task` entity
(checklist services): the flatten + write runs inside ClickHouse as one
INSERT ... SELECT per slice per target table (see sql_flatten_common.py for
how a run works). Writes to <database>.service_task_entity_sql_test AND
<database>.service_task_attribute_entity_sql_test, never to the real tables.
Entity name `service_task_test` (dag_id service_task_test_transformation).

Known differences from service_task_transformation.py (all deliberate)
    - stg_service is read with FINAL (the Python DAG reads every version).
    - The project resolved BY NAME (for services without an account_id) is the
      lowest project id per name through 18's prj_by_name, as the Python DAG's
      `LIMIT 1 BY name`; stg_service_definition is read with FINAL (own PK).
    - Attribute rows are one per stg_service_attribute_value id (newest
      version); the Python DAG writes every bronze row it reads. A nested
      object/array inside an attribute value keeps its source text (the Python
      DAG re-serialises it; only whitespace can differ).
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
    json_object_sql,
    one_row_per_key_sql,
    own_key_join_sql,
    parse_boundary_code_sql,
    project_join_sql,
    python_float_text_sql,
    table,
    user_lookup,
)

# Sources, in the order they appear in the query:
#
#   source                        alias            keyed / joined on                                    feeds
#   ----------------------------  ---------------  ---------------------------------------------------  ------------------------------------------
#   stg_service (FINAL)           service          tenant_id + id range of the slice                    every plain column, latitude/longitude and
#                                                                                                       the boundary code from its additional_details
#   stg_project (FINAL)           project          project.id = service.account_id AND tenant (own PK)  project_* columns, hierarchy_type, cycles
#                                                                                                       -- the primary tier
#   stg_service_definition(FINAL) definition       definition.id = service.service_def_id AND tenant    code "<projectName>.<checklist>.<level>"
#   stg_project                   project_by_name  project_by_name.name = the code's project name AND   the same project columns, used ONLY when
#                                                  tenant (18's prj_by_name, lowest id per name)         service.account_id is empty
#   boundary-service response     levels           (tenant_id, hierarchy_type, boundary_code)           level_one_code .. level_nine_code
#   user-service response         users            (tenant_id, created_by)                              user_name, name_of_user, role, user_address
#
# Second target, service_task_attribute_entity_sql_test:
#   stg_service_attribute_value   attribute        attribute.reference_id IN the slice's service ids    every column; `value` unwrapped from its
#                                                  (18's prj_by_reference; no tenant_id column)          {"value": ...} wrapper


class ServiceTaskSliceFilter(SliceFilter):
    driving_alias = "service"

    def projects(self) -> str:
        return self.own_key_in("id", self.slice_column("account_id"))

    def definitions(self) -> str:
        return self.own_key_in("id", self.slice_column("service_def_id"))

    def projects_by_name(self) -> str:
        """The project names parsed from the slice's definition codes (raw superset)."""
        return self.own_key_in(
            "name",
            f"(SELECT DISTINCT splitByChar('.', code)[1] AS name FROM {table('stg_service_definition')} "
            f"WHERE {self.definitions()} AND length(splitByChar('.', code)) >= 3 AND name != '')")

    def attributes(self) -> str:
        return f"{self.id_range('reference_id')} AND reference_id IN {self.in_window_ids}"


def project_by_name_join_sql(f: ServiceTaskSliceFilter) -> str:
    inner = one_row_per_key_sql(
        "stg_project", f.projects_by_name(), "name",
        ["id", "project_type", "project_type_id", "reference_id", "additional_details"],
        has_is_deleted=False, pick="lowest_id",
    )
    return f"""
    LEFT JOIN
    ({inner}
    ) AS project_by_name
        ON project_by_name.name = definition_project_name AND project_by_name.tenant_id = service.tenant_id"""


# "<projectName>.<checklistName>.<supervisorLevel>"; fewer than 3 parts -> nothing (= _parse_service_definition_code)
CODE_PARTS_SQL = "if(length(splitByChar('.', definition.code)) >= 3, splitByChar('.', definition.code), [])"


def _joins(f: ServiceTaskSliceFilter) -> str:
    return (
        project_join_sql(f.projects(), "service.account_id", "service.tenant_id")
        + own_key_join_sql("stg_service_definition", f.definitions(), "definition", "id, tenant_id, code",
                           [("id", "service.service_def_id"), ("tenant_id", "service.tenant_id")])
        + project_by_name_join_sql(f)
    )


def joined_rows_sql(f: ServiceTaskSliceFilter) -> str:
    """One row per service for the slice, aliased `joined` by the caller. The
    project columns come from the account_id project, else from the project
    matched by the definition code's project name."""
    def project_column(column: str) -> str:
        return f"if(service.account_id != '', project.{column}, project_by_name.latest_{column})"
    return f"""
    SELECT
        service.id                    AS id,
        service.tenant_id             AS tenant_id,
        service.service_def_id        AS service_def_id,
        service.account_id            AS account_id,
        service.client_id             AS client_id,
        service.additional_details    AS additional_details,
        service.created_by            AS created_by,
        service.last_modified_by      AS last_modified_by,
        service.created_time          AS created_time,
        service.last_modified_time    AS last_modified_time,
        {CODE_PARTS_SQL}              AS code_parts,
        {CODE_PARTS_SQL}[1]           AS definition_project_name,
        if(service.account_id != '', service.account_id, project_by_name.latest_id) AS project_id,
        {project_column('project_type')}        AS project_type,
        {project_column('project_type_id')}     AS project_type_id,
        if(service.account_id != '', project.name, project_by_name.name) AS project_name,
        {project_column('reference_id')}        AS campaign_number,
        {project_column('additional_details')}  AS project_additional_details,
        JSONExtractString({project_column('additional_details')}, 'hierarchyType') AS hierarchy_type,
        {parse_boundary_code_sql('service.additional_details')} AS boundary_code
    FROM {table('stg_service')} AS service FINAL{_joins(f)}
    WHERE {f.driving()}"""


def boundary_keys_sql(f: ServiceTaskSliceFilter) -> str:
    return f"""
SELECT DISTINCT tenant_id, hierarchy_type, boundary_code
FROM
({joined_rows_sql(f)}
)
WHERE hierarchy_type != '' AND boundary_code != ''
"""


def user_keys_sql(f: ServiceTaskSliceFilter) -> str:
    return f"""
SELECT DISTINCT service.tenant_id AS tenant_id, service.created_by AS user_id
FROM {table('stg_service')} AS service FINAL
WHERE {f.driving()} AND service.created_by != ''
"""


def helper_expressions(f: ServiceTaskSliceFilter) -> list[tuple[str, str]]:
    return [
        # geo point (= _get_geo_point): a flat object with lat AND lng, both parsing as floats, else 0.0 / 0.0
        ("geo_ok",
         "JSONType(joined.additional_details) = 'Object' AND JSONHas(joined.additional_details, 'lat') "
         "AND JSONHas(joined.additional_details, 'lng') "
         "AND toFloat64OrNull(JSONExtractString(joined.additional_details, 'lat')) IS NOT NULL "
         "AND toFloat64OrNull(JSONExtractString(joined.additional_details, 'lng')) IS NOT NULL"),
        ("geo_lat", "if(geo_ok, toFloat64OrZero(JSONExtractString(joined.additional_details, 'lat')), 0.0)"),
        ("geo_lng", "if(geo_ok, toFloat64OrZero(JSONExtractString(joined.additional_details, 'lng')), 0.0)"),
        # cycleIndex on the SERVER created_time (= _format_cycle_index)
        *cycle_index_expressions("joined.project_additional_details", "joined.created_time"),
        # (= _build_derived_additional_details) the service's own object (if it is one) plus cycleIndex
        ("own_pairs",
         "if(JSONType(joined.additional_details) = 'Object', JSONExtractKeysAndValuesRaw(joined.additional_details), [])"),
        ("additional_details_json", json_object_sql("arrayConcat(own_pairs, [('cycleIndex', cycle_index_json)])")),
    ]


# (silver column, SQL expression) in service_task_entity's column order.
SILVER_COLUMNS: list[tuple[str, str]] = [
    ("id", "joined.id"),
    ("created_time", "joined.created_time"),
    ("created_by", "joined.created_by"),
    ("supervisor_level", "joined.code_parts[3]"),
    ("checklist_name", "joined.code_parts[2]"),
    ("service_definition_id", "joined.service_def_id"),
    ("user_name", "users.user_name"),
    ("name_of_user", "users.name_of_user"),
    ("role", "users.role"),
    ("user_address", "users.user_address"),
    *[(column, f"levels.{column}") for column in LEVEL_COLUMNS],
    ("hierarchy_type", "if(joined.boundary_code != '', joined.hierarchy_type, '')"),
    ("tenant_id", "joined.tenant_id"),
    ("user_id", "joined.account_id"),
    ("client_reference_id", "joined.client_id"),
    # synced_time_stamp from created_time, the other two from last_modified_time (as the Python DAG does)
    ("synced_time_stamp", "fromUnixTimestamp64Milli(joined.created_time, 'UTC')"),
    ("synced_time", "joined.last_modified_time"),
    ("task_dates", "toDate32(fromUnixTimestamp64Milli(joined.last_modified_time, 'UTC'))"),
    ("additional_details", "additional_details_json"),
    ("latitude", "geo_lat"),
    ("longitude", "geo_lng"),
    ("project_id", "joined.project_id"),
    ("project_type", "joined.project_type"),
    ("project_type_id", "joined.project_type_id"),
    ("project_name", "joined.project_name"),
    ("campaign_number", "joined.campaign_number"),
    ("campaign_id", "''"),
]


# --- second target: the service's attribute values ---------------------------------

def attribute_rows_sql(f: ServiceTaskSliceFilter) -> str:
    """One row per attribute value of the slice's services (newest version per id)."""
    columns = ["reference_id", "attribute_code", "value", "created_by", "last_modified_by", "created_time",
               "last_modified_time", "additional_details", "client_reference_id", "service_client_reference_id"]
    latest = ",\n        ".join(f"argMax({c}, last_modified_time) AS latest_{c}" for c in columns)
    return f"""
    SELECT
        id,
        {latest}
    FROM {table('stg_service_attribute_value')}
    WHERE {f.attributes()}
    GROUP BY id"""


def attribute_helper_expressions(f: ServiceTaskSliceFilter) -> list[tuple[str, str]]:
    # (= _extract_attribute_value) unwrap {"value": X}: a string stays, anything else keeps its JSON text;
    # not valid JSON -> the raw text; '' -> ''
    return [
        ("value_text",
         "multiIf(joined.latest_value = '', '', "
         "NOT isValidJSON(joined.latest_value), joined.latest_value, "
         "JSONType(joined.latest_value) = 'Object' AND JSONHas(joined.latest_value, 'value'), "
         "if(JSONType(joined.latest_value, 'value') = 'String', JSONExtractString(joined.latest_value, 'value'), JSONExtractRaw(joined.latest_value, 'value')), "
         "JSONType(joined.latest_value) = 'String', JSONExtractString(joined.latest_value), "
         "joined.latest_value)"),
    ]


ATTRIBUTE_SILVER_COLUMNS: list[tuple[str, str]] = [
    ("id", "joined.id"),
    ("reference_id", "joined.latest_reference_id"),
    ("attribute_code", "joined.latest_attribute_code"),
    ("value", "value_text"),
    ("created_by", "joined.latest_created_by"),
    ("last_modified_by", "joined.latest_last_modified_by"),
    ("created_time", "joined.latest_created_time"),
    ("last_modified_time", "joined.latest_last_modified_time"),
    ("additional_details", "joined.latest_additional_details"),
    ("client_reference_id", "joined.latest_client_reference_id"),
    ("service_client_reference_id", "joined.latest_service_client_reference_id"),
]


SPEC = EntitySpec(
    entity="service_task_test",
    python_entity="service_task",
    driving_table="stg_service",
    slice_filter_class=ServiceTaskSliceFilter,
    target=SilverTarget(
        silver_table="service_task_entity",
        joined_rows_sql=joined_rows_sql,
        helper_expressions=helper_expressions,
        silver_columns=SILVER_COLUMNS,
        lookups=(
            boundary_lookup(boundary_keys_sql, code_expr="joined.boundary_code"),
            user_lookup(user_keys_sql, user_expr="joined.created_by"),
        ),
    ),
    extra_targets=(
        SilverTarget(
            silver_table="service_task_attribute_entity",
            joined_rows_sql=attribute_rows_sql,
            helper_expressions=attribute_helper_expressions,
            silver_columns=ATTRIBUTE_SILVER_COLUMNS,
        ),
    ),
)

# The Airflow DAG object for this entity, built from SPEC by sql_flatten_common
# (airflow safe-mode discovery needs the words "airflow" and "dag" in this file).
dag = build_sql_test_dag(SPEC)
