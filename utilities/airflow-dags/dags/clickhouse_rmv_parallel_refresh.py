"""
ClickHouse Refreshable Materialized View (RMV) Parallel Refresh DAG

Triggers all RMVs in the clickhouse database in parallel.
Each MV refresh runs as an independent Airflow task.

If an MV fails, only that task fails.
The DAG completes after all tasks finish.

Schedule: Manual trigger only
Timeout:  1 hour per MV refresh
Poll:     Every 60 seconds
"""

import os
import time
import logging
import resource
from datetime import datetime

from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.operators.empty import EmptyOperator
from airflow.exceptions import AirflowException
from airflow.utils.timezone import make_aware
import clickhouse_connect

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

CLICKHOUSE_HOST = os.getenv('CLICKHOUSE_HOST', 'clickhouse-clickstack-clickhouse-clickhouse-headless.clickhouse.svc.cluster.local')
CLICKHOUSE_PORT = int(os.getenv('CLICKHOUSE_PORT', '8123'))
CLICKHOUSE_USER = os.getenv('CLICKHOUSE_USER', 'default')
CLICKHOUSE_PASSWORD = os.getenv('CLICKHOUSE_PASSWORD', 'egov')
CLICKHOUSE_DB = os.getenv('CLICKHOUSE_DB', 'punjab_property_tax')

POLL_INTERVAL_SECONDS = int(os.getenv('RMV_POLL_INTERVAL', '60'))
REFRESH_TIMEOUT_SECONDS = int(os.getenv('RMV_REFRESH_TIMEOUT', '3600'))

REFRESH_VIEWS = [
    # Property
    'rmv_mart_active_property_distribution_summary',
    'rmv_mart_new_properties_by_fy',
    # Demand
    'rmv_mart_demand_and_collection_summary',
    'rmv_mart_collections_by_month',
    'rmv_mart_properties_with_demand_by_fy',
    'rmv_mart_defaulters',
    # Change metrics (base layer — dependents run after)
    'rmv_property_change_metrics',
    # Assessment / Payment / Rebate
    'rmv_mart_assessment_summary_by_fy',
    'rmv_mart_payment_summary_by_fy',
    'rmv_mart_rebate_summary_by_fy',
    'rmv_mart_daily_collection_summary',
    'rmv_mart_property_payment_mode_by_fy',
    'rmv_mart_collection_by_transaction_fy_and_demand_fy',
]

# Views that depend on other views completing first.
# Key: view name, Value: list of views it depends on.
DEPENDENT_VIEWS = {
    # Risk summaries read from mart_property_change_metrics
    'rmv_property_risk_summary': ['rmv_property_change_metrics'],
    'rmv_property_changes_by_fy': ['rmv_property_change_metrics'],
    # Demand vs assessed reads from mart_properties_with_demand_by_fy
    'rmv_property_demand_vs_assessed_by_fy': ['rmv_mart_properties_with_demand_by_fy'],
}

default_args = {
    'owner': 'property_tax',
    'depends_on_past': False,
    'email_on_failure': False,
    'retries': 0,
}

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

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
        },
    )


def get_row_count(client, view_name):
    """Return the number of rows currently stored in the view's target table."""
    try:
        rows = client.query(
            f"SELECT count() FROM `{CLICKHOUSE_DB}`.`{view_name}`"
        ).result_rows
        return rows[0][0] if rows else None
    except Exception as exc:  # noqa: BLE001 - counting must never fail the task
        logger.warning(f"{view_name}: could not read row count ({exc})")
        return None


def _read_first_int(path):
    """Read a single integer from a file (e.g. a cgroup stat). None on failure."""
    try:
        with open(path) as f:
            return int(f.read().strip())
    except Exception:  # noqa: BLE001 - file may be absent or hold 'max'
        return None


