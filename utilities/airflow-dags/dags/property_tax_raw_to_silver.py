"""
Property Tax Raw-to-Silver DAG

Reads raw JSON events from ClickHouse raw tables, parses JSON,
and inserts into silver tables using ReplacingMergeTree.

Schedule: Daily at 1:30 AM
Window:   data_interval_start .. data_interval_end (by event_time column)

Architecture (Dynamic Task Mapping — separate E/T/L per chunk):
  Property pipeline (runs in parallel with Demand pipeline):
    extract_property_chunks  (count rows, emit chunk offsets)
        -> transform_property_chunk  [mapped per chunk]  (fetch + parse + extract)
            -> load_property_chunk  [mapped per chunk]  (insert into silver tables)
                -> property_address_entity
                -> property_unit_entity
                -> property_owner_entity

  Demand pipeline:
    extract_demand_chunks  (count rows, emit chunk offsets)
        -> transform_demand_chunk  [mapped per chunk]  (fetch + parse + pivot)
            -> load_demand_chunk  [mapped per chunk]  (insert into silver table)
                -> demand_with_details_entity

  Each chunk processes STREAM_BATCH_SIZE rows (default 10k) with bounded
  memory. Data is passed between Transform -> Load via XCom per chunk.

ReplacingMergeTree Logic:
  Uses last_modified_time as the version key. Latest version of each record
  (based on last_modified_time) is automatically kept after merges.
"""

import os
import json
import logging
from datetime import datetime, timedelta
from decimal import Decimal, InvalidOperation
from typing import List, Dict, Optional, Tuple

from airflow import DAG
from airflow.decorators import task
from airflow.operators.empty import EmptyOperator
from airflow.utils.timezone import utcnow
from airflow.utils.timezone import make_aware
from airflow.operators.trigger_dagrun import TriggerDagRunOperator
import clickhouse_connect

logger = logging.getLogger(__name__)

# -- Configuration -----------------------------------------------------------

CLICKHOUSE_HOST = os.getenv('CLICKHOUSE_HOST', 'clickstack-clickhouse.clickhouse.svc.cluster.local')
CLICKHOUSE_PORT = int(os.getenv('CLICKHOUSE_PORT', '8123'))
CLICKHOUSE_USER = os.getenv('CLICKHOUSE_USER', 'default')
CLICKHOUSE_PASSWORD = os.getenv('CLICKHOUSE_PASSWORD', '')
CLICKHOUSE_DB = os.getenv('CLICKHOUSE_DB', 'airflow_test')

# Streaming configuration for large datasets
STREAM_BATCH_SIZE = 10000  # Process records in batches of 50k

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
    )


def parse_ts(val) -> Optional[datetime]:
    """Parse epoch-millis, datetime, or ISO string into timezone-aware datetime."""
    if val is None:
        return None

    if isinstance(val, datetime):
        return make_aware(val) if val.tzinfo is None else val

    if isinstance(val, (int, float)):
        if val == 0:
            return None
        return make_aware(datetime.fromtimestamp(val / 1000))

    if isinstance(val, str):
        try:
            dt = datetime.fromisoformat(val.replace('Z', '+00:00'))
            return make_aware(dt) if dt.tzinfo is None else dt
        except ValueError:
            try:
                return make_aware(datetime.fromtimestamp(int(val) / 1000))
            except (ValueError, OSError):
                return None

    return None

def safe_dec(val, scale=2) -> Decimal:
    if val is None:
        return Decimal('0')
    try:
        return round(Decimal(str(val)), scale)
    except (InvalidOperation, ValueError, TypeError):
        return Decimal('0')


def safe_int(val, default=0) -> int:
    if val is None:
        return default
    try:
        return int(val)
    except (ValueError, TypeError):
        return default

def get_window(context):
    """
    Scheduled Run:
        Uses Airflow's data interval

    All other runs (manual/backfill/etc):
        Processes last 24 hours minus overlap
    """

    dag_run = context.get("dag_run")

    # Scheduled runs use Airflow interval
    if dag_run and dag_run.run_type == "scheduled":
        return dag_run.data_interval_start, dag_run.data_interval_end

    # Manual/backfill/etc → rolling 24 hr window
    end_time = utcnow()
    start_time = end_time - timedelta(hours=24) + timedelta(milliseconds=1)

    logger.info(f"Manual window: [{start_time}, {end_time})")

    return start_time, end_time


