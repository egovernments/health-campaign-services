"""
bill_test_transformation.py

TEST twin of bill_transformation.py for the `bill` entity (expense bills): the
flatten + write runs inside ClickHouse as one INSERT ... SELECT per slice
(see sql_flatten_common.py for how a run works). Writes to
<database>.bill_entity_sql_test, never to bill_entity.
Entity name `bill_test` (dag_id bill_test_transformation).

Known differences from bill_transformation.py (all deliberate)
    - stg_expense_bill is read with FINAL (the Python DAG reads every version).
    - The payer is the LOWEST-id party of the bill after taking each party row's
      newest version (the Python DAG's `ORDER BY party.id LIMIT 1 BY b.id` over
      a FINAL join has the same intent).
    - bill_details lists each linked bill detail id once, sorted; the Python
      DAG lists the raw rows of stg_expense_billdetail in query order (a
      re-ingested detail would appear twice there).
    - The user -> project bridge takes the newest version of each staff row and
      drops it if deleted, then the lowest staff row id per user.
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
    json_string_array_sql,
    json_string_sql,
    one_row_per_key_sql,
    project_address_join_sql,
    project_join_sql,
    staff_bridge_join_sql,
    staff_projects_subquery_sql,
    table,
    user_lookup,
    workflow_lookup,
)

# Sources, in the order they appear in the query:
#
#   source                     alias            keyed / joined on                                     feeds
#   -------------------------  ---------------  ----------------------------------------------------  -----------------------------------------
#   stg_expense_bill (FINAL)   bill             tenant_id + id range of the slice                     every plain column, boundary_code
#                                                                                                     (= locality_code), additional_details
#   stg_expense_party          party            party.parent_id = bill.id AND tenant                  payer (JSON of the lowest-id party)
#                                               (18's prj_by_parent, one party per bill)
#   stg_expense_billdetail     details          details.bill_id = bill.id AND tenant                  bill_details (JSON array of detail ids)
#                                               (18's prj_by_bill, grouped per bill)
#   stg_project_staff          staff            staff.staff_id = bill.last_modified_by AND tenant     the project id of the user's lowest-id
#                                               (17's prj_by_staff)                                   non-deleted staff row
#   stg_project (FINAL)        project          project.id = staff.first_project_id AND tenant        project_* columns, campaign_number,
#                                                                                                     hierarchy_type
#   boundary-service response  levels           (tenant_id, hierarchy_type, bill.locality_code)       level_one_code .. level_nine_code
#                                               -- the bill's own code, no project fallback
#   user-service response      users            (tenant_id, last_modified_by)                         user_name, name_of_user, role
#   workflow-service response  workflow         (tenant_id, bill_number)                              wf_status, process_instance, wf_status_info

PARTY_COLUMNS = ["type", "identifier", "payment_provider", "payee_name", "payee_phone_number",
                 "bank_account", "bank_code", "beneficiary_code", "status"]
# json.dumps key order of _build_payer_json
PARTY_JSON_KEYS = [("id", "id"), ("type", "type"), ("identifier", "identifier"), ("paymentProvider", "payment_provider"),
                   ("payeeName", "payee_name"), ("payeePhoneNumber", "payee_phone_number"), ("bankAccount", "bank_account"),
                   ("bankCode", "bank_code"), ("beneficiaryCode", "beneficiary_code"), ("status", "status")]


class BillSliceFilter(SliceFilter):
    driving_alias = "bill"

    def parties(self) -> str:
        return f"tenant_id = {self.tenant} AND {self.id_range('parent_id')} AND parent_id IN {self.in_window_ids}"

    def details(self) -> str:
        return f"tenant_id = {self.tenant} AND {self.id_range('bill_id')} AND bill_id IN {self.in_window_ids}"

    def staff(self) -> str:
        return self.own_key_in("staff_id", self.slice_column("last_modified_by"))

    def projects(self) -> str:
        return self.own_key_in("id", staff_projects_subquery_sql(self.staff()))


def party_join_sql(f: BillSliceFilter) -> str:
    """LEFT JOIN stg_expense_party AS party: newest version per party row, then
    the LOWEST party id per bill (= ORDER BY party.id LIMIT 1 BY bill)."""
    latest = ",\n                ".join(f"argMax({c}, last_modified_time) AS latest_{c}" for c in PARTY_COLUMNS)
    first = ",\n            ".join(f"argMin(latest_{c}, id) AS {c}" for c in PARTY_COLUMNS)
    return f"""
    LEFT JOIN
    (
        SELECT
            tenant_id,
            parent_id,
            min(id) AS first_id,
            {first}
        FROM
        (
            SELECT
                tenant_id,
                parent_id,
                id,
                {latest}
            FROM {table('stg_expense_party')}
            WHERE {f.parties()}
            GROUP BY tenant_id, parent_id, id
        )
        GROUP BY tenant_id, parent_id
    ) AS party
        ON party.parent_id = bill.id AND party.tenant_id = bill.tenant_id"""


def details_join_sql(f: BillSliceFilter) -> str:
    """LEFT JOIN the bill's detail ids AS details (one array per bill, sorted, each id once)."""
    return f"""
    LEFT JOIN
    (
        SELECT tenant_id, bill_id, arraySort(groupUniqArray(id)) AS detail_ids
        FROM {table('stg_expense_billdetail')}
        WHERE {f.details()}
        GROUP BY tenant_id, bill_id
    ) AS details
        ON details.bill_id = bill.id AND details.tenant_id = bill.tenant_id"""


