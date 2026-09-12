from __future__ import annotations

import os
import sys

from airflow import DAG
from airflow.decorators import task
from airflow.models import Variable
from airflow.sdk import get_current_context
from pendulum import datetime, duration, now as pendulum_now

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from raw_event_bronze_config import RAW_EVENT_BRONZE_TABLES  # noqa: E402
from raw_event_bronze_processor import process_table  # noqa: E402


DAG_ID = "raw_event_to_bronze"

# Default schedule: run the Raw -> Bronze pipeline every hour.
DEFAULT_SCHEDULE = "0 * * * *"

# Re-read 1 minute before the window to avoid missing boundary records.
OVERLAP_MINUTES = 1

# Default manual-run lookback. # Example: {"lookback_minutes": 1440} -> previous 24 hours.
MANUAL_LOOKBACK_MINUTES = 60

# Limit concurrent ClickHouse tasks. # Create once: airflow pools set clickhouse_bronze_extraction 5
BRONZE_EXTRACTION_POOL = "clickhouse_bronze_extraction"
MAX_ACTIVE_TASKS = 5


def get_schedule():
    return Variable.get(
        "raw_to_bronze_schedule",
        default_var=DEFAULT_SCHEDULE,
    )


with DAG(
    dag_id=DAG_ID,
    schedule=get_schedule(),
    start_date=datetime(2026, 1, 1),
    catchup=False,
    max_active_runs=1,
    max_active_tasks=MAX_ACTIVE_TASKS,
    tags=[
        "cdc",
        "raw",
        "bronze",
        "clickhouse",
    ],
) as dag:

    @task
    def get_processing_window():
        context = get_current_context()
        conf = (context["dag_run"].conf or {}) if context.get("dag_run") else {}

        #   Explicit backfill window.
        #   {"window_start": "2026-09-06T00:00:00+00:00",
        #    "window_end":   "2026-09-07T00:00:00+00:00"}
        if conf.get("window_start") and conf.get("window_end"):
            return {
                "window_start": conf["window_start"],
                "window_end": conf["window_end"],
            }

        # Use Airflow's scheduled data interval.
        dag_run = context.get("dag_run")
        data_interval_start = context.get("data_interval_start")
        data_interval_end = context.get("data_interval_end")

        if data_interval_end is None:
            # Manual run without an explicit window. # Example: {"lookback_minutes": 1440} -> previous 24 hours.
            data_interval_end = (
                getattr(dag_run, "run_after", None)
                or getattr(dag_run, "logical_date", None)
                or pendulum_now("UTC")
            )

        # Absent or zero-length interval -> substitute a real span, otherwise
        # the window below would be only OVERLAP_MINUTES wide.
        if data_interval_start is None or data_interval_end <= data_interval_start:
            lookback = int(conf.get("lookback_minutes", MANUAL_LOOKBACK_MINUTES))
            data_interval_start = data_interval_end - duration(minutes=lookback)

        window_start = (
            data_interval_start
            - duration(minutes=OVERLAP_MINUTES)
        )

        window_end = data_interval_end

        return {
            "window_start": window_start.isoformat(),
            "window_end": window_end.isoformat(),
        }

    @task(
        pool=BRONZE_EXTRACTION_POOL,
        map_index_template="{{ table_name }}",
    )
    def process_table_task(
        table_config: dict,
        window: dict,
    ):
        context = get_current_context()

        table_name = table_config["name"]

        # Feeds map_index_template, so the UI shows process_table_task[household]
        # rather than process_table_task[0].
        context["table_name"] = table_name

        return process_table(
            table_config=table_config,
            window_start=window["window_start"],
            window_end=window["window_end"],
        )

    processing_window = get_processing_window()

    process_table_task.partial(
        window=processing_window,
    ).expand(
        table_config=RAW_EVENT_BRONZE_TABLES,
    )