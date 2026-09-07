"""Shared ClickHouse connectivity helper for bronze-to-silver transformation DAGs."""
from __future__ import annotations

import clickhouse_connect
from airflow.hooks.base import BaseHook

DEFAULT_CLICKHOUSE_CONN_ID = "clickhouse_default"


def get_clickhouse_client(conn_id: str = DEFAULT_CLICKHOUSE_CONN_ID):
    """Builds a clickhouse-connect client from an Airflow Connection."""
    conn = BaseHook.get_connection(conn_id)
    return clickhouse_connect.get_client(
        host=conn.host,
        port=conn.port or 8123,
        username=conn.login,
        password=conn.password or "",
        database=conn.schema or "analytics",
        **conn.extra_dejson,
    )
