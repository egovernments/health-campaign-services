"""
Property Tax Raw-to-Silver DAG (Optimized)

Uses ClickHouse INSERT...SELECT for all transformations.
JSON parsing happens natively inside ClickHouse (no Python parsing).

Schedule: Daily at 1:30 AM
Window:   data_interval_start .. data_interval_end (by event_time column)

Architecture:
  property_events_raw (JSON String)
      -> ClickHouse INSERT...SELECT (JSON functions)
          -> property_address_entity
          -> property_unit_entity   (ARRAY JOIN on units)
          -> property_owner_entity  (ARRAY JOIN on owners)

  demand_events_raw (JSON String)
      -> ClickHouse INSERT...SELECT (JSON functions + array pivot)
          -> demand_with_details_entity

ReplacingMergeTree Logic:
  Uses last_modified_time as the version key. Latest version of each record
  (based on last_modified_time) is automatically kept after merges.

Performance: ~10-15x faster than Python-based parsing by eliminating
  network round-trips and leveraging ClickHouse's native C++ JSON engine.
"""

import os
import logging
from datetime import datetime, timedelta

from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.operators.empty import EmptyOperator
from airflow.utils.timezone import utcnow, make_aware
import clickhouse_connect

logger = logging.getLogger(__name__)

# -- Configuration -----------------------------------------------------------

CLICKHOUSE_HOST = os.getenv('CLICKHOUSE_HOST', 'clickstack-clickhouse.clickhouse.svc.cluster.local')
CLICKHOUSE_PORT = int(os.getenv('CLICKHOUSE_PORT', '8123'))
CLICKHOUSE_USER = os.getenv('CLICKHOUSE_USER', 'default')
CLICKHOUSE_PASSWORD = os.getenv('CLICKHOUSE_PASSWORD', '')
CLICKHOUSE_DB = os.getenv('CLICKHOUSE_DB', 'airflow_test')

# Records per INSERT...SELECT chunk. Set to 0 to disable chunking.
CHUNK_TARGET_RECORDS = int(os.getenv('CHUNK_TARGET_RECORDS', '200000'))

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
        settings={
            'max_memory_usage': 10_000_000_000,
            'max_execution_time': 7200,
            'max_insert_block_size': 1_000_000,
        },
    )


def get_window(context):
    """
    Scheduled Run: Uses Airflow's data interval.
    Manual/backfill: Processes last 24 hours.
    """
    dag_run = context.get("dag_run")
    if dag_run and dag_run.run_type == "scheduled":
        return dag_run.data_interval_start, dag_run.data_interval_end

    end_time = utcnow()
    start_time = end_time - timedelta(hours=24) + timedelta(milliseconds=1)
    logger.info(f"Manual window: [{start_time}, {end_time})")
    return start_time, end_time


