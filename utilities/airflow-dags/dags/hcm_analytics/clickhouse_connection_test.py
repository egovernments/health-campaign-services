"""Manual smoke test for the ClickHouse Airflow Connection.

Trigger it by hand. With no conf it tests "clickhouse_default"; to test a
different Connection, trigger with conf: {"conn_id": "<your_conn_id>"}.

It builds the client through clickhouse_utils.get_clickhouse_client, the same
code path the raw-to-bronze and bronze-to-silver DAGs use, so a green run here
means those DAGs can reach ClickHouse with this Connection.
"""
from __future__ import annotations

import os
import sys

from airflow import DAG
from airflow.decorators import task
from airflow.sdk import get_current_context
from pendulum import datetime

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))


with DAG(
    dag_id="clickhouse_connection_test",
    schedule=None,
    start_date=datetime(2026, 1, 1),
    catchup=False,
    max_active_runs=1,
    tags=["clickhouse-analytics", "clickhouse", "test"],
) as dag:

    @task
    def test_clickhouse_connection():
        # Imported inside the task so a missing clickhouse_connect package
        # fails this task with a clear error rather than a DAG import error.
        from airflow.hooks.base import BaseHook

        from clickhouse_utils import DEFAULT_CLICKHOUSE_CONN_ID, get_clickhouse_client

        context = get_current_context()
        conf = (context["dag_run"].conf or {}) if context.get("dag_run") else {}
        conn_id = conf.get("conn_id", DEFAULT_CLICKHOUSE_CONN_ID)

        conn = BaseHook.get_connection(conn_id)
        print(
            f"Connection '{conn_id}': host={conn.host} port={conn.port or 8123} "
            f"user={conn.login} database={conn.schema or 'analytics'}"
        )

        client = get_clickhouse_client(conn_id)

        if not client.ping():
            raise RuntimeError(f"ClickHouse ping failed for connection '{conn_id}'")

        row = client.query(
            "SELECT version(), currentUser(), currentDatabase(), now()"
        ).result_rows[0]
        print(
            f"OK - version={row[0]} user={row[1]} database={row[2]} server_time={row[3]}"
        )

        databases = [r[0] for r in client.query("SHOW DATABASES").result_rows]
        print(f"Visible databases: {databases}")

        return {"conn_id": conn_id, "version": row[0], "user": row[1], "database": row[2]}

    test_clickhouse_connection()
