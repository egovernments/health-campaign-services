"""
pgr_test_transformation.py

TEST twin of pgr_transformation.py for the `pgr` entity (PGR complaints): the
flatten + write runs inside ClickHouse as one INSERT ... SELECT per slice
(see sql_flatten_common.py for how a run works). Writes to
<database>.pgr_complaints_entity_sql_test, never to pgr_complaints_entity.
Entity name `pgr_test` (dag_id pgr_test_transformation).

Known differences from pgr_transformation.py (all deliberate)
    - stg_pgr_service is read with FINAL (the Python DAG reads every version).
      Its sort key is (tenant_id, service_request_id), so the slice's `id`
      range does not prune below the tenant -- the same is true of the Python
      DAG's `id IN (...)` read; the table is small.
    - stg_pgr_address is collapsed to ONE address per service (newest version,
      ties by id); the Python DAG's FINAL join fans out on several addresses.
    - The user -> project bridge takes the newest version of each staff row and
      drops it if deleted, then the lowest staff row id per user (see
      sql_flatten_common.staff_bridge_join_sql).
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
    json_string_sql,
    one_row_per_key_sql,
    project_join_sql,
    staff_bridge_join_sql,
    staff_projects_subquery_sql,
    table,
    user_lookup,
)

# Sources, in the order they appear in the query:
#
#   source                     alias     keyed / joined on                                        feeds
#   -------------------------  --------  -------------------------------------------------------  ----------------------------------------------
#   stg_pgr_service (FINAL)    service   tenant_id + id range of the slice                        every plain column, hierarchy_type (a bronze
#                                                                                                 column here, no project needed)
#   stg_pgr_address            address   address.parent_id = service.id AND tenant                address_* columns, boundary_code
#                                        (18's prj_by_parent, one address per service)
#   stg_project_staff          staff     staff.staff_id = service.last_modified_by AND tenant     the project id of the user's lowest-id staff row
#   stg_project (FINAL)        project   project.id = staff.first_project_id AND tenant           project_* columns, campaign_number
#   boundary-service response  levels    (tenant_id, service.hierarchy_type, address.locality)    level_one_code .. level_nine_code
#   user-service response      users     (tenant_id, created_by)                                  user_name, name_of_user, role, user_address


class PgrSliceFilter(SliceFilter):
    driving_alias = "service"

    def addresses(self) -> str:
        """For stg_pgr_address via prj_by_parent (tenant_id, parent_id)."""
        return f"tenant_id = {self.tenant} AND {self.id_range('parent_id')} AND parent_id IN {self.in_window_ids}"

    def staff(self) -> str:
        return self.own_key_in("staff_id", self.slice_column("last_modified_by"))

    def projects(self) -> str:
        return self.own_key_in("id", staff_projects_subquery_sql(self.staff()))


def address_join_sql(f: PgrSliceFilter) -> str:
    inner = one_row_per_key_sql(
        "stg_pgr_address", f.addresses(), "parent_id",
        ["id", "locality", "latitude", "longitude", "additional_details"], has_is_deleted=False,
    )
    return f"""
    LEFT JOIN
    ({inner}
    ) AS address
        ON address.parent_id = service.id AND address.tenant_id = service.tenant_id"""


def _joins(f: PgrSliceFilter) -> str:
    return (
        address_join_sql(f)
        + staff_bridge_join_sql(f.staff(), "service.last_modified_by", "service.tenant_id")
        + project_join_sql(f.projects(), "staff.first_project_id", "service.tenant_id")
    )


def joined_rows_sql(f: PgrSliceFilter) -> str:
    """One row per PGR service (complaint) for the slice, aliased `joined` by the caller."""
    return f"""
    SELECT
        service.id                          AS id,
        service.tenant_id                   AS tenant_id,
        service.service_code                AS service_code,
        service.service_request_id          AS service_request_id,
        service.description                 AS description,
        service.account_id                  AS account_id,
        service.additional_details          AS additional_details,
        service.application_status          AS application_status,
        service.rating                      AS rating,
        service.source                      AS source,
        service.created_by                  AS created_by,
        service.created_time                AS created_time,
        service.last_modified_by            AS last_modified_by,
        service.last_modified_time          AS last_modified_time,
        service.active                      AS active,
        service.self_complaint              AS self_complaint,
        service.hierarchy_type              AS hierarchy_type,
        address.latest_id                   AS address_id,
        address.latest_locality             AS address_locality_code,
        address.latest_latitude             AS address_latitude,
        address.latest_longitude            AS address_longitude,
        address.latest_additional_details   AS address_additional_details,
        project.id                          AS project_id,
        project.project_type                AS project_type,
        project.project_type_id             AS project_type_id,
        project.name                        AS project_name,
        project.reference_id                AS campaign_number
    FROM {table('stg_pgr_service')} AS service FINAL{_joins(f)}
    WHERE {f.driving()}"""


def boundary_keys_sql(f: PgrSliceFilter) -> str:
    """(tenant, the service's own hierarchy_type column, the address locality code)."""
    return f"""
SELECT DISTINCT tenant_id, hierarchy_type, boundary_code
FROM
(
    SELECT
        service.tenant_id        AS tenant_id,
        service.hierarchy_type   AS hierarchy_type,
        address.latest_locality  AS boundary_code
    FROM {table('stg_pgr_service')} AS service FINAL{address_join_sql(f)}
    WHERE {f.driving()}
)
WHERE hierarchy_type != '' AND boundary_code != ''
"""


def user_keys_sql(f: PgrSliceFilter) -> str:
    """(tenant_id, created_by) for display; the project bridge uses last_modified_by."""
    return f"""
SELECT DISTINCT service.tenant_id AS tenant_id, service.created_by AS user_id
FROM {table('stg_pgr_service')} AS service FINAL
WHERE {f.driving()} AND service.created_by != ''
"""


def helper_expressions(f: PgrSliceFilter) -> list[tuple[str, str]]:
    return []


# (silver column, SQL expression) in pgr_complaints_entity's column order.
SILVER_COLUMNS: list[tuple[str, str]] = [
    ("id", "joined.id"),
    ("tenant_id", "joined.tenant_id"),
    ("service_code", "joined.service_code"),
    ("service_request_id", "joined.service_request_id"),
    ("description", "joined.description"),
    ("account_id", "joined.account_id"),
    ("rating", "toInt32(joined.rating)"),
    ("application_status", "joined.application_status"),
    ("source", "joined.source"),
    ("active", "joined.active"),
    ("self_complaint", "joined.self_complaint"),
    ("service_additional_detail", "joined.additional_details"),
    # complainant_* has no bronze source (Service.user is not modelled) -- defaults, as in the Python DAG
    ("complainant_id", "toInt64(0)"),
    ("complainant_user_name", "''"),
    ("complainant_name", "''"),
    ("complainant_type", "''"),
    ("complainant_mobile_number", "''"),
    ("complainant_email_id", "''"),
    ("complainant_tenant_id", "''"),
    ("complainant_uuid", "''"),
    ("complainant_active", "false"),
    ("complainant_roles", "''"),
    ("address_id", "joined.address_id"),
    ("address_locality",
     f"if(joined.address_locality_code != '', concat('{{\"code\": ', {json_string_sql('joined.address_locality_code')}, '}}'), '')"),
    ("address_addition_details", "joined.address_additional_details"),
    ("address_geo_lat", "joined.address_latitude"),
    ("address_geo_lon", "joined.address_longitude"),
    ("address_geo_additional_details", "''"),
    ("created_by", "joined.created_by"),
    ("last_modified_by", "joined.last_modified_by"),
    ("created_time", "joined.created_time"),
    ("last_modified_time", "joined.last_modified_time"),
    ("user_name", "users.user_name"),
    ("name_of_user", "users.name_of_user"),
    ("role", "users.role"),
    ("user_address", "users.user_address"),
    *[(column, f"levels.{column}") for column in LEVEL_COLUMNS],
    ("hierarchy_type", "if(joined.address_locality_code != '', joined.hierarchy_type, '')"),
    ("task_dates", "toDate32(fromUnixTimestamp64Milli(joined.last_modified_time, 'UTC'))"),
    ("boundary_code", "joined.address_locality_code"),
    ("additional_details", "''"),  # PGRIndex has no additionalDetails field in Java
    ("project_id", "joined.project_id"),
    ("project_type", "joined.project_type"),
    ("project_type_id", "joined.project_type_id"),
    ("project_name", "joined.project_name"),
    ("campaign_number", "joined.campaign_number"),
    ("campaign_id", "''"),
]


SPEC = EntitySpec(
    entity="pgr_test",
    python_entity="pgr",
    driving_table="stg_pgr_service",
    slice_filter_class=PgrSliceFilter,
    target=SilverTarget(
        silver_table="pgr_complaints_entity",
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
