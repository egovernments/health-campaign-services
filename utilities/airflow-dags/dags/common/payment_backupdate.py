"""Payment → Demand collection back-update (ClickHouse analytics mirror of
billing-service's payment back-update).

APPROACH A — recompute-by-final-status (no ledger, no new tables)

WHY
    In billing-service, a payment adds to (payment-create) or reverses from
    (payment-cancel) each demand detail's collectionAmount. That post-payment demand
    state is not re-emitted to demand_events_raw, so demand_with_details_entity would
    otherwise never reflect what was actually collected. This task reconstructs the
    collected amounts from the payments themselves and writes corrected demand rows.

INPUT ARCHITECTURE
    payment_events_raw is fed by ONE Kafka table subscribing to BOTH
    egov.collection.payment-create AND egov.collection.payment-cancel. It is
    append-only, so every event of a payment persists (create as NEW/DEPOSITED, then
    cancel as CANCELLED, etc.). There is NO topic column, so direction (add vs
    reverse) is not known per row — we derive it from each payment's FINAL status.

HOW (idempotent, ClickHouse has no cheap in-place UPDATE)
    demand_with_details_entity is ReplacingMergeTree(last_modified_time) keyed on
    (tenant_id, demand_id). Each run:
      1. Affected demands: demands referenced by payment events IN THE WINDOW.
      2. Recompute (full history up to window end): for every payment touching an
         affected demand, keep its LATEST event (by lastModifiedTime) → that gives the
         payment's final status and its apportioned amounts
         (Payment.paymentDetails[].bill.billDetails[].billAccountDetails[].adjustedAmount).
         Count a payment ONLY if its final status is collecting (NEW/DEPOSITED/
         RECONCILED); cancelled/dishonoured contribute nothing.
      3. SET each affected demand's *_collection columns to the recomputed sums,
         recompute total_collection_amount / outstanding_amount / is_paid, bump
         last_modified_time, and INSERT a new RMT version.

    Because step 3 SETs (not adds), the task is idempotent: reruns, overlapping
    windows, and cancellations all converge to the correct value. A cancelled payment
    drops out (final status CANCELLED); a remitted payment (NEW→DEPOSITED) is counted
    once (final status DEPOSITED) — no double-count, no false reverse.

CAVEATS
  * "Final status" is derived from raw itself (latest event per payment_id), not from
    payment_with_details_entity, because that silver table only stores the first
    paymentDetail's billid and would miss multi-bill payments.
  * COST: to recompute an affected demand correctly, this scans payment_events_raw
    over full history (up to window end) — memory is bounded to affected demands'
    payments, but reads grow with payment history. If that becomes expensive, flatten
    the apportionment into a table or add a _topic column + incremental+ledger.
  * A newer demand event (from the demand pipeline) can overwrite this correction if
    it lands with a larger last_modified_time — expected RMT behaviour.
"""

import gc
import json
import logging
from decimal import Decimal
from typing import Dict, Set, Tuple

from airflow.utils.timezone import utcnow, make_aware

from common.ch_utils import (
    get_client, InsertBuffer, safe_dec, get_window, fetch_raw_events, CH_FETCH_SIZE, EPOCH,
)

logger = logging.getLogger(__name__)

DEMAND_TABLE = 'demand_with_details_entity'
PAYMENT_RAW_TABLE = 'payment_events_raw'

# A payment counts as collected money only if its FINAL status is one of these.
# Valid PaymentStatusEnum: NEW, DEPOSITED, RECONCILED, CANCELLED, DISHONOURED.
COLLECTING_STATUSES = {'NEW', 'DEPOSITED', 'RECONCILED'}

# Read-side batch size for the demand_id IN (...) lookup.
DEMAND_LOOKUP_CHUNK = 5000


def _collection_col(tax_head_code: str) -> str:
    """Map a tax-head code to its collection column, e.g. PT_TAX -> pt_tax_collection.

    Matches the naming used by extract_demand() in the raw-to-silver DAG.
    """
    return tax_head_code.lower() + '_collection'