def _joins(f: BillSliceFilter) -> str:
    return (
        party_join_sql(f)
        + details_join_sql(f)
        + staff_bridge_join_sql(f.staff(), "bill.last_modified_by", "bill.tenant_id")
        + project_join_sql(f.projects(), "staff.first_project_id", "bill.tenant_id")
    )


def joined_rows_sql(f: BillSliceFilter) -> str:
    """One row per bill for the slice, aliased `joined` by the caller."""
    party_columns = ",\n        ".join(f"party.{c:24s} AS party_{c}" for c in PARTY_COLUMNS)
    return f"""
    SELECT
        bill.id                       AS id,
        bill.tenant_id                AS tenant_id,
        bill.bill_date                AS bill_date,
        bill.due_date                 AS due_date,
        bill.total_amount             AS total_amount,
        bill.total_paid_amount        AS total_paid_amount,
        bill.business_service         AS business_service,
        bill.reference_id             AS reference_id,
        bill.from_period              AS from_period,
        bill.to_period                AS to_period,
        bill.status                   AS status,
        bill.payment_status           AS payment_status,
        bill.bill_number              AS bill_number,
        bill.locality_code            AS locality_code,
        bill.created_by               AS created_by,
        bill.created_time             AS created_time,
        bill.last_modified_by         AS last_modified_by,
        bill.last_modified_time       AS last_modified_time,
        bill.additional_details       AS additional_details,
        party.first_id                AS party_id,
        {party_columns},
        details.detail_ids            AS detail_ids,
        project.id                    AS project_id,
        project.project_type          AS project_type,
        project.project_type_id       AS project_type_id,
        project.name                  AS project_name,
        project.reference_id          AS campaign_number,
        JSONExtractString(project.additional_details, 'hierarchyType') AS hierarchy_type
    FROM {table('stg_expense_bill')} AS bill FINAL{_joins(f)}
    WHERE {f.driving()}"""


def boundary_keys_sql(f: BillSliceFilter) -> str:
    return f"""
SELECT DISTINCT tenant_id, hierarchy_type, boundary_code
FROM
(
    SELECT
        bill.tenant_id                                                 AS tenant_id,
        JSONExtractString(project.additional_details, 'hierarchyType') AS hierarchy_type,
        bill.locality_code                                             AS boundary_code
    FROM {table('stg_expense_bill')} AS bill FINAL{staff_bridge_join_sql(f.staff(), 'bill.last_modified_by', 'bill.tenant_id')}{project_join_sql(f.projects(), 'staff.first_project_id', 'bill.tenant_id')}
    WHERE {f.driving()}
)
WHERE hierarchy_type != '' AND boundary_code != ''
"""


