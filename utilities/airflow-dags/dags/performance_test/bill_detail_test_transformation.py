"""
bill_detail_test_transformation.py

TEST twin of bill_detail_transformation.py for the `bill_detail` entity
(expense bill details): the flatten + write runs inside ClickHouse as one
INSERT ... SELECT per slice (see sql_flatten_common.py for how a run works).
Writes to <database>.bill_detail_entity_sql_test, never to bill_detail_entity.
Entity name `bill_detail_test` (dag_id bill_detail_test_transformation).

Known differences from bill_detail_transformation.py (all deliberate)
    - stg_expense_billdetail is read with FINAL (the Python DAG reads every version).
    - line_items / payable_line_items are built in SQL, sorted by line item id,
      with amount / paidAmount written as plain decimal numbers. The Python
      DAG's json.dumps fails on those Decimal values and silently DROPS the row
      (PERFORMANCE_REPORT: 8,305 of 8,308 bill details lost), so every bill
      detail that has line items is a row this twin writes and the Python DAG
      does not.
    - The payee is the LOWEST-id party of the detail after taking each party
      row's newest version; the bridge takes the newest version of each staff
      row and drops it if deleted, then the lowest staff row id per user.
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
    own_key_join_sql,
    project_join_sql,
    staff_bridge_join_sql,
    staff_projects_subquery_sql,
    table,
    user_lookup,
    workflow_lookup,
)

# Sources, in the order they appear in the query:
#
#   source                         alias        keyed / joined on                                      feeds
#   -----------------------------  -----------  -----------------------------------------------------  ------------------------------------------
#   stg_expense_billdetail (FINAL) detail       tenant_id + id range of the slice                      every plain column, bill_detail_edited
#   stg_expense_bill (FINAL)       bill         bill.id = detail.bill_id AND tenant (own PK)           the boundary lookup code (the PARENT
#                                                                                                      bill's locality_code), bill_number for
#                                                                                                      the bill-level workflow lookup
#   stg_expense_party              party        party.parent_id = detail.id AND tenant                 payee (JSON of the lowest-id party)
#                                               (18's prj_by_parent)
#   stg_expense_lineitem           line_items   line_items.bill_detail_id = detail.id AND tenant       line_items, payable_line_items (JSON
#                                               (18's prj_by_billdetail, grouped per detail)           arrays)
#   stg_project_staff              staff        staff.staff_id = detail.last_modified_by AND tenant    the project id of the user's lowest-id
#                                               (17's prj_by_staff)                                    non-deleted staff row
#   stg_project (FINAL)            project      project.id = staff.first_project_id AND tenant         project_* columns, campaign_number,
#                                                                                                      hierarchy_type
#   boundary-service response      levels       (tenant_id, hierarchy_type, bill.locality_code)        level_one_code .. level_nine_code
#   user-service response          users        (tenant_id, last_modified_by)                          user_name, name_of_user, role
#   workflow-service response      workflow     (tenant_id, detail.id)                                 wf_status, process_instance, wf_status_info
#   workflow-service response      bill_workflow (tenant_id, bill.bill_number)                         bill_wf_status_info

PARTY_COLUMNS = ["type", "identifier", "payment_provider", "payee_name", "payee_phone_number",
                 "bank_account", "bank_code", "beneficiary_code", "status"]
PARTY_JSON_KEYS = [("id", "id"), ("type", "type"), ("identifier", "identifier"), ("paymentProvider", "payment_provider"),
                   ("payeeName", "payee_name"), ("payeePhoneNumber", "payee_phone_number"), ("bankAccount", "bank_account"),
                   ("bankCode", "bank_code"), ("beneficiaryCode", "beneficiary_code"), ("status", "status")]
LINE_ITEM_COLUMNS = ["head_code", "amount", "paid_amount", "type", "status", "payment_status", "is_line_item_payable"]


class BillDetailSliceFilter(SliceFilter):
    driving_alias = "detail"

    def bills(self) -> str:
        return self.own_key_in("id", self.slice_column("bill_id"))

    def parties(self) -> str:
        return f"tenant_id = {self.tenant} AND {self.id_range('parent_id')} AND parent_id IN {self.in_window_ids}"

    def line_items(self) -> str:
        return f"tenant_id = {self.tenant} AND {self.id_range('bill_detail_id')} AND bill_detail_id IN {self.in_window_ids}"

    def staff(self) -> str:
        return self.own_key_in("staff_id", self.slice_column("last_modified_by"))

    def projects(self) -> str:
        return self.own_key_in("id", staff_projects_subquery_sql(self.staff()))


def party_join_sql(f: BillDetailSliceFilter) -> str:
    """LEFT JOIN stg_expense_party AS party: newest version per party row, then
    the LOWEST party id per detail (= ORDER BY party.id LIMIT 1 BY detail)."""
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
        ON party.parent_id = detail.id AND party.tenant_id = detail.tenant_id"""


