"""
bill_transformation.py

Bronze -> silver transformation DAG for the `bill` entity (Bill ->
BillIndexV1 in the Java reference). Triggered exclusively by
bronze_to_silver_orchestrator with conf={"start_time": ..., "end_time": ...};
not scheduled on its own.

Bronze exists under different names than the silver table suggests:
analytics.stg_expense_bill (-> bill_entity), analytics.stg_expense_party
(payer/payee), analytics.stg_expense_billdetail (bill_detail_transformation.py's
own bronze table, referenced here only for the bill_details cross-reference).
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

DAG_ID = "bill_transformation"

BRONZE_TABLE = "analytics.stg_expense_bill"
PARTY_TABLE = "analytics.stg_expense_party"
BILL_DETAIL_TABLE = "analytics.stg_expense_billdetail"
PROJECT_STAFF_TABLE = "analytics.stg_project_staff"
PROJECT_TABLE = "analytics.stg_project"
PROJECT_ADDRESS_TABLE = "analytics.stg_project_address"
CHUNK_SIZE_VARIABLE = "bronze_to_silver_chunk_size"
DEFAULT_CHUNK_SIZE = 5000

SILVER_TABLE = "bill_entity"

SILVER_COLUMNS = [
    "id", "tenant_id", "boundary_code", "bill_date", "due_date", "total_amount", "total_wage_amount",
    "total_food_amount", "total_transport_amount", "total_paid_amount", "business_service", "reference_id",
    "from_period", "to_period", "payment_status", "status", "bill_number", "payer", "bill_details",
    "additional_details", "created_by", "last_modified_by", "created_time", "last_modified_time",
    "wf_status", "process_instance", "wf_status_info", "user_name", "name_of_user", "role",
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


def _fetch_enriched_bill_rows(client, bill_ids: list[str]) -> list[dict]:
    """
    Left-joins this chunk's stg_expense_bill rows with their own
    stg_expense_party row (the bill's payer) -- party has a generic
    parent_id column, not a billId field, so the join direction is the
    reverse of the usual child-has-fk-to-parent shape (same as
    pgr_transformation.py's stg_pgr_address.parent_id join).

    LIMIT 1 BY b.id guards against an unexpected multi-party match (should
    only ever be one payer per bill). b's own columns are individually
    aliased rather than `b.*` -- a single join happens not to trigger
    ClickHouse's column-qualifying behavior on this instance today, but
    would silently break (any b column colliding with party's, e.g. id,
    tenant_id, additional_details, created_by/time, last_modified_by/time)
    the moment a second join is added here, same failure mode as
    attendee_transformation.py's reported crash -- explicit aliasing
    removes that landmine proactively. FINAL is used on the joined table
    to avoid row versions from un-merged ReplacingMergeTree duplicates.
    """
    result = client.query(
        f"""
        SELECT
            b.id                  AS id,
            b.tenant_id           AS tenant_id,
            b.bill_date           AS bill_date,
            b.due_date            AS due_date,
            b.total_amount        AS total_amount,
            b.total_paid_amount   AS total_paid_amount,
            b.business_service    AS business_service,
            b.reference_id        AS reference_id,
            b.from_period         AS from_period,
            b.to_period           AS to_period,
            b.status              AS status,
            b.payment_status      AS payment_status,
            b.bill_number         AS bill_number,
            b.locality_code       AS locality_code,
            b.created_by          AS created_by,
            b.created_time        AS created_time,
            b.last_modified_by    AS last_modified_by,
            b.last_modified_time  AS last_modified_time,
            b.additional_details  AS additional_details,
            party.id                 AS payer_party_id,
            party.type               AS payer_type,
            party.identifier         AS payer_identifier,
            party.payment_provider   AS payer_payment_provider,
            party.payee_name         AS payer_name,
            party.payee_phone_number AS payer_phone_number,
            party.bank_account       AS payer_bank_account,
            party.bank_code          AS payer_bank_code,
            party.beneficiary_code   AS payer_beneficiary_code,
            party.status             AS payer_status
        FROM {BRONZE_TABLE} AS b
        LEFT JOIN {PARTY_TABLE} AS party FINAL
            ON party.parent_id = b.id AND party.tenant_id = b.tenant_id
        WHERE b.id IN %(bill_ids)s
        ORDER BY party.id ASC
        LIMIT 1 BY b.id
        """,
        parameters={"bill_ids": bill_ids},
    )
    return list(result.named_results())


def _fetch_bill_detail_ids(client, bill_ids: list[str]) -> dict[str, list[str]]:
    """
    A lightweight cross-reference for bill_entity's bill_details column --
    NOT a full nested dump of each linked BillDetail (that's what
    bill_detail_entity already owns and computes independently; see plan
    Context for why re-serializing full BillDetail objects here would be a
    pure duplication).
    """
    result = client.query(
        f"SELECT bill_id, id FROM {BILL_DETAIL_TABLE} WHERE bill_id IN %(bill_ids)s",
        parameters={"bill_ids": bill_ids},
    )
    detail_ids: dict[str, list[str]] = {bill_id: [] for bill_id in bill_ids}
    for row in result.named_results():
        detail_ids.setdefault(row["bill_id"], []).append(row["id"])
    return detail_ids


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
    """
    hierarchy_type comes from the project-staff bridge's resolved
    project_additional_details (this repo's established per-project
    convention). The CODE is the bill's own locality_code directly --
    Java's real getBoundaryHierarchyWithLocalityCode call for Bill has no
    project-id fallback branch at all, unlike attendee/attendance-log.
    """
    hierarchy_type = parse_hierarchy_type(row.get("project_additional_details"))
    if not hierarchy_type:
        return None
    code = row.get("locality_code")
    if not code:
        return None
    return row["tenant_id"], hierarchy_type, code


def _get_user_lookup_key(row: dict) -> tuple[str, str] | None:
    user_id = row.get("last_modified_by")
    if not user_id:
        return None
    return row["tenant_id"], user_id


def _get_workflow_lookup_key(row: dict) -> tuple[str, str] | None:
    bill_number = row.get("bill_number")
    if not bill_number:
        return None
    return row["tenant_id"], bill_number


def _default_str(value) -> str:
    return value if value is not None else ""


def _default_int(value) -> int:
    return 0 if value is None else int(round(value))


def _default_decimal(value):
    return value if value is not None else 0


def _build_payer_json(row: dict) -> str:
    if not row.get("payer_party_id"):
        return ""
    return json.dumps({
        "id": row.get("payer_party_id"),
        "type": row.get("payer_type"),
        "identifier": row.get("payer_identifier"),
        "paymentProvider": row.get("payer_payment_provider"),
        "payeeName": row.get("payer_name"),
        "payeePhoneNumber": row.get("payer_phone_number"),
        "bankAccount": row.get("payer_bank_account"),
        "bankCode": row.get("payer_bank_code"),
        "beneficiaryCode": row.get("payer_beneficiary_code"),
        "status": row.get("payer_status"),
    })


def _build_silver_row(row: dict, bill_detail_ids: dict[str, list[str]], workflow_summaries: dict) -> dict:
    """
    Maps one fully-enriched joined row onto bill_entity's exact column set.
    total_wage_amount/total_food_amount/total_transport_amount have no
    bronze source at all (stg_expense_bill only has total_amount/
    total_paid_amount) and default to 0. wf_status/process_instance
    approximate Java's raw-passthrough Bill.wfStatus/.processInstance
    fields (not in bronze, since they arrive pre-populated on the incoming
    payload rather than via CDC) using the SAME workflow-service call
    already made for wf_status_info -- see egov_api_utils.get_workflow_summary.
    """
    summary = workflow_summaries.get(_get_workflow_lookup_key(row)) or {}
    latest_instance = summary.get("_latestInstance")
    summary_without_instance = {k: v for k, v in summary.items() if k != "_latestInstance"}

    return {
        "id": row["id"],
        "tenant_id": _default_str(row.get("tenant_id")),
        "boundary_code": _default_str(row.get("locality_code")),
        "bill_date": _default_int(row.get("bill_date")),
        "due_date": _default_int(row.get("due_date")),
        "total_amount": _default_decimal(row.get("total_amount")),
        "total_wage_amount": 0,  # TODO: no bronze source at all
        "total_food_amount": 0,  # TODO: no bronze source at all
        "total_transport_amount": 0,  # TODO: no bronze source at all
        "total_paid_amount": _default_decimal(row.get("total_paid_amount")),
        "business_service": _default_str(row.get("business_service")),
        "reference_id": _default_str(row.get("reference_id")),
        "from_period": _default_int(row.get("from_period")),
        "to_period": _default_int(row.get("to_period")),
        "payment_status": _default_str(row.get("payment_status")),
        "status": _default_str(row.get("status")),
        "bill_number": _default_str(row.get("bill_number")),
        "payer": _build_payer_json(row),
        "bill_details": json.dumps(bill_detail_ids.get(row["id"], [])),
        "additional_details": _default_str(row.get("additional_details")),
        "created_by": _default_str(row.get("created_by")),
        "last_modified_by": _default_str(row.get("last_modified_by")),
        "created_time": _default_int(row.get("created_time")),
        "last_modified_time": _default_int(row.get("last_modified_time")),
        "wf_status": _default_str(summary.get("currentStatus")),
        "process_instance": json.dumps(latest_instance) if latest_instance else "",
        "wf_status_info": json.dumps(summary_without_instance) if summary else "",
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


def _build_silver_rows(joined_rows: list[dict], bill_detail_ids: dict, workflow_summaries: dict) -> list[dict]:
    """Builds each row independently; a malformed row is logged and
    skipped rather than failing the whole chunk's write."""
    silver_rows = []
    for row in joined_rows:
        try:
            silver_rows.append(_build_silver_row(row, bill_detail_ids, workflow_summaries))
        except Exception:
            log.exception(
                "bill: failed to build silver row for bill id=%s; skipping this row",
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
    description="Transforms bill bronze events into the bill_entity silver table.",
    schedule=None,
    start_date=pendulum.datetime(2024, 1, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    tags=["bronze-to-silver", "bill"],
)
def bill_transformation():

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
        Reads bill bronze rows for this run's window in fixed-size chunks
        via keyset pagination, transforms, and writes each chunk to
        bill_entity before moving to the next chunk.

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
            "bill bronze records ingested in [%s, %s): %d (chunk_size=%d)",
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
                "bill chunk %d: %d bill rows (cumulative %d/%d)",
                chunk_num, len(chunk), rows_seen, total,
            )

            bill_ids = [row["id"] for row in chunk]
            joined_rows = _fetch_enriched_bill_rows(client, bill_ids)
            bill_detail_ids = _fetch_bill_detail_ids(client, bill_ids)
            log.info(
                "bill chunk %d: %d rows after LEFT JOIN with party",
                chunk_num, len(joined_rows),
            )

            staff_lookup_keys = _extract_staff_lookup_keys(joined_rows)
            unique_staff_count = sum(len(user_ids) for user_ids in staff_lookup_keys.values())
            user_project_context = _resolve_user_project_context(client, staff_lookup_keys)
            _attach_project_context(joined_rows, user_project_context)
            log.info(
                "bill chunk %d: resolved project-staff bridge for %d/%d unique user(s)",
                chunk_num, len(user_project_context), unique_staff_count,
            )

            lookup_keys = extract_boundary_lookup_keys(joined_rows, _get_boundary_lookup_key)
            resolved_levels = resolve_boundary_levels(lookup_keys)
            attach_boundary_levels(joined_rows, resolved_levels, _get_boundary_lookup_key)
            log.info(
                "bill chunk %d: attached boundary hierarchy levels to %d rows",
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
                "bill chunk %d: attached user info to %d rows (%d unique user(s))",
                chunk_num, len(joined_rows), len(user_lookup_keys),
            )

            workflow_lookup_keys = extract_user_lookup_keys(joined_rows, _get_workflow_lookup_key)
            workflow_summaries = resolve_workflow_summaries(workflow_lookup_keys)
            log.info(
                "bill chunk %d: resolved workflow summaries for %d unique bill number(s)",
                chunk_num, len(workflow_lookup_keys),
            )

            silver_rows = _build_silver_rows(joined_rows, bill_detail_ids, workflow_summaries)
            _write_silver_chunk(client, silver_rows)
            log.info(
                "bill chunk %d: wrote %d/%d rows to %s",
                chunk_num, len(silver_rows), len(joined_rows), SILVER_TABLE,
            )

    transform_bronze_to_silver(parse_time_window())


bill_transformation()