def get_time_chunks(client, table, window_start, window_end):
    """Split window into adaptive time-based chunks targeting CHUNK_TARGET_RECORDS each."""
    row = client.query(
        f"SELECT count(), min(event_time), max(event_time) FROM {table} "
        "WHERE event_time >= {s:DateTime64(3)} AND event_time < {e:DateTime64(3)}",
        parameters={'s': window_start, 'e': window_end},
    ).result_rows[0]

    total, tmin, tmax = row[0], row[1], row[2]

    if total == 0:
        return [], 0

    if CHUNK_TARGET_RECORDS == 0 or total <= CHUNK_TARGET_RECORDS:
        return [(window_start, window_end)], total

    num_chunks = max(1, total // CHUNK_TARGET_RECORDS)
    span_secs = (tmax - tmin).total_seconds()

    if span_secs < 1:
        return [(window_start, window_end)], total

    step = span_secs / num_chunks
    chunks = []
    for i in range(num_chunks):
        cs = tmin + timedelta(seconds=i * step)
        ce = (tmin + timedelta(seconds=(i + 1) * step)) if i < num_chunks - 1 else window_end
        chunks.append((cs, ce))

    return chunks, total


def run_sql_chunked(client, sql, raw_table, window_start, window_end, label):
    """Execute an INSERT...SELECT in time-based chunks with progress logging."""
    chunks, total = get_time_chunks(client, raw_table, window_start, window_end)

    if total == 0:
        logger.info(f"[{label}] No records in window")
        return 0

    logger.info(f"[{label}] Total: {total} records, {len(chunks)} chunk(s)")

    for idx, (cs, ce) in enumerate(chunks):
        logger.info(f"[{label}] Chunk {idx + 1}/{len(chunks)}: [{cs}, {ce})")
        client.command(sql, parameters={'start': cs, 'end': ce})
        logger.info(f"[{label}] Chunk {idx + 1}/{len(chunks)} complete")

    logger.info(f"[{label}] All chunks done. Total records: {total}")
    return total


# -- SQL: property_address_entity --------------------------------------------

PROPERTY_ADDRESS_SQL = """
INSERT INTO property_address_entity
SELECT
    now64(3)                                                           AS _ingested_at,
    JSONExtractString(raw, 'property', 'id')                          AS id,
    JSONExtractString(raw, 'tenantId')                                AS tenant_id,
    JSONExtractString(raw, 'property', 'propertyId')                  AS property_id,
    JSONExtractString(raw, 'property', 'surveyId')                    AS survey_id,
    JSONExtractString(raw, 'property', 'accountId')                   AS account_id,
    JSONExtractString(raw, 'property', 'oldPropertyId')               AS old_property_id,
    JSONExtractString(raw, 'property', 'propertyType')                AS property_type,
    JSONExtractString(raw, 'property', 'usageCategory')               AS usage_category,
    JSONExtractString(raw, 'property', 'ownershipCategory')           AS ownership_category,
    JSONExtractString(raw, 'property', 'status')                      AS status,
    JSONExtractString(raw, 'property', 'acknowldgementNumber')        AS acknowledgement_number,
    JSONExtractString(raw, 'property', 'creationReason')              AS creation_reason,
    toInt32(JSONExtractInt(raw, 'property', 'noOfFloors'))             AS no_of_floors,
    JSONExtractString(raw, 'property', 'source')                      AS source,
    JSONExtractString(raw, 'property', 'channel')                     AS channel,
    toDecimal64(JSONExtractFloat(raw, 'property', 'landArea'), 2)     AS land_area,
    toDecimal64(JSONExtractFloat(raw, 'property', 'superBuiltUpArea'), 2) AS super_built_up_area,
    JSONExtractString(raw, 'property', 'auditDetails', 'createdBy')   AS created_by,
    IF(JSONExtractUInt(raw, 'property', 'auditDetails', 'createdTime') > 0,
       fromUnixTimestamp64Milli(toInt64(JSONExtractUInt(raw, 'property', 'auditDetails', 'createdTime'))),
       toDateTime64('1970-01-01 00:00:00', 3))                        AS created_time,
    JSONExtractString(raw, 'property', 'auditDetails', 'lastModifiedBy') AS last_modified_by,
    IF(JSONExtractUInt(raw, 'property', 'auditDetails', 'lastModifiedTime') > 0,
       fromUnixTimestamp64Milli(toInt64(JSONExtractUInt(raw, 'property', 'auditDetails', 'lastModifiedTime'))),
       toDateTime64('1970-01-01 00:00:00', 3))                        AS last_modified_time,
    IF(JSONHas(raw, 'property', 'additionalDetails'),
       JSONExtractRaw(raw, 'property', 'additionalDetails'), '')      AS additionaldetails,
    JSONExtractString(raw, 'property', 'address', 'doorNo')           AS door_no,
    JSONExtractString(raw, 'property', 'address', 'plotNo')           AS plot_no,
    JSONExtractString(raw, 'property', 'address', 'buildingName')     AS building_name,
    JSONExtractString(raw, 'property', 'address', 'street')           AS street,
    JSONExtractString(raw, 'property', 'address', 'landmark')         AS landmark,
    JSONExtractString(raw, 'property', 'address', 'locality')         AS locality,
    JSONExtractString(raw, 'property', 'address', 'city')             AS city,
    JSONExtractString(raw, 'property', 'address', 'district')         AS district,
    JSONExtractString(raw, 'property', 'address', 'region')           AS region,
    JSONExtractString(raw, 'property', 'address', 'state')            AS state,
    IF(JSONExtractString(raw, 'property', 'address', 'country') != '',
       JSONExtractString(raw, 'property', 'address', 'country'), 'IN') AS country,
    JSONExtractString(raw, 'property', 'address', 'pincode')          AS pin_code,
    toDecimal64(JSONExtractFloat(raw, 'property', 'address', 'latitude'), 6)  AS latitude,
    toDecimal64(JSONExtractFloat(raw, 'property', 'address', 'longitude'), 7) AS longitude
FROM property_events_raw
WHERE event_time >= {start:DateTime64(3)}
  AND event_time < {end:DateTime64(3)}
  AND JSONExtractString(raw, 'property', 'propertyId') != ''
"""


# -- SQL: property_unit_entity (ARRAY JOIN on units) -------------------------

PROPERTY_UNIT_SQL = """
INSERT INTO property_unit_entity
SELECT
    now64(3)                                                           AS _ingested_at,
    JSONExtractString(raw, 'tenantId')                                AS tenant_id,
    JSONExtractString(raw, 'property', 'id')                          AS property_uuid,
    JSONExtractString(u, 'id')                                        AS unit_id,
    toInt32(JSONExtractInt(u, 'floorNo'))                              AS floor_no,
    JSONExtractString(u, 'unitType')                                   AS unit_type,
    JSONExtractString(u, 'usageCategory')                              AS usage_category,
    JSONExtractString(u, 'occupancyType')                              AS occupancy_type,
    toDate(IF(JSONExtractUInt(u, 'occupancyDate') > 0,
              fromUnixTimestamp64Milli(toInt64(JSONExtractUInt(u, 'occupancyDate'))),
              toDateTime64('1970-01-01 00:00:00', 3)))                 AS occupancy_date,
    toDecimal64(JSONExtractFloat(u, 'carpetArea'), 2)                  AS carpet_area,
    toDecimal64(JSONExtractFloat(u, 'builtUpArea'), 2)                 AS built_up_area,
    toDecimal64(JSONExtractFloat(u, 'plinthArea'), 2)                  AS plinth_area,
    toDecimal64(JSONExtractFloat(u, 'superBuiltUpArea'), 2)            AS super_built_up_area,
    toDecimal64(JSONExtractFloat(u, 'arv'), 2)                         AS arv,
    JSONExtractString(u, 'constructionType')                           AS construction_type,
    toInt32(JSONExtractInt(u, 'constructionDate'))                      AS construction_date,
    IF(JSONHas(u, 'active'), toUInt8(JSONExtractBool(u, 'active')), 1) AS active,
    JSONExtractString(u, 'auditDetails', 'createdBy')                  AS created_by,
    IF(JSONExtractUInt(u, 'auditDetails', 'createdTime') > 0,
       fromUnixTimestamp64Milli(toInt64(JSONExtractUInt(u, 'auditDetails', 'createdTime'))),
       toDateTime64('1970-01-01 00:00:00', 3))                         AS created_time,
    JSONExtractString(u, 'auditDetails', 'lastModifiedBy')             AS last_modified_by,
    IF(JSONExtractUInt(u, 'auditDetails', 'lastModifiedTime') > 0,
       fromUnixTimestamp64Milli(toInt64(JSONExtractUInt(u, 'auditDetails', 'lastModifiedTime'))),
       toDateTime64('1970-01-01 00:00:00', 3))                         AS last_modified_time,
    JSONExtractString(raw, 'property', 'propertyId')                   AS property_id,
    JSONExtractString(raw, 'property', 'propertyType')                 AS property_type,
    JSONExtractString(raw, 'property', 'ownershipCategory')            AS ownership_category,
    JSONExtractString(raw, 'property', 'status')                       AS property_status,
    toInt32(JSONExtractInt(raw, 'property', 'noOfFloors'))              AS no_of_floors
FROM property_events_raw
ARRAY JOIN JSONExtractArrayRaw(raw, 'property', 'units') AS u
WHERE event_time >= {start:DateTime64(3)}
  AND event_time < {end:DateTime64(3)}
  AND JSONExtractString(raw, 'property', 'propertyId') != ''
  AND JSONExtractString(u, 'id') != ''
"""


# -- SQL: property_owner_entity (ARRAY JOIN on owners) -----------------------

PROPERTY_OWNER_SQL = """
INSERT INTO property_owner_entity
SELECT
    now64(3)                                                           AS _ingested_at,
    JSONExtractString(raw, 'tenantId')                                AS tenant_id,
    JSONExtractString(raw, 'property', 'id')                          AS property_uuid,
    JSONExtractString(o, 'ownerInfoUuid')                              AS owner_info_uuid,
    JSONExtractString(o, 'userId')                                     AS user_id,
    JSONExtractString(o, 'status')                                     AS status,
    toUInt8(JSONExtractBool(o, 'isPrimaryOwner'))                      AS is_primary_owner,
    JSONExtractString(o, 'ownerType')                                  AS owner_type,
    toString(JSONExtractFloat(o, 'ownershipPercentage'))               AS ownership_percentage,
    JSONExtractString(o, 'institutionId')                              AS institution_id,
    JSONExtractString(o, 'relationship')                               AS relationship,
    JSONExtractString(o, 'auditDetails', 'createdBy')                  AS created_by,
    IF(JSONExtractUInt(o, 'auditDetails', 'createdTime') > 0,
       fromUnixTimestamp64Milli(toInt64(JSONExtractUInt(o, 'auditDetails', 'createdTime'))),
       toDateTime64('1970-01-01 00:00:00', 3))                         AS created_time,
    JSONExtractString(o, 'auditDetails', 'lastModifiedBy')             AS last_modified_by,
    IF(JSONExtractUInt(o, 'auditDetails', 'lastModifiedTime') > 0,
       fromUnixTimestamp64Milli(toInt64(JSONExtractUInt(o, 'auditDetails', 'lastModifiedTime'))),
       toDateTime64('1970-01-01 00:00:00', 3))                         AS last_modified_time,
    JSONExtractString(raw, 'property', 'propertyId')                   AS property_id,
    JSONExtractString(raw, 'property', 'propertyType')                 AS property_type,
    JSONExtractString(raw, 'property', 'ownershipCategory')            AS ownership_category,
    JSONExtractString(raw, 'property', 'status')                       AS property_status,
    toInt32(JSONExtractInt(raw, 'property', 'noOfFloors'))              AS no_of_floors
FROM property_events_raw
ARRAY JOIN JSONExtractArrayRaw(raw, 'property', 'owners') AS o
WHERE event_time >= {start:DateTime64(3)}
  AND event_time < {end:DateTime64(3)}
  AND JSONExtractString(raw, 'property', 'propertyId') != ''
  AND JSONExtractString(o, 'ownerInfoUuid') != ''
"""


# -- SQL: demand_with_details_entity (pivot demandDetails array) -------------
#
# Three-level subquery:
#   1. Inner:  extract raw JSON fields + demandDetails array (dd)
#   2. Middle: compute tax totals, per-tax-head amounts, financial year
#   3. Outer:  derive outstanding_amount and is_paid

DEMAND_SQL = """
INSERT INTO demand_with_details_entity
SELECT
    now64(3)                                                            AS _ingested_at,
    tenant_id, demand_id, consumer_code, consumer_type,
    business_service, payer, tax_period_from, tax_period_to,
    demand_status, is_payment_completed, financial_year,
    minimum_amount_payable, bill_expiry_time, fixed_bill_expiry_date,
    total_tax_amount, total_collection_amount,
    pt_tax, pt_cancer_cess, pt_fire_cess, pt_roundoff,
    pt_owner_exemption, pt_unit_usage_exemption,
    round(total_tax_amount - total_collection_amount, 2)                AS outstanding_amount,
    if(round(total_tax_amount - total_collection_amount, 2) <= 0, 1, 0) AS is_paid,
    created_by, created_time, last_modified_by, last_modified_time
FROM (
    SELECT
        tenant_id, demand_id, consumer_code, consumer_type,
        business_service, payer, tax_period_from, tax_period_to,
        demand_status, is_payment_completed,
        IF(explicit_fy != '', explicit_fy,
           IF(tax_period_from_ms > 0,
              IF(toMonth(tax_period_from) >= 4,
                 concat(toString(toYear(tax_period_from)), '-',
                        lpad(toString((toYear(tax_period_from) + 1) % 100), 2, '0')),
                 concat(toString(toYear(tax_period_from) - 1), '-',
                        lpad(toString(toYear(tax_period_from) % 100), 2, '0'))
              ), ''
           )
        )                                                               AS financial_year,
        minimum_amount_payable, bill_expiry_time, fixed_bill_expiry_date,
        round(arraySum(arrayMap(x -> JSONExtractFloat(x, 'taxAmount'), dd)), 2)
                                                                        AS total_tax_amount,
        round(arraySum(arrayMap(x -> JSONExtractFloat(x, 'collectionAmount'), dd)), 2)
                                                                        AS total_collection_amount,
        toDecimal64(arraySum(arrayMap(x -> JSONExtractFloat(x, 'taxAmount'),
            arrayFilter(x -> JSONExtractString(x, 'taxHeadCode') = 'PT_TAX', dd))), 4)
                                                                        AS pt_tax,
        toDecimal64(arraySum(arrayMap(x -> JSONExtractFloat(x, 'taxAmount'),
            arrayFilter(x -> JSONExtractString(x, 'taxHeadCode') = 'PT_CANCER_CESS', dd))), 4)
                                                                        AS pt_cancer_cess,
        toDecimal64(arraySum(arrayMap(x -> JSONExtractFloat(x, 'taxAmount'),
            arrayFilter(x -> JSONExtractString(x, 'taxHeadCode') = 'PT_FIRE_CESS', dd))), 4)
                                                                        AS pt_fire_cess,
        toDecimal64(arraySum(arrayMap(x -> JSONExtractFloat(x, 'taxAmount'),
            arrayFilter(x -> JSONExtractString(x, 'taxHeadCode') = 'PT_ROUNDOFF', dd))), 4)
                                                                        AS pt_roundoff,
        toDecimal64(arraySum(arrayMap(x -> JSONExtractFloat(x, 'taxAmount'),
            arrayFilter(x -> JSONExtractString(x, 'taxHeadCode') = 'PT_OWNER_EXEMPTION', dd))), 4)
                                                                        AS pt_owner_exemption,
        toDecimal64(arraySum(arrayMap(x -> JSONExtractFloat(x, 'taxAmount'),
            arrayFilter(x -> JSONExtractString(x, 'taxHeadCode') = 'PT_UNIT_USAGE_EXEMPTION', dd))), 4)
                                                                        AS pt_unit_usage_exemption,
        created_by, created_time, last_modified_by, last_modified_time
    FROM (
        SELECT
            JSONExtractString(raw, 'tenantId')                         AS tenant_id,
            JSONExtractString(raw, 'demand', 'id')                     AS demand_id,
            JSONExtractString(raw, 'demand', 'consumerCode')           AS consumer_code,
            JSONExtractString(raw, 'demand', 'consumerType')           AS consumer_type,
            JSONExtractString(raw, 'demand', 'businessService')        AS business_service,
            JSONExtractString(raw, 'demand', 'payer')                  AS payer,
            JSONExtractUInt(raw, 'demand', 'taxPeriodFrom')            AS tax_period_from_ms,
            IF(JSONExtractUInt(raw, 'demand', 'taxPeriodFrom') > 0,
               fromUnixTimestamp64Milli(toInt64(JSONExtractUInt(raw, 'demand', 'taxPeriodFrom'))),
               toDateTime64('1970-01-01 00:00:00', 3))                 AS tax_period_from,
            IF(JSONExtractUInt(raw, 'demand', 'taxPeriodTo') > 0,
               fromUnixTimestamp64Milli(toInt64(JSONExtractUInt(raw, 'demand', 'taxPeriodTo'))),
               toDateTime64('1970-01-01 00:00:00', 3))                 AS tax_period_to,
            JSONExtractString(raw, 'demand', 'status')                 AS demand_status,
            toUInt8(JSONExtractBool(raw, 'demand', 'isPaymentCompleted'))
                                                                        AS is_payment_completed,
            JSONExtractString(raw, 'demand', 'financialYear')          AS explicit_fy,
            toDecimal64(JSONExtractFloat(raw, 'demand', 'minimumAmountPayable'), 4)
                                                                        AS minimum_amount_payable,
            toInt64(JSONExtractInt(raw, 'demand', 'billExpiryTime'))    AS bill_expiry_time,
            toInt64(JSONExtractInt(raw, 'demand', 'fixedBillExpiryDate'))
                                                                        AS fixed_bill_expiry_date,
            JSONExtractArrayRaw(raw, 'demand', 'demandDetails')        AS dd,
            JSONExtractString(raw, 'demand', 'auditDetails', 'createdBy')
                                                                        AS created_by,
            IF(JSONExtractUInt(raw, 'demand', 'auditDetails', 'createdTime') > 0,
               fromUnixTimestamp64Milli(toInt64(JSONExtractUInt(raw, 'demand', 'auditDetails', 'createdTime'))),
               toDateTime64('1970-01-01 00:00:00', 3))                 AS created_time,
            JSONExtractString(raw, 'demand', 'auditDetails', 'lastModifiedBy')
                                                                        AS last_modified_by,
            IF(JSONExtractUInt(raw, 'demand', 'auditDetails', 'lastModifiedTime') > 0,
               fromUnixTimestamp64Milli(toInt64(JSONExtractUInt(raw, 'demand', 'auditDetails', 'lastModifiedTime'))),
               toDateTime64('1970-01-01 00:00:00', 3))                 AS last_modified_time
        FROM demand_events_raw
        WHERE event_time >= {start:DateTime64(3)}
          AND event_time < {end:DateTime64(3)}
          AND JSONExtractString(raw, 'demand', 'id') != ''
    )
)
"""


# -- Task functions -----------------------------------------------------------


def process_property_events(**context):
    """Process property_events_raw -> 3 silver tables via INSERT...SELECT."""
    window_start, window_end = get_window(context)
    logger.info(f"Run type: {context['dag_run'].run_type}")
    logger.info(f"Logical date: {context['logical_date']}")
    logger.info(f"Property window: [{window_start}, {window_end})")

    client = get_client()
    try:
        addr_count = run_sql_chunked(
            client, PROPERTY_ADDRESS_SQL, 'property_events_raw',
            window_start, window_end, 'property_address')

        unit_count = run_sql_chunked(
            client, PROPERTY_UNIT_SQL, 'property_events_raw',
            window_start, window_end, 'property_unit')

        owner_count = run_sql_chunked(
            client, PROPERTY_OWNER_SQL, 'property_events_raw',
            window_start, window_end, 'property_owner')

        counts = {
            'raw_events': addr_count,
            'address_rows': addr_count,
            'unit_source_events': unit_count,
            'owner_source_events': owner_count,
        }
        logger.info(f"Property processing complete: {counts}")
        return counts
    finally:
        client.close()


def process_demand_events(**context):
    """Process demand_events_raw -> demand_with_details_entity via INSERT...SELECT."""
    window_start, window_end = get_window(context)
    logger.info(f"Run type: {context['dag_run'].run_type}")
    logger.info(f"Logical date: {context['logical_date']}")
    logger.info(f"Demand window: [{window_start}, {window_end})")

    client = get_client()
    try:
        demand_count = run_sql_chunked(
            client, DEMAND_SQL, 'demand_events_raw',
            window_start, window_end, 'demand')

        logger.info(f"Demand processing complete: {demand_count} records")
        return {'demands': demand_count}
    finally:
        client.close()


# -- DAG definition -----------------------------------------------------------

with DAG(
    'property_tax_raw_to_silver',
    default_args=default_args,
    description='Daily raw JSON -> ReplacingMergeTree silver tables (INSERT...SELECT)',
    schedule='30 1 * * *',
    start_date=make_aware(datetime(2025, 1, 1)),
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