def batch_insert(client, table: str, rows: List[dict], chunk_size: int = 10000):
    """Insert rows in chunks to manage memory and handle large datasets.
    
    Args:
        client: ClickHouse client connection
        table: Target table name
        rows: List of dictionaries to insert
        chunk_size: Number of rows to insert per batch (default: 10000)
    """
    if not rows:
        return
    
    cols = list(rows[0].keys())
    total_inserted = 0
    
    # Process in chunks
    for i in range(0, len(rows), chunk_size):
        chunk = rows[i:i + chunk_size]
        data = [[r.get(c) for c in cols] for r in chunk]
        
        try:
            client.insert(table, data=data, column_names=cols)
            total_inserted += len(chunk)
            logger.info(f"Inserted {len(chunk)} rows into {table} (progress: {total_inserted}/{len(rows)})")
        except Exception as e:
            logger.error(f"Failed to insert chunk into {table} at offset {i}: {e}")
            raise
    
    logger.info(f"Completed: {total_inserted} rows inserted into {table}")


# -- Fetch by lastModifiedTime window ----------------------------------------


def fetch_property_events(client, window_start: datetime,
                          window_end: datetime, limit: int = None,
                          offset: int = 0) -> List[str]:
    """Fetch raw JSON strings where event_time falls within
    [window_start, window_end) with optional pagination."""
    query = (
        "SELECT raw FROM property_events_raw "
        "WHERE event_time >= {start:DateTime64(3)} "
        "AND event_time < {end:DateTime64(3)} "
        "ORDER BY event_time "
    )

    params = {'start': window_start, 'end': window_end}

    if limit is not None:
        query += "LIMIT {limit:UInt64} OFFSET {offset:UInt64}"
        params['limit'] = limit
        params['offset'] = offset

    result = client.query(query, parameters=params)
    return [r[0] for r in result.result_rows]


def count_property_events(client, window_start: datetime,
                          window_end: datetime) -> int:
    """Count total property events in the time window."""
    result = client.query(
        "SELECT count() FROM property_events_raw "
        "WHERE event_time >= {start:DateTime64(3)} "
        "AND event_time < {end:DateTime64(3)}",
        parameters={'start': window_start, 'end': window_end},
    )
    return result.result_rows[0][0]


def fetch_demand_events(client, window_start: datetime,
                        window_end: datetime, limit: int = None,
                        offset: int = 0) -> List[str]:
    """Fetch raw JSON strings where event_time falls within
    [window_start, window_end) with optional pagination."""
    query = (
        "SELECT raw FROM demand_events_raw "
        "WHERE event_time >= {start:DateTime64(3)} "
        "AND event_time < {end:DateTime64(3)} "
        "ORDER BY event_time "
    )

    params = {'start': window_start, 'end': window_end}

    if limit is not None:
        query += "LIMIT {limit:UInt64} OFFSET {offset:UInt64}"
        params['limit'] = limit
        params['offset'] = offset

    result = client.query(query, parameters=params)
    return [r[0] for r in result.result_rows]


def count_demand_events(client, window_start: datetime,
                        window_end: datetime) -> int:
    """Count total demand events in the time window."""
    result = client.query(
        "SELECT count() FROM demand_events_raw "
        "WHERE event_time >= {start:DateTime64(3)} "
        "AND event_time < {end:DateTime64(3)}",
        parameters={'start': window_start, 'end': window_end},
    )
    return result.result_rows[0][0]


# -- Extraction helpers -------------------------------------------------------


EPOCH = make_aware(datetime(1970, 1, 1))


