"""
bronze_to_silver_task_orchestrator.py

The bronze-to-silver DAG, bronze_to_silver_task_orchestrator, scheduled hourly
by default. Each run resolves one time window, then runs every entity's
transformation (the <entity>_transformation.py files in this folder) as a TASK
of this DAG: each `transform_<entity>` task calls `run_transformation(SPEC,
window)` directly -- no other DAG is triggered. Retries, logs and durations are
therefore per entity in this DAG's grid. (Each entity file also registers a
standalone `<entity>_transformation` DAG on the same code path, for running one
entity by hand.)

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
    - Within an entity, a slice that fails is retried straight away; if it
      fails again it is logged, the remaining slices still run, and the task
      then fails listing the failed slices (sql_flatten_common.run_transformation).
      Airflow then retries the task task_orchestrator_task_retries times,
      re-running the entity for the whole window -- except when every failed
      slice hit a size guard, which fails without retry (lower the chunk size).
    - An entity name with no module in this folder, or whose module fails to import,
      is skipped at parse time with a warning; the other entities still run.

Variables (all read at parse time except the window overrides)
    task_orchestrator_entity_order     JSON list of entity names, e.g. ["project", "project_task"];
                                       default: every entity module in this folder, alphabetical
    task_orchestrator_schedule         cron / preset / "None" (manual only); default "@hourly"
    task_orchestrator_max_active_tasks entities run at once; default 3 (1 = serial). Parse-time, so a
                                       change applies once the file is reparsed, to runs created after
    task_orchestrator_task_retries     Airflow retries per task; default 1. Parse-time, same as above
    task_orchestrator_task_timeout_minutes
                                       time limit per entity task; default 120. Parse-time
    bronze_to_silver_chunk_size        rows per slice, shared by every entity;
                                       default 5000
    bronze_to_silver_window_start_override / _end_override
                                       read at run time, only on manual/backfill runs (or an "@once"
                                       schedule) and only when both are set: reprocess that window

Task pods (KubernetesExecutor) request TASK_POD_REQUESTS instead of the
chart's 0.5 CPU / 1 GiB: a task only plans slices, calls the eGov APIs and sends
one INSERT at a time (measured on unified-dev: about 5 millicores and 290 MiB,
max 313 MiB), and the smaller request still schedules on a nearly full cluster.
The ClickHouse work itself runs in the ClickHouse pod, not here. Without the
kubernetes client (local runs, no KubernetesExecutor) no override is set.

A manual run may also pass {"start_time", "end_time"} in dag_run.conf; conf
wins over the override Variables. truncate_target is refused: the entities
write the main silver tables. This file mentions
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

try:
    from kubernetes.client import models as k8s
except ImportError:  # local runs: no KubernetesExecutor, nothing to override
    k8s = None

_ENTITIES_DIR = os.path.dirname(os.path.abspath(__file__))  # this folder: the entity modules and sql_flatten_common
sys.path.insert(0, _ENTITIES_DIR)
import sql_flatten_common  # noqa: E402
from sql_flatten_common import reject_truncate_target, run_transformation  # noqa: E402

log = logging.getLogger(__name__)

ENTITY_ORDER_VARIABLE = "task_orchestrator_entity_order"
SCHEDULE_VARIABLE = "task_orchestrator_schedule"
DEFAULT_SCHEDULE = "@hourly"
MAX_ACTIVE_TASKS_VARIABLE = "task_orchestrator_max_active_tasks"
DEFAULT_MAX_ACTIVE_TASKS = 3
RETRIES_VARIABLE = "task_orchestrator_task_retries"
DEFAULT_RETRIES = 1
WINDOW_START_OVERRIDE_VARIABLE = "bronze_to_silver_window_start_override"
WINDOW_END_OVERRIDE_VARIABLE = "bronze_to_silver_window_end_override"
MODULE_SUFFIX = "_transformation"
TIMEOUT_MINUTES_VARIABLE = "task_orchestrator_task_timeout_minutes"
DEFAULT_TIMEOUT_MINUTES = 120
TASK_POD_REQUESTS = {"cpu": "100m", "memory": "512Mi"}
TASK_POD_LIMITS = {"cpu": "1", "memory": "2Gi"}


def _resolve_schedule(raw: str) -> str | CronDataIntervalTimetable | None:
    """"none" -> unscheduled, "@once" as-is, anything else (cron or preset) ->
    CronDataIntervalTimetable, so a scheduled run gets a real [previous tick,
    this tick) window rather than Airflow's zero-width CronTriggerTimetable one."""
    normalized = raw.strip()
    if normalized.lower() == "none":
        return None
    if normalized == "@once":
        return normalized
    return CronDataIntervalTimetable(cron_presets.get(normalized, normalized), timezone="UTC")


def _available_entities() -> list[str]:
    """Entity names that have a module here, e.g. `project_task` for
    project_task_transformation.py."""
    paths = glob.glob(os.path.join(_ENTITIES_DIR, f"*{MODULE_SUFFIX}.py"))
    return sorted(os.path.basename(path)[: -len(f"{MODULE_SUFFIX}.py")] for path in paths)