def user_keys_sql(f: BillSliceFilter) -> str:
    return f"""
SELECT DISTINCT bill.tenant_id AS tenant_id, bill.last_modified_by AS user_id
FROM {table('stg_expense_bill')} AS bill FINAL
WHERE {f.driving()} AND bill.last_modified_by != ''
"""


def workflow_keys_sql(f: BillSliceFilter) -> str:
    return f"""
SELECT DISTINCT bill.tenant_id AS tenant_id, bill.bill_number AS business_id
FROM {table('stg_expense_bill')} AS bill FINAL
WHERE {f.driving()} AND bill.bill_number != ''
"""


def helper_expressions(f: BillSliceFilter) -> list[tuple[str, str]]:
    payer_pairs = ", ".join(
        f"concat('\"{json_key}\": ', {json_string_sql('joined.party_' + column if column != 'id' else 'joined.party_id')})"
        for json_key, column in PARTY_JSON_KEYS
    )
    return [
        # payer (= _build_payer_json): '' when the bill has no party
        ("payer_json", f"if(joined.party_id != '', concat('{{', arrayStringConcat([{payer_pairs}], ', '), '}}'), '')"),
        ("bill_details_json", json_string_array_sql("joined.detail_ids")),
    ]


# (silver column, SQL expression) in bill_entity's column order.
SILVER_COLUMNS: list[tuple[str, str]] = [
    ("id", "joined.id"),
    ("tenant_id", "joined.tenant_id"),
    ("boundary_code", "joined.locality_code"),
    ("bill_date", "joined.bill_date"),
    ("due_date", "joined.due_date"),
    ("total_amount", "joined.total_amount"),
    ("total_wage_amount", "toDecimal64(0, 2)"),       # no bronze source
    ("total_food_amount", "toDecimal64(0, 2)"),       # no bronze source
    ("total_transport_amount", "toDecimal64(0, 2)"),  # no bronze source
    ("total_paid_amount", "joined.total_paid_amount"),
    ("business_service", "joined.business_service"),
    ("reference_id", "joined.reference_id"),
    ("from_period", "joined.from_period"),
    ("to_period", "joined.to_period"),
    ("payment_status", "joined.payment_status"),
    ("status", "joined.status"),
    ("bill_number", "joined.bill_number"),
    ("payer", "payer_json"),
    ("bill_details", "bill_details_json"),
    ("additional_details", "joined.additional_details"),
    ("created_by", "joined.created_by"),
    ("last_modified_by", "joined.last_modified_by"),
    ("created_time", "joined.created_time"),
    ("last_modified_time", "joined.last_modified_time"),
    ("wf_status", "workflow.wf_status"),
    ("process_instance", "workflow.process_instance"),
    ("wf_status_info", "workflow.wf_status_info"),
    ("user_name", "users.user_name"),
    ("name_of_user", "users.name_of_user"),
    ("role", "users.role"),
    *[(column, f"levels.{column}") for column in LEVEL_COLUMNS],
    ("hierarchy_type", "if(joined.locality_code != '', joined.hierarchy_type, '')"),
    ("project_id", "joined.project_id"),
    ("project_type", "joined.project_type"),
    ("project_type_id", "joined.project_type_id"),
    ("project_name", "joined.project_name"),
    ("campaign_number", "joined.campaign_number"),
    ("campaign_id", "''"),
]


SPEC = EntitySpec(
    entity="bill_test",
    python_entity="bill",
    driving_table="stg_expense_bill",
    slice_filter_class=BillSliceFilter,
    target=SilverTarget(
        silver_table="bill_entity",
        joined_rows_sql=joined_rows_sql,
        helper_expressions=helper_expressions,
        silver_columns=SILVER_COLUMNS,
        lookups=(
            boundary_lookup(boundary_keys_sql, code_expr="joined.locality_code"),
            user_lookup(user_keys_sql, user_expr="joined.last_modified_by"),
            workflow_lookup(workflow_keys_sql, business_id_expr="joined.bill_number"),
        ),
    ),
)

# The Airflow DAG object for this entity, built from SPEC by sql_flatten_common
# (airflow safe-mode discovery needs the words "airflow" and "dag" in this file).
dag = build_sql_test_dag(SPEC)