def extract_property_address(event: dict, prop: dict) -> dict:
    audit = prop.get('auditDetails', {}) or {}
    addr = prop.get('address', {}) or {}
    return {
        'id': prop.get('id', ''),
        'tenant_id': event.get('tenantId', ''),
        'property_id': prop.get('propertyId', ''),
        'survey_id': prop.get('surveyId', ''),
        'account_id': prop.get('accountId', ''),
        'old_property_id': prop.get('oldPropertyId', ''),
        'property_type': prop.get('propertyType', ''),
        'usage_category': prop.get('usageCategory', ''),
        'ownership_category': prop.get('ownershipCategory', ''),
        'status': prop.get('status', ''),
        'acknowledgement_number': prop.get('acknowldgementNumber', ''),
        'creation_reason': prop.get('creationReason', ''),
        'no_of_floors': safe_int(prop.get('noOfFloors', 0)),
        'source': prop.get('source', ''),
        'channel': prop.get('channel', ''),
        'land_area': safe_dec(prop.get('landArea')),
        'super_built_up_area': safe_dec(prop.get('superBuiltUpArea')),
        'created_by': audit.get('createdBy', ''),
        'created_time': parse_ts(audit.get('createdTime')) or EPOCH,
        'last_modified_by': audit.get('lastModifiedBy', ''),
        'last_modified_time': parse_ts(audit.get('lastModifiedTime')) or EPOCH,
        'additionaldetails': json.dumps(prop.get('additionalDetails')) if prop.get('additionalDetails') else '',
        'door_no': addr.get('doorNo', ''),
        'plot_no': addr.get('plotNo', ''),
        'building_name': addr.get('buildingName', ''),
        'street': addr.get('street', ''),
        'landmark': addr.get('landmark', ''),
        'locality': addr.get('locality', ''),
        'city': addr.get('city', ''),
        'district': addr.get('district', ''),
        'region': addr.get('region', ''),
        'state': addr.get('state', ''),
        'country': addr.get('country', 'IN'),
        'pin_code': addr.get('pincode', ''),
        'latitude': safe_dec(addr.get('latitude'), 6),
        'longitude': safe_dec(addr.get('longitude'), 7),
    }


def extract_units(event: dict, prop: dict) -> List[dict]:
    tenant_id = event.get('tenantId', '')
    property_uuid = prop.get('id', '')
    property_id = prop.get('propertyId', '')
    rows = []
    for u in (prop.get('units', []) or []):
        uid = u.get('id', '')
        if not uid:
            continue
        u_audit = u.get('auditDetails', {}) or {}
        rows.append({
            'tenant_id': tenant_id,
            'property_uuid': property_uuid,
            'unit_id': uid,
            'floor_no': safe_int(u.get('floorNo', 0)),
            'unit_type': u.get('unitType', ''),
            'usage_category': u.get('usageCategory', ''),
            'occupancy_type': u.get('occupancyType', ''),
            'occupancy_date': (parse_ts(u.get('occupancyDate')) or EPOCH).date(),
            'carpet_area': safe_dec(u.get('carpetArea')),
            'built_up_area': safe_dec(u.get('builtUpArea')),
            'plinth_area': safe_dec(u.get('plinthArea')),
            'super_built_up_area': safe_dec(u.get('superBuiltUpArea')),
            'arv': safe_dec(u.get('arv')),
            'construction_type': u.get('constructionType', ''),
            'construction_date': safe_int(u.get('constructionDate', 0)),
            'active': 1 if u.get('active', True) else 0,
            'created_by': u_audit.get('createdBy', ''),
            'created_time': parse_ts(u_audit.get('createdTime')) or EPOCH,
            'last_modified_by': u_audit.get('lastModifiedBy', ''),
            'last_modified_time': parse_ts(u_audit.get('lastModifiedTime')) or EPOCH,
            'property_id': property_id,
            'property_type': prop.get('propertyType', ''),
            'ownership_category': prop.get('ownershipCategory', ''),
            'property_status': prop.get('status', ''),
            'no_of_floors': safe_int(prop.get('noOfFloors', 0)),
        })
    return rows


