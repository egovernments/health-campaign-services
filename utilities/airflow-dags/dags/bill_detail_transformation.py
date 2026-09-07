"""
bill_detail_transformation.py

Bronze -> silver transformation DAG for the `bill_detail` entity (BillDetail
-> BillDetailIndexV1 in the Java reference). Triggered exclusively by
bronze_to_silver_orchestrator with conf={"start_time": ..., "end_time": ...};
not scheduled on its own.

Java's transformBillDetails additionally forks a synthetic duplicate row
(billDetailEdited=true, id=billDetail.id + "-" + editTimestamp) when
additionalDetails.editInfo shows a payables/payee edit -- a workaround for
Java's own non-versioned downstream index. This pipeline already receives
every edit as its own CDC event, and bill_detail_entity is a
ReplacingMergeTree(last_modified_time) keyed on the same id (i.e. it already
converges to the latest edit by design, per airflow_dags/CLAUDE.md's own
stated engine-choice convention) -- so that duplication is deliberately NOT
ported. bill_detail_edited instead reflects a plain "has this ever been
edited" boolean; each bronze row produces exactly one silver row.
"""
from __future__ import annotations

import json
import logging
import os
import sys

import pendulum
from airflow.decorators import dag, task
from airflow.exceptions import AirflowFailException
from airflow.models import Variable

# Airflow 3's DAG bundle loader doesn't guarantee the dags/ folder is on
# sys.path, unlike plain-module imports elsewhere -- add it explicitly so
# sibling helper modules (clickhouse_utils.py) are importable.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from clickhouse_utils import get_clickhouse_client  # noqa: E402
from egov_api_utils import (  # noqa: E402
    attach_boundary_levels,
    extract_boundary_lookup_keys,
    extract_user_lookup_keys,
    parse_hierarchy_type,
    resolve_boundary_levels,
    resolve_user_info,
    resolve_workflow_summaries,
)

log = logging.getLogger(__name__)

DAG_ID = "bill_detail_transformation"

BRONZE_TABLE = "analytics.stg_expense_billdetail"
BILL_TABLE = "analytics.stg_expense_bill"
PARTY_TABLE = "analytics.stg_expense_party"
LINE_ITEM_TABLE = "analytics.stg_expense_lineitem"
PROJECT_STAFF_TABLE = "analytics.stg_project_staff"
PROJECT_TABLE = "analytics.stg_project"
PROJECT_ADDRESS_TABLE = "analytics.stg_project_address"
CHUNK_SIZE_VARIABLE = "bronze_to_silver_chunk_size"
DEFAULT_CHUNK_SIZE = 5000

SILVER_TABLE = "bill_detail_entity"

SILVER_COLUMNS = [
    "id", "tenant_id", "bill_id", "total_amount", "total_paid_amount", "reference_id", "payment_status",
    "status", "from_period", "to_period", "worker_id", "payee", "line_items", "payable_line_items",
    "created_by", "last_modified_by", "created_time", "last_modified_time", "additional_details",
    "total_attendance", "wf_status", "process_instance", "bill_detail_edited", "bill_wf_status_info",
    "wf_status_info", "user_name", "name_of_user", "role",
    "level_one_code", "level_two_code", "level_three_code", "level_four_code", "level_five_code",
    "level_six_code", "level_seven_code", "level_eight_code", "level_nine_code", "hierarchy_type",
    "project_id", "project_type", "project_type_id", "project_name", "campaign_number", "campaign_id",
]


def _parse_window_bound(iso_ts: str):
    return pendulum.parse(iso_ts)


def _count_bronze_records(client, start_dt, end_dt) -> int:
    result = client.query(
        f"SELECT count() FROM {BRONZE_TABLE} "
        f"WHERE _ingested_at >= %(start_dt)s AND _ingested_at < %(end_dt)s",
        parameters={"start_dt": start_dt, "end_dt": end_dt},
    )
    return result.result_rows[0][0]


