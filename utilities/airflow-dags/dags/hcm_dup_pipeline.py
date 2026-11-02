"""
hcm_dup_pipeline.py

HCM duplicate pipeline: Postgres -> ClickHouse -> duplicate pairs, one DAG per Postgres schema in
hcm_dup/config.py TENANTS:  hcm_dup_pipeline_<schema>  (schedule from the tenant entry, default every 15 min)

Tasks (each step's logic is in hcm_dup/pipeline.py, its SQL in hcm_dup/sql/):
  prepare         creates raw_<schema> + hcm_<schema> in ClickHouse on the first run; full or incremental?
  sync_reference  boundary_relationship + message, reloaded once a day
  sync_tables     household, household_member, individual, individual_address (changed rows), address
  boundary        boundary hierarchy + names (only after sync_reference reloaded them)
  beneficiary     one row per member for households synced since the last completed run
  match           duplicate pairs for those members against everyone -> hcm_<schema>.dup_pairs
  complete        checkpoint; the next run starts from here

The first run copies everything (in chunks, resumable); later runs only what changed. A failed run is caught up
by the next one. Trigger manually with {"full": true} to re-copy and rebuild everything (after changing the
match settings or boundaries), with {"refresh_reference": true} to reload boundaries now.

Required env vars (Secret hcm-dup-sync + airflow values): HCM_CH_USER, HCM_CH_PASSWORD, HCM_PG_HOST, HCM_PG_DB,
HCM_PG_USER, HCM_PG_PASSWORD. Optional: HCM_CH_HOST, HCM_CH_PORT, HCM_PG_PORT, HCM_PG_SSLMODE.
"""
from __future__ import annotations

from datetime import timedelta

import pendulum
from airflow.providers.standard.operators.python import PythonOperator
from airflow.sdk import DAG, Param, get_current_context

# Airflow does not always put the directory holding this file on sys.path; the hcm_dup package sits next to it.
import os as _os, sys as _sys
_sys.path.insert(0, _os.path.dirname(_os.path.abspath(__file__)))

from hcm_dup import config, pipeline


def _prepare(schema):
    params = get_current_context()["params"]
    return pipeline.step_prepare(schema, full=params["full"], refresh_reference=params["refresh_reference"])


def _with_run(step):
    """Task callable that passes the run context from the prepare task to a pipeline step."""
    def call(schema):
        ti = get_current_context()["ti"]
        return step(schema, ti.xcom_pull(task_ids="prepare"))
    return call


def _boundary(schema):
    ti = get_current_context()["ti"]
    pipeline.step_boundary(schema, ti.xcom_pull(task_ids="prepare"), bool(ti.xcom_pull(task_ids="sync_reference")))


for tenant in config.enabled_tenants():
    schema = tenant["schema"]
    with DAG(
        dag_id=f"hcm_dup_pipeline_{schema}",
        description=f"Postgres schema {schema} -> ClickHouse raw_{schema} / hcm_{schema}: sync + duplicate matching",
        schedule=tenant.get("schedule", "*/15 * * * *"),
        start_date=pendulum.datetime(2026, 10, 1, tz="UTC"),
        catchup=False,
        max_active_runs=1,                      # runs never overlap (they share the sync state)
        dagrun_timeout=timedelta(hours=8),      # the first copy / a full reload can be long
        default_args={
            "owner": "hcm",
            "retries": 2,
            "retry_delay": timedelta(minutes=5),
        },
        params={
            "full": Param(False, type="boolean",
                          description="Re-copy every table and rebuild/match every record"),
            "refresh_reference": Param(False, type="boolean",
                                       description="Reload boundaries and boundary names now"),
        },
        tags=["hcm", "clickhouse", "duplicates", schema],
        doc_md=__doc__,
    ) as dag:
        prepare = PythonOperator(task_id="prepare", python_callable=_prepare, op_args=[schema])
        sync_reference = PythonOperator(task_id="sync_reference", op_args=[schema],
                                        python_callable=_with_run(pipeline.step_sync_reference))
        sync_tables = PythonOperator(task_id="sync_tables", op_args=[schema],
                                     python_callable=_with_run(pipeline.step_sync_tables),
                                     execution_timeout=timedelta(hours=6))
        boundary = PythonOperator(task_id="boundary", python_callable=_boundary, op_args=[schema])
        beneficiary = PythonOperator(task_id="beneficiary", op_args=[schema],
                                     python_callable=_with_run(pipeline.step_beneficiary))
        match = PythonOperator(task_id="match", op_args=[schema],
                               python_callable=_with_run(pipeline.step_match))
        complete = PythonOperator(task_id="complete", op_args=[schema],
                                  python_callable=_with_run(pipeline.step_complete))

        prepare >> sync_reference >> sync_tables >> boundary >> beneficiary >> match >> complete
