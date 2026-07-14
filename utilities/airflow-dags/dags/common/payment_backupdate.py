"""Payment → Demand collection back-update — ClickHouse-native, window-scoped.

Runs as a single INSERT ... SELECT inside ClickHouse (nothing large held in the
Airflow worker; the Python version OOM-killed at 1M). Each run recomputes ONLY the
demands whose payments landed in the current execution window — not the whole table.

Flow (idempotent, recompute-by-final-status; silver tables only):
  1. affected demands = demands referenced by payments whose raw event_time is in
     [window_start, window_end). Resolved cheaply: shallow-parse Payment.id from
     payment_events_raw in the window → payment silver (billid) → bill_detail (demand).
  2. For each affected demand, collected = Σ of its paid share
     (bill_detail_entity.amount_paid) over payments whose FINAL status is collecting
     (NEW/DEPOSITED/RECONCILED). Cancelled/dishonoured contribute 0 → reversal.
  3. Write a new ReplacingMergeTree version (last_modified_time = now64(3)) for the
     affected demands only.

Notes:
  * total_collection_amount / outstanding_amount / is_paid are EXACT (from amount_paid).
  * Per-tax-head *_collection is the demand's tax split scaled by collected/total_tax
    (proportional — exact for proportional/full payments; matches the generators).
  * Ratio math is done in Float64 to avoid Decimal overflow; the INSERT casts back.
  * On a fresh full load (all payments in one window) "affected" = all demands, so it
    is a full recompute; the window scoping pays off on incremental/scheduled runs.
"""

import logging
from datetime import timezone

from common.ch_utils import get_client, get_window, CLICKHOUSE_DB

logger = logging.getLogger(__name__)

DEMAND_TABLE = 'demand_with_details_entity'

_ATTR_HEAD = [
    '_ingested_at', 'tenant_id', 'demand_id', 'consumer_code', 'consumer_type',
    'business_service', 'payer', 'tax_period_from', 'tax_period_to', 'demand_status',
    'financial_year', 'minimum_amount_payable', 'bill_expiry_time', 'fixed_bill_expiry_date',
]
_TAX_COLS = [
    'pt_tax', 'pt_cancer_cess', 'pt_fire_cess', 'pt_roundoff', 'pt_owner_exemption',
    'pt_unit_usage_exemption', 'pt_advance_carryforward', 'pt_decimal_ceiling_debit',
    'pt_time_rebate', 'pt_decimal_ceiling_credit', 'pt_time_penalty', 'pt_adhoc_penalty',
    'pt_adhoc_rebate', 'pt_time_interest',
]
_ATTR_TAIL = ['created_by', 'created_time', 'last_modified_by']

COLLECTING = "('NEW','DEPOSITED','RECONCILED')"


def _affected_in_clause(db: str, ws: str, we: str) -> str:
    """`(tenant_id, demand_id) IN (...)` — demands referenced by payments whose raw
    event landed in [ws, we). Uses a shallow Payment.id parse + silver joins (no deep
    JSON parsing).
    """
    return (
        "(tenant_id, demand_id) IN ("
        f"SELECT tenant_id, demand_id FROM {db}.bill_detail_entity FINAL "
        "WHERE bill_id IN ("
        f"SELECT billid FROM {db}.payment_with_details_entity FINAL "
        "WHERE payment_id IN ("
        f"SELECT JSONExtractString(raw, 'Payment', 'id') FROM {db}.payment_events_raw "
        f"WHERE event_time >= toDateTime64('{ws}', 3) AND event_time < toDateTime64('{we}', 3)"
        ")))"
    )


def _collected_subquery(db: str, affected: str) -> str:
    """Per affected (tenant_id, demand_id): collected = Σ paid share over collecting
    payments. bill_detail rows are deduped per (tenant, demand, bill) first.
    """
    return f"""
(
    SELECT b.tenant_id AS tenant_id, b.demand_id AS demand_id,
           sumIf(b.amount_paid, upper(p.payment_status) IN {COLLECTING}) AS collected
    FROM
    (
        -- One row per (tenant, demand, bill); take the LATEST version's amount_paid
        -- (argMax on last_modified_time) — deterministic, unlike any().
        SELECT tenant_id, demand_id, bill_id,
               argMax(amount_paid, last_modified_time) AS amount_paid
        FROM {db}.bill_detail_entity FINAL
        WHERE {affected}
        GROUP BY tenant_id, demand_id, bill_id
    ) AS b
    INNER JOIN {db}.payment_with_details_entity AS p FINAL ON p.billid = b.bill_id
    GROUP BY b.tenant_id, b.demand_id
)
"""


def _build_insert_sql(db: str, affected: str) -> str:
    t = f"{db}.{DEMAND_TABLE}"
    # Ratio math in Float64 to avoid Decimal overflow; the INSERT casts back to Decimal.
    ratio = "if(d.total_tax_amount != 0, toFloat64(pd.collected) / toFloat64(d.total_tax_amount), 0)"

    select_cols = []
    select_cols += [f"d.{c}" for c in _ATTR_HEAD]
    select_cols.append("d.total_tax_amount")
    select_cols.append("round(pd.collected, 2) AS total_collection_amount")
    select_cols += [f"d.{c}" for c in _TAX_COLS]
    select_cols += [f"round(toFloat64(d.{c}) * {ratio}, 4) AS {c}_collection" for c in _TAX_COLS]
    select_cols.append("round(d.total_tax_amount - pd.collected, 4) AS outstanding_amount")
    select_cols.append("if(d.total_tax_amount - pd.collected <= 0, 1, 0) AS is_paid")
    select_cols += [f"d.{c}" for c in _ATTR_TAIL]
    select_cols.append("now64(3) AS last_modified_time")

    insert_cols = (
        _ATTR_HEAD + ['total_tax_amount', 'total_collection_amount']
        + _TAX_COLS + [f"{c}_collection" for c in _TAX_COLS]
        + ['outstanding_amount', 'is_paid'] + _ATTR_TAIL + ['last_modified_time']
    )

    return (
        f"INSERT INTO {t} ({', '.join(insert_cols)})\n"
        f"SELECT {', '.join(select_cols)}\n"
        f"FROM {t} AS d FINAL\n"
        f"INNER JOIN {_collected_subquery(db, affected)} AS pd\n"
        f"ON d.tenant_id = pd.tenant_id AND d.demand_id = pd.demand_id"
    )


def _fmt(dt) -> str:
    """Format a (tz-aware) datetime as a UTC 'YYYY-MM-DD HH:MM:SS.mmm' literal."""
    return dt.astimezone(timezone.utc).strftime('%Y-%m-%d %H:%M:%S.%f')[:-3]


def back_update_demand_collection(**context):
    """Airflow entrypoint: recompute collection for demands affected in the current
    window, entirely in ClickHouse (set-based). Idempotent; safe to re-run.
    """
    ws, we = get_window(context)
    ws_s, we_s = _fmt(ws), _fmt(we)

    client = get_client()
    try:
        db = CLICKHOUSE_DB
        affected = _affected_in_clause(db, ws_s, we_s)
        logger.info(f"Back-update window [{ws_s}, {we_s}) — recomputing affected demands only")

        client.command(_build_insert_sql(db, affected))

        res = client.query(f"SELECT count() FROM {_collected_subquery(db, affected)} AS pd")
        n = res.result_rows[0][0] if res.result_rows else 0
        logger.info(f"Back-update complete: {n} affected demand(s) recomputed")
        return {'demands_recomputed': n}
    finally:
        client.close()