def extract_owners(event: dict, prop: dict) -> List[dict]:
    tenant_id = event.get('tenantId', '')
    property_uuid = prop.get('id', '')
    property_id = prop.get('propertyId', '')
    rows = []
    for o in (prop.get('owners', []) or []):
        oid = o.get('ownerInfoUuid', '')
        if not oid:
            continue
        o_audit = o.get('auditDetails', {}) or {}
        rows.append({
            'tenant_id': tenant_id,
            'property_uuid': property_uuid,
            'owner_info_uuid': oid,
            'user_id': o.get('userId', ''),
            'status': o.get('status', ''),
            'is_primary_owner': 1 if o.get('isPrimaryOwner', False) else 0,
            'owner_type': o.get('ownerType', ''),
            'ownership_percentage': str(o.get('ownershipPercentage', '')),
            'institution_id': o.get('institutionId', ''),
            'relationship': o.get('relationship', ''),
            'created_by': o_audit.get('createdBy', ''),
            'created_time': parse_ts(o_audit.get('createdTime')) or EPOCH,
            'last_modified_by': o_audit.get('lastModifiedBy', ''),
            'last_modified_time': parse_ts(o_audit.get('lastModifiedTime')) or EPOCH,
            'property_id': property_id,
            'property_type': prop.get('propertyType', ''),
            'ownership_category': prop.get('ownershipCategory', ''),
            'property_status': prop.get('status', ''),
            'no_of_floors': safe_int(prop.get('noOfFloors', 0)),
        })
    return rows


def compute_financial_year(tax_period_from_ms) -> str:
    dt = parse_ts(tax_period_from_ms)
    if dt is None:
        return ''
    if dt.month >= 4:
        return f"{dt.year}-{(dt.year + 1) % 100:02d}"
    return f"{dt.year - 1}-{dt.year % 100:02d}"


def extract_demand(event: dict, demand: dict) -> dict:
    audit = demand.get('auditDetails', {}) or {}
    details = demand.get('demandDetails', []) or []

    tax_totals: Dict[str, Decimal] = {}
    total_tax = Decimal('0')
    total_collection = Decimal('0')

    for d in details:
        code = d.get('taxHeadCode', '')
        if not code:
            continue
        ta = safe_dec(d.get('taxAmount'), 4)
        ca = safe_dec(d.get('collectionAmount'), 4)
        tax_totals[code] = tax_totals.get(code, Decimal('0')) + ta
        total_tax += ta
        total_collection += ca

    explicit_fy = demand.get('financialYear', '')
    fy = explicit_fy if explicit_fy else compute_financial_year(
        demand.get('taxPeriodFrom'))

    # Calculate outstanding_amount and is_paid
    outstanding_amount = round(total_tax - total_collection, 2)
    is_paid = 1 if outstanding_amount <= 0 else 0

    return {
        'tenant_id': event.get('tenantId', ''),
        'demand_id': demand.get('id', ''),
        'consumer_code': demand.get('consumerCode', ''),
        'consumer_type': demand.get('consumerType', ''),
        'business_service': demand.get('businessService', ''),
        'payer': demand.get('payer', ''),
        'tax_period_from': parse_ts(demand.get('taxPeriodFrom')) or EPOCH,
        'tax_period_to': parse_ts(demand.get('taxPeriodTo')) or EPOCH,
        'demand_status': demand.get('status', ''),
        'is_payment_completed': 1 if demand.get('isPaymentCompleted', False) else 0,
        'financial_year': fy,
        'minimum_amount_payable': safe_dec(demand.get('minimumAmountPayable'), 4),
        'bill_expiry_time': safe_int(demand.get('billExpiryTime', 0)),
        'fixed_bill_expiry_date': safe_int(demand.get('fixedBillExpiryDate', 0)),
        'total_tax_amount': round(total_tax, 2),
        'total_collection_amount': round(total_collection, 2),
        'pt_tax': safe_dec(tax_totals.get('PT_TAX', 0), 4),
        'pt_cancer_cess': safe_dec(tax_totals.get('PT_CANCER_CESS', 0), 4),
        'pt_fire_cess': safe_dec(tax_totals.get('PT_FIRE_CESS', 0), 4),
        'pt_roundoff': safe_dec(tax_totals.get('PT_ROUNDOFF', 0), 4),
        'pt_owner_exemption': safe_dec(tax_totals.get('PT_OWNER_EXEMPTION', 0), 4),
        'pt_unit_usage_exemption': safe_dec(tax_totals.get('PT_UNIT_USAGE_EXEMPTION', 0), 4),
        'outstanding_amount': outstanding_amount,
        'is_paid': is_paid,
        'created_by': audit.get('createdBy', ''),
        'created_time': parse_ts(audit.get('createdTime')) or EPOCH,
        'last_modified_by': audit.get('lastModifiedBy', ''),
        'last_modified_time': parse_ts(audit.get('lastModifiedTime')) or EPOCH,
    }


