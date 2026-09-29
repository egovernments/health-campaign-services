"""
bronze_to_silver_task_orchestrator.py

The SQL-side twins in ../performance_test/, run as TASKS of this one DAG
instead of as separately triggered DAGs. For each entity in the order list
this DAG has a `transform_<entity>` task that calls the twin's own
`run_transformation(SPEC, window)` in-process -- the same code path as the
twin DAG's transform task, so the output is identical.

Compared with bronze-to-silver_orcestrator.py (TriggerDagRunOperator per
entity), per entity this drops: the REST-API existence / unpause check, the
separate DAG run and its parse_time_window task, and the trigger's 30 s
completion poke. Each entity is a plain task, so retries, logs and durations
are per entity in this DAG's grid.

Ordering and failure semantics
    - Every transform task depends only on resolve_time_window. The DAG's
      max_active_tasks (Variable below, default 3) caps how many entities
      run at once; they start in list order (the scheduler picks the highest
      priority_weight first and the weights descend down the list), and each
      time one finishes the next in the list takes its place. 1 = serial.
      Any overlap is safe -- entities share no tables (each reads bronze and
      writes its own silver table); the limit is ClickHouse CPU, not
      correctness (each running entity issues one INSERT at a time, at
      max_threads = 2).
    - One entity failing does not stop the others (no task depends on
      another entity). all_entities_succeeded depends on every transform task,
      so the DAG run is still marked failed if any entity failed.
    - An entity name with no twin module, or whose module fails to import,
      is skipped at parse time with a warning, like the trigger orchestrator
      skips an entity whose DAG isn't registered.

Variables (all read at parse time except the window overrides)
    task_orchestrator_entity_order     JSON list of entity names, e.g. ["project", "project_task"];
                                       default: every twin in ../performance_test/, alphabetical
    task_orchestrator_schedule         cron / preset / "None"; default "None" (manual only), so
                                       deploying this next to the trigger orchestrator doesn't
                                       double the hourly load
    task_orchestrator_max_active_tasks entities run at once; default 3 (1 = serial). Parse-time, so a
                                       change applies once the file is reparsed, to runs created after
    bronze_to_silver_window_start_override / _end_override
                                       shared with the trigger orchestrator, same rules
    sql_test_database                  read by the twins (default `analytics`)

A manual run may also pass {"start_time", "end_time", "truncate_target"} in
dag_run.conf; conf wins over the override Variables. This file mentions
airflow and dag, as safe-mode discovery requires.
"""
from __future__ import annotations

import glob
import importlib
import logging
import os
import sys
from datetime import timedelta

import pendulum
from airflow.decorators import task
from airflow.exceptions import AirflowFailException
from airflow.models import DAG, Variable
from airflow.timetables.interval import CronDataIntervalTimetable
from airflow.utils.dates import cron_presets
from airflow.utils.types import DagRunType

_TWINS_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "performance_test")
sys.path.insert(0, _TWINS_DIR)
import sql_flatten_common  # noqa: E402
from sql_flatten_common import run_transformation  # noqa: E402

log = logging.getLogger(__name__)

ENTITY_ORDER_VARIABLE = "task_orchestrator_entity_order"
SCHEDULE_VARIABLE = "task_orchestrator_schedule"
DEFAULT_SCHEDULE = "None"
MAX_ACTIVE_TASKS_VARIABLE = "task_orchestrator_max_active_tasks"
DEFAULT_MAX_ACTIVE_TASKS = 3
WINDOW_START_OVERRIDE_VARIABLE = "bronze_to_silver_window_start_override"
WINDOW_END_OVERRIDE_VARIABLE = "bronze_to_silver_window_end_override"
TWIN_MODULE_SUFFIX = "_test_transformation"
ENTITY_EXECUTION_TIMEOUT = timedelta(hours=2)


def _resolve_schedule(raw: str) -> str | CronDataIntervalTimetable | None:
    """Same rules as bronze-to-silver_orcestrator.py: "none" -> unscheduled,
    "@once" as-is, anything else -> CronDataIntervalTimetable so a scheduled
    run gets a real [previous tick, this tick) window."""
    normalized = raw.strip()
    if normalized.lower() == "none":
        return None
    if normalized == "@once":
        return normalized
    return CronDataIntervalTimetable(cron_presets.get(normalized, normalized), timezone="UTC")


def _available_entities() -> list[str]:
    """Entity names that have a twin module, e.g. `project_task` for
    project_task_test_transformation.py."""
    paths = glob.glob(os.path.join(_TWINS_DIR, f"*{TWIN_MODULE_SUFFIX}.py"))
    return sorted(os.path.basename(path)[: -len(f"{TWIN_MODULE_SUFFIX}.py")] for path in paths)


