"""
Property Tax Raw-to-Silver DAG (orjson — Airflow-side processing)

All JSON parsing and transformation runs inside the Airflow worker using
orjson (C extension, ~10x faster than stdlib json). ClickHouse only does
simple SELECT reads and column-based INSERTs — near-zero CPU/RAM usage.

Architecture:
  Airflow reads raw JSON strings from ClickHouse (streaming) →
  orjson parses each JSON blob (C-speed) →
  Python extracts and transforms fields →
  Airflow inserts structured rows back to ClickHouse

Resource profile (3M properties + 39M demands):
  ClickHouse CPU:  ~10-20%  (just I/O — streaming reads + column writes)
  ClickHouse RAM:  ~200-500 MB
  Airflow CPU:     ~80% of 1 core  (orjson + Python field extraction)
  Airflow RAM:     ~500 MB - 1 GB  (one chunk at a time)
  Speed:           ~15-30 min total (comparable to INSERT...SELECT)
"""

import os
import logging
from datetime import datetime, timedelta, timezone, date

from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.operators.empty import EmptyOperator
from airflow.operators.trigger_dagrun import TriggerDagRunOperator
from airflow.utils.timezone import utcnow, make_aware
import clickhouse_connect

try:
    import orjson
    def parse_json(s):
        return orjson.loads(s)
except ImportError:
    import json
    logging.getLogger(__name__).warning(
        "orjson not installed, falling back to stdlib json (3-10x slower). "
        "Install with: pip install orjson"
    )
    def parse_json(s):
        return json.loads(s)

logger = logging.getLogger(__name__)

# -- Configuration -----------------------------------------------------------

CLICKHOUSE_HOST = os.getenv('CLICKHOUSE_HOST', 'clickstack-clickhouse.clickhouse.svc.cluster.local')
CLICKHOUSE_PORT = int(os.getenv('CLICKHOUSE_PORT', '8123'))
CLICKHOUSE_USER = os.getenv('CLICKHOUSE_USER', 'default')
CLICKHOUSE_PASSWORD = os.getenv('CLICKHOUSE_PASSWORD', '')
CLICKHOUSE_DB = os.getenv('CLICKHOUSE_DB', 'airflow_test')

# Rows per streaming block from ClickHouse
BLOCK_SIZE = int(os.getenv('BLOCK_SIZE', '100000'))

EPOCH_DT = datetime(1970, 1, 1, tzinfo=timezone.utc)
EPOCH_DATE = date(1970, 1, 1)

default_args = {
    'owner': 'property_tax',
    'depends_on_past': False,
    'email_on_failure': False,
    'retries': 2,
    'retry_delay': timedelta(minutes=3),
}

# -- Helpers -----------------------------------------------------------------


def get_client():
    return clickhouse_connect.get_client(
        host=CLICKHOUSE_HOST,
        port=CLICKHOUSE_PORT,
        username=CLICKHOUSE_USER,
        password=CLICKHOUSE_PASSWORD,
        database=CLICKHOUSE_DB,
        send_receive_timeout=7200,
    )


def get_window(context):
    dag_run = context.get("dag_run")
    if dag_run and dag_run.run_type == "scheduled":
        return dag_run.data_interval_start, dag_run.data_interval_end
    end_time = utcnow()
    start_time = end_time - timedelta(hours=24)
    logger.info(f"Manual window: [{start_time}, {end_time})")
    return start_time, end_time


def ms_to_dt(ms):
    if ms and ms > 0:
        return datetime.fromtimestamp(ms / 1000, tz=timezone.utc)
    return EPOCH_DT


def ms_to_date(ms):
    if ms and ms > 0:
        return datetime.fromtimestamp(ms / 1000, tz=timezone.utc).date()
    return EPOCH_DATE


def compute_fy(explicit_fy, tax_period_from_ms):
    if explicit_fy:
        return explicit_fy
    if tax_period_from_ms and tax_period_from_ms > 0:
        dt = datetime.fromtimestamp(tax_period_from_ms / 1000, tz=timezone.utc)
        if dt.month >= 4:
            return f"{dt.year}-{(dt.year + 1) % 100:02d}"
        return f"{dt.year - 1}-{dt.year % 100:02d}"
    return ''


def get_count(client, table, ws, we):
    return client.query(
        f"SELECT count() FROM {table} "
        "WHERE event_time >= {s:DateTime64(3)} AND event_time < {e:DateTime64(3)}",
        parameters={'s': ws, 'e': we},
    ).result_rows[0][0]


# -- Column definitions ------------------------------------------------------