# -- Extract task functions ----------------------------------------------------


@task
def extract_property_chunks(**context):
    """Count property events and emit chunk offsets (lightweight metadata).

    Returns a list of dicts, each describing one chunk to process.
    Airflow dynamically maps one Transform+Load task per chunk.
    """
    window_start, window_end = get_window(context)
    logger.info(f"Run type: {context['dag_run'].run_type}")
    logger.info(f"Logical date: {context['logical_date']}")
    logger.info(f"Property extract window: [{window_start}, {window_end})")

    client = get_client()
    try:
        total_count = count_property_events(client, window_start, window_end)
        if total_count == 0:
            logger.info("No property events in window")
            return []

        chunks = []
        for offset in range(0, total_count, STREAM_BATCH_SIZE):
            chunks.append({
                'offset': offset,
                'limit': STREAM_BATCH_SIZE,
                'window_start': window_start.isoformat(),
                'window_end': window_end.isoformat(),
            })

        logger.info(f"Property: {total_count} rows -> {len(chunks)} chunks of {STREAM_BATCH_SIZE}")
        return chunks

    finally:
        client.close()


@task
def extract_demand_chunks(**context):
    """Count demand events and emit chunk offsets (lightweight metadata).

    Returns a list of dicts, each describing one chunk to process.
    Airflow dynamically maps one Transform+Load task per chunk.
    """
    window_start, window_end = get_window(context)
    logger.info(f"Run type: {context['dag_run'].run_type}")
    logger.info(f"Logical date: {context['logical_date']}")
    logger.info(f"Demand extract window: [{window_start}, {window_end})")

    client = get_client()
    try:
        total_count = count_demand_events(client, window_start, window_end)
        if total_count == 0:
            logger.info("No demand events in window")
            return []

        chunks = []
        for offset in range(0, total_count, STREAM_BATCH_SIZE):
            chunks.append({
                'offset': offset,
                'limit': STREAM_BATCH_SIZE,
                'window_start': window_start.isoformat(),
                'window_end': window_end.isoformat(),
            })

        logger.info(f"Demand: {total_count} rows -> {len(chunks)} chunks of {STREAM_BATCH_SIZE}")
        return chunks

    finally:
        client.close()


# -- Transform task functions -------------------------------------------------


@task
def transform_property_chunk(chunk_info):
    """Fetch and transform a single chunk of property events.

    Reads STREAM_BATCH_SIZE raw JSONs from ClickHouse, parses them,
    and returns structured rows for the 3 property silver tables.
    Memory usage is bounded to one chunk at a time.
    """
    ws = datetime.fromisoformat(chunk_info['window_start'])
    we = datetime.fromisoformat(chunk_info['window_end'])
    offset = chunk_info['offset']
    limit = chunk_info['limit']

    logger.info(f"Transform property chunk: offset={offset}, limit={limit}")

    client = get_client()
    try:
        raw_jsons = fetch_property_events(client, ws, we, limit=limit, offset=offset)

        prop_rows = []
        unit_rows = []
        owner_rows = []

        for raw_json in raw_jsons:
            try:
                event = json.loads(raw_json)
            except json.JSONDecodeError:
                logger.warning("Skipping invalid JSON")
                continue

            prop = event.get('property', {}) or {}
            if not prop.get('propertyId', ''):
                continue

            prop_rows.append(extract_property_address(event, prop))
            unit_rows.extend(extract_units(event, prop))
            owner_rows.extend(extract_owners(event, prop))

        logger.info(
            f"Property chunk done: {len(prop_rows)} props, "
            f"{len(unit_rows)} units, {len(owner_rows)} owners"
        )
        return {
            'property_rows': prop_rows,
            'unit_rows': unit_rows,
            'owner_rows': owner_rows,
        }

    finally:
        client.close()