def _load_spec(entity: str):
    """The twin's EntitySpec, or None (logged) if the entity can't be run."""
    module_name = f"{entity}{TWIN_MODULE_SUFFIX}"
    if not os.path.exists(os.path.join(_TWINS_DIR, f"{module_name}.py")):
        log.warning("Entity '%s' has no twin module %s.py in %s; skipping it.", entity, module_name, _TWINS_DIR)
        return None
    # SPEC only: don't let the import build the twin's DAG (sql_flatten_common.BUILD_DAGS).
    sql_flatten_common.BUILD_DAGS = False
    try:
        spec = importlib.import_module(module_name).SPEC
    except Exception:
        log.exception("Entity '%s': importing %s failed; skipping it.", entity, module_name)
        return None
    finally:
        sql_flatten_common.BUILD_DAGS = True
    if spec.python_entity != entity:
        log.warning("Entity '%s': %s declares python_entity '%s'; skipping it.", entity, module_name, spec.python_entity)
        return None
    return spec


default_args = {
    "owner": "data-platform",
    "retries": 1,
    "retry_delay": timedelta(minutes=5),
}

with DAG(
    dag_id="bronze_to_silver_task_orchestrator",
    description="Runs every entity's SQL-side bronze-to-silver transform as a task of this one DAG.",
    schedule=_resolve_schedule(Variable.get(SCHEDULE_VARIABLE, default_var=DEFAULT_SCHEDULE)),
    start_date=pendulum.datetime(2024, 1, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    max_active_tasks=int(Variable.get(MAX_ACTIVE_TASKS_VARIABLE, default_var=DEFAULT_MAX_ACTIVE_TASKS)),
    default_args=default_args,
    tags=["bronze-to-silver", "orchestrator", "sql-test"],
) as dag:

    entity_order = Variable.get(ENTITY_ORDER_VARIABLE, deserialize_json=True, default_var=None)
    if entity_order is None:
        entity_order = _available_entities()
    specs = [(entity, spec) for entity in entity_order if (spec := _load_spec(entity)) is not None]
    if not specs:
        log.warning("'%s' has no runnable entities (Variable '%s' = %r).", dag.dag_id, ENTITY_ORDER_VARIABLE, entity_order)

    @task
    def resolve_time_window(**context) -> dict:
        """dag_run.conf {start_time, end_time} first (manual runs); then, for a
        manual/backfill run or an @once schedule, the override Variables; else
        this run's data_interval. Same guard against a forgotten override as
        the trigger orchestrator."""
        dag_run = context["dag_run"]
        conf = dag_run.conf or {}
        configured_schedule = Variable.get(SCHEDULE_VARIABLE, default_var=DEFAULT_SCHEDULE).strip().lower()
        override_eligible = dag_run.run_type != DagRunType.SCHEDULED or configured_schedule == "@once"

        start_time = end_time = None
        if conf.get("start_time") and conf.get("end_time"):
            start_time, end_time = conf["start_time"], conf["end_time"]
            log.info("using dag_run.conf window [%s, %s)", start_time, end_time)
        elif override_eligible:
            start_override = Variable.get(WINDOW_START_OVERRIDE_VARIABLE, default_var="").strip()
            end_override = Variable.get(WINDOW_END_OVERRIDE_VARIABLE, default_var="").strip()
            if start_override and end_override:
                start_time, end_time = start_override, end_override
                log.info("run_type='%s': using window override [%s, %s)", dag_run.run_type, start_time, end_time)
            elif start_override or end_override:
                log.warning("Only one of '%s'/'%s' is set; ignoring the partial override.",
                            WINDOW_START_OVERRIDE_VARIABLE, WINDOW_END_OVERRIDE_VARIABLE)

        if start_time is None:
            start_time = context["data_interval_start"].to_iso8601_string()
            end_time = context["data_interval_end"].to_iso8601_string()

        start_time = pendulum.parse(start_time).to_iso8601_string()
        end_time = pendulum.parse(end_time).to_iso8601_string()
        if start_time == end_time:
            raise AirflowFailException(
                f"Resolved window is zero-width ({start_time}). With schedule '{configured_schedule}' pass "
                f"start_time/end_time in dag_run.conf or set both '{WINDOW_START_OVERRIDE_VARIABLE}' and "
                f"'{WINDOW_END_OVERRIDE_VARIABLE}'."
            )
        return {"start_time": start_time, "end_time": end_time,
                "truncate_target": bool(conf.get("truncate_target", False))}

    time_window = resolve_time_window()

    def _transform_task(entity: str, spec, priority_weight: int):
        @task(
            task_id=f"transform_{entity}",
            priority_weight=priority_weight,
            weight_rule="absolute",
            execution_timeout=ENTITY_EXECUTION_TIMEOUT,
        )
        def transform(window: dict) -> None:
            run_transformation(spec, window)

        return transform

    transform_tasks = [
        _transform_task(entity, spec, priority_weight=len(specs) - position)(time_window)
        for position, (entity, spec) in enumerate(specs)
    ]

    @task
    def all_entities_succeeded() -> None:
        """Runs only if every transform task succeeded; otherwise it ends
        upstream_failed and the DAG run is marked failed."""
        log.info("all %d entities succeeded", len(specs))

    transform_tasks >> all_entities_succeeded()