def get_clickhouse_memory(client, view_name):
    """
    Memory used on the ClickHouse server.

    Reports memory of any query currently running for this view (from
    system.processes) plus the server-wide tracked memory (system.metrics).
    Never raises - monitoring must not fail the refresh task.
    """
    parts = []
    try:
        rows = client.query(
            """
            SELECT count(), sum(memory_usage), max(peak_memory_usage)
            FROM system.processes
            WHERE query ILIKE {pat:String}
            """,
            parameters={'pat': f'%{view_name}%'},
        ).result_rows
        if rows and rows[0][0]:
            n, cur, peak = rows[0]
            parts.append(
                f"{n} running query(s): current {cur / 1024 / 1024:.0f} MiB, "
                f"peak {peak / 1024 / 1024:.0f} MiB"
            )
    except Exception as exc:  # noqa: BLE001
        logger.warning(f"{view_name}: could not read query memory ({exc})")

    try:
        rows = client.query(
            "SELECT value FROM system.metrics WHERE metric = 'MemoryTracking'"
        ).result_rows
        if rows:
            parts.append(f"server total {int(rows[0][0]) / 1024 / 1024:.0f} MiB")
    except Exception as exc:  # noqa: BLE001
        logger.warning(f"{view_name}: could not read server memory ({exc})")

    return ", ".join(parts) if parts else None


def get_pod_memory():
    """
    Memory used by the Airflow task itself (the worker process / pod).

    Combines the process RSS with the container's cgroup memory so you can see
    both what this task consumes and how close the pod is to its limit.
    Never raises.
    """
    parts = []

    # Peak resident memory of this process (ru_maxrss is in KiB on Linux)
    try:
        peak_kb = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
        parts.append(f"process peak memory {peak_kb / 1024:.0f} MiB")
    except Exception:  # noqa: BLE001
        pass

    # Current resident memory from /proc/self/status (VmRSS is in KiB)
    try:
        with open('/proc/self/status') as f:
            for line in f:
                if line.startswith('VmRSS:'):
                    parts.append(f"process memory in use {int(line.split()[1]) / 1024:.0f} MiB")
                    break
    except Exception:  # noqa: BLE001
        pass

    # Container/pod memory from cgroup (v2 first, then v1)
    cur = _read_first_int('/sys/fs/cgroup/memory.current')          # cgroup v2
    lim = _read_first_int('/sys/fs/cgroup/memory.max')              # 'max' -> None
    if cur is None:
        cur = _read_first_int('/sys/fs/cgroup/memory/memory.usage_in_bytes')  # v1
        lim = _read_first_int('/sys/fs/cgroup/memory/memory.limit_in_bytes')
    if cur is not None:
        s = f"pod memory {cur / 1024 / 1024:.0f} MiB"
        # cgroup v1 reports a huge sentinel value when there is no real limit
        if lim and lim < (1 << 62):
            s += f" / {lim / 1024 / 1024:.0f} MiB limit"
        parts.append(s)

    return ", ".join(parts) if parts else "memory usage unavailable"


def wait_for_refresh(client, view_name):
    """Poll system.view_refreshes until the MV finishes."""

    start_time = time.time()

    while True:
        elapsed = time.time() - start_time

        if elapsed > REFRESH_TIMEOUT_SECONDS:
            return f"Timeout after {REFRESH_TIMEOUT_SECONDS}s"

        rows = client.query(
            """
            SELECT status, exception, progress,
                   read_rows, total_rows, written_rows, written_bytes
            FROM system.view_refreshes
            WHERE database = {db:String} AND view = {v:String}
            """,
            parameters={'db': CLICKHOUSE_DB, 'v': view_name},
        ).result_rows

        if not rows:
            return f"No entry in system.view_refreshes for {CLICKHOUSE_DB}.{view_name}"

        (status, exception, progress,
         read_rows, total_rows, written_rows, written_bytes) = rows[0]

        if status == 'Running':
            logger.info(
                f"{view_name}: Running (progress={progress:.1%}, "
                f"read {read_rows:,}/{total_rows:,} rows, "
                f"written {written_rows:,} rows / {written_bytes:,} bytes, "
                f"elapsed={int(elapsed)}s)"
            )
            ch_mem = get_clickhouse_memory(client, view_name)
            if ch_mem:
                logger.info(f"{view_name}: ClickHouse memory - {ch_mem}")
            logger.info(f"{view_name}: Airflow task - {get_pod_memory()}")
            time.sleep(POLL_INTERVAL_SECONDS)
            continue

        if exception:
            return exception

        if progress == 1:
            logger.info(
                f"{view_name}: Refresh completed in {int(elapsed)}s "
                f"(read {read_rows:,} rows, "
                f"written {written_rows:,} rows / {written_bytes:,} bytes)"
            )
            return None

        time.sleep(POLL_INTERVAL_SECONDS)


