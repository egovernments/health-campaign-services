"""
Property Tax Raw-to-Silver DAG

Reads raw JSON events from ClickHouse raw tables, parses JSON,
and inserts into silver tables using ReplacingMergeTree.

Schedule: Daily at 1:30 AM
Window:   data_interval_start .. data_interval_end (by event_time column)

Architecture (Extract → Transform+Load per chunk):
  Property pipeline (runs in parallel with Demand pipeline):
    extract_property_events  (count + pass window metadata via XCom)
        -> transform_load_property_events  (per chunk: fetch → transform → insert)
            -> property_address_entity
            -> property_unit_entity
            -> property_owner_entity
            -> property_audit_entity     (MergeTree; one audit row per event, not replacing)

  Demand pipeline:
    extract_demand_events  (count + pass window metadata via XCom)
        -> transform_load_demand_events  (per chunk: fetch → transform → insert)
            -> demand_with_details_entity

  Payment pipeline (runs in parallel with Property and Demand pipelines):
    extract_payment_events  (count + pass window metadata via XCom)
        -> transform_load_payment_events  (per chunk: fetch → transform → insert)
            -> payment_with_details_entity

  Bill pipeline (runs in parallel with all other pipelines):
    extract_bill_events  (count + pass window metadata via XCom)
        -> transform_load_bill_events  (per chunk: fetch → transform → insert)
            -> bill_entity               (one row per bill)
            -> bill_detail_entity        (one row per billDetail inside each bill)

  Assessment pipeline (runs in parallel with all other pipelines):
    extract_assessment_events  (count + pass window metadata via XCom)
        -> transform_load_assessment_events  (per chunk: fetch → transform → insert)
            -> property_assessment_entity    (one row per assessment)

  Extract passes only lightweight metadata (window + count) via XCom.
  Transform+Load reads from ClickHouse one chunk at a time, transforms it,
  inserts into silver tables, then moves to the next chunk.
  Only one chunk of raw JSON is ever in memory.

ReplacingMergeTree Logic:
  Uses last_modified_time as the version key. Latest version of each record
  (based on last_modified_time) is automatically kept after merges.
"""

import os
import json
import logging
import gc
import resource
import time
import random
from datetime import datetime, timedelta, timezone
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

CLICKHOUSE_HOST = os.getenv('CLICKHOUSE_HOST', 'clickhouse-clickstack-clickhouse-clickhouse-headless.clickhouse.svc.cluster.local')
CLICKHOUSE_PORT = int(os.getenv('CLICKHOUSE_PORT', '8123'))
CLICKHOUSE_USER = os.getenv('CLICKHOUSE_USER', 'default')
CLICKHOUSE_PASSWORD = os.getenv('CLICKHOUSE_PASSWORD', 'egov')
CLICKHOUSE_DB = os.getenv('CLICKHOUSE_DB', 'punjab_property_tax')

# Streaming configuration for large datasets
STREAM_BATCH_SIZE = 10000  # INSERT chunk size: large batches → fewer ClickHouse parts created
CH_FETCH_SIZE = 500        # SELECT fetch size: small → low concurrent ClickHouse SELECT memory
                            # Rows accumulate in InsertBuffer until STREAM_BATCH_SIZE is reached
CHUNK_SLEEP_SEC = 1.0       # Base sleep between chunk iterations
CHUNK_SLEEP_JITTER = 1.0    # Random jitter added to base sleep each iteration.
                            # Combined uniform(CHUNK_SLEEP_SEC, CHUNK_SLEEP_SEC+JITTER) breaks
                            # lockstep: 5 tasks starting together drift apart and stay apart.
JEMALLOC_PURGE_INTERVAL = 3   # Run SYSTEM JEMALLOC PURGE every N chunks to force JeMalloc to
                               # return freed INSERT/SELECT arenas to the OS, keeping RSS under
                               # the 1.80 GiB server limit despite 5 concurrent transforms.
TASK_START_JITTER = 15.0      # Max random delay (seconds) before each transform's first SELECT,
                               # to stagger 5 simultaneous task starts and avoid the collective
                               # initial RSS spike that pushed CH past the 1.80 GiB limit.

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
        settings={
            # ClickHouse defaults to max_threads = number of CPUs, meaning each query
            # spawns N parallel decompression threads. With 5 concurrent transform tasks
            # this multiplies peak RSS by N×5 and easily exceeds the 1.80 GiB server limit.
            # Single-threaded reads use ~5-10× less peak decompression memory per query.
            'max_threads': 1,
            # Prevent concurrent queries from accumulating in the global uncompressed cache,
            # which persists between queries and pushes the server RSS to its ceiling.
            'use_uncompressed_cache': 0,
        },
    )


# -- Observability: timing + memory instrumentation -------------------------
#
# Three layers are measured per task and logged (logs only, no metrics tables):
#   1. ClickHouse side  - app-side wall-clock per SELECT/INSERT (OpStats), plus
#      authoritative server peak memory + duration from system.query_log.
#   2. Airflow worker   - wall-clock + peak process RSS (resource.getrusage).
#   3. Task pod         - peak pod memory from cgroup accounting.
#
# run_query/run_insert read the per-task log_comment + OpStats off the client
# object (set by instrument_client), so the fetch_*/batch_insert helpers don't
# each need new parameters threaded through their signatures.


class OpStats:
    """Accumulates per-operation wall-clock timing for one task.

    Summarised once at task end (count / total / avg / max per op type) instead
    of logging one line per 500-row page, which would flood the task log.
    """

    def __init__(self):
        self._ops = {}  # op -> [count, total_ms, max_ms, rows]

    def record(self, op: str, wall_ms: float, rows: int = 0) -> None:
        s = self._ops.get(op)
        if s is None:
            self._ops[op] = [1, wall_ms, wall_ms, rows]
        else:
            s[0] += 1
            s[1] += wall_ms
            s[2] = max(s[2], wall_ms)
            s[3] += rows

    def summary(self) -> str:
        parts = []
        for op, (count, total_ms, max_ms, rows) in sorted(self._ops.items()):
            avg = total_ms / count if count else 0.0
            parts.append(f"{op}: n={count} total={total_ms / 1000:.2f}s "
                         f"avg={avg:.1f}ms max={max_ms:.1f}ms rows={rows}")
        return " | ".join(parts) if parts else "no ops"


def instrument_client(client, log_comment: str, op_stats: 'OpStats'):
    """Attach a per-task log_comment + OpStats accumulator to a client so that
    run_query/run_insert can tag every operation and time it."""
    client._etl_log_comment = log_comment
    client._etl_op_stats = op_stats
    return client


def run_query(client, query, parameters=None):
    """client.query with per-task log_comment tagging + wall-clock timing.

    The log_comment lets us attribute server-side peak memory back to this task
    via system.query_log. Falls back to a plain query if the client was never
    instrumented (e.g. ad-hoc use)."""
    log_comment = getattr(client, '_etl_log_comment', None)
    op_stats = getattr(client, '_etl_op_stats', None)
    settings = {'log_comment': log_comment} if log_comment else None
    t0 = time.monotonic()
    result = client.query(query, parameters=parameters, settings=settings)
    if op_stats is not None:
        op_stats.record('select', (time.monotonic() - t0) * 1000.0,
                        len(result.result_rows))
    return result


def run_insert(client, table, data, column_names):
    """client.insert with per-task log_comment tagging + wall-clock timing."""
    log_comment = getattr(client, '_etl_log_comment', None)
    op_stats = getattr(client, '_etl_op_stats', None)
    settings = {'log_comment': log_comment} if log_comment else None
    t0 = time.monotonic()
    client.insert(table, data=data, column_names=column_names, settings=settings)
    if op_stats is not None:
        op_stats.record('insert', (time.monotonic() - t0) * 1000.0, len(data))


def peak_rss_mib() -> int:
    """Peak resident set size of THIS worker process in MiB.

    On Linux ru_maxrss is reported in KiB, so divide by 1024 for MiB."""
    return resource.getrusage(resource.RUSAGE_SELF).ru_maxrss // 1024