def _heads_for_affected(payment: dict, affected: Set[Tuple[str, str]]) -> Dict[Tuple[str, str], Dict[str, Decimal]]:
    """Per-(tenant_id, demand_id) apportioned collection carried by this payment,
    restricted to demands in `affected`. Uses adjustedAmount (what billing-service
    applies to collectionAmount), falling back to amount.
    """
    heads: Dict[Tuple[str, str], Dict[str, Decimal]] = {}
    for pd in (payment.get('paymentDetails', []) or []):
        bill = pd.get('bill', {}) or {}
        tenant = bill.get('tenantId') or payment.get('tenantId', '')
        for bd in (bill.get('billDetails', []) or []):
            demand_id = bd.get('demandId', '')
            key = (tenant, demand_id)
            if key not in affected:
                continue
            dst = heads.setdefault(key, {})
            for bad in (bd.get('billAccountDetails', []) or []):
                code = bad.get('taxHeadCode', '')
                if not code:
                    continue
                amt = bad.get('adjustedAmount')
                if amt is None:
                    amt = bad.get('amount')
                col = _collection_col(code)
                dst[col] = dst.get(col, Decimal('0')) + safe_dec(amt, 4)
    return heads


def _affected_demands_in_window(client, ws, we) -> Set[Tuple[str, str]]:
    """Demands referenced by payment events in [ws, we)."""
    affected: Set[Tuple[str, str]] = set()
    last_ms = None
    last_id = None

    while True:
        rows = fetch_raw_events(client, PAYMENT_RAW_TABLE, ws, we,
                                limit=CH_FETCH_SIZE, last_ms=last_ms, last_id=last_id)
        if not rows:
            break
        for raw_json, _et, _id, _ms in rows:
            try:
                payment = (json.loads(raw_json).get('Payment') or {})
            except json.JSONDecodeError:
                continue
            for pd in (payment.get('paymentDetails', []) or []):
                bill = pd.get('bill', {}) or {}
                tenant = bill.get('tenantId') or payment.get('tenantId', '')
                for bd in (bill.get('billDetails', []) or []):
                    demand_id = bd.get('demandId', '')
                    if demand_id:
                        affected.add((tenant, demand_id))
        chunk_len = len(rows)
        last_ms, last_id = rows[-1][3], rows[-1][2]
        del rows
        gc.collect()
        if chunk_len < CH_FETCH_SIZE:
            break

    return affected


def _recompute_collection(client, we, affected: Set[Tuple[str, str]]) -> Dict[Tuple[str, str], Dict[str, Decimal]]:
    """Recompute collection per affected demand from full payment history up to `we`.

    Keeps the LATEST event per payment_id (final status + apportionment); counts a
    payment only if its final status is collecting.
    """
    # payment_id -> {'lmt': int, 'status': str, 'heads': {(tenant, demand): {col: Decimal}}}
    latest: Dict[str, dict] = {}
    last_ms = None
    last_id = None

    while True:
        rows = fetch_raw_events(client, PAYMENT_RAW_TABLE, EPOCH, we,
                                limit=CH_FETCH_SIZE, last_ms=last_ms, last_id=last_id)
        if not rows:
            break
        for raw_json, _et, _id, _ms in rows:
            try:
                payment = (json.loads(raw_json).get('Payment') or {})
            except json.JSONDecodeError:
                continue
            pid = payment.get('id', '')
            if not pid:
                continue
            heads = _heads_for_affected(payment, affected)
            if not heads:
                continue  # this payment doesn't touch any affected demand

            audit = payment.get('auditDetails', {}) or {}
            try:
                lmt = int(audit.get('lastModifiedTime'))
            except (TypeError, ValueError):
                lmt = int(_ms)

            prev = latest.get(pid)
            if prev is None or lmt >= prev['lmt']:
                latest[pid] = {
                    'lmt': lmt,
                    'status': str(payment.get('paymentStatus', '')).upper(),
                    'heads': heads,
                }
        chunk_len = len(rows)
        last_ms, last_id = rows[-1][3], rows[-1][2]
        del rows
        gc.collect()
        if chunk_len < CH_FETCH_SIZE:
            break

    # Aggregate: count only payments whose final status is collecting.
    sums: Dict[Tuple[str, str], Dict[str, Decimal]] = {}
    for info in latest.values():
        if info['status'] not in COLLECTING_STATUSES:
            continue
        for key, cols in info['heads'].items():
            dst = sums.setdefault(key, {})
            for col, amt in cols.items():
                dst[col] = dst.get(col, Decimal('0')) + amt
    return sums


