"""Payment → Demand collection back-update — ClickHouse-native (set-based).

Rewritten to run as a single INSERT ... SELECT inside ClickHouse so nothing large
is held in the Airflow worker. (The earlier Python version materialised ~1.3M
demands + ~1M payment apportionments in RAM and was OOM-killed at 1M scale.)

Logic (idempotent, recompute-by-final-status; uses ONLY silver tables):
  * For every demand that has a payment, collected = Σ of the demand's paid share
    (bill_detail_entity.amount_paid) over payments whose FINAL status is collecting
    (NEW / DEPOSITED / RECONCILED). Cancelled/dishonoured contribute 0 — so a demand
    whose payment is later cancelled is reversed to 0.
  * total_collection_amount / outstanding_amount / is_paid are EXACT (from amount_paid).
  * Per-tax-head *_collection columns are the demand's tax split scaled by
    collected / total_tax (proportional). Exact for proportional apportionment
    (matches the generators and full payments); an approximation only if the source
    ever apportions non-proportionally.
  * A new ReplacingMergeTree version is written with last_modified_time = now64(3),
    so it wins over the demand event; FINAL/merge keeps it.

No raw-JSON parsing, no Python memory — scales to millions. The payment's final
status is resolved by reading payment_with_details_entity FINAL (RMT dedup), so a
create(NEW)+cancel(CANCELLED) pair for one payment collapses to CANCELLED.
"""

import logging

from common.ch_utils import get_client, CLICKHOUSE_DB

logger = logging.getLogger(__name__)

DEMAND_TABLE = 'demand_with_details_entity'

# Demand attribute columns copied through unchanged (in table order), split around
# the amount/collection columns we recompute.
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

# Statuses that count as money collected (final status).
COLLECTING = "('NEW','DEPOSITED','RECONCILED')"


def _collected_subquery(db: str) -> str:
    """Per (tenant_id, demand_id): collected = Σ paid share over collecting payments.

    bill_detail rows are deduped per (tenant, demand, bill) first (a create+cancel
    pair for one payment yields two identical detail rows with the same amount_paid),
    then joined to the payment's FINAL status.
    """
    return f"""
(
    SELECT b.tenant_id AS tenant_id, b.demand_id AS demand_id,
           sumIf(b.amount_paid, upper(p.payment_status) IN {COLLECTING}) AS collected
    FROM
    (
        SELECT tenant_id, demand_id, bill_id, any(amount_paid) AS amount_paid
        FROM {db}.bill_detail_entity FINAL
        GROUP BY tenant_id, demand_id, bill_id
    ) AS b
    INNER JOIN {db}.payment_with_details_entity AS p FINAL ON p.billid = b.bill_id
    GROUP BY b.tenant_id, b.demand_id
)
"""


def _build_insert_sql(db: str) -> str:
    t = f"{db}.{DEMAND_TABLE}"
    # Ratio math in Float64 to avoid Decimal overflow; the INSERT casts the Float64
    # results back to the Decimal(18,4) collection columns.
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
        f"INNER JOIN {_collected_subquery(db)} AS pd\n"
        f"ON d.tenant_id = pd.tenant_id AND d.demand_id = pd.demand_id"
    )


def back_update_demand_collection(**context):
    """Airflow entrypoint: recompute demand collection from payments entirely in
    ClickHouse (set-based INSERT ... SELECT). Idempotent; safe to re-run.
    """
    client = get_client()
    try:
        db = CLICKHOUSE_DB
        logger.info("Running ClickHouse-native demand back-update (set-based INSERT..SELECT)")
        client.command(_build_insert_sql(db))

        res = client.query(f"SELECT count() FROM {_collected_subquery(db)} AS pd")
        n = res.result_rows[0][0] if res.result_rows else 0
        logger.info(f"Back-update complete: {n} demand(s) recomputed")
        return {'demands_recomputed': n}
    finally:
        client.close()