ADDRESS_COLS = [
    '_ingested_at', 'id', 'tenant_id', 'property_id', 'survey_id',
    'account_id', 'old_property_id', 'property_type', 'usage_category',
    'ownership_category', 'status', 'acknowledgement_number', 'creation_reason',
    'no_of_floors', 'source', 'channel', 'land_area', 'super_built_up_area',
    'created_by', 'created_time', 'last_modified_by', 'last_modified_time',
    'additionaldetails', 'door_no', 'plot_no', 'building_name', 'street',
    'landmark', 'locality', 'city', 'district', 'region', 'state',
    'country', 'pin_code', 'latitude', 'longitude',
]

UNIT_COLS = [
    '_ingested_at', 'tenant_id', 'property_uuid', 'unit_id', 'floor_no',
    'unit_type', 'usage_category', 'occupancy_type', 'occupancy_date',
    'carpet_area', 'built_up_area', 'plinth_area', 'super_built_up_area',
    'arv', 'construction_type', 'construction_date', 'active',
    'created_by', 'created_time', 'last_modified_by', 'last_modified_time',
    'property_id', 'property_type', 'ownership_category', 'property_status',
    'no_of_floors',
]

OWNER_COLS = [
    '_ingested_at', 'tenant_id', 'property_uuid', 'owner_info_uuid', 'user_id',
    'status', 'is_primary_owner', 'owner_type', 'ownership_percentage',
    'institution_id', 'relationship', 'created_by', 'created_time',
    'last_modified_by', 'last_modified_time', 'property_id', 'property_type',
    'ownership_category', 'property_status', 'no_of_floors',
]

DEMAND_COLS = [
    '_ingested_at', 'tenant_id', 'demand_id', 'consumer_code', 'consumer_type',
    'business_service', 'payer', 'tax_period_from', 'tax_period_to',
    'demand_status', 'is_payment_completed', 'financial_year',
    'minimum_amount_payable', 'bill_expiry_time', 'fixed_bill_expiry_date',
    'total_tax_amount', 'total_collection_amount',
    'pt_tax', 'pt_cancer_cess', 'pt_fire_cess', 'pt_roundoff',
    'pt_owner_exemption', 'pt_unit_usage_exemption',
    'outstanding_amount', 'is_paid',
    'created_by', 'created_time', 'last_modified_by', 'last_modified_time',
]

# -- Property chunk processor ------------------------------------------------