def _load_spec(entity: str):
    """The entity's EntitySpec, or None (logged) if the entity can't be run."""
    module_name = f"{entity}{MODULE_SUFFIX}"
    if not os.path.exists(os.path.join(_ENTITIES_DIR, f"{module_name}.py")):
        log.warning("Entity '%s' has no module %s.py in %s; skipping it.", entity, module_name, _ENTITIES_DIR)
        return None
    # SPEC only: don't let the import build the entity's DAG (sql_flatten_common.BUILD_DAGS).
    sql_flatten_common.BUILD_DAGS = False
    try:
        spec = importlib.import_module(module_name).SPEC
    except Exception:
        log.exception("Entity '%s': importing %s failed; skipping it.", entity, module_name)
        return None
    finally:
        sql_flatten_common.BUILD_DAGS = True
    if spec.entity != entity:
        log.warning("Entity '%s': %s declares entity '%s'; skipping it.", entity, module_name, spec.entity)
        return None
    return spec


def _task_retries() -> int:
    """Airflow retries per task from RETRIES_VARIABLE; a value that isn't a
    non-negative integer falls back to DEFAULT_RETRIES with a warning."""
    raw = Variable.get(RETRIES_VARIABLE, default_var=DEFAULT_RETRIES)
    try:
        retries = int(raw)
    except (TypeError, ValueError):
        retries = -1
    if retries < 0:
        log.warning("Variable '%s' = %r is not a non-negative integer; using %d.",
                    RETRIES_VARIABLE, raw, DEFAULT_RETRIES)
        return DEFAULT_RETRIES
    return retries


def _task_timeout() -> timedelta:
    """Time limit per entity task from TIMEOUT_MINUTES_VARIABLE; a value that
    isn't a positive integer falls back to DEFAULT_TIMEOUT_MINUTES with a warning."""
    raw = Variable.get(TIMEOUT_MINUTES_VARIABLE, default_var=DEFAULT_TIMEOUT_MINUTES)
    try:
        minutes = int(raw)
    except (TypeError, ValueError):
        minutes = 0
    if minutes <= 0:
        log.warning("Variable '%s' = %r is not a positive integer; using %d.",
                    TIMEOUT_MINUTES_VARIABLE, raw, DEFAULT_TIMEOUT_MINUTES)
        minutes = DEFAULT_TIMEOUT_MINUTES
    return timedelta(minutes=minutes)


def _task_pod_executor_config() -> dict:
    """pod_override for the KubernetesExecutor's task container (named `base`)."""
    if k8s is None:
        return {}
    resources = k8s.V1ResourceRequirements(requests=TASK_POD_REQUESTS, limits=TASK_POD_LIMITS)
    return {"pod_override": k8s.V1Pod(spec=k8s.V1PodSpec(containers=[k8s.V1Container(name="base", resources=resources)]))}


default_args = {
    "owner": "data-platform",
    "retries": _task_retries(),
    "retry_delay": timedelta(minutes=5),
    "executor_config": _task_pod_executor_config(),
}


def _resolve_time_window(**context) -> dict:
    """dag_run.conf {start_time, end_time} first (manual runs); then, for a
    manual/backfill run or an @once schedule, the override Variables; else
    this run's data_interval. A recurring scheduled run never reads the
    overrides, so a forgotten override can't freeze every later run."""
    dag_run = context["dag_run"]
    conf = dag_run.conf or {}
    reject_truncate_target(conf)
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
    return {"start_time": start_time, "end_time": end_time}


def _build_dag(dag_id: str, description: str, tags: list[str], runners: list[tuple[str, object]]) -> DAG:
    """One orchestrator DAG: resolve_time_window -> transform_<entity> (one per
    runner, `runner(window)`) -> all_entities_succeeded."""
    task_timeout = _task_timeout()
    with DAG(
        dag_id=dag_id,
        description=description,
        schedule=_resolve_schedule(Variable.get(SCHEDULE_VARIABLE, default_var=DEFAULT_SCHEDULE)),
        start_date=pendulum.datetime(2024, 1, 1, tz="UTC"),
        catchup=False,
        max_active_runs=1,
        max_active_tasks=int(Variable.get(MAX_ACTIVE_TASKS_VARIABLE, default_var=DEFAULT_MAX_ACTIVE_TASKS)),
        default_args=default_args,
        tags=tags,
    ) as built:
        if not runners:
            log.warning("'%s' has no runnable entities.", dag_id)
        time_window = task(task_id="resolve_time_window")(_resolve_time_window)()

        def _transform_task(entity: str, runner, priority_weight: int):
            @task(
                task_id=f"transform_{entity}",
                priority_weight=priority_weight,
                weight_rule="absolute",
                execution_timeout=task_timeout,
            )
            def transform(window: dict) -> None:
                runner(window)

            return transform

        transform_tasks = [
            _transform_task(entity, runner, priority_weight=len(runners) - position)(time_window)
            for position, (entity, runner) in enumerate(runners)
        ]

        @task
        def all_entities_succeeded() -> None:
            """Runs only if every transform task succeeded; otherwise it ends
            upstream_failed and the DAG run is marked failed."""
            log.info("all %d entities succeeded", len(runners))

        transform_tasks >> all_entities_succeeded()
    return built


_entity_order = Variable.get(ENTITY_ORDER_VARIABLE, deserialize_json=True, default_var=None)


def _runner(spec):
    return lambda window: run_transformation(spec, window)


_entities = _entity_order if _entity_order is not None else _available_entities()
dag = _build_dag(
    "bronze_to_silver_task_orchestrator",
    "Runs every entity's bronze-to-silver transform as a task of this one DAG.",
    ["bronze-to-silver", "orchestrator", "sql"],
    [(entity, _runner(spec)) for entity in _entities if (spec := _load_spec(entity)) is not None],
)