# ---------------------------------------------------------------------------
# Per-view refresh function (NEW)
# ---------------------------------------------------------------------------

def refresh_single_mv(view_name, **context):
    """
    Refresh a single RMV.
    Runs as an independent Airflow task.
    """

    logger.info(f"Starting refresh for {CLICKHOUSE_DB}.{view_name}")

    client = get_client()

    try:
        # Row count before refresh
        rows_before = get_row_count(client, view_name)
        logger.info(f"{view_name}: {rows_before:,} rows before refresh"
                    if rows_before is not None
                    else f"{view_name}: row count before refresh unavailable")

        # Trigger refresh
        client.command(f"SYSTEM REFRESH VIEW `{CLICKHOUSE_DB}`.`{view_name}`")
        logger.info(f"{view_name}: Refresh triggered")

        # Wait for completion
        error = wait_for_refresh(client, view_name)

        if error:
            raise AirflowException(f"{view_name} failed: {error}")

        # Row count after refresh
        rows_after = get_row_count(client, view_name)
        if rows_after is not None:
            delta = (f" ({rows_after - rows_before:+,} vs before)"
                     if rows_before is not None else "")
            logger.info(f"{view_name}: {rows_after:,} rows after refresh{delta}")

        logger.info(f"{view_name}: Airflow task - {get_pod_memory()}")
        logger.info(f"{view_name}: SUCCESS")

    finally:
        client.close()


# ---------------------------------------------------------------------------
# DAG Definition
# ---------------------------------------------------------------------------

with DAG(
    dag_id='clickhouse_rmv_parallel_refresh',
    default_args=default_args,
    description='Parallel refresh of ClickHouse RMVs',
    schedule=None,
    start_date=make_aware(datetime(2025, 1, 1)),
    catchup=False,
    tags=['clickhouse', 'rmv', 'parallel', 'materialized_view'],
    max_active_runs=1,
) as dag:

    start = EmptyOperator(task_id='start')

    end = EmptyOperator(
        task_id='end',
        trigger_rule='all_done',  # ensures it runs even if some tasks fail
    )

    # -----------------------------------------------------------------------
    # Dynamically create one task per MV (parallel group)
    # -----------------------------------------------------------------------

    tasks = {}

    for view_name in REFRESH_VIEWS:

        task = PythonOperator(
            task_id=f'refresh_{view_name}',
            python_callable=refresh_single_mv,
            op_kwargs={'view_name': view_name},
        )

        start >> task >> end
        tasks[view_name] = task

    # -----------------------------------------------------------------------
    # Dependent views – wait for upstream MV refreshes before running
    # -----------------------------------------------------------------------

    for view_name, upstream_views in DEPENDENT_VIEWS.items():

        dep_task = PythonOperator(
            task_id=f'refresh_{view_name}',
            python_callable=refresh_single_mv,
            op_kwargs={'view_name': view_name},
        )

        for upstream in upstream_views:
            tasks[upstream] >> dep_task

        dep_task >> end