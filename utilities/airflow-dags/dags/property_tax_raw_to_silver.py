"""
Property Tax Raw-to-Silver DAG

Reads raw JSON events from ClickHouse raw tables, parses JSON,
and inserts into silver tables using ReplacingMergeTree.

Schedule: Daily at 1:30 AM
Window:   data_interval_start .. data_interval_end (by event_time column)

Architecture (separate Extract, Transform, Load tasks per pipeline):
  Property pipeline (runs in parallel with Demand pipeline):
    extract_property_events  (fetch raw JSON from property_events_raw)
        -> transform_property_events  (parse JSON, extract fields)
            -> load_property_events  (insert into silver tables)
                -> property_address_entity
                -> property_unit_entity
                -> property_owner_entity

  Demand pipeline:
    extract_demand_events  (fetch raw JSON from demand_events_raw)
        -> transform_demand_events  (parse JSON, pivot details)
            -> load_demand_events  (insert into silver table)
                -> demand_with_details_entity

  Data is passed between tasks via XCom.

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
from airflow.operators.python import PythonOperator
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


def extract_property_events(**context):
    """Extract raw JSON strings from property_events_raw in batches.

    Fetches data in chunks of STREAM_BATCH_SIZE and passes the collected
    raw JSON strings to the next task via XCom.
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

        logger.info(f"Total property events to extract: {total_count}")

        all_raw_jsons = []
        offset = 0

        while offset < total_count:
            logger.info(f"Extracting batch: {offset} to {offset + STREAM_BATCH_SIZE} of {total_count}")

            raw_jsons = fetch_property_events(
                client, window_start, window_end,
                limit=STREAM_BATCH_SIZE, offset=offset
            )

            if not raw_jsons:
                break

            all_raw_jsons.extend(raw_jsons)
            offset += STREAM_BATCH_SIZE

        logger.info(f"Property extraction complete: {len(all_raw_jsons)} raw events")
        return all_raw_jsons

    finally:
        client.close()