def pod_peak_mem_mib():
    """Peak memory of the whole task pod from cgroup accounting, in MiB.

    Tries cgroup v2 (memory.peak) then v1 (memory.max_usage_in_bytes). Returns
    None if neither is readable (e.g. local non-container runs) so callers can
    degrade gracefully rather than crash."""
    for path in ('/sys/fs/cgroup/memory.peak',
                 '/sys/fs/cgroup/memory/memory.max_usage_in_bytes'):
        try:
            with open(path) as fh:
                return int(fh.read().strip()) // (1024 * 1024)
        except (OSError, ValueError):
            continue
    return None


def log_clickhouse_query_stats(client, log_comment: str) -> None:
    """Log ClickHouse-measured time + authoritative peak memory per op type for
    this task, read from system.query_log.

    Best-effort: SYSTEM FLUSH LOGS surfaces the just-finished ops (query_log
    flushes asynchronously). Wrapped so a missing privilege never fails the task.
    """
    try:
        client.command("SYSTEM FLUSH LOGS")
        result = client.query(
            "SELECT "
            "  multiIf(query_kind='Select','select', "
            "          query_kind='Insert','insert', lower(query_kind)) AS op, "
            "  count() AS n, "
            "  round(avg(query_duration_ms)) AS avg_ms, "
            "  max(query_duration_ms) AS max_ms, "
            "  formatReadableSize(max(memory_usage)) AS peak_mem, "
            "  formatReadableSize(sum(read_bytes) + sum(written_bytes)) AS io "
            "FROM system.query_log "
            "WHERE type = 'QueryFinish' AND log_comment = {lc:String} "
            "GROUP BY op ORDER BY op",
            parameters={'lc': log_comment},
        )
        if not result.result_rows:
            logger.info(f"clickhouse_query_stats[{log_comment}]: no rows in query_log yet")
            return
        for op, n, avg_ms, max_ms, peak_mem, io in result.result_rows:
            logger.info(f"clickhouse_query_stats[{log_comment}] {op}: n={n} "
                        f"avg={avg_ms}ms max={max_ms}ms peak_mem={peak_mem} io={io}")
    except Exception as e:
        logger.warning(f"clickhouse_query_stats unavailable for {log_comment}: {e}")


def log_clickhouse_select_breakdown(client, log_comment: str) -> None:
    """Log peak memory + duration for EVERY individual SELECT of this task, read
    from system.query_log.

    Use this to see whether per-query memory is flat or growing across pages.
    Logs a one-line distribution (min/avg/max) first so the "same or different?"
    answer is immediate, then one line per SELECT. Best-effort; never raises.

    Note: this is verbose (one line per page). It is wired into the property
    task only, on purpose.
    """
    try:
        client.command("SYSTEM FLUSH LOGS")
        dist = client.query(
            "SELECT count() AS n, "
            "  formatReadableSize(min(memory_usage)) AS min_mem, "
            "  formatReadableSize(round(avg(memory_usage))) AS avg_mem, "
            "  formatReadableSize(max(memory_usage)) AS max_mem "
            "FROM system.query_log "
            "WHERE type = 'QueryFinish' AND log_comment = {lc:String} "
            "AND query_kind = 'Select'",
            parameters={'lc': log_comment},
        )
        if not dist.result_rows or not dist.result_rows[0][0]:
            logger.info(f"select_mem[{log_comment}]: no SELECTs in query_log yet")
            return
        n, min_mem, avg_mem, max_mem = dist.result_rows[0]
        logger.info(f"select_mem_dist[{log_comment}]: n={n} "
                    f"min={min_mem} avg={avg_mem} max={max_mem}")

        rows = client.query(
            "SELECT query_duration_ms, "
            "  formatReadableSize(memory_usage) AS peak_mem, "
            "  read_rows, "
            "  formatReadableSize(read_bytes) AS read_bytes "
            "FROM system.query_log "
            "WHERE type = 'QueryFinish' AND log_comment = {lc:String} "
            "AND query_kind = 'Select' "
            "ORDER BY event_time_microseconds",
            parameters={'lc': log_comment},
        )
        for page, (dur, peak_mem, read_rows, read_bytes) in enumerate(rows.result_rows, 1):
            logger.info(f"select_mem[{log_comment}] page={page} dur={dur}ms "
                        f"peak_mem={peak_mem} read_rows={read_rows} read_bytes={read_bytes}")
    except Exception as e:
        logger.warning(f"select breakdown unavailable for {log_comment}: {e}")


def log_resource_summary(label: str, t0: float, op_stats: 'OpStats' = None) -> None:
    """Log the worker/pod resource line (and optional app-side op summary) for a task.

    t0 is a time.monotonic() captured at task start."""
    if op_stats is not None:
        logger.info(f"app_ops[{label}]: {op_stats.summary()}")
    pod = pod_peak_mem_mib()
    pod_str = f"{pod} MiB" if pod is not None else "n/a"
    logger.info(f"resource_summary[{label}]: wall={time.monotonic() - t0:.1f}s "
                f"worker_peak_rss={peak_rss_mib()} MiB pod_peak_mem={pod_str}")


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
            run_insert(client, table, data, cols)
            total_inserted += len(chunk)
            logger.info(f"Inserted {len(chunk)} rows into {table} (progress: {total_inserted}/{len(rows)})")
        except Exception as e:
            logger.error(f"Failed to insert chunk into {table} at offset {i}: {e}")
            raise
    
    logger.info(f"Completed: {total_inserted} rows inserted into {table}")


