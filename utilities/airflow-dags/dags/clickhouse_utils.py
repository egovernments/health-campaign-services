"""Shared ClickHouse connectivity helper for bronze-to-silver transformation DAGs."""
from __future__ import annotations

import clickhouse_connect
from airflow.hooks.base import BaseHook

DEFAULT_CLICKHOUSE_CONN_ID = "clickhouse_default"

# Per-query ClickHouse settings applied to every client built here. A
# Connection can override or extend them via a "settings" object in its
# `extra` JSON (other `extra` keys are still passed straight to get_client).
#
# max_query_size: the per-entity DAGs inline each chunk's id list into the
# query text (`WHERE id IN %(ids)s`). ClickHouse's default (256 KiB) caps that
# at ~6,700 uuids, so bronze_to_silver_chunk_size=10000 failed four entities
# with `Code: 62 Max query size exceeded` (airflow_dags/BRONZE_READ_SCALABILITY.md,
# Option F). 10 MB was measured to run a 1.84 MB / 39,382-id query cleanly and
# removes that ceiling. Query text still grows linearly with chunk size, so
# this is a safety margin, not a license for unbounded chunks.
DEFAULT_CLIENT_SETTINGS = {"max_query_size": 10_000_000}


def get_clickhouse_client(conn_id: str = DEFAULT_CLICKHOUSE_CONN_ID):
    """Builds a clickhouse-connect client from an Airflow Connection."""
    conn = BaseHook.get_connection(conn_id)
    extra = dict(conn.extra_dejson)
    settings = {**DEFAULT_CLIENT_SETTINGS, **(extra.pop("settings", None) or {})}
    return clickhouse_connect.get_client(
        host=conn.host,
        port=conn.port or 8123,
        username=conn.login,
        password=conn.password or "",
        database=conn.schema or "analytics",
        settings=settings,
        **extra,
    )