def _iter_bronze_chunks(client, start_dt, end_dt, chunk_size: int):
    """
    Yields lists of row dicts, walking the [start_dt, end_dt) bronze-ingestion
    window via keyset pagination on (id, _ingested_at) -- id first, not
    _ingested_at, because id is the only column here guaranteed to round-trip
    exactly through clickhouse-connect's query parameters. _ingested_at
    (DateTime64(3)) gets serialized as a plain string truncated to whole
    seconds when sent back as a cursor value -- confirmed directly against
    ClickHouse (a parameterized SELECT of a millisecond-precision datetime
    came back as the literal string with sub-second precision silently
    dropped). With _ingested_at as the *first* tuple element (as this used to
    be ordered), any batch of rows sharing one ingest timestamp -- e.g. a
    bulk bronze load done in a single insert, or a bulk raw-event-store-to-
    bronze flush inserting more rows than one chunk_size at once -- would
    have every row's true (sub-second) _ingested_at compare greater than the
    truncated cursor on that first element alone, so id (the correct
    tiebreaker) would never even be reached: the same page would be returned
    forever, a real infinite loop (reproduced directly against ClickHouse).
    id is unique per row and a plain string, immune to this truncation.
    _ingested_at is kept as a tiebreaker for id itself only out of caution
    (ids are already unique, so it's not expected to matter in practice) --
    compared as whole milliseconds (toUnixTimestamp64Milli), an integer, for
    the same round-trip-exactness reason, so even that tiebreaker can't
    silently lose precision either.
    """
    cursor_id, cursor_ms = None, None
    while True:
        cursor_clause = ""
        params = {"start_dt": start_dt, "end_dt": end_dt, "limit": chunk_size}
        if cursor_id is not None:
            cursor_clause = "AND (id, toUnixTimestamp64Milli(_ingested_at)) > (%(cursor_id)s, %(cursor_ms)s)"
            params["cursor_id"] = cursor_id
            params["cursor_ms"] = cursor_ms

        result = client.query(
            f"""
            SELECT id, toUnixTimestamp64Milli(_ingested_at) AS ingested_at_ms
            FROM {BRONZE_TABLE}
            WHERE _ingested_at >= %(start_dt)s AND _ingested_at < %(end_dt)s
            {cursor_clause}
            ORDER BY id, ingested_at_ms
            LIMIT %(limit)s
            """,
            parameters=params,
        )
        rows = list(result.named_results())
        if not rows:
            return

        yield rows

        last_row = rows[-1]
        cursor_id, cursor_ms = last_row["id"], last_row["ingested_at_ms"]
        if len(rows) < chunk_size:
            return


def _fetch_enriched_billdetail_rows(client, detail_ids: list[str]) -> list[dict]:
    """
    Left-joins this chunk's stg_expense_billdetail rows with their parent
    stg_expense_bill row (for the parent's locality_code/bill_number -- a
    bronze-join simplification of Java's own billService.searchBill round
    trip) and their own stg_expense_party row (the bill detail's payee,
    same reverse-FK shape as bill_transformation.py's payer join).

    LIMIT 1 BY bd.id guards against an unexpected multi-party match. bd's
    own columns are individually aliased rather than `bd.*` -- with 2+
    joined tables, ClickHouse silently qualifies any bd column whose bare
    name collides with a column in b/party as `bd.<col>` in the result
    set, breaking downstream lookups expecting bare names. FINAL is used
    on both joined tables to avoid row versions from un-merged
    ReplacingMergeTree duplicates.
    """
    result = client.query(
        f"""
        SELECT
            bd.id                   AS id,
            bd.tenant_id             AS tenant_id,
            bd.reference_id          AS reference_id,
            bd.bill_id               AS bill_id,
            bd.total_amount          AS total_amount,
            bd.total_paid_amount     AS total_paid_amount,
            bd.payment_status        AS payment_status,
            bd.status                AS status,
            bd.from_period           AS from_period,
            bd.to_period             AS to_period,
            bd.net_line_item_amount  AS net_line_item_amount,
            bd.total_attendance      AS total_attendance,
            bd.worker_id             AS worker_id,
            bd.created_by            AS created_by,
            bd.created_time          AS created_time,
            bd.last_modified_by      AS last_modified_by,
            bd.last_modified_time    AS last_modified_time,
            bd.additional_details    AS additional_details,
            b.locality_code          AS bill_locality_code,
            b.bill_number            AS bill_bill_number,
            party.id                 AS payee_party_id,
            party.type               AS payee_type,
            party.identifier         AS payee_identifier,
            party.payment_provider   AS payee_payment_provider,
            party.payee_name         AS payee_name,
            party.payee_phone_number AS payee_phone_number,
            party.bank_account       AS payee_bank_account,
            party.bank_code          AS payee_bank_code,
            party.beneficiary_code   AS payee_beneficiary_code,
            party.status             AS payee_status
        FROM {BRONZE_TABLE} AS bd
        LEFT JOIN {BILL_TABLE} AS b FINAL
            ON b.id = bd.bill_id AND b.tenant_id = bd.tenant_id
        LEFT JOIN {PARTY_TABLE} AS party FINAL
            ON party.parent_id = bd.id AND party.tenant_id = bd.tenant_id
        WHERE bd.id IN %(detail_ids)s
        ORDER BY party.id ASC
        LIMIT 1 BY bd.id
        """,
        parameters={"detail_ids": detail_ids},
    )
    return list(result.named_results())