def _process_property_block(write_client, raw_strings):
    """Parse a block of property JSON strings → insert into 3 silver tables."""
    now = datetime.now(timezone.utc)
    addr_rows, unit_rows, owner_rows = [], [], []

    for raw in raw_strings:
        data = parse_json(raw)
        prop = data.get('property', {})
        pid = prop.get('propertyId', '')
        if not pid:
            continue

        tid = data.get('tenantId', '')
        addr = prop.get('address', {})
        audit = prop.get('auditDetails', {})
        ct = ms_to_dt(audit.get('createdTime', 0))
        lmt = ms_to_dt(audit.get('lastModifiedTime', 0))

        ad = prop.get('additionalDetails')
        ad_str = ''
        if ad:
            try:
                ad_str = orjson.dumps(ad).decode()
            except Exception:
                import json
                ad_str = json.dumps(ad)

        addr_rows.append((
            now, prop.get('id', ''), tid, pid,
            prop.get('surveyId', ''), prop.get('accountId', ''),
            prop.get('oldPropertyId', ''), prop.get('propertyType', ''),
            prop.get('usageCategory', ''), prop.get('ownershipCategory', ''),
            prop.get('status', ''), prop.get('acknowledgementNumber', ''),
            prop.get('creationReason', ''), int(prop.get('noOfFloors', 0)),
            prop.get('source', ''), prop.get('channel', ''),
            round(float(prop.get('landArea', 0)), 2),
            round(float(prop.get('superBuiltUpArea', 0)), 2),
            audit.get('createdBy', ''), ct,
            audit.get('lastModifiedBy', ''), lmt,
            ad_str,
            addr.get('doorNo', ''), addr.get('plotNo', ''),
            addr.get('buildingName', ''), addr.get('street', ''),
            addr.get('landmark', ''), addr.get('locality', ''),
            addr.get('city', ''), addr.get('district', ''),
            addr.get('region', ''), addr.get('state', ''),
            addr.get('country', '') or 'IN', addr.get('pinCode', ''),
            round(float(addr.get('latitude', 0)), 6),
            round(float(addr.get('longitude', 0)), 7),
        ))

        # Shared property-level fields for unit/owner rows
        puuid = prop.get('id', '')
        ptype = prop.get('propertyType', '')
        ocat = prop.get('ownershipCategory', '')
        pstatus = prop.get('status', '')
        nfloors = int(prop.get('noOfFloors', 0))

        # Units (equivalent to ARRAY JOIN)
        for u in prop.get('units', []):
            uid = u.get('id', '')
            if not uid:
                continue
            ua = u.get('auditDetails', {})
            unit_rows.append((
                now, tid, puuid, uid,
                int(u.get('floorNo', 0)), u.get('unitType', ''),
                u.get('usageCategory', ''), u.get('occupancyType', ''),
                ms_to_date(u.get('occupancyDate', 0)),
                round(float(u.get('carpetArea', 0)), 2),
                round(float(u.get('builtUpArea', 0)), 2),
                round(float(u.get('plinthArea', 0)), 2),
                round(float(u.get('superBuiltUpArea', 0)), 2),
                round(float(u.get('arv', 0)), 2),
                u.get('constructionType', ''),
                int(u.get('constructionDate', 0)),
                1 if u.get('active', True) else 0,
                ua.get('createdBy', ''),
                ms_to_dt(ua.get('createdTime', 0)),
                ua.get('lastModifiedBy', ''),
                ms_to_dt(ua.get('lastModifiedTime', 0)),
                pid, ptype, ocat, pstatus, nfloors,
            ))

        # Owners (equivalent to ARRAY JOIN)
        for o in prop.get('owners', []):
            oiuuid = o.get('ownerInfoUuid', '')
            if not oiuuid:
                continue
            oa = o.get('auditDetails', {})
            owner_rows.append((
                now, tid, puuid, oiuuid,
                o.get('userId', ''), o.get('status', ''),
                1 if o.get('isPrimaryOwner', False) else 0,
                o.get('ownerType', ''),
                str(o.get('ownershipPercentage', '')),
                o.get('institutionId', ''), o.get('relationship', ''),
                oa.get('createdBy', ''),
                ms_to_dt(oa.get('createdTime', 0)),
                oa.get('lastModifiedBy', ''),
                ms_to_dt(oa.get('lastModifiedTime', 0)),
                pid, ptype, ocat, pstatus, nfloors,
            ))

    if addr_rows:
        write_client.insert('property_address_entity', addr_rows, column_names=ADDRESS_COLS)
    if unit_rows:
        write_client.insert('property_unit_entity', unit_rows, column_names=UNIT_COLS)
    if owner_rows:
        write_client.insert('property_owner_entity', owner_rows, column_names=OWNER_COLS)

    return len(addr_rows), len(unit_rows), len(owner_rows)


# -- Demand chunk processor --------------------------------------------------


def _process_demand_block(write_client, raw_strings):
    """Parse a block of demand JSON strings → insert into demand silver table."""
    now = datetime.now(timezone.utc)
    rows = []

    for raw in raw_strings:
        data = parse_json(raw)
        demand = data.get('demand', {})
        did = demand.get('id', '')
        if not did:
            continue

        tid = data.get('tenantId', '')
        audit = demand.get('auditDetails', {})
        details = demand.get('demandDetails', [])
        tpf_ms = demand.get('taxPeriodFrom', 0)

        # Single pass over demandDetails to compute all aggregates
        total_tax = total_col = 0.0
        pt_tax = pt_cc = pt_fc = pt_ro = pt_oe = pt_uue = 0.0

        for d in details:
            amt = float(d.get('taxAmount', 0))
            col = float(d.get('collectionAmount', 0))
            total_tax += amt
            total_col += col

            code = d.get('taxHeadCode', '')
            if code == 'PT_TAX':
                pt_tax += amt
            elif code == 'PT_CANCER_CESS':
                pt_cc += amt
            elif code == 'PT_FIRE_CESS':
                pt_fc += amt
            elif code == 'PT_ROUNDOFF':
                pt_ro += amt
            elif code == 'PT_OWNER_EXEMPTION':
                pt_oe += amt
            elif code == 'PT_UNIT_USAGE_EXEMPTION':
                pt_uue += amt

        outstanding = round(total_tax - total_col, 2)

        rows.append((
            now, tid, did,
            demand.get('consumerCode', ''), demand.get('consumerType', ''),
            demand.get('businessService', ''), demand.get('payer', ''),
            ms_to_dt(tpf_ms),
            ms_to_dt(demand.get('taxPeriodTo', 0)),
            demand.get('status', ''),
            1 if demand.get('isPaymentCompleted', False) else 0,
            compute_fy(demand.get('financialYear', ''), tpf_ms),
            round(float(demand.get('minimumAmountPayable', 0)), 4),
            int(demand.get('billExpiryTime', 0)),
            int(demand.get('fixedBillExpiryDate', 0)),
            round(total_tax, 2), round(total_col, 2),
            round(pt_tax, 4), round(pt_cc, 4),
            round(pt_fc, 4), round(pt_ro, 4),
            round(pt_oe, 4), round(pt_uue, 4),
            outstanding, 1 if outstanding <= 0 else 0,
            audit.get('createdBy', ''),
            ms_to_dt(audit.get('createdTime', 0)),
            audit.get('lastModifiedBy', ''),
            ms_to_dt(audit.get('lastModifiedTime', 0)),
        ))

    if rows:
        write_client.insert('demand_with_details_entity', rows, column_names=DEMAND_COLS)

    return len(rows)


