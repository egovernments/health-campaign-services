"""
ClickHouse Refreshable Materialized View (RMV) Sequential Refresh DAG

Triggers 7 RMVs in the replacing_test database in sequence, polling each
until it completes (or fails) before moving to the next.

If an MV fails, the error is recorded and the remaining MVs are still
refreshed. The task fails at the end if any MV had an error.

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

# -- Configuration -----------------------------------------------------------

CLICKHOUSE_HOST = os.getenv('CLICKHOUSE_HOST', 'clickstack-clickhouse.clickhouse.svc.cluster.local')
CLICKHOUSE_PORT = int(os.getenv('CLICKHOUSE_PORT', '8123'))
CLICKHOUSE_USER = os.getenv('CLICKHOUSE_USER', 'default')
CLICKHOUSE_PASSWORD = os.getenv('CLICKHOUSE_PASSWORD', '')
CLICKHOUSE_DB = os.getenv('CLICKHOUSE_DB', 'replacing_test')

POLL_INTERVAL_SECONDS = int(os.getenv('RMV_POLL_INTERVAL', '60'))
REFRESH_TIMEOUT_SECONDS = int(os.getenv('RMV_REFRESH_TIMEOUT', '3600'))

REFRESH_ORDER = [
    'rmv_mart_collections_by_fy',
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
        },
    )


def wait_for_refresh(client, view_name):
    """Poll system.view_refreshes until the MV finishes. Returns error string or None."""
    start_time = time.time()

    while True:
        elapsed = time.time() - start_time
        if elapsed > REFRESH_TIMEOUT_SECONDS:
            return f"Timeout after {REFRESH_TIMEOUT_SECONDS}s"

        rows = client.query(
            "SELECT status, exception, progress "
            "FROM system.view_refreshes "
            "WHERE database = {db:String} AND view = {v:String}",
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
            return f"{exception}"

        if progress == 1:
            logger.info(
                f"{view_name}: Refresh completed successfully "
                f"in {int(elapsed)}s"
            )
            return None

        logger.info(
            f"{view_name}: status={status}, progress={progress}, "
            f"exception='{exception}', elapsed={int(elapsed)}s. Waiting..."
        )
        time.sleep(POLL_INTERVAL_SECONDS)


def refresh_all_mvs(**context):
    """Refresh all MVs in sequence. Continues on failure, fails at the end."""
    client = get_client()
    failed = {}
    succeeded = []

    try:
        for view_name in REFRESH_ORDER:
            logger.info(f"--- Triggering refresh for {CLICKHOUSE_DB}.{view_name} ---")
            try:
                client.command(f"SYSTEM REFRESH VIEW {CLICKHOUSE_DB}.{view_name}")
            except Exception as e:
                logger.error(f"{view_name}: Failed to trigger refresh: {e}")
                failed[view_name] = f"Trigger failed: {e}"
                continue

            logger.info(f"{view_name}: Refresh triggered, polling for completion...")
            error = wait_for_refresh(client, view_name)

            if error:
                logger.error(f"{view_name}: FAILED - {error}")
                failed[view_name] = error
            else:
                succeeded.append(view_name)

        logger.info("=" * 60)
        logger.info(f"Succeeded: {len(succeeded)}/{len(REFRESH_ORDER)}")
        for v in succeeded:
            logger.info(f"  OK: {v}")

        if failed:
            logger.error(f"Failed: {len(failed)}/{len(REFRESH_ORDER)}")
            for v, err in failed.items():
                logger.error(f"  FAIL: {v} - {err}")

            raise AirflowException(
                f"{len(failed)} MV(s) failed: "
                + ", ".join(f"{v} ({err})" for v, err in failed.items())
            )

        logger.info("All MVs refreshed successfully")

    finally:
        client.close()


# -- DAG definition -----------------------------------------------------------

with DAG(
    'clickhouse_rmv_sequential_refresh',
    default_args=default_args,
    description='Sequentially refresh ClickHouse RMVs and poll for completion',
    schedule=None,
    start_date=make_aware(datetime(2025, 1, 1)),
    catchup=False,
    tags=['clickhouse', 'rmv', 'materialized_view', 'refresh'],
    max_active_runs=1,
) as dag:

    start = EmptyOperator(task_id='start')

    refresh = PythonOperator(
        task_id='refresh_all_rmvs',
        python_callable=refresh_all_mvs,
    )

    end = EmptyOperator(task_id='end')

    start >> refresh >> end
