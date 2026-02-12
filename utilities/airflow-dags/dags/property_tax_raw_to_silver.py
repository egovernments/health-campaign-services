"""
Property Tax Raw-to-Silver DAG

Reads raw JSON events from ClickHouse raw tables, parses JSON,
and inserts into silver tables using ReplacingMergeTree.

Schedule: Daily at 1:30 AM
Window:   data_interval_start .. data_interval_end (by event_time column)

Architecture:
  property_events_raw (JSON String)
      -> Airflow (parse)
          -> property_address_fact
          -> property_unit_fact
          -> property_owner_fact

  demand_events_raw (JSON String)
      -> Airflow (parse + pivot)
          -> demand_with_details_fact

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
import clickhouse_connect

logger = logging.getLogger(__name__)

# -- Configuration -----------------------------------------------------------

CLICKHOUSE_HOST = os.getenv('CLICKHOUSE_HOST', 'clickhouse')
CLICKHOUSE_PORT = int(os.getenv('CLICKHOUSE_PORT', '8123'))
CLICKHOUSE_USER = os.getenv('CLICKHOUSE_USER', 'default')
CLICKHOUSE_PASSWORD = os.getenv('CLICKHOUSE_PASSWORD', '')
CLICKHOUSE_DB = os.getenv('CLICKHOUSE_DB', 'replacing_test')

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
    """Parse epoch-millis, datetime, or ISO string into Python datetime."""
    if val is None:
        return None
    if isinstance(val, datetime):
        return val
    if isinstance(val, (int, float)):
        if val == 0:
            return None
        return datetime.fromtimestamp(val / 1000)
    if isinstance(val, str):
        try:
            return datetime.fromisoformat(val.replace('Z', '+00:00'))
        except ValueError:
            try:
                return datetime.fromtimestamp(int(val) / 1000)
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


def batch_insert(client, table: str, rows: List[dict]):
    """Insert a list of dicts into a table. Column names from first row."""
    if not rows:
        return
    cols = list(rows[0].keys())
    data = [[r.get(c) for c in cols] for r in rows]
    client.insert(table, data=data, column_names=cols)
    logger.info(f"Inserted {len(rows)} rows into {table}")


# -- Fetch by lastModifiedTime window ----------------------------------------


def fetch_property_events(client, window_start: datetime,
                          window_end: datetime) -> List[str]:
    """Fetch raw JSON strings where event_time falls within
    [window_start, window_end)."""
    result = client.query(
        "SELECT raw FROM property_events_raw "
        "WHERE event_time >= {start:DateTime64(3)} "
        "AND event_time < {end:DateTime64(3)}",
        parameters={'start': window_start, 'end': window_end},
    )
    return [r[0] for r in result.result_rows]


def fetch_demand_events(client, window_start: datetime,
                        window_end: datetime) -> List[str]:
    """Fetch raw JSON strings where event_time falls within
    [window_start, window_end)."""
    result = client.query(
        "SELECT raw FROM demand_events_raw "
        "WHERE event_time >= {start:DateTime64(3)} "
        "AND event_time < {end:DateTime64(3)}",
        parameters={'start': window_start, 'end': window_end},
    )
    return [r[0] for r in result.result_rows]


# -- Extraction helpers -------------------------------------------------------


EPOCH = datetime(1970, 1, 1)


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
    property_id = prop.get('propertyId', '')
    rows = []
    for u in (prop.get('units', []) or []):
        uid = u.get('id', '')
        if not uid:
            continue
        u_audit = u.get('auditDetails', {}) or {}
        rows.append({
            'tenant_id': tenant_id,
            'property_uuid': property_id,
            'unit_id': uid,
            'floor_no': safe_int(u.get('floorNo', 0)),
            'unit_type': u.get('unitType', ''),
            'usage_category': u.get('usageCategory', ''),
            'occupancy_type': u.get('occupancyType', ''),
            'occupancy_date': (parse_ts(u.get('occupancyDate')) or EPOCH).strftime('%Y-%m-%d'),
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
    property_id = prop.get('propertyId', '')
    rows = []
    for o in (prop.get('owners', []) or []):
        oid = o.get('ownerInfoUuid', '')
        if not oid:
            continue
        o_audit = o.get('auditDetails', {}) or {}
        rows.append({
            'tenant_id': tenant_id,
            'property_uuid': property_id,
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
    is_paid = 1 if outstanding_amount > 0 else 0

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


# -- Task functions -----------------------------------------------------------


def process_property_events(**context):
    """Process property_events_raw -> ReplacingMergeTree tables for one daily window."""
    window_start = context['data_interval_start']
    window_end = context['data_interval_end']
    logger.info(f"Property window: [{window_start}, {window_end})")

    client = get_client()
    try:
        raw_jsons = fetch_property_events(client, window_start, window_end)
        if not raw_jsons:
            logger.info("No property events in window")
            return {'properties': 0, 'units': 0, 'owners': 0}

        logger.info(f"Fetched {len(raw_jsons)} property events")

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

            # -- Property + Address --
            p_row = extract_property_address(event, prop)
            prop_rows.append(p_row)

            # -- Units --
            for u_row in extract_units(event, prop):
                unit_rows.append(u_row)

            # -- Owners --
            for o_row in extract_owners(event, prop):
                owner_rows.append(o_row)

        # Batch inserts
        batch_insert(client, 'property_address_fact', prop_rows)
        batch_insert(client, 'property_unit_fact', unit_rows)
        batch_insert(client, 'property_owner_fact', owner_rows)

        counts = {
            'properties': len(prop_rows),
            'units': len(unit_rows),
            'owners': len(owner_rows),
        }
        logger.info(f"Property processing complete: {counts}")
        return counts

    finally:
        client.close()


def process_demand_events(**context):
    """Process demand_events_raw -> demand_with_details_fact for one daily window."""
    window_start = context['data_interval_start']
    window_end = context['data_interval_end']
    logger.info(f"Demand window: [{window_start}, {window_end})")

    client = get_client()
    try:
        raw_jsons = fetch_demand_events(client, window_start, window_end)
        if not raw_jsons:
            logger.info("No demand events in window")
            return {'demands': 0}

        logger.info(f"Fetched {len(raw_jsons)} demand events")

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

            d_row = extract_demand(event, demand)
            demand_rows.append(d_row)

        # Batch insert
        batch_insert(client, 'demand_with_details_fact', demand_rows)

        logger.info(f"Demand processing complete: {len(demand_rows)} new rows")
        return {'demands': len(demand_rows)}

    finally:
        client.close()


# -- DAG definition -----------------------------------------------------------

with DAG(
    'property_tax_raw_to_silver',
    default_args=default_args,
    description='Daily raw JSON -> ReplacingMergeTree silver tables',
    schedule='30 1 * * *',
    start_date=utcnow() - timedelta(days=1),
    catchup=False,
    tags=['property_tax', 'clickhouse', 'raw_to_silver'],
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

    end = EmptyOperator(task_id='end')

    start >> [process_properties, process_demands] >> end