def line_items_join_sql(f: BillDetailSliceFilter) -> str:
    """LEFT JOIN the detail's line items AS line_items: newest version per line
    item, rendered as one JSON object each (json.dumps key order of
    _fetch_line_items), sorted by id; `payable` marks the payable subset."""
    latest = ",\n                ".join(f"argMax({c}, last_modified_time) AS latest_{c}" for c in LINE_ITEM_COLUMNS)
    item_json = (
        "concat('{\"id\": ', " + json_string_sql("id") + ", "
        "', \"headCode\": ', " + json_string_sql("latest_head_code") + ", "
        "', \"amount\": ', toString(latest_amount), "
        "', \"type\": ', " + json_string_sql("latest_type") + ", "
        "', \"paidAmount\": ', toString(latest_paid_amount), "
        "', \"status\": ', " + json_string_sql("latest_status") + ", "
        "', \"paymentStatus\": ', " + json_string_sql("latest_payment_status") + ", '}')"
    )
    return f"""
    LEFT JOIN
    (
        SELECT
            tenant_id,
            bill_detail_id,
            arrayMap(t -> t.2, arraySort(t -> t.1, groupArray((id, item_json)))) AS items,
            arrayMap(t -> t.2, arraySort(t -> t.1, groupArrayIf((id, item_json), latest_is_line_item_payable))) AS payable_items
        FROM
        (
            SELECT
                tenant_id,
                bill_detail_id,
                id,
                {latest},
                {item_json} AS item_json
            FROM {table('stg_expense_lineitem')}
            WHERE {f.line_items()}
            GROUP BY tenant_id, bill_detail_id, id
        )
        GROUP BY tenant_id, bill_detail_id
    ) AS line_items
        ON line_items.bill_detail_id = detail.id AND line_items.tenant_id = detail.tenant_id"""


def _joins(f: BillDetailSliceFilter) -> str:
    return (
        own_key_join_sql("stg_expense_bill", f.bills(), "bill", "id, tenant_id, locality_code, bill_number",
                         [("id", "detail.bill_id"), ("tenant_id", "detail.tenant_id")])
        + party_join_sql(f)
        + line_items_join_sql(f)
        + staff_bridge_join_sql(f.staff(), "detail.last_modified_by", "detail.tenant_id")
        + project_join_sql(f.projects(), "staff.first_project_id", "detail.tenant_id")
    )


def joined_rows_sql(f: BillDetailSliceFilter) -> str:
    """One row per bill detail for the slice, aliased `joined` by the caller."""
    party_columns = ",\n        ".join(f"party.{c:24s} AS party_{c}" for c in PARTY_COLUMNS)
    return f"""
    SELECT
        detail.id                     AS id,
        detail.tenant_id              AS tenant_id,
        detail.reference_id           AS reference_id,
        detail.bill_id                AS bill_id,
        detail.total_amount           AS total_amount,
        detail.total_paid_amount      AS total_paid_amount,
        detail.payment_status         AS payment_status,
        detail.status                 AS status,
        detail.from_period            AS from_period,
        detail.to_period              AS to_period,
        detail.total_attendance       AS total_attendance,
        detail.worker_id              AS worker_id,
        detail.created_by             AS created_by,
        detail.created_time           AS created_time,
        detail.last_modified_by       AS last_modified_by,
        detail.last_modified_time     AS last_modified_time,
        detail.additional_details     AS additional_details,
        bill.locality_code            AS bill_locality_code,
        bill.bill_number              AS bill_number,
        party.first_id                AS party_id,
        {party_columns},
        line_items.items              AS line_item_jsons,
        line_items.payable_items      AS payable_line_item_jsons,
        project.id                    AS project_id,
        project.project_type          AS project_type,
        project.project_type_id       AS project_type_id,
        project.name                  AS project_name,
        project.reference_id          AS campaign_number,
        JSONExtractString(project.additional_details, 'hierarchyType') AS hierarchy_type
    FROM {table('stg_expense_billdetail')} AS detail FINAL{_joins(f)}
    WHERE {f.driving()}"""


def boundary_keys_sql(f: BillDetailSliceFilter) -> str:
    return f"""
SELECT DISTINCT tenant_id, hierarchy_type, boundary_code
FROM
(
    SELECT
        detail.tenant_id                                               AS tenant_id,
        JSONExtractString(project.additional_details, 'hierarchyType') AS hierarchy_type,
        bill.locality_code                                             AS boundary_code
    FROM {table('stg_expense_billdetail')} AS detail FINAL{own_key_join_sql('stg_expense_bill', f.bills(), 'bill', 'id, tenant_id, locality_code', [('id', 'detail.bill_id'), ('tenant_id', 'detail.tenant_id')])}{staff_bridge_join_sql(f.staff(), 'detail.last_modified_by', 'detail.tenant_id')}{project_join_sql(f.projects(), 'staff.first_project_id', 'detail.tenant_id')}
    WHERE {f.driving()}
)
WHERE hierarchy_type != '' AND boundary_code != ''
"""