def _write_new_versions(client, affected: Set[Tuple[str, str]],
                        sums: Dict[Tuple[str, str], Dict[str, Decimal]]) -> dict:
    """Read affected demands (FINAL), SET their collection to the recomputed sums,
    recompute derived fields, and insert new RMT versions.
    """
    demand_ids = sorted({demand_id for (_tenant, demand_id) in affected})

    current = {}
    columns = None
    for i in range(0, len(demand_ids), DEMAND_LOOKUP_CHUNK):
        batch = demand_ids[i:i + DEMAND_LOOKUP_CHUNK]
        res = client.query(
            f"SELECT * FROM {DEMAND_TABLE} FINAL WHERE demand_id IN {{ids:Array(String)}}",
            parameters={'ids': batch},
        )
        columns = res.column_names
        for row in res.result_rows:
            d = dict(zip(columns, row))
            current[(d.get('tenant_id'), d.get('demand_id'))] = d

    if not columns:
        logger.warning("No matching demands found in silver — nothing to write")
        return {'demands_recomputed': 0, 'demands_not_found': len(affected)}

    collection_cols = [c for c in columns if c.endswith('_collection')]
    now = make_aware(utcnow())

    out_buf = InsertBuffer(client, DEMAND_TABLE)
    updated = 0
    not_found = 0

    for key in affected:
        row = current.get(key)
        if row is None:
            not_found += 1
            continue

        heads = sums.get(key, {})          # empty → all collection reset to 0
        new_row = dict(row)

        # SET each known per-tax-head collection column from the recompute.
        for col in collection_cols:
            new_row[col] = safe_dec(heads.get(col, Decimal('0')), 4)

        # Total from ALL collected heads (covers any tax head without a column).
        total_collection = round(sum((safe_dec(v, 4) for v in heads.values()), Decimal('0')), 2)
        total_tax = safe_dec(new_row.get('total_tax_amount'), 2)
        outstanding = round(total_tax - total_collection, 2)

        new_row['total_collection_amount'] = total_collection
        new_row['outstanding_amount'] = outstanding
        new_row['is_paid'] = 1 if outstanding <= 0 else 0
        new_row['last_modified_time'] = now   # bump version so RMT keeps this row

        out_buf.add([{c: new_row.get(c) for c in columns}])
        updated += 1

    out_buf.flush()
    return {'demands_recomputed': updated, 'demands_not_found': not_found}


def back_update_demand_collection(**context):
    """Airflow entrypoint: recompute affected demands' collection from payments
    (Approach A) and write corrected demand versions.
    """
    ws, we = get_window(context)
    logger.info(f"Back-update window: [{ws}, {we})")

    client = get_client()
    try:
        affected = _affected_demands_in_window(client, ws, we)
        if not affected:
            logger.info("No demands touched by payments in window — nothing to back-update")
            return {'demands_recomputed': 0, 'demands_not_found': 0}
        logger.info(f"{len(affected)} demand(s) affected by payments in window")

        sums = _recompute_collection(client, we, affected)
        logger.info(f"Recomputed collection for {len(sums)} demand(s) with collecting payments")

        result = _write_new_versions(client, affected, sums)
        logger.info(f"Back-update complete: {result}")
        return result
    finally:
        client.close()