def _fetch_line_items(client, detail_ids: list[str]) -> dict[str, dict]:
    """
    Per bill_detail_id: line_items (every linked stg_expense_lineitem row,
    JSON-serialized) and payable_line_items (the subset with
    is_line_item_payable = true) -- the same one-to-many rollup shape as
    attendance_register_transformation.py's attendee/staff rollup. A bill
    detail with zero line items still gets {"line_items": [], "payable_line_items": []},
    not a KeyError.
    """
    result = client.query(
        f"""
        SELECT bill_detail_id, id, head_code, amount, paid_amount, type, status,
               payment_status, is_line_item_payable
        FROM {LINE_ITEM_TABLE}
        WHERE bill_detail_id IN %(detail_ids)s
        """,
        parameters={"detail_ids": detail_ids},
    )

    bundles: dict[str, dict] = {
        detail_id: {"line_items": [], "payable_line_items": []} for detail_id in detail_ids
    }
    for row in result.named_results():
        item = {
            "id": row["id"],
            "headCode": row["head_code"],
            "amount": row["amount"],
            "type": row["type"],
            "paidAmount": row["paid_amount"],
            "status": row["status"],
            "paymentStatus": row["payment_status"],
        }
        bundle = bundles[row["bill_detail_id"]]
        bundle["line_items"].append(item)
        if row["is_line_item_payable"]:
            bundle["payable_line_items"].append(item)
    return bundles


def _extract_staff_lookup_keys(joined_rows: list[dict]) -> dict[str, set[str]]:
    lookup_keys: dict[str, set[str]] = {}
    for row in joined_rows:
        user_id = row.get("last_modified_by")
        if user_id:
            lookup_keys.setdefault(row["tenant_id"], set()).add(user_id)
    return lookup_keys


def _resolve_user_project_context(client, lookup_keys: dict[str, set[str]]) -> dict[tuple[str, str], dict]:
    """
    LEFT JOIN stg_project_staff -> stg_project -> stg_project_address,
    LIMIT 1 BY staff_id -- same tie-break already established for every
    prior entity needing this bridge (confirmed against the live
    project-service source: GenericRepository's default ORDER BY id ASC).
    Keyed on this bill detail's OWN last_modified_by -- no asymmetry, Java
    uses the same key for both the trailer and (via the shared audit block)
    the user-info lookup.
    """
    resolved: dict[tuple[str, str], dict] = {}
    for tenant_id, user_ids in lookup_keys.items():
        result = client.query(
            f"""
            SELECT
                ps.staff_id           AS user_id,
                p.id                  AS project_id,
                p.project_type        AS project_type,
                p.project_type_id     AS project_type_id,
                p.name                AS project_name,
                p.reference_id        AS campaign_number,
                p.additional_details  AS project_additional_details,
                paddr.boundary        AS project_boundary_code
            FROM {PROJECT_STAFF_TABLE} AS ps
            LEFT JOIN {PROJECT_TABLE} AS p
                ON p.id = ps.project_id AND p.tenant_id = ps.tenant_id
            LEFT JOIN {PROJECT_ADDRESS_TABLE} AS paddr
                ON paddr.project_id = p.id AND paddr.tenant_id = p.tenant_id
            WHERE ps.tenant_id = %(tenant_id)s AND ps.staff_id IN %(user_ids)s AND ps.is_deleted = false
            ORDER BY ps.id ASC
            LIMIT 1 BY ps.staff_id
            """,
            parameters={"tenant_id": tenant_id, "user_ids": list(user_ids)},
        )
        for r in result.named_results():
            resolved[(tenant_id, r["user_id"])] = {
                "project_id": r["project_id"] or "",
                "project_type": r["project_type"] or "",
                "project_type_id": r["project_type_id"] or "",
                "project_name": r["project_name"] or "",
                "campaign_number": r["campaign_number"] or "",
                "project_additional_details": r["project_additional_details"],
                "project_boundary_code": r["project_boundary_code"],
            }
    return resolved


