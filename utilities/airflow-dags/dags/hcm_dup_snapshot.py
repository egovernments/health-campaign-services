"""
hcm_dup_snapshot.py

Duplicate analysis of a ClickHouse database raw_<name> that was loaded once (e.g. CSV exports of one LGA,
BAUCHI_ANALYSIS/load_bauchi_lga.sh), instead of synced from Postgres like hcm_dup_pipeline_<schema>.
Manual trigger only, with the database name: {"raw_db": "raw_bauchi_lga"}  ->  results in hcm_bauchi_lga.
Every run rebuilds hcm_<name> from scratch with the same boundary / beneficiary / matching SQL as the live DAG.

Tasks (logic in hcm_dup/snapshot.py, SQL in hcm_dup/sql/):
  prepare      checks raw_<name>, creates hcm_<name>, empties its derived tables
  boundary     boundary hierarchy + names; fails if block_level is not a boundary type of the hierarchy
  beneficiary  one row per household member, joined from all raw tables
  cycles       campaign cycles from the daily registrations (or the "cycles" param); every member gets one
  match        duplicate pairs of children within one block_level boundary (default Health Facility),
               across all cycles and years at once
  cycle_pairs  each pair labelled SAME_CYCLE / CROSS_CYCLE_SAME_YEAR / CROSS_YEAR / UNASSIGNED
  report       time / rows / memory per step (system.query_log), extrapolated to target_members

Cycles: a cycle is a burst of registration days (4-6 peak days, then a tail of late syncs). Window = 3 days
before its peak .. the day before the next cycle's window; a year's last cycle runs to 31 Dec. A record's cycle
comes from its client registration date, or from the server date when the client date is in no window.
To fix the windows by hand: {"cycles": [{"start": "2025-06-23"}, {"start": "2025-07-21"}, ...]}
(optional "end" and "cycle_id" per entry).

Results in hcm_<name>:
  dup_pairs_cycle         pairs + cycle_a/b, cycle_relation, earlier/later_cycle, duplicate_id, block_code/name
  v_scenario_pairs_cycle  the same per dashboard scenario (dup_scenarios)
  v_cycle_duplicate_rate  per scenario x cycle x health facility: registrations and duplicates (same cycle,
                          earlier cycle of the year, previous year)
  v_cycle_matrix          earlier cycle x later cycle per scenario
  v_cycle_overview        cycle windows and sizes
  benchmark, benchmark_metrics   timings per step and run, dataset sizes, extrapolation
  + everything of the live pipeline (beneficiary, dup_pairs, v_scenario_pairs, flat views, v_data_quality)

For clean timings, run nothing else on ClickHouse meanwhile (e.g. pause hcm_dup_pipeline_ba).
ClickHouse settings go in "ch_settings" (default in hcm_dup/config.py SNAPSHOT_DEFAULTS: grace_hash joins that
spill to disk, for a 6Gi pod); {} runs with the server defaults.
"""
from __future__ import annotations

from datetime import timedelta

import pendulum
from airflow.providers.standard.operators.python import PythonOperator
from airflow.sdk import DAG, Param, get_current_context

# Airflow does not always put the directory holding this file on sys.path; the hcm_dup package sits next to it.
import os as _os, sys as _sys
_sys.path.insert(0, _os.path.dirname(_os.path.abspath(__file__)))

from hcm_dup import config, snapshot

D = config.SNAPSHOT_DEFAULTS


def _prepare():
    return snapshot.step_prepare(dict(get_current_context()["params"]))


def _with_run(step):
    """Task callable that passes the run context from the prepare task to a snapshot step."""
    def call():
        return step(get_current_context()["ti"].xcom_pull(task_ids="prepare"))
    return call


with DAG(
    dag_id="hcm_dup_snapshot",
    description="ClickHouse raw_<name> (loaded once) -> hcm_<name>: duplicates per health facility, "
                "within / across cycles and years, with timings",
    schedule=None,
    start_date=pendulum.datetime(2026, 11, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,                          # runs share the ClickHouse server; overlapping runs skew timings
    dagrun_timeout=timedelta(hours=8),
    default_args={
        "owner": "hcm",
        "retries": 0,                           # a retried step would mix timings; re-trigger the DAG instead
    },
    params={
        "raw_db": Param(D["raw_db"], type="string", pattern=r"^raw_[a-z][a-z0-9_]*$",
                        description="ClickHouse database with the raw tables; results go to hcm_<name>"),
        "block_level": Param(D["block_level"], type="string",
                             description="Boundary type both records must share (duplicates are searched within it)"),
        "boundary_tenant": Param(D["boundary_tenant"], type="string",
                                 description="tenantid of the boundary_relationship / message rows"),
        "boundary_hierarchy": Param(D["boundary_hierarchy"], type="string",
                                    description="boundary_relationship.hierarchytype"),
        "message_module": Param(D["message_module"], type="string", description="Module with the boundary names"),
        "boundary_locales": Param(D["boundary_locales"], type="array", description="Name locales, first wins"),
        "timezone": Param(D["timezone"], type="string", description="Decides the registration date / day"),
        "cycles": Param([], type="array",
                        description='Optional fixed cycle windows, e.g. [{"start": "2025-06-23"}, '
                                    '{"start": "2025-07-21", "end": "2025-08-17", "cycle_id": "2025-C2"}]; '
                                    "empty = detect from the data"),
        "cycle_detection": Param({}, type="object",
                                 description="Overrides of " + str(config.SNAPSHOT_CYCLE_DEFAULTS)),
        "match": Param(D["match"], type="object",
                       description="Overrides of the base matching conditions (config.MATCH_DEFAULTS); default: "
                                   "child criteria only, DOBs up to 150 days apart"),
        "ch_settings": Param(D["ch_settings"], type="object",
                             description="ClickHouse settings of this DAG's queries; {} = server defaults"),
        "target_members": Param(D["target_members"], type="integer",
                                description="Dataset size (household members) to extrapolate the timings to"),
    },
    tags=["hcm", "clickhouse", "duplicates", "snapshot"],
    doc_md=__doc__,
) as dag:
    prepare = PythonOperator(task_id="prepare", python_callable=_prepare)
    boundary = PythonOperator(task_id="boundary", python_callable=_with_run(snapshot.step_boundary))
    beneficiary = PythonOperator(task_id="beneficiary", python_callable=_with_run(snapshot.step_beneficiary),
                                 execution_timeout=timedelta(hours=3))
    cycles = PythonOperator(task_id="cycles", python_callable=_with_run(snapshot.step_cycles))
    match = PythonOperator(task_id="match", python_callable=_with_run(snapshot.step_match),
                           execution_timeout=timedelta(hours=4))
    cycle_pairs = PythonOperator(task_id="cycle_pairs", python_callable=_with_run(snapshot.step_cycle_pairs))
    report = PythonOperator(task_id="report", python_callable=_with_run(snapshot.step_report))

    prepare >> boundary >> beneficiary >> cycles >> match >> cycle_pairs >> report