# -- Task functions -----------------------------------------------------------


def process_property_events(**context):
    """Stream property_events_raw → parse with orjson → insert 3 silver tables."""
    window_start, window_end = get_window(context)
    logger.info(f"Property window: [{window_start}, {window_end})")

    read_client = get_client()
    write_client = get_client()

    try:
        total = get_count(read_client, 'property_events_raw', window_start, window_end)
        if total == 0:
            logger.info("No property events in window")
            return {'address_rows': 0, 'unit_rows': 0, 'owner_rows': 0}

        logger.info(f"Streaming {total} property events (block_size={BLOCK_SIZE})")

        tot_a = tot_u = tot_o = 0
        processed = 0
        chunk = 0

        with read_client.query_column_block_stream(
            "SELECT raw FROM property_events_raw "
            "WHERE event_time >= {s:DateTime64(3)} AND event_time < {e:DateTime64(3)}",
            parameters={'s': window_start, 'e': window_end},
            settings={'max_block_size': BLOCK_SIZE},
        ) as stream:
            for block in stream:
                raw_strings = block[0]
                chunk += 1
                a, u, o = _process_property_block(write_client, raw_strings)
                tot_a += a
                tot_u += u
                tot_o += o
                processed += len(raw_strings)
                logger.info(
                    f"[property] Chunk {chunk}: {len(raw_strings)} events → "
                    f"{a} addr, {u} units, {o} owners ({processed}/{total})"
                )

        result = {'address_rows': tot_a, 'unit_rows': tot_u, 'owner_rows': tot_o}
        logger.info(f"Property complete: {result}")
        return result
    finally:
        read_client.close()
        write_client.close()


def process_demand_events(**context):
    """Stream demand_events_raw → parse with orjson → insert demand silver table."""
    window_start, window_end = get_window(context)
    logger.info(f"Demand window: [{window_start}, {window_end})")

    read_client = get_client()
    write_client = get_client()

    try:
        total = get_count(read_client, 'demand_events_raw', window_start, window_end)
        if total == 0:
            logger.info("No demand events in window")
            return {'demands': 0}

        logger.info(f"Streaming {total} demand events (block_size={BLOCK_SIZE})")

        tot_d = 0
        processed = 0
        chunk = 0

        with read_client.query_column_block_stream(
            "SELECT raw FROM demand_events_raw "
            "WHERE event_time >= {s:DateTime64(3)} AND event_time < {e:DateTime64(3)}",
            parameters={'s': window_start, 'e': window_end},
            settings={'max_block_size': BLOCK_SIZE},
        ) as stream:
            for block in stream:
                raw_strings = block[0]
                chunk += 1
                d = _process_demand_block(write_client, raw_strings)
                tot_d += d
                processed += len(raw_strings)
                logger.info(
                    f"[demand] Chunk {chunk}: {len(raw_strings)} events → "
                    f"{d} demands ({processed}/{total})"
                )

        logger.info(f"Demand complete: {tot_d} records")
        return {'demands': tot_d}
    finally:
        read_client.close()
        write_client.close()


# -- DAG definition -----------------------------------------------------------

with DAG(
    'property_tax_raw_to_silver_orjson',
    default_args=default_args,
    description='Raw JSON -> silver tables (orjson parsing in Airflow, low CH usage)',
    schedule='30 1 * * *',
    start_date=make_aware(datetime(2025, 1, 1)),
    catchup=False,
    tags=['property_tax', 'clickhouse', 'raw_to_silver', 'orjson'],
    max_active_runs=1,
) as dag:

    start = EmptyOperator(task_id='start')

    process_properties = PythonOperator(
        task_id='process_property_events',
        python_callable=process_property_events,
    )

    process_demands = PythonOperator(
        task_id='process_demand_events',
        python_callable=process_demand_events,
    )

    trigger_rmv_refresh = TriggerDagRunOperator(
        task_id='trigger_rmv_refresh',
        trigger_dag_id='clickhouse_rmv_sequential_refresh',
        wait_for_completion=False,
    )

    end = EmptyOperator(task_id='end')

    start >> [process_properties, process_demands] >> trigger_rmv_refresh >> end
