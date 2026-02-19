"""
ClickHouse Refreshable Materialized View (RMV) Parallel Refresh DAG

Triggers all RMVs in the airflow_test database in parallel.
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

CLICKHOUSE_HOST = os.getenv(
    'CLICKHOUSE_HOST',
    'clickstack-clickhouse.clickhouse.svc.cluster.local'
)
CLICKHOUSE_PORT = int(os.getenv('CLICKHOUSE_PORT', '8123'))
CLICKHOUSE_USER = os.getenv('CLICKHOUSE_USER', 'default')
CLICKHOUSE_PASSWORD = os.getenv('CLICKHOUSE_PASSWORD', '')
CLICKHOUSE_DB = os.getenv('CLICKHOUSE_DB', 'airflow_test')

POLL_INTERVAL_SECONDS = int(os.getenv('RMV_POLL_INTERVAL', '60'))
REFRESH_TIMEOUT_SECONDS = int(os.getenv('RMV_REFRESH_TIMEOUT', '3600'))

REFRESH_VIEWS = [
    'rmv_mart_demand_values_by_fy',
    'rmv_mart_collections_by_month',
    'rmv_mart_defaulters',
    'rmv_mart_new_properties_by_fy',
    'rmv_mart_properties_with_demand_by_fy',
    'rmv_mart_property_agg',
]

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


def wait_for_refresh(client, view_name):
    """Poll system.view_refreshes until the MV finishes."""

    start_time = time.time()

    while True:
        elapsed = time.time() - start_time

        if elapsed > REFRESH_TIMEOUT_SECONDS:
            return f"Timeout after {REFRESH_TIMEOUT_SECONDS}s"

        rows = client.query(
            """
            SELECT status, exception, progress
            FROM system.view_refreshes
            WHERE database = {db:String} AND view = {v:String}
            """,
            parameters={'db': CLICKHOUSE_DB, 'v': view_name},
        ).result_rows

        if not rows:
            return f"No entry in system.view_refreshes for {CLICKHOUSE_DB}.{view_name}"

        status, exception, progress = rows[0]

        if status == 'Running':
            logger.info(
                f"{view_name}: Running (progress={progress}, "
                f"elapsed={int(elapsed)}s)"
            )
            time.sleep(POLL_INTERVAL_SECONDS)
            continue

        if exception:
            return exception

        if progress == 1:
            logger.info(
                f"{view_name}: Refresh completed in {int(elapsed)}s"
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
        # Trigger refresh
        client.command(f"SYSTEM REFRESH VIEW {CLICKHOUSE_DB}.{view_name}")
        logger.info(f"{view_name}: Refresh triggered")

        # Wait for completion
        error = wait_for_refresh(client, view_name)

        if error:
            raise AirflowException(f"{view_name} failed: {error}")

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
    # Dynamically create one task per MV
    # -----------------------------------------------------------------------

    for view_name in REFRESH_VIEWS:

        task = PythonOperator(
            task_id=f'refresh_{view_name}',
            python_callable=refresh_single_mv,
            op_kwargs={'view_name': view_name},
        )

        start >> task >> end