def _attach_project_context(joined_rows: list[dict], user_project_context: dict) -> None:
    for row in joined_rows:
        row["project_id"] = ""
        row["project_type"] = ""
        row["project_type_id"] = ""
        row["project_name"] = ""
        row["campaign_number"] = ""
        row["project_additional_details"] = None
        row["project_boundary_code"] = None
        details = user_project_context.get((row["tenant_id"], row.get("last_modified_by")))
        if details:
            row.update(details)


def _get_boundary_lookup_key(row: dict) -> tuple[str, str, str] | None:
    """The CODE is the PARENT bill's own locality_code (joined), not the
    bill detail's own -- bill detail bronze has no such column."""
    hierarchy_type = parse_hierarchy_type(row.get("project_additional_details"))
    if not hierarchy_type:
        return None
    code = row.get("bill_locality_code")
    if not code:
        return None
    return row["tenant_id"], hierarchy_type, code


def _get_user_lookup_key(row: dict) -> tuple[str, str] | None:
    user_id = row.get("last_modified_by")
    if not user_id:
        return None
    return row["tenant_id"], user_id


def _get_detail_workflow_lookup_key(row: dict) -> tuple[str, str] | None:
    return row["tenant_id"], row["id"]


def _get_bill_workflow_lookup_key(row: dict) -> tuple[str, str] | None:
    bill_number = row.get("bill_bill_number")
    if not bill_number:
        return None
    return row["tenant_id"], bill_number


def _was_edited(additional_details) -> bool:
    """
    Reflects whether this bill detail has ever been edited -- true if
    additional_details.editInfo is present at all (Java further branches on
    editInfo.payablesUpdatedAtEpochMs/.payeeUpdatedAtEpochMs specifically to
    mint a synthetic duplicate row id, which this port does NOT replicate;
    see module docstring). Deliberately a plain boolean, not a duplication
    trigger.
    """
    if not additional_details:
        return False
    try:
        parsed = json.loads(additional_details)
    except (TypeError, ValueError):
        return False
    edit_info = parsed.get("editInfo") if isinstance(parsed, dict) else None
    return isinstance(edit_info, dict) and bool(edit_info)


def _default_str(value) -> str:
    return value if value is not None else ""


def _default_int(value) -> int:
    return 0 if value is None else int(round(value))


def _default_decimal(value):
    return value if value is not None else 0


def _build_payee_json(row: dict) -> str:
    if not row.get("payee_party_id"):
        return ""
    return json.dumps({
        "id": row.get("payee_party_id"),
        "type": row.get("payee_type"),
        "identifier": row.get("payee_identifier"),
        "paymentProvider": row.get("payee_payment_provider"),
        "payeeName": row.get("payee_name"),
        "payeePhoneNumber": row.get("payee_phone_number"),
        "bankAccount": row.get("payee_bank_account"),
        "bankCode": row.get("payee_bank_code"),
        "beneficiaryCode": row.get("payee_beneficiary_code"),
        "status": row.get("payee_status"),
    })