def user_keys_sql(f: BillDetailSliceFilter) -> str:
    return f"""
SELECT DISTINCT detail.tenant_id AS tenant_id, detail.last_modified_by AS user_id
FROM {table('stg_expense_billdetail')} AS detail FINAL
WHERE {f.driving()} AND detail.last_modified_by != ''
"""


def detail_workflow_keys_sql(f: BillDetailSliceFilter) -> str:
    return f"""
SELECT DISTINCT detail.tenant_id AS tenant_id, detail.id AS business_id
FROM {table('stg_expense_billdetail')} AS detail FINAL
WHERE {f.driving()}
"""


def bill_workflow_keys_sql(f: BillDetailSliceFilter) -> str:
    return f"""
SELECT DISTINCT detail.tenant_id AS tenant_id, bill.bill_number AS business_id
FROM {table('stg_expense_billdetail')} AS detail FINAL{own_key_join_sql('stg_expense_bill', f.bills(), 'bill', 'id, tenant_id, bill_number', [('id', 'detail.bill_id'), ('tenant_id', 'detail.tenant_id')])}
WHERE {f.driving()} AND bill.bill_number != ''
"""


def helper_expressions(f: BillDetailSliceFilter) -> list[tuple[str, str]]:
    payee_pairs = ", ".join(
        f"concat('\"{json_key}\": ', {json_string_sql('joined.party_' + column if column != 'id' else 'joined.party_id')})"
        for json_key, column in PARTY_JSON_KEYS
    )
    return [
        ("payee_json", f"if(joined.party_id != '', concat('{{', arrayStringConcat([{payee_pairs}], ', '), '}}'), '')"),
        ("line_items_json", "concat('[', arrayStringConcat(joined.line_item_jsons, ', '), ']')"),
        ("payable_line_items_json", "concat('[', arrayStringConcat(joined.payable_line_item_jsons, ', '), ']')"),
        # bill_detail_edited (= _was_edited): additionalDetails.editInfo is a non-empty object
        ("edited",
         "JSONType(joined.additional_details, 'editInfo') = 'Object' "
         "AND length(JSONExtractKeys(joined.additional_details, 'editInfo')) > 0"),
    ]


# (silver column, SQL expression) in bill_detail_entity's column order.
SILVER_COLUMNS: list[tuple[str, str]] = [
    ("id", "joined.id"),
    ("tenant_id", "joined.tenant_id"),
    ("bill_id", "joined.bill_id"),
    ("total_amount", "joined.total_amount"),
    ("total_paid_amount", "joined.total_paid_amount"),
    ("reference_id", "joined.reference_id"),
    ("payment_status", "joined.payment_status"),
    ("status", "joined.status"),
    ("from_period", "joined.from_period"),
    ("to_period", "joined.to_period"),
    ("worker_id", "joined.worker_id"),
    ("payee", "payee_json"),
    ("line_items", "line_items_json"),
    ("payable_line_items", "payable_line_items_json"),
    ("created_by", "joined.created_by"),
    ("last_modified_by", "joined.last_modified_by"),
    ("created_time", "joined.created_time"),
    ("last_modified_time", "joined.last_modified_time"),
    ("additional_details", "joined.additional_details"),
    ("total_attendance", "CAST(joined.total_attendance AS Decimal(12, 2))"),
    ("wf_status", "workflow.wf_status"),
    ("process_instance", "workflow.process_instance"),
    ("bill_detail_edited", "edited"),
    ("bill_wf_status_info", "bill_workflow.wf_status_info"),
    ("wf_status_info", "workflow.wf_status_info"),
    ("user_name", "users.user_name"),
    ("name_of_user", "users.name_of_user"),
    ("role", "users.role"),
    *[(column, f"levels.{column}") for column in LEVEL_COLUMNS],
    ("hierarchy_type", "if(joined.bill_locality_code != '', joined.hierarchy_type, '')"),
    ("project_id", "joined.project_id"),
    ("project_type", "joined.project_type"),
    ("project_type_id", "joined.project_type_id"),
    ("project_name", "joined.project_name"),
    ("campaign_number", "joined.campaign_number"),
    ("campaign_id", "''"),
]


SPEC = EntitySpec(
    entity="bill_detail_test",
    python_entity="bill_detail",
    driving_table="stg_expense_billdetail",
    slice_filter_class=BillDetailSliceFilter,
    target=SilverTarget(
        silver_table="bill_detail_entity",
        joined_rows_sql=joined_rows_sql,
        helper_expressions=helper_expressions,
        silver_columns=SILVER_COLUMNS,
        lookups=(
            boundary_lookup(boundary_keys_sql, code_expr="joined.bill_locality_code"),
            user_lookup(user_keys_sql, user_expr="joined.last_modified_by"),
            workflow_lookup(detail_workflow_keys_sql, business_id_expr="joined.id"),
            workflow_lookup(bill_workflow_keys_sql, business_id_expr="joined.bill_number", alias="bill_workflow"),
        ),
    ),
)

# The Airflow DAG object for this entity, built from SPEC by sql_flatten_common
# (airflow safe-mode discovery needs the words "airflow" and "dag" in this file).
dag = build_sql_test_dag(SPEC)
