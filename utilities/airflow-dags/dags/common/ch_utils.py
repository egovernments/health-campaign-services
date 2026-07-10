"""Shared ClickHouse helpers for the property-tax pipelines.

Self-contained: this module does NOT import any DAG module, so it can be safely
imported by both DAGs and task modules (e.g. payment_backupdate) without pulling
a DAG into the import graph. The implementations mirror those originally defined
inline in property_tax_raw_to_silver.py.
"""

import os
import logging
from datetime import datetime, timedelta, timezone
from decimal import Decimal, InvalidOperation
from typing import List, Optional

from airflow.utils.timezone import utcnow, make_aware
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
CH_FETCH_SIZE = 2000       # SELECT fetch size: small → low concurrent ClickHouse SELECT memory

EPOCH = make_aware(datetime(1970, 1, 1))


# -- Client ------------------------------------------------------------------


def get_client():
    return clickhouse_connect.get_client(
        host=CLICKHOUSE_HOST,
        port=CLICKHOUSE_PORT,
        username=CLICKHOUSE_USER,
        password=CLICKHOUSE_PASSWORD,
        database=CLICKHOUSE_DB,
        settings={
            # Single-threaded reads keep peak RSS low when several pipelines read
            # concurrently; our pages are small so 1 thread costs ~nothing in speed.
            'max_threads': 1,
            # This ETL reads each block once, so the uncompressed cache gives no
            # hit-rate benefit — disable it so it can't accumulate server RSS.
            'use_uncompressed_cache': 0,
        },
    )


# -- Query helpers -----------------------------------------------------------


def run_query(client, query, parameters=None):
    return client.query(query, parameters=parameters)


def run_insert(client, table, data, column_names):
    client.insert(table, data=data, column_names=column_names)


# -- Parsing / coercion helpers ----------------------------------------------


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


# -- Window ------------------------------------------------------------------


def get_window(context):
    """Scheduled runs use Airflow's data interval; all other runs use a rolling 24h window."""
    dag_run = context.get("dag_run")

    if dag_run and dag_run.run_type == "scheduled":
        logger.info(f"context data_interval_start = {context.get('data_interval_start')}")
        logger.info(f"context data_interval_end   = {context.get('data_interval_end')}")
        return (
            context.get("data_interval_start"),
            context.get("data_interval_end"),
        )

    end_time = utcnow()
    start_time = end_time - timedelta(hours=24) + timedelta(milliseconds=1)
    logger.info(f"Manual window: [{start_time}, {end_time})")
    return start_time, end_time


# -- Insert helpers ----------------------------------------------------------


def batch_insert(client, table: str, rows: List[dict], chunk_size: int = STREAM_BATCH_SIZE):
    """Insert rows in chunks to manage memory and handle large datasets."""
    if not rows:
        return

    cols = list(rows[0].keys())
    for i in range(0, len(rows), chunk_size):
        chunk = rows[i:i + chunk_size]
        data = [[r.get(c) for c in cols] for r in chunk]
        try:
            run_insert(client, table, data, cols)
            logger.info(f"Inserted {len(chunk)} rows into {table}")
        except Exception as e:
            logger.error(f"Failed to insert chunk into {table} at offset {i}: {e}")
            raise


class InsertBuffer:
    """Accumulates transformed rows and flushes to ClickHouse in flush_size chunks.

    Decouples the ClickHouse SELECT fetch size (small) from the INSERT batch size
    (large): keeps concurrent SELECT memory low while minimising ClickHouse parts.
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


# -- Raw event fetch ----------------------------------------------------------


def fetch_raw_events(client, table: str, window_start: datetime,
                     window_end: datetime, limit: int,
                     last_ms: int = None,
                     last_id: str = None) -> List[tuple]:
    """Fetch one keyset page of raw events from `table` in [window_start, window_end).

    Cursor-based on the (event_time, id) sort key: each page continues strictly
    after the last (event_time, id) seen. Because that key is unique the read stays
    an index seek that always advances and never skips/duplicates rows on event_time
    ties. The cursor timestamp is carried as integer ms (toUnixTimestamp64Milli) and
    rebuilt with fromUnixTimestamp64Milli so the DateTime64(3) comparison stays exact.

    Returns (raw, event_time, id, et_ms) tuples ordered by (event_time, id).
    """
    query = (
        "SELECT raw, event_time, id, toUnixTimestamp64Milli(event_time) AS et_ms "
        f"FROM {table} "
        "WHERE event_time >= {start:DateTime64(3)} "
        "AND event_time < {end:DateTime64(3)} "
    )

    params = {'start': window_start, 'end': window_end, 'limit': limit}

    if last_ms is not None:
        query += ("AND (event_time, id) > "
                  "(fromUnixTimestamp64Milli({last_ms:Int64}), {last_id:UUID}) ")
        params['last_ms'] = last_ms
        params['last_id'] = str(last_id)

    query += "ORDER BY event_time, id LIMIT {limit:UInt64}"

    result = run_query(client, query, parameters=params)
    return result.result_rows