def _build_silver_row(row: dict, line_item_bundles: dict, workflow_summaries: dict) -> dict:
    """
    Maps one fully-enriched joined row onto bill_detail_entity's exact
    column set. Exactly one silver row per bronze row (see module
    docstring for why the edited-copy duplication isn't ported).
    """
    bundle = line_item_bundles.get(row["id"], {"line_items": [], "payable_line_items": []})

    detail_summary = workflow_summaries.get(_get_detail_workflow_lookup_key(row)) or {}
    detail_latest_instance = detail_summary.get("_latestInstance")
    detail_summary_without_instance = {k: v for k, v in detail_summary.items() if k != "_latestInstance"}

    bill_summary = workflow_summaries.get(_get_bill_workflow_lookup_key(row)) or {}
    bill_summary_without_instance = {k: v for k, v in bill_summary.items() if k != "_latestInstance"}

    return {
        "id": row["id"],
        "tenant_id": _default_str(row.get("tenant_id")),
        "bill_id": _default_str(row.get("bill_id")),
        "total_amount": _default_decimal(row.get("total_amount")),
        "total_paid_amount": _default_decimal(row.get("total_paid_amount")),
        "reference_id": _default_str(row.get("reference_id")),
        "payment_status": _default_str(row.get("payment_status")),
        "status": _default_str(row.get("status")),
        "from_period": _default_int(row.get("from_period")),
        "to_period": _default_int(row.get("to_period")),
        "worker_id": _default_str(row.get("worker_id")),
        "payee": _build_payee_json(row),
        "line_items": json.dumps(bundle["line_items"]),
        "payable_line_items": json.dumps(bundle["payable_line_items"]),
        "created_by": _default_str(row.get("created_by")),
        "last_modified_by": _default_str(row.get("last_modified_by")),
        "created_time": _default_int(row.get("created_time")),
        "last_modified_time": _default_int(row.get("last_modified_time")),
        "additional_details": _default_str(row.get("additional_details")),
        "total_attendance": _default_decimal(row.get("total_attendance")),
        "wf_status": _default_str(detail_summary.get("currentStatus")),
        "process_instance": json.dumps(detail_latest_instance) if detail_latest_instance else "",
        "bill_detail_edited": _was_edited(row.get("additional_details")),
        "bill_wf_status_info": json.dumps(bill_summary_without_instance) if bill_summary else "",
        "wf_status_info": json.dumps(detail_summary_without_instance) if detail_summary else "",
        "user_name": _default_str(row.get("user_name")),
        "name_of_user": _default_str(row.get("name_of_user")),
        "role": _default_str(row.get("role")),
        "level_one_code": _default_str(row.get("level_one_code")),
        "level_two_code": _default_str(row.get("level_two_code")),
        "level_three_code": _default_str(row.get("level_three_code")),
        "level_four_code": _default_str(row.get("level_four_code")),
        "level_five_code": _default_str(row.get("level_five_code")),
        "level_six_code": _default_str(row.get("level_six_code")),
        "level_seven_code": _default_str(row.get("level_seven_code")),
        "level_eight_code": _default_str(row.get("level_eight_code")),
        "level_nine_code": _default_str(row.get("level_nine_code")),
        "hierarchy_type": _default_str(row.get("hierarchy_type")),
        "project_id": _default_str(row.get("project_id")),
        "project_type": _default_str(row.get("project_type")),
        "project_type_id": _default_str(row.get("project_type_id")),
        "project_name": _default_str(row.get("project_name")),
        "campaign_number": _default_str(row.get("campaign_number")),
        "campaign_id": "",  # TODO: needs project-factory service integration (not yet built)
    }


def _build_silver_rows(joined_rows: list[dict], line_item_bundles: dict, workflow_summaries: dict) -> list[dict]:
    """Builds each row independently; a malformed row is logged and
    skipped rather than failing the whole chunk's write."""
    silver_rows = []
    for row in joined_rows:
        try:
            silver_rows.append(_build_silver_row(row, line_item_bundles, workflow_summaries))
        except Exception:
            log.exception(
                "bill_detail: failed to build silver row for bill detail id=%s; skipping this row",
                row.get("id"),
            )
    return silver_rows


def _write_silver_chunk(client, silver_rows: list[dict]) -> None:
    if not silver_rows:
        return
    data = [[row[column] for column in SILVER_COLUMNS] for row in silver_rows]
    client.insert(SILVER_TABLE, data, column_names=SILVER_COLUMNS)