class InsertBuffer:
    """Accumulates transformed rows and flushes to ClickHouse in STREAM_BATCH_SIZE chunks.

    Decouples the ClickHouse SELECT fetch size (CH_FETCH_SIZE, small) from the INSERT
    batch size (STREAM_BATCH_SIZE, large). This keeps concurrent SELECT memory low while
    maintaining large INSERT batches to minimise ClickHouse part creation.
    """

    def __init__(self, client, table: str, flush_size: int = STREAM_BATCH_SIZE):
        self._client = client
        self._table = table
        self._flush_size = flush_size
        self._buf: List[dict] = []
        self.total_inserted = 0

    def add(self, rows: List[dict]) -> None:
        self._buf.extend(rows)
        while len(self._buf) >= self._flush_size:
            batch = self._buf[:self._flush_size]
            batch_insert(self._client, self._table, batch, chunk_size=self._flush_size)
            self.total_inserted += len(batch)
            self._buf = self._buf[self._flush_size:]

    def flush(self) -> None:
        if self._buf:
            batch_insert(self._client, self._table, self._buf, chunk_size=self._flush_size)
            self.total_inserted += len(self._buf)
            self._buf = []


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
    result = run_query(
        client,
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

    result = run_query(client, query, parameters=params)
    return [r[0] for r in result.result_rows]


def count_demand_events(client, window_start: datetime,
                        window_end: datetime) -> int:
    """Count total demand events in the time window."""
    result = run_query(
        client,
        "SELECT count() FROM demand_events_raw "
        "WHERE event_time >= {start:DateTime64(3)} "
        "AND event_time < {end:DateTime64(3)}",
        parameters={'start': window_start, 'end': window_end},
    )
    return result.result_rows[0][0]


def fetch_payment_events(client, window_start: datetime,
                         window_end: datetime, limit: int = None,
                         offset: int = 0) -> List[str]:
    """Fetch raw JSON strings where event_time falls within
    [window_start, window_end) with optional pagination."""
    query = (
        "SELECT raw FROM payment_events_raw "
        "WHERE event_time >= {start:DateTime64(3)} "
        "AND event_time < {end:DateTime64(3)} "
        "ORDER BY event_time "
    )

    params = {'start': window_start, 'end': window_end}

    if limit is not None:
        query += "LIMIT {limit:UInt64} OFFSET {offset:UInt64}"
        params['limit'] = limit
        params['offset'] = offset

    result = run_query(client, query, parameters=params)
    return [r[0] for r in result.result_rows]


def count_payment_events(client, window_start: datetime,
                         window_end: datetime) -> int:
    """Count total payment events in the time window."""
    result = run_query(
        client,
        "SELECT count() FROM payment_events_raw "
        "WHERE event_time >= {start:DateTime64(3)} "
        "AND event_time < {end:DateTime64(3)}",
        parameters={'start': window_start, 'end': window_end},
    )
    return result.result_rows[0][0]


def fetch_bill_events(client, window_start: datetime,
                      window_end: datetime, limit: int = None,
                      offset: int = 0) -> List[str]:
    """Fetch raw payment JSON — bill data is embedded in Payment.paymentDetails[n].bill."""
    query = (
        "SELECT raw FROM payment_events_raw "
        "WHERE event_time >= {start:DateTime64(3)} "
        "AND event_time < {end:DateTime64(3)} "
        "ORDER BY event_time "
    )

    params = {'start': window_start, 'end': window_end}

    if limit is not None:
        query += "LIMIT {limit:UInt64} OFFSET {offset:UInt64}"
        params['limit'] = limit
        params['offset'] = offset

    result = run_query(client, query, parameters=params)
    return [r[0] for r in result.result_rows]


def count_bill_events(client, window_start: datetime,
                      window_end: datetime) -> int:
    """Count payment events — bill data is embedded inside each payment."""
    result = run_query(
        client,
        "SELECT count() FROM payment_events_raw "
        "WHERE event_time >= {start:DateTime64(3)} "
        "AND event_time < {end:DateTime64(3)}",
        parameters={'start': window_start, 'end': window_end},
    )
    return result.result_rows[0][0]


def fetch_assessment_events(client, window_start: datetime,
                            window_end: datetime, limit: int = None,
                            offset: int = 0) -> List[str]:
    """Fetch raw JSON strings from assessment_events_raw within the time window."""
    query = (
        "SELECT raw FROM assessment_events_raw "
        "WHERE event_time >= {start:DateTime64(3)} "
        "AND event_time < {end:DateTime64(3)} "
        "ORDER BY event_time "
    )

    params = {'start': window_start, 'end': window_end}

    if limit is not None:
        query += "LIMIT {limit:UInt64} OFFSET {offset:UInt64}"
        params['limit'] = limit
        params['offset'] = offset

    result = run_query(client, query, parameters=params)
    return [r[0] for r in result.result_rows]


def count_assessment_events(client, window_start: datetime,
                            window_end: datetime) -> int:
    """Count total assessment events in the time window."""
    result = run_query(
        client,
        "SELECT count() FROM assessment_events_raw "
        "WHERE event_time >= {start:DateTime64(3)} "
        "AND event_time < {end:DateTime64(3)}",
        parameters={'start': window_start, 'end': window_end},
    )
    return result.result_rows[0][0]


# -- Extraction helpers -------------------------------------------------------


EPOCH = make_aware(datetime(1970, 1, 1))


def extract_property_address(prop: dict) -> dict:
    audit = prop.get('auditDetails', {}) or {}
    addr = prop.get('address', {}) or {}
    return {
        'id': prop.get('id', ''),
        'tenant_id': prop.get('tenantId', ''),
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
        'financial_year': compute_financial_year(audit.get('createdTime')),
        'additionaldetails': json.dumps(prop.get('additionalDetails')) if prop.get('additionalDetails') else '',
        'door_no': addr.get('doorNo', ''),
        'plot_no': addr.get('plotNo', ''),
        'building_name': addr.get('buildingName', ''),
        'street': addr.get('street', ''),
        'landmark': addr.get('landmark', ''),
        'locality': addr.get('locality', {}).get('code', '') if isinstance(addr.get('locality'), dict) else addr.get('locality', ''),
        'city': addr.get('city', ''),
        'district': addr.get('district', ''),
        'region': addr.get('region', ''),
        'state': addr.get('state', ''),
        'country': addr.get('country', 'IN'),
        'pin_code': addr.get('pincode', ''),
        'latitude': safe_dec((addr.get('geoLocation') or {}).get('latitude') or addr.get('latitude'), 6),
        'longitude': safe_dec((addr.get('geoLocation') or {}).get('longitude') or addr.get('longitude'), 7),
    }


def extract_units(prop: dict) -> List[dict]:
    tenant_id = prop.get('tenantId', '')
    property_uuid = prop.get('id', '')
    property_id = prop.get('propertyId', '')
    rows = []
    for u in (prop.get('units', []) or []):
        uid = u.get('id', '')
        if not uid:
            continue
        u_audit = u.get('auditDetails', {}) or {}
        cd = u.get('constructionDetail', {}) or {}
        rows.append({
            'tenant_id': tenant_id,
            'property_uuid': property_uuid,
            'unit_id': uid,
            'floor_no': safe_int(u.get('floorNo', 0)),
            'unit_type': u.get('unitType', ''),
            'usage_category': u.get('usageCategory', ''),
            'occupancy_type': u.get('occupancyType', ''),
            'occupancy_date': (parse_ts(u.get('occupancyDate')) or EPOCH).date(),
            'carpet_area': safe_dec(cd.get('carpetArea')),
            'built_up_area': safe_dec(cd.get('builtUpArea')),
            'plinth_area': safe_dec(cd.get('plinthArea')),
            'super_built_up_area': safe_dec(cd.get('superBuiltUpArea')),
            'arv': safe_dec(u.get('arv')),
            'construction_type': cd.get('constructionType', ''),
            'construction_date': safe_int(cd.get('constructionDate', 0)),
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


def extract_owners(prop: dict) -> List[dict]:
    tenant_id = prop.get('tenantId', '')
    property_uuid = prop.get('id', '')
    property_id = prop.get('propertyId', '')
    rows = []
    for o in (prop.get('owners', []) or []):
        oid = o.get('ownerInfoUuid', '')
        if not oid:
            continue
        rows.append({
            'tenant_id': tenant_id,
            'property_uuid': property_uuid,
            'owner_info_uuid': oid,
            'user_id': o.get('uuid', ''),
            'status': o.get('status', ''),
            'is_primary_owner': 1 if o.get('isPrimaryOwner', False) else 0,
            'owner_type': o.get('ownerType', ''),
            'ownership_percentage': str(o.get('ownerShipPercentage') or o.get('ownershipPercentage') or ''),
            'institution_id': o.get('institutionId', ''),
            'relationship': o.get('relationship', ''),
            'created_by': o.get('createdBy', ''),
            'created_time': parse_ts(o.get('createdDate')) or EPOCH,
            'last_modified_by': o.get('lastModifiedBy', ''),
            'last_modified_time': parse_ts(o.get('lastModifiedDate')) or EPOCH,
            'property_id': property_id,
            'property_type': prop.get('propertyType', ''),
            'ownership_category': prop.get('ownershipCategory', ''),
            'property_status': prop.get('status', ''),
            'no_of_floors': safe_int(prop.get('noOfFloors', 0)),
        })
    return rows


def compute_financial_year(epoch_ms) -> str:
    """Derive Indian fiscal year (Apr–Mar) from epoch-millis using UTC."""
    if not epoch_ms:
        return ''
    try:
        ms = int(epoch_ms)
    except (TypeError, ValueError):
        return ''
    if ms == 0:
        return ''
    dt = datetime.fromtimestamp(ms / 1000, tz=timezone.utc)
    start_year = dt.year if dt.month >= 4 else dt.year - 1
    return f"{start_year}-{(start_year + 1) % 100:02d}"


def extract_demand(demand: dict) -> dict:
    audit = demand.get('auditDetails', {}) or {}
    details = demand.get('demandDetails', []) or []

    tax_amounts: Dict[str, Decimal] = {}
    collection_amounts: Dict[str, Decimal] = {}
    total_tax = Decimal('0')
    total_collection = Decimal('0')

    for d in details:
        code = d.get('taxHeadMasterCode', '')
        if not code:
            continue
        ta = safe_dec(d.get('taxAmount'), 4)
        ca = safe_dec(d.get('collectionAmount'), 4)
        tax_amounts[code] = tax_amounts.get(code, Decimal('0')) + ta
        collection_amounts[code] = collection_amounts.get(code, Decimal('0')) + ca
        total_tax += ta
        total_collection += ca

    fy = compute_financial_year(demand.get('taxPeriodFrom'))

    outstanding_amount = round(total_tax - total_collection, 2)
    is_paid = 1 if outstanding_amount <= 0 else 0

    return {
        'tenant_id': demand.get('tenantId', ''),
        'demand_id': demand.get('id', ''),
        'consumer_code': demand.get('consumerCode', ''),
        'consumer_type': demand.get('consumerType', ''),
        'business_service': demand.get('businessService', ''),
        'payer': demand.get('payer', {}).get('uuid', '') if isinstance(demand.get('payer'), dict) else demand.get('payer', ''),
        'tax_period_from': parse_ts(demand.get('taxPeriodFrom')) or EPOCH,
        'tax_period_to': parse_ts(demand.get('taxPeriodTo')) or EPOCH,
        'demand_status': demand.get('status', ''),
        'financial_year': fy,
        'minimum_amount_payable': safe_dec(demand.get('minimumAmountPayable'), 4),
        'bill_expiry_time': safe_int(demand.get('billExpiryTime', 0)),
        'fixed_bill_expiry_date': safe_int(demand.get('fixedBillExpiryDate', 0)),
        'total_tax_amount': round(total_tax, 2),
        'total_collection_amount': round(total_collection, 2),
        # Tax amounts by tax head
        'pt_tax': safe_dec(tax_amounts.get('PT_TAX', 0), 4),
        'pt_cancer_cess': safe_dec(tax_amounts.get('PT_CANCER_CESS', 0), 4),
        'pt_fire_cess': safe_dec(tax_amounts.get('PT_FIRE_CESS', 0), 4),
        'pt_roundoff': safe_dec(tax_amounts.get('PT_ROUNDOFF', 0), 4),
        'pt_owner_exemption': safe_dec(tax_amounts.get('PT_OWNER_EXEMPTION', 0), 4),
        'pt_unit_usage_exemption': safe_dec(tax_amounts.get('PT_UNIT_USAGE_EXEMPTION', 0), 4),
        'pt_advance_carryforward': safe_dec(tax_amounts.get('PT_ADVANCE_CARRYFORWARD', 0), 4),
        'pt_decimal_ceiling_debit': safe_dec(tax_amounts.get('PT_DECIMAL_CEILING_DEBIT', 0), 4),
        'pt_time_rebate': safe_dec(tax_amounts.get('PT_TIME_REBATE', 0), 4),
        'pt_decimal_ceiling_credit': safe_dec(tax_amounts.get('PT_DECIMAL_CEILING_CREDIT', 0), 4),
        'pt_time_penalty': safe_dec(tax_amounts.get('PT_TIME_PENALTY', 0), 4),
        'pt_adhoc_penalty': safe_dec(tax_amounts.get('PT_ADHOC_PENALTY', 0), 4),
        'pt_adhoc_rebate': safe_dec(tax_amounts.get('PT_ADHOC_REBATE', 0), 4),
        'pt_time_interest': safe_dec(tax_amounts.get('PT_TIME_INTEREST', 0), 4),
        # Collection amounts by tax head
        'pt_tax_collection': safe_dec(collection_amounts.get('PT_TAX', 0), 4),
        'pt_cancer_cess_collection': safe_dec(collection_amounts.get('PT_CANCER_CESS', 0), 4),
        'pt_fire_cess_collection': safe_dec(collection_amounts.get('PT_FIRE_CESS', 0), 4),
        'pt_roundoff_collection': safe_dec(collection_amounts.get('PT_ROUNDOFF', 0), 4),
        'pt_owner_exemption_collection': safe_dec(collection_amounts.get('PT_OWNER_EXEMPTION', 0), 4),
        'pt_unit_usage_exemption_collection': safe_dec(collection_amounts.get('PT_UNIT_USAGE_EXEMPTION', 0), 4),
        'pt_advance_carryforward_collection': safe_dec(collection_amounts.get('PT_ADVANCE_CARRYFORWARD', 0), 4),
        'pt_decimal_ceiling_debit_collection': safe_dec(collection_amounts.get('PT_DECIMAL_CEILING_DEBIT', 0), 4),
        'pt_time_rebate_collection': safe_dec(collection_amounts.get('PT_TIME_REBATE', 0), 4),
        'pt_decimal_ceiling_credit_collection': safe_dec(collection_amounts.get('PT_DECIMAL_CEILING_CREDIT', 0), 4),
        'pt_time_penalty_collection': safe_dec(collection_amounts.get('PT_TIME_PENALTY', 0), 4),
        'pt_adhoc_penalty_collection': safe_dec(collection_amounts.get('PT_ADHOC_PENALTY', 0), 4),
        'pt_adhoc_rebate_collection': safe_dec(collection_amounts.get('PT_ADHOC_REBATE', 0), 4),
        'pt_time_interest_collection': safe_dec(collection_amounts.get('PT_TIME_INTEREST', 0), 4),
        # Derived
        'outstanding_amount': outstanding_amount,
        'is_paid': is_paid,
        'created_by': audit.get('createdBy', ''),
        'created_time': parse_ts(audit.get('createdTime')) or EPOCH,
        'last_modified_by': audit.get('lastModifiedBy', ''),
        'last_modified_time': parse_ts(audit.get('lastModifiedTime')) or EPOCH,
    }



def extract_bill(bill: dict) -> dict:
    audit = bill.get('auditDetails', {}) or {}
    fy = compute_financial_year(audit.get('createdTime'))

    return {
        'tenant_id': bill.get('tenantId', ''),
        'bill_id': bill.get('id', ''),
        'status': bill.get('status', ''),
        'iscancelled': 1 if bill.get('isCancelled', False) else 0,
        'additionaldetails': json.dumps(bill.get('additionalDetails')) if bill.get('additionalDetails') else '',
        'collectionmodesnotallowed': ','.join(bill.get('collectionModesNotAllowed') or []),
        'partpaymentallowed': 1 if bill.get('partPaymentAllowed', False) else 0,
        'isadvanceallowed': 1 if bill.get('isAdvanceAllowed', False) else 0,
        'minimumamounttobepaid': safe_dec(bill.get('minimumAmountToBePaid'), 2),
        'businessservice': bill.get('businessService', ''),
        'totalamount': safe_dec(bill.get('totalAmount'), 2),
        'consumercode': bill.get('consumerCode', ''),
        'billnumber': bill.get('billNumber', ''),
        'billdate': parse_ts(bill.get('billDate')) or EPOCH,
        'reasonforcancellation': bill.get('reasonForCancellation', ''),
        'created_by': audit.get('createdBy', ''),
        'created_time': parse_ts(audit.get('createdTime')) or EPOCH,
        'last_modified_by': audit.get('lastModifiedBy', ''),
        'last_modified_time': parse_ts(audit.get('lastModifiedTime')) or EPOCH,
        'financial_year': fy,
    }


def extract_bill_details(bill: dict) -> List[dict]:
    tenant_id = bill.get('tenantId', '')
    bill_id = bill.get('id', '')
    audit = bill.get('auditDetails', {}) or {}

    rows = []
    for detail in (bill.get('billDetails', []) or []):
        detail_id = detail.get('id', '')
        if not detail_id:
            continue

        from_period = safe_int(detail.get('fromPeriod', 0))
        to_period = safe_int(detail.get('toPeriod', 0))
        fy = compute_financial_year(from_period)

        rows.append({
               'id': detail_id,
            'tenant_id': tenant_id,
            'demand_id': detail.get('demandId', ''),
            'bill_id': bill_id,
            'amount': safe_dec(detail.get('amount'), 2),
            'amount_paid': safe_dec(detail.get('amountPaid'), 2),
            'from_period': from_period,
            'to_period': to_period,
            'additional_details': json.dumps(detail.get('additionalDetails')) if detail.get('additionalDetails') else '',
            'channel': detail.get('channel', ''),
            'voucher_header': detail.get('voucherHeader', ''),
            'boundary': detail.get('boundary', ''),
            'collection_type': detail.get('collectionType', ''),
            'bill_description': detail.get('billDescription', ''),
            'expiry_date': str(detail.get('expiryDate', '')) if detail.get('expiryDate') is not None else '',
            'display_message': detail.get('displayMessage', ''),
            'call_back_for_apportioning': detail.get('callBackForApportioning', ''),
            'cancellation_remarks': detail.get('cancellationRemarks', ''),
            'created_by': audit.get('createdBy', ''),
            'created_time': parse_ts(audit.get('createdTime')) or EPOCH,
            'last_modified_by': audit.get('lastModifiedBy', ''),
            'last_modified_time': parse_ts(audit.get('lastModifiedTime')) or EPOCH,
            'financial_year': fy,
        })
    return rows


def extract_payment(payment: dict) -> dict:
    """Extract and flatten a single payment event into a row for payment_with_details_entity.

    The raw event is expected to carry a top-level 'Payment' object whose
    'paymentDetails' array contains one or more receipt records.  When
    multiple paymentDetails exist we take the first one (index 0) for the
    scalar receipt columns; all detail rows share the same payment-level
    fields.
    """
    audit = payment.get('auditDetails', {}) or {}
    details = payment.get('paymentDetails', []) or []

    # Pick the first payment detail for receipt-level scalar columns.
    # Downstream consumers that need all details should use the raw table.
    detail = details[0] if details else {}

    return {
        'tenant_id': payment.get('tenantId', ''),
        'payment_id': payment.get('id', ''),
        'total_due': safe_dec(payment.get('totalDue'), 2),
        'total_amount_paid': safe_dec(payment.get('totalAmountPaid'), 2),
        'transaction_number': payment.get('transactionNumber', ''),
        'transaction_date': parse_ts(payment.get('transactionDate')) or EPOCH,
        'payment_mode': payment.get('paymentMode', ''),
        'instrument_date': parse_ts(payment.get('instrumentDate')) or EPOCH,
        'instrument_number': payment.get('instrumentNumber', ''),
        'instrument_status': payment.get('instrumentStatus', ''),
        'ifsc_code': payment.get('ifscCode', ''),
        'additional_details': json.dumps(payment.get('additionalDetails')) if payment.get('additionalDetails') else '',
        'payer_id': payment.get('payerId', ''),
        'payment_status': payment.get('paymentStatus', ''),
        'created_by': audit.get('createdBy', ''),
        'created_time': parse_ts(audit.get('createdTime')) or EPOCH,
        'last_modified_by': audit.get('lastModifiedBy', ''),
        'last_modified_time': parse_ts(audit.get('lastModifiedTime')) or EPOCH,
        'financial_year': compute_financial_year(payment.get('transactionDate')),
        'filestore_id': payment.get('fileStoreId', ''),
        # Payment detail / receipt fields (from first paymentDetail entry)
        'receiptnumber': detail.get('receiptNumber', ''),
        'receiptdate': parse_ts(detail.get('receiptDate')) or EPOCH,
        'receipttype': detail.get('receiptType', ''),
        'businessservice': detail.get('businessService', ''),
        'billid': detail.get('billId', ''),
        'manualreceiptnumber': detail.get('manualReceiptNumber', ''),
        'manualreceiptdate': parse_ts(detail.get('manualReceiptDate')) or EPOCH,
    }


def extract_property_audit(prop: dict) -> dict:
    """Snapshot the current property state into property_audit_entity.

    Uses plain MergeTree (no ReplacingMergeTree) — every ingest intentionally
    creates a new audit record for change-history tracking.
    audit_created_time is left to ClickHouse DEFAULT now64(3).
    """
    audit = prop.get('auditDetails', {}) or {}
    owners = prop.get('owners', []) or []
    units = prop.get('units', []) or []

    # Sum builtUpArea and superBuiltUpArea across all units (lives in constructionDetail)
    built_up_area = sum(safe_dec((u.get('constructionDetail') or {}).get('builtUpArea'), 2) for u in units)
    super_built_up_area = sum(safe_dec((u.get('constructionDetail') or {}).get('superBuiltUpArea'), 2) for u in units)

    return {
        'tenant_id': prop.get('tenantId', ''),
        'property_id': prop.get('propertyId', ''),
        'property_type': prop.get('propertyType', ''),
        'ownership_category': prop.get('ownershipCategory', ''),
        'usage_category': prop.get('usageCategory', ''),
        'property_status': prop.get('status', ''),
        'workflow_state': ((prop.get('workflow') or {}).get('state') or {}).get('state', ''),
        'super_built_up_area': super_built_up_area,
        'built_up_area': built_up_area,
        'land_area': safe_dec(prop.get('landArea'), 2),
        'owner_count': safe_int(len(owners)),
        'created_time': parse_ts(audit.get('createdTime')) or EPOCH,
        'last_modified_time': parse_ts(audit.get('lastModifiedTime')) or EPOCH,
    }



def extract_assessment(assessment: dict) -> dict:
    audit = assessment.get('auditDetails', {}) or {}

    return {
        'tenant_id': assessment.get('tenantId', ''),
        'assessmentnumber': assessment.get('assessmentNumber', ''),
        'financialyear': assessment.get('financialYear', ''),
        'propertyid': assessment.get('propertyId', ''),
        'status': assessment.get('status', ''),
        'source': assessment.get('source', ''),
        'channel': assessment.get('channel', ''),
        'assessmentdate': parse_ts(assessment.get('assessmentDate')) or EPOCH,
        'additionaldetails': json.dumps(assessment.get('additionalDetails')) if assessment.get('additionalDetails') else '',
        'created_by': audit.get('createdBy', ''),
        'created_time': parse_ts(audit.get('createdTime')) or EPOCH,
        'last_modified_by': audit.get('lastModifiedBy', ''),
        'last_modified_time': parse_ts(audit.get('lastModifiedTime')) or EPOCH,
    }


# -- Extract task functions ----------------------------------------------------


def extract_property_events(**context):
    """Count property events and pass window metadata via XCom.

    Only passes lightweight metadata (window timestamps + total count).
    No raw data is loaded into memory or XCom.
    """
    window_start, window_end = get_window(context)
    logger.info(f"Run type: {context['dag_run'].run_type}")
    logger.info(f"Logical date: {context['dag_run'].logical_date}")
    logger.info(f"Property extract window: [{window_start}, {window_end})")

    t0 = time.monotonic()
    op_stats = OpStats()
    log_comment = f"{context['ti'].task_id}:{context['ti'].run_id}"
    client = instrument_client(get_client(), log_comment, op_stats)
    try:
        total_count = count_property_events(client, window_start, window_end)
        logger.info(f"Property events found: {total_count}")

        return {
            'total_count': total_count,
            'window_start': window_start.isoformat(),
            'window_end': window_end.isoformat(),
        }

    finally:
        log_resource_summary(context['ti'].task_id, t0, op_stats)
        client.close()


def extract_demand_events(**context):
    """Count demand events and pass window metadata via XCom.

    Only passes lightweight metadata (window timestamps + total count).
    No raw data is loaded into memory or XCom.
    """
    window_start, window_end = get_window(context)
    logger.info(f"Run type: {context['dag_run'].run_type}")
    logger.info(f"Logical date: {context['dag_run'].logical_date}")
    logger.info(f"Demand extract window: [{window_start}, {window_end})")

    t0 = time.monotonic()
    op_stats = OpStats()
    log_comment = f"{context['ti'].task_id}:{context['ti'].run_id}"
    client = instrument_client(get_client(), log_comment, op_stats)
    try:
        total_count = count_demand_events(client, window_start, window_end)
        logger.info(f"Demand events found: {total_count}")

        return {
            'total_count': total_count,
            'window_start': window_start.isoformat(),
            'window_end': window_end.isoformat(),
        }

    finally:
        log_resource_summary(context['ti'].task_id, t0, op_stats)
        client.close()


def extract_payment_events(**context):
    """Count payment events and pass window metadata via XCom.

    Only passes lightweight metadata (window timestamps + total count).
    No raw data is loaded into memory or XCom.
    """
    window_start, window_end = get_window(context)
    logger.info(f"Run type: {context['dag_run'].run_type}")
    logger.info(f"Logical date: {context['dag_run'].logical_date}")
    logger.info(f"Payment extract window: [{window_start}, {window_end})")

    t0 = time.monotonic()
    op_stats = OpStats()
    log_comment = f"{context['ti'].task_id}:{context['ti'].run_id}"
    client = instrument_client(get_client(), log_comment, op_stats)
    try:
        total_count = count_payment_events(client, window_start, window_end)
        logger.info(f"Payment events found: {total_count}")

        return {
            'total_count': total_count,
            'window_start': window_start.isoformat(),
            'window_end': window_end.isoformat(),
        }

    finally:
        log_resource_summary(context['ti'].task_id, t0, op_stats)
        client.close()


# -- Transform + Load task functions ------------------------------------------


def transform_load_property_events(**context):
    """For each chunk: extract from ClickHouse → transform → load into silver tables.

    Only one chunk (STREAM_BATCH_SIZE rows) of raw JSON is in memory at a time.
    Each chunk is transformed and inserted before the next chunk is fetched.
    No data accumulation, no XCom bloat.
    """
    ti = context['ti']
    metadata = ti.xcom_pull(task_ids='extract_property_events')

    total_count = metadata['total_count']
    if total_count == 0:
        logger.info("No property events to process")
        return {'properties': 0, 'units': 0, 'owners': 0, 'audits': 0}

    ws = datetime.fromisoformat(metadata['window_start'])
    we = datetime.fromisoformat(metadata['window_end'])

    t0 = time.monotonic()
    op_stats = OpStats()
    log_comment = f"{ti.task_id}:{ti.run_id}"
    logger.info(f"Processing {total_count} property events | fetch={CH_FETCH_SIZE}/insert={STREAM_BATCH_SIZE} | base_memory={peak_rss_mib()} MiB")

    client = instrument_client(get_client(), log_comment, op_stats)
    try:
        total_props = 0
        total_units = 0
        total_owners = 0
        total_audits = 0
        offset = 0

        prop_buf = InsertBuffer(client, 'property_address_entity')
        unit_buf = InsertBuffer(client, 'property_unit_entity')
        owner_buf = InsertBuffer(client, 'property_owner_entity')
        audit_buf = InsertBuffer(client, 'property_audit_entity')

        try:
            client.command("SYSTEM JEMALLOC PURGE")
        except Exception:
            pass
        time.sleep(random.uniform(0, TASK_START_JITTER))

        while offset < total_count:
            # -- EXTRACT: small fetch → low concurrent ClickHouse SELECT memory --
            raw_jsons = fetch_property_events(client, ws, we,
                                              limit=CH_FETCH_SIZE, offset=offset)
            if not raw_jsons:
                break

            # -- TRANSFORM: parse JSON, extract fields --
            prop_rows = []
            unit_rows = []
            owner_rows = []
            audit_rows = []

            for raw_json in raw_jsons:
                try:
                    event = json.loads(raw_json)
                except json.JSONDecodeError:
                    logger.warning("Skipping invalid JSON")
                    continue

                prop = event.get('Property', {}) or {}
                if not prop.get('propertyId', ''):
                    continue

                prop_rows.append(extract_property_address(prop))
                unit_rows.extend(extract_units(prop))
                owner_rows.extend(extract_owners(prop))
                audit_rows.append(extract_property_audit(prop))


            # -- LOAD: buffer accumulates; flushes in STREAM_BATCH_SIZE chunks --
            chunk_len = len(raw_jsons)
            del raw_jsons
            n_props = len(prop_rows); total_props += n_props
            n_units = len(unit_rows); total_units += n_units
            n_owners = len(owner_rows); total_owners += n_owners
            n_audits = len(audit_rows); total_audits += n_audits

            prop_buf.add(prop_rows); prop_rows.clear()
            unit_buf.add(unit_rows); unit_rows.clear()
            owner_buf.add(owner_rows); owner_rows.clear()
            audit_buf.add(audit_rows); audit_rows.clear()

            gc.collect()

            logger.info(
                f"Chunk {offset}-{offset + chunk_len}: "
                f"{n_props} props, {n_units} units, {n_owners} owners, {n_audits} audits | "
                f"Total: {total_props}/{total_units}/{total_owners}/{total_audits}"
            )
            offset += CH_FETCH_SIZE
            chunk_idx = offset // CH_FETCH_SIZE
            if chunk_idx % JEMALLOC_PURGE_INTERVAL == 0:
                try:
                    client.command("SYSTEM JEMALLOC PURGE")
                except Exception:
                    pass
            time.sleep(CHUNK_SLEEP_SEC + random.uniform(0, CHUNK_SLEEP_JITTER))

        # Flush any remaining rows in buffers
        prop_buf.flush()
        unit_buf.flush()
        owner_buf.flush()
        audit_buf.flush()

        counts = {
            'properties': total_props,
            'units': total_units,
            'owners': total_owners,
            'audits': total_audits,
        }
        logger.info(f"Property processing complete: {counts}")
        return counts

    finally:
        log_clickhouse_query_stats(client, log_comment)
        log_clickhouse_select_breakdown(client, log_comment)
        log_resource_summary(ti.task_id, t0, op_stats)
        client.close()


def transform_load_demand_events(**context):
    """For each chunk: extract from ClickHouse → transform → load into silver table.

    Only one chunk (STREAM_BATCH_SIZE rows) of raw JSON is in memory at a time.
    Each chunk is transformed and inserted before the next chunk is fetched.
    No data accumulation, no XCom bloat.
    """
    ti = context['ti']
    metadata = ti.xcom_pull(task_ids='extract_demand_events')

    total_count = metadata['total_count']
    if total_count == 0:
        logger.info("No demand events to process")
        return {'demands': 0}

    ws = datetime.fromisoformat(metadata['window_start'])
    we = datetime.fromisoformat(metadata['window_end'])

    t0 = time.monotonic()
    op_stats = OpStats()
    log_comment = f"{ti.task_id}:{ti.run_id}"
    logger.info(f"Processing {total_count} demand events | fetch={CH_FETCH_SIZE}/insert={STREAM_BATCH_SIZE} | base_memory={peak_rss_mib()} MiB")

    client = instrument_client(get_client(), log_comment, op_stats)
    try:
        total_demands = 0
        offset = 0
        demand_buf = InsertBuffer(client, 'demand_with_details_entity')

        try:
            client.command("SYSTEM JEMALLOC PURGE")
        except Exception:
            pass
        time.sleep(random.uniform(0, TASK_START_JITTER))

        while offset < total_count:
            # -- EXTRACT: small fetch → low concurrent ClickHouse SELECT memory --
            raw_jsons = fetch_demand_events(client, ws, we,
                                            limit=CH_FETCH_SIZE, offset=offset)
            if not raw_jsons:
                break

            # -- TRANSFORM: parse JSON, extract fields --
            demand_rows = []

            for raw_json in raw_jsons:
                try:
                    event = json.loads(raw_json)
                except json.JSONDecodeError:
                    logger.warning("Skipping invalid JSON")
                    continue

                demands_list = event.get('Demands', []) or []
                demand = demands_list[0] if demands_list else {}
                if not demand.get('id', ''):
                    continue

                demand_rows.append(extract_demand(demand))

            # -- LOAD: buffer accumulates; flushes in STREAM_BATCH_SIZE chunks --
            chunk_len = len(raw_jsons)
            del raw_jsons
            n_demands = len(demand_rows); total_demands += n_demands
            demand_buf.add(demand_rows); demand_rows.clear()
            gc.collect()

            logger.info(f"Chunk {offset}-{offset + chunk_len}: {n_demands} demands | Total: {total_demands}")
            offset += CH_FETCH_SIZE
            chunk_idx = offset // CH_FETCH_SIZE
            if chunk_idx % JEMALLOC_PURGE_INTERVAL == 0:
                try:
                    client.command("SYSTEM JEMALLOC PURGE")
                except Exception:
                    pass
            time.sleep(CHUNK_SLEEP_SEC + random.uniform(0, CHUNK_SLEEP_JITTER))

        demand_buf.flush()

        logger.info(f"Demand processing complete: {total_demands} rows")
        return {'demands': total_demands}

    finally:
        log_clickhouse_query_stats(client, log_comment)
        log_resource_summary(ti.task_id, t0, op_stats)
        client.close()


def transform_load_payment_events(**context):
    """For each chunk: extract from ClickHouse → transform → load into silver table.

    Only one chunk (STREAM_BATCH_SIZE rows) of raw JSON is in memory at a time.
    Each chunk is transformed and inserted before the next chunk is fetched.
    No data accumulation, no XCom bloat.
    """
    ti = context['ti']
    metadata = ti.xcom_pull(task_ids='extract_payment_events')

    total_count = metadata['total_count']
    if total_count == 0:
        logger.info("No payment events to process")
        return {'payments': 0}

    ws = datetime.fromisoformat(metadata['window_start'])
    we = datetime.fromisoformat(metadata['window_end'])

    t0 = time.monotonic()
    op_stats = OpStats()
    log_comment = f"{ti.task_id}:{ti.run_id}"
    logger.info(f"Processing {total_count} payment events | fetch={CH_FETCH_SIZE}/insert={STREAM_BATCH_SIZE} | base_memory={peak_rss_mib()} MiB")

    client = instrument_client(get_client(), log_comment, op_stats)
    try:
        total_payments = 0
        offset = 0
        payment_buf = InsertBuffer(client, 'payment_with_details_entity')

        try:
            client.command("SYSTEM JEMALLOC PURGE")
        except Exception:
            pass
        time.sleep(random.uniform(0, TASK_START_JITTER))

        while offset < total_count:
            # -- EXTRACT: small fetch → low concurrent ClickHouse SELECT memory --
            raw_jsons = fetch_payment_events(client, ws, we,
                                             limit=CH_FETCH_SIZE, offset=offset)
            if not raw_jsons:
                break

            # -- TRANSFORM: parse JSON, extract fields --
            payment_rows = []

            for raw_json in raw_jsons:
                try:
                    event = json.loads(raw_json)
                except json.JSONDecodeError:
                    logger.warning("Skipping invalid JSON")
                    continue

                payment = event.get('Payment', {}) or {}
                if not payment.get('id', ''):
                    continue

                payment_rows.append(extract_payment(payment))

            # -- LOAD: buffer accumulates; flushes in STREAM_BATCH_SIZE chunks --
            chunk_len = len(raw_jsons)
            del raw_jsons
            n_payments = len(payment_rows); total_payments += n_payments
            payment_buf.add(payment_rows); payment_rows.clear()
            gc.collect()

            logger.info(f"Chunk {offset}-{offset + chunk_len}: {n_payments} payments | Total: {total_payments}")
            offset += CH_FETCH_SIZE
            chunk_idx = offset // CH_FETCH_SIZE
            if chunk_idx % JEMALLOC_PURGE_INTERVAL == 0:
                try:
                    client.command("SYSTEM JEMALLOC PURGE")
                except Exception:
                    pass
            time.sleep(CHUNK_SLEEP_SEC + random.uniform(0, CHUNK_SLEEP_JITTER))

        payment_buf.flush()

        logger.info(f"Payment processing complete: {total_payments} rows")
        return {'payments': total_payments}

    finally:
        log_clickhouse_query_stats(client, log_comment)
        log_resource_summary(ti.task_id, t0, op_stats)
        client.close()


def extract_bill_events(**context):
    """Count bill events and pass window metadata via XCom.

    Only passes lightweight metadata (window timestamps + total count).
    No raw data is loaded into memory or XCom.
    """
    window_start, window_end = get_window(context)
    logger.info(f"Run type: {context['dag_run'].run_type}")
    logger.info(f"Logical date: {context['dag_run'].logical_date}")
    logger.info(f"Bill extract window: [{window_start}, {window_end})")

    t0 = time.monotonic()
    op_stats = OpStats()
    log_comment = f"{context['ti'].task_id}:{context['ti'].run_id}"
    client = instrument_client(get_client(), log_comment, op_stats)
    try:
        total_count = count_bill_events(client, window_start, window_end)
        logger.info(f"Bill events found: {total_count}")

        return {
            'total_count': total_count,
            'window_start': window_start.isoformat(),
            'window_end': window_end.isoformat(),
        }

    finally:
        log_resource_summary(context['ti'].task_id, t0, op_stats)
        client.close()


def transform_load_bill_events(**context):
    """For each chunk: extract from ClickHouse -> transform -> load into silver tables.

    Each raw bill event carries a list of bills under the 'bills' key.
    For every bill we write to two silver tables:
      - bill_entity               (one row per bill)
      - bill_detail_entity        (one row per billDetail)

    Only one chunk (STREAM_BATCH_SIZE rows) of raw JSON is in memory at a time.
    """
    ti = context['ti']
    metadata = ti.xcom_pull(task_ids='extract_bill_events')

    total_count = metadata['total_count']
    if total_count == 0:
        logger.info("No bill events to process")
        return {'bills': 0, 'bill_details': 0}

    ws = datetime.fromisoformat(metadata['window_start'])
    we = datetime.fromisoformat(metadata['window_end'])

    t0 = time.monotonic()
    op_stats = OpStats()
    log_comment = f"{ti.task_id}:{ti.run_id}"
    logger.info(f"Processing {total_count} bill events | fetch={CH_FETCH_SIZE}/insert={STREAM_BATCH_SIZE} | base_memory={peak_rss_mib()} MiB")

    client = instrument_client(get_client(), log_comment, op_stats)
    try:
        total_bills = 0
        total_details = 0
        offset = 0
        bill_buf = InsertBuffer(client, 'bill_entity')
        detail_buf = InsertBuffer(client, 'bill_detail_entity')

        try:
            client.command("SYSTEM JEMALLOC PURGE")
        except Exception:
            pass
        time.sleep(random.uniform(0, TASK_START_JITTER))

        while offset < total_count:
            # -- EXTRACT: small fetch → low concurrent ClickHouse SELECT memory --
            raw_jsons = fetch_bill_events(client, ws, we,
                                          limit=CH_FETCH_SIZE, offset=offset)
            if not raw_jsons:
                break

            # -- TRANSFORM: parse JSON, extract fields --
            bill_rows = []
            detail_rows = []

            seen_bill_ids: set = set()

            for raw_json in raw_jsons:
                try:
                    event = json.loads(raw_json)
                except json.JSONDecodeError:
                    logger.warning("Skipping invalid JSON")
                    continue

                # Bill is embedded in Payment.paymentDetails[n].bill
                payment = event.get('Payment', {}) or {}
                for pd in (payment.get('paymentDetails', []) or []):
                    bill = pd.get('bill', {}) or {}
                    bill_id = bill.get('id', '')
                    if not bill_id or bill_id in seen_bill_ids:
                        continue
                    seen_bill_ids.add(bill_id)
                    bill_rows.append(extract_bill(bill))
                    detail_rows.extend(extract_bill_details(bill))

            # -- LOAD: buffer accumulates; flushes in STREAM_BATCH_SIZE chunks --
            chunk_len = len(raw_jsons)
            del raw_jsons
            n_bills = len(bill_rows); total_bills += n_bills
            n_details = len(detail_rows); total_details += n_details

            bill_buf.add(bill_rows); bill_rows.clear()
            detail_buf.add(detail_rows); detail_rows.clear()

            gc.collect()

            logger.info(
                f"Chunk {offset}-{offset + chunk_len}: "
                f"{n_bills} bills, {n_details} details | "
                f"Total: {total_bills}/{total_details}"
            )
            offset += CH_FETCH_SIZE
            chunk_idx = offset // CH_FETCH_SIZE
            if chunk_idx % JEMALLOC_PURGE_INTERVAL == 0:
                try:
                    client.command("SYSTEM JEMALLOC PURGE")
                except Exception:
                    pass
            time.sleep(CHUNK_SLEEP_SEC + random.uniform(0, CHUNK_SLEEP_JITTER))

        bill_buf.flush()
        detail_buf.flush()

        counts = {
            'bills': total_bills,
            'bill_details': total_details,
        }
        logger.info(f"Bill processing complete: {counts}")
        return counts

    finally:
        log_clickhouse_query_stats(client, log_comment)
        log_resource_summary(ti.task_id, t0, op_stats)
        client.close()


# -- DAG definition -----------------------------------------------------------


def extract_assessment_events(**context):
    """Count assessment events and pass window metadata via XCom.

    Only passes lightweight metadata (window timestamps + total count).
    No raw data is loaded into memory or XCom.
    """
    window_start, window_end = get_window(context)
    logger.info(f"Run type: {context['dag_run'].run_type}")
    logger.info(f"Logical date: {context['dag_run'].logical_date}")
    logger.info(f"Assessment extract window: [{window_start}, {window_end})")

    t0 = time.monotonic()
    op_stats = OpStats()
    log_comment = f"{context['ti'].task_id}:{context['ti'].run_id}"
    client = instrument_client(get_client(), log_comment, op_stats)
    try:
        total_count = count_assessment_events(client, window_start, window_end)
        logger.info(f"Assessment events found: {total_count}")

        return {
            'total_count': total_count,
            'window_start': window_start.isoformat(),
            'window_end': window_end.isoformat(),
        }

    finally:
        log_resource_summary(context['ti'].task_id, t0, op_stats)
        client.close()


def transform_load_assessment_events(**context):
    """For each chunk: extract from ClickHouse -> transform -> load into silver table.

    Each raw assessment event maps directly to one property_assessment_entity row.
    The event payload is the assessment itself (no nested wrapper key).
    Only one chunk (STREAM_BATCH_SIZE rows) of raw JSON is in memory at a time.
    """
    ti = context['ti']
    metadata = ti.xcom_pull(task_ids='extract_assessment_events')

    total_count = metadata['total_count']
    if total_count == 0:
        logger.info("No assessment events to process")
        return {'assessments': 0}

    ws = datetime.fromisoformat(metadata['window_start'])
    we = datetime.fromisoformat(metadata['window_end'])

    t0 = time.monotonic()
    op_stats = OpStats()
    log_comment = f"{ti.task_id}:{ti.run_id}"
    logger.info(f"Processing {total_count} assessment events | fetch={CH_FETCH_SIZE}/insert={STREAM_BATCH_SIZE} | base_memory={peak_rss_mib()} MiB")

    client = instrument_client(get_client(), log_comment, op_stats)
    try:
        total_assessments = 0
        offset = 0
        assessment_buf = InsertBuffer(client, 'property_assessment_entity')

        try:
            client.command("SYSTEM JEMALLOC PURGE")
        except Exception:
            pass
        time.sleep(random.uniform(0, TASK_START_JITTER))

        while offset < total_count:
            # -- EXTRACT: small fetch → low concurrent ClickHouse SELECT memory --
            raw_jsons = fetch_assessment_events(client, ws, we,
                                                limit=CH_FETCH_SIZE, offset=offset)
            if not raw_jsons:
                break

            # -- TRANSFORM: parse JSON, extract fields --
            assessment_rows = []

            for raw_json in raw_jsons:
                try:
                    event = json.loads(raw_json)
                except json.JSONDecodeError:
                    logger.warning("Skipping invalid JSON")
                    continue

                assessment = event.get('Assessment', {}) or {}
                if not assessment.get('assessmentNumber', ''):
                    continue

                assessment_rows.append(extract_assessment(assessment))

            # -- LOAD: buffer accumulates; flushes in STREAM_BATCH_SIZE chunks --
            chunk_len = len(raw_jsons)
            del raw_jsons
            n_assessments = len(assessment_rows); total_assessments += n_assessments
            assessment_buf.add(assessment_rows); assessment_rows.clear()
            gc.collect()

            logger.info(
                f"Chunk {offset}-{offset + chunk_len}: "
                f"{n_assessments} assessments | Total: {total_assessments}"
            )
            offset += CH_FETCH_SIZE
            chunk_idx = offset // CH_FETCH_SIZE
            if chunk_idx % JEMALLOC_PURGE_INTERVAL == 0:
                try:
                    client.command("SYSTEM JEMALLOC PURGE")
                except Exception:
                    pass
            time.sleep(CHUNK_SLEEP_SEC + random.uniform(0, CHUNK_SLEEP_JITTER))

        assessment_buf.flush()

        logger.info(f"Assessment processing complete: {total_assessments} rows")
        return {'assessments': total_assessments}

    finally:
        log_clickhouse_query_stats(client, log_comment)
        log_resource_summary(ti.task_id, t0, op_stats)
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

    # Property pipeline: Extract -> Transform+Load (2 pods)
    extract_props = PythonOperator(
        task_id='extract_property_events',
        python_callable=extract_property_events,
    )
    transform_load_props = PythonOperator(
        task_id='transform_load_property_events',
        python_callable=transform_load_property_events,
    )

    # Demand pipeline: Extract -> Transform+Load (2 pods)
    extract_demands = PythonOperator(
        task_id='extract_demand_events',
        python_callable=extract_demand_events,
    )
    transform_load_demands = PythonOperator(
        task_id='transform_load_demand_events',
        python_callable=transform_load_demand_events,
    )

    trigger_rmv_refresh = TriggerDagRunOperator(
        task_id='trigger_rmv_refresh',
        trigger_dag_id='clickhouse_rmv_parallel_refresh',
        wait_for_completion=False,
    )

    end = EmptyOperator(task_id='end')
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    
    # Payment pipeline: Extract -> Transform+Load
    extract_payments = PythonOperator(
        task_id='extract_payment_events',
        python_callable=extract_payment_events,
    )
    transform_load_payments = PythonOperator(
        task_id='transform_load_payment_events',
        python_callable=transform_load_payment_events,
    )

    # Bill pipeline: Extract -> Transform+Load
    extract_bills = PythonOperator(
        task_id='extract_bill_events',
        python_callable=extract_bill_events,
    )
    transform_load_bills = PythonOperator(
        task_id='transform_load_bill_events',
        python_callable=transform_load_bill_events,
    )

    # Assessment pipeline: Extract -> Transform+Load
    extract_assessments = PythonOperator(
        task_id='extract_assessment_events',
        python_callable=extract_assessment_events,
    )
    transform_load_assessments = PythonOperator(
        task_id='transform_load_assessment_events',
        python_callable=transform_load_assessment_events,
    )

    # Property pipeline
    start >> extract_props >> transform_load_props
    # Demand pipeline (runs in parallel)
    start >> extract_demands >> transform_load_demands
    # Payment pipeline (runs in parallel)
    start >> extract_payments >> transform_load_payments
    # Bill pipeline (runs in parallel)
    start >> extract_bills >> transform_load_bills
    # Assessment pipeline (runs in parallel)
    start >> extract_assessments >> transform_load_assessments

    # Fan-in: all pipelines must complete before triggering downstream refresh
    [
        transform_load_props,
        transform_load_demands,
        transform_load_payments,
        transform_load_bills,
        transform_load_assessments,
    ] >> trigger_rmv_refresh >> end