@task
def transform_demand_chunk(chunk_info):
    """Fetch and transform a single chunk of demand events.

    Reads STREAM_BATCH_SIZE raw JSONs from ClickHouse, parses them,
    and returns structured demand rows.
    Memory usage is bounded to one chunk at a time.
    """
    ws = datetime.fromisoformat(chunk_info['window_start'])
    we = datetime.fromisoformat(chunk_info['window_end'])
    offset = chunk_info['offset']
    limit = chunk_info['limit']

    logger.info(f"Transform demand chunk: offset={offset}, limit={limit}")

    client = get_client()
    try:
        raw_jsons = fetch_demand_events(client, ws, we, limit=limit, offset=offset)

        demand_rows = []

        for raw_json in raw_jsons:
            try:
                event = json.loads(raw_json)
            except json.JSONDecodeError:
                logger.warning("Skipping invalid JSON")
                continue

            demand = event.get('demand', {}) or {}
            if not demand.get('id', ''):
                continue

            demand_rows.append(extract_demand(event, demand))

        logger.info(f"Demand chunk done: {len(demand_rows)} demands")
        return {'demand_rows': demand_rows}

    finally:
        client.close()


# -- Load task functions ------------------------------------------------------


@task
def load_property_chunk(transformed_data):
    """Load a single chunk of transformed property data into ClickHouse.

    Receives structured rows from the corresponding transform task
    and batch-inserts into the 3 property silver tables.
    """
    prop_rows = transformed_data.get('property_rows', [])
    unit_rows = transformed_data.get('unit_rows', [])
    owner_rows = transformed_data.get('owner_rows', [])

    if not prop_rows and not unit_rows and not owner_rows:
        return {'properties': 0, 'units': 0, 'owners': 0}

    logger.info(
        f"Loading property chunk: {len(prop_rows)} props, "
        f"{len(unit_rows)} units, {len(owner_rows)} owners"
    )

    client = get_client()
    try:
        batch_insert(client, 'property_address_entity', prop_rows, chunk_size=10000)
        batch_insert(client, 'property_unit_entity', unit_rows, chunk_size=10000)
        batch_insert(client, 'property_owner_entity', owner_rows, chunk_size=10000)

        return {
            'properties': len(prop_rows),
            'units': len(unit_rows),
            'owners': len(owner_rows),
        }

    finally:
        client.close()


@task
def load_demand_chunk(transformed_data):
    """Load a single chunk of transformed demand data into ClickHouse.

    Receives structured rows from the corresponding transform task
    and batch-inserts into the demand silver table.
    """
    demand_rows = transformed_data.get('demand_rows', [])

    if not demand_rows:
        return {'demands': 0}

    logger.info(f"Loading demand chunk: {len(demand_rows)} demands")

    client = get_client()
    try:
        batch_insert(client, 'demand_with_details_entity', demand_rows, chunk_size=10000)
        return {'demands': len(demand_rows)}

    finally:
        client.close()


# -- DAG definition -----------------------------------------------------------

with DAG(
    'property_tax_raw_to_silver',
    default_args=default_args,
    description='Daily raw JSON -> ReplacingMergeTree silver tables (dynamic task mapping)',
    schedule='30 1 * * *',
    start_date=make_aware(datetime(2025, 1, 1)),
    catchup=False,
    tags=['property_tax', 'clickhouse', 'raw_to_silver'],
    max_active_runs=1,
) as dag:

    start = EmptyOperator(task_id='start')

    # Property pipeline: Extract chunk offsets -> Transform per chunk -> Load per chunk
    property_chunks = extract_property_chunks()
    transformed_props = transform_property_chunk.expand(chunk_info=property_chunks)
    loaded_props = load_property_chunk.expand(transformed_data=transformed_props)

    # Demand pipeline: Extract chunk offsets -> Transform per chunk -> Load per chunk
    demand_chunks = extract_demand_chunks()
    transformed_demands = transform_demand_chunk.expand(chunk_info=demand_chunks)
    loaded_demands = load_demand_chunk.expand(transformed_data=transformed_demands)

    trigger_rmv_refresh = TriggerDagRunOperator(
        task_id='trigger_rmv_refresh',
        trigger_dag_id='clickhouse_rmv_sequential_refresh',
        wait_for_completion=False,
        trigger_rule='none_failed_min_one_success',
    )

    end = EmptyOperator(task_id='end')

    # Wire pipelines (both run in parallel)
    start >> property_chunks
    start >> demand_chunks
    loaded_props >> trigger_rmv_refresh
    loaded_demands >> trigger_rmv_refresh
    trigger_rmv_refresh >> end