@dag(
    dag_id=DAG_ID,
    description="Transforms bill-detail bronze events into the bill_detail_entity silver table.",
    schedule=None,
    start_date=pendulum.datetime(2024, 1, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    tags=["bronze-to-silver", "bill_detail"],
)
def bill_detail_transformation():

    @task
    def parse_time_window(**context) -> dict:
        """Reads and validates start_time/end_time injected by the orchestrator."""
        conf = context["dag_run"].conf or {}
        start_time_raw = conf.get("start_time")
        end_time_raw = conf.get("end_time")

        if not start_time_raw or not end_time_raw:
            raise AirflowFailException(
                f"{DAG_ID} requires 'start_time' and 'end_time' in dag_run.conf; "
                f"got conf={conf!r}. This DAG must be triggered by "
                f"bronze_to_silver_orchestrator (or manually with an equivalent conf)."
            )

        start_time = pendulum.parse(start_time_raw)
        end_time = pendulum.parse(end_time_raw)
        return {
            "start_time": start_time.to_iso8601_string(),
            "end_time": end_time.to_iso8601_string(),
        }

    @task
    def transform_bronze_to_silver(time_window: dict) -> None:
        """
        Reads bill-detail bronze rows for this run's window in fixed-size
        chunks via keyset pagination, transforms, and writes each chunk to
        bill_detail_entity before moving to the next chunk.

        Filtered on _ingested_at (bronze arrival time), not
        last_modified_time (source modification time) -- see
        airflow_dags/CLAUDE.md's "Bronze read window column" convention for
        why.
        """
        start_dt = _parse_window_bound(time_window["start_time"])
        end_dt = _parse_window_bound(time_window["end_time"])
        chunk_size = int(Variable.get(CHUNK_SIZE_VARIABLE, default_var=DEFAULT_CHUNK_SIZE))

        client = get_clickhouse_client()

        total = _count_bronze_records(client, start_dt, end_dt)
        log.info(
            "bill_detail bronze records ingested in [%s, %s): %d (chunk_size=%d)",
            time_window["start_time"], time_window["end_time"], total, chunk_size,
        )
        if total == 0:
            return

        chunk_num = 0
        rows_seen = 0
        for chunk in _iter_bronze_chunks(client, start_dt, end_dt, chunk_size):
            chunk_num += 1
            rows_seen += len(chunk)
            log.info(
                "bill_detail chunk %d: %d bill detail rows (cumulative %d/%d)",
                chunk_num, len(chunk), rows_seen, total,
            )

            detail_ids = [row["id"] for row in chunk]
            joined_rows = _fetch_enriched_billdetail_rows(client, detail_ids)
            log.info(
                "bill_detail chunk %d: %d rows after LEFT JOIN with bill/party",
                chunk_num, len(joined_rows),
            )

            line_item_bundles = _fetch_line_items(client, detail_ids)
            log.info(
                "bill_detail chunk %d: built line-item rollups for %d bill detail(s)",
                chunk_num, len(line_item_bundles),
            )

            staff_lookup_keys = _extract_staff_lookup_keys(joined_rows)
            unique_staff_count = sum(len(user_ids) for user_ids in staff_lookup_keys.values())
            user_project_context = _resolve_user_project_context(client, staff_lookup_keys)
            _attach_project_context(joined_rows, user_project_context)
            log.info(
                "bill_detail chunk %d: resolved project-staff bridge for %d/%d unique user(s)",
                chunk_num, len(user_project_context), unique_staff_count,
            )

            lookup_keys = extract_boundary_lookup_keys(joined_rows, _get_boundary_lookup_key)
            resolved_levels = resolve_boundary_levels(lookup_keys)
            attach_boundary_levels(joined_rows, resolved_levels, _get_boundary_lookup_key)
            log.info(
                "bill_detail chunk %d: attached boundary hierarchy levels to %d rows",
                chunk_num, len(joined_rows),
            )

            user_lookup_keys = extract_user_lookup_keys(joined_rows, _get_user_lookup_key)
            resolved_user_info = resolve_user_info(user_lookup_keys)
            for row in joined_rows:
                info = resolved_user_info.get(_get_user_lookup_key(row)) or {}
                row["user_name"] = info.get("USERNAME") or ""
                row["name_of_user"] = info.get("NAME") or ""
                row["role"] = info.get("ROLE") or ""
            log.info(
                "bill_detail chunk %d: attached user info to %d rows (%d unique user(s))",
                chunk_num, len(joined_rows), len(user_lookup_keys),
            )

            detail_workflow_keys = extract_user_lookup_keys(joined_rows, _get_detail_workflow_lookup_key)
            bill_workflow_keys = extract_user_lookup_keys(joined_rows, _get_bill_workflow_lookup_key)
            workflow_summaries = resolve_workflow_summaries(detail_workflow_keys | bill_workflow_keys)
            log.info(
                "bill_detail chunk %d: resolved workflow summaries for %d bill detail(s), "
                "%d parent bill(s)",
                chunk_num, len(detail_workflow_keys), len(bill_workflow_keys),
            )

            silver_rows = _build_silver_rows(joined_rows, line_item_bundles, workflow_summaries)
            _write_silver_chunk(client, silver_rows)
            log.info(
                "bill_detail chunk %d: wrote %d/%d rows to %s",
                chunk_num, len(silver_rows), len(joined_rows), SILVER_TABLE,
            )

    transform_bronze_to_silver(parse_time_window())


bill_detail_transformation()