def extract_demand_events(**context):
    """Extract raw JSON strings from demand_events_raw in batches.

    Fetches data in chunks of STREAM_BATCH_SIZE and passes the collected
    raw JSON strings to the next task via XCom.
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

        logger.info(f"Total demand events to extract: {total_count}")

        all_raw_jsons = []
        offset = 0

        while offset < total_count:
            logger.info(f"Extracting batch: {offset} to {offset + STREAM_BATCH_SIZE} of {total_count}")

            raw_jsons = fetch_demand_events(
                client, window_start, window_end,
                limit=STREAM_BATCH_SIZE, offset=offset
            )

            if not raw_jsons:
                break

            all_raw_jsons.extend(raw_jsons)
            offset += STREAM_BATCH_SIZE

        logger.info(f"Demand extraction complete: {len(all_raw_jsons)} raw events")
        return all_raw_jsons

    finally:
        client.close()


# -- Transform task functions -------------------------------------------------


def transform_property_events(**context):
    """Transform raw JSON strings into structured rows for silver tables.

    Pulls raw JSONs from the extract task via XCom, parses each event,
    and returns structured rows for property_address, unit, and owner entities.
    """
    ti = context['ti']
    raw_jsons = ti.xcom_pull(task_ids='extract_property_events') or []

    if not raw_jsons:
        logger.info("No raw property events to transform")
        return {'property_rows': [], 'unit_rows': [], 'owner_rows': []}

    logger.info(f"Transforming {len(raw_jsons)} raw property events")

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
        pid = prop.get('propertyId', '')
        if not pid:
            continue

        prop_rows.append(extract_property_address(event, prop))
        unit_rows.extend(extract_units(event, prop))
        owner_rows.extend(extract_owners(event, prop))

    logger.info(
        f"Property transform complete: {len(prop_rows)} properties, "
        f"{len(unit_rows)} units, {len(owner_rows)} owners"
    )
    return {
        'property_rows': prop_rows,
        'unit_rows': unit_rows,
        'owner_rows': owner_rows,
    }


def transform_demand_events(**context):
    """Transform raw JSON strings into structured rows for demand silver table.

    Pulls raw JSONs from the extract task via XCom, parses each event,
    and returns structured demand rows.
    """
    ti = context['ti']
    raw_jsons = ti.xcom_pull(task_ids='extract_demand_events') or []

    if not raw_jsons:
        logger.info("No raw demand events to transform")
        return {'demand_rows': []}

    logger.info(f"Transforming {len(raw_jsons)} raw demand events")

    demand_rows = []

    for raw_json in raw_jsons:
        try:
            event = json.loads(raw_json)
        except json.JSONDecodeError:
            logger.warning("Skipping invalid JSON")
            continue

        demand = event.get('demand', {}) or {}
        did = demand.get('id', '')
        if not did:
            continue

        demand_rows.append(extract_demand(event, demand))

    logger.info(f"Demand transform complete: {len(demand_rows)} demands")
    return {'demand_rows': demand_rows}


# -- Load task functions ------------------------------------------------------


def load_property_events(**context):
    """Load transformed property rows into ClickHouse silver tables.

    Pulls structured rows from the transform task via XCom and inserts
    them into property_address_entity, property_unit_entity, and
    property_owner_entity tables in chunks.
    """
    ti = context['ti']
    transformed = ti.xcom_pull(task_ids='transform_property_events') or {}

    prop_rows = transformed.get('property_rows', [])
    unit_rows = transformed.get('unit_rows', [])
    owner_rows = transformed.get('owner_rows', [])

    if not prop_rows and not unit_rows and not owner_rows:
        logger.info("No property data to load")
        return {'properties': 0, 'units': 0, 'owners': 0}

    logger.info(
        f"Loading {len(prop_rows)} properties, "
        f"{len(unit_rows)} units, {len(owner_rows)} owners"
    )

    client = get_client()
    try:
        batch_insert(client, 'property_address_entity', prop_rows, chunk_size=10000)
        batch_insert(client, 'property_unit_entity', unit_rows, chunk_size=10000)
        batch_insert(client, 'property_owner_entity', owner_rows, chunk_size=10000)

        counts = {
            'properties': len(prop_rows),
            'units': len(unit_rows),
            'owners': len(owner_rows),
        }
        logger.info(f"Property load complete: {counts}")
        return counts

    finally:
        client.close()


def load_demand_events(**context):
    """Load transformed demand rows into ClickHouse silver table.

    Pulls structured rows from the transform task via XCom and inserts
    them into demand_with_details_entity table in chunks.
    """
    ti = context['ti']
    transformed = ti.xcom_pull(task_ids='transform_demand_events') or {}

    demand_rows = transformed.get('demand_rows', [])

    if not demand_rows:
        logger.info("No demand data to load")
        return {'demands': 0}

    logger.info(f"Loading {len(demand_rows)} demands")

    client = get_client()
    try:
        batch_insert(client, 'demand_with_details_entity', demand_rows, chunk_size=10000)

        logger.info(f"Demand load complete: {len(demand_rows)} rows")
        return {'demands': len(demand_rows)}

    finally:
        client.close()


# -- DAG definition -----------------------------------------------------------

with DAG(
    'property_tax_raw_to_silver',
    default_args=default_args,
    description='Daily raw JSON -> ReplacingMergeTree silver tables',
    schedule='30 1 * * *',
    start_date=make_aware(datetime(2025, 1, 1)),
    catchup=False,
    tags=['property_tax', 'clickhouse', 'raw_to_silver'],
    max_active_runs=1,
) as dag:

    start = EmptyOperator(task_id='start')

    # Property pipeline: Extract -> Transform -> Load
    extract_props = PythonOperator(
        task_id='extract_property_events',
        python_callable=extract_property_events,
    )
    transform_props = PythonOperator(
        task_id='transform_property_events',
        python_callable=transform_property_events,
    )
    load_props = PythonOperator(
        task_id='load_property_events',
        python_callable=load_property_events,
    )

    # Demand pipeline: Extract -> Transform -> Load
    extract_demands = PythonOperator(
        task_id='extract_demand_events',
        python_callable=extract_demand_events,
    )
    transform_demands = PythonOperator(
        task_id='transform_demand_events',
        python_callable=transform_demand_events,
    )
    load_demands = PythonOperator(
        task_id='load_demand_events',
        python_callable=load_demand_events,
    )

    trigger_rmv_refresh = TriggerDagRunOperator(
        task_id='trigger_rmv_refresh',
        trigger_dag_id='clickhouse_rmv_sequential_refresh',
        wait_for_completion=False,
    )

    end = EmptyOperator(task_id='end')

    # Property pipeline
    start >> extract_props >> transform_props >> load_props >> trigger_rmv_refresh
    # Demand pipeline (runs in parallel with property pipeline)
    start >> extract_demands >> transform_demands >> load_demands >> trigger_rmv_refresh
    # Final
    trigger_rmv_refresh >> end