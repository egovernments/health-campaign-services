"""
bronze-to-silver_orcestrator.py

Reads the ordered list of entities to transform from the Airflow Variable
`entity_transformation_order`, and for each entity (in order) triggers that
entity's `<entity>_transformation` DAG, blocking until it finishes before
moving on to the next one. The time window passed to every entity DAG is
this orchestrator DAG's own scheduled data interval.

If an entity in the order list has no matching DAG registered yet (e.g. its
transformation file hasn't been written), that entity is skipped and the
chain continues with the next one. A genuine failure in a triggered DAG is
logged (via that entity's own failed `trigger_<entity>` task) but does not
stop the chain either -- every entity in the order list is still attempted.
The orchestrator's own run is still marked failed overall if any entity
failed, so monitoring/alerting on this DAG is unaffected.
"""
from __future__ import annotations

import logging
from datetime import timedelta

import pendulum
import requests
from airflow.exceptions import AirflowFailException
from airflow.hooks.base import BaseHook
from airflow.models import DAG, Variable
from airflow.decorators import task
from airflow.providers.standard.operators.trigger_dagrun import TriggerDagRunOperator
from airflow.timetables.interval import CronDataIntervalTimetable
from airflow.utils.dates import cron_presets
from airflow.utils.types import DagRunType

log = logging.getLogger(__name__)

ENTITY_ORDER_VARIABLE = "entity_transformation_order"
SCHEDULE_VARIABLE = "bronze_to_silver_orchestrator_schedule"
DEFAULT_SCHEDULE = "@hourly"
DAG_ID_SUFFIX = "_transformation"

# Optional manual override for the read window, checked at task-execution
# time. Both must be set (non-empty) to take effect, and only on a
# manual/backfill run (or a one-off "@once" schedule) -- see
# resolve_time_window. Otherwise the run falls back to this DAG's own
# data_interval_start/data_interval_end, which (thanks to _resolve_schedule
# below wrapping cron/preset schedules in a CronDataIntervalTimetable)
# reflects the configured schedule frequency (e.g. previous hour for
# "@hourly", previous 24h for "@daily").
WINDOW_START_OVERRIDE_VARIABLE = "bronze_to_silver_window_start_override"
WINDOW_END_OVERRIDE_VARIABLE = "bronze_to_silver_window_end_override"

# Airflow's own stable REST API -- used to check whether a target entity DAG
# is registered.
#  In production this
# Connection is populated by the deployment's Helm chart (e.g. as
# AIRFLOW_CONN_<CONN_ID> from a Kubernetes secret), not committed here.
AIRFLOW_API_CONN_ID = "airflow_api_default"
AIRFLOW_API_TOKEN_PATH = "/auth/token"
AIRFLOW_API_DAG_PATH_TEMPLATE = "/api/v2/dags/{dag_id}"
AIRFLOW_API_TIMEOUT_SECONDS = 15


def _dag_id_for_entity(entity: str) -> str:
    """
    Single source of truth for the entity -> dag_id mapping.
    The mapping between the entity and the dag_id is : dag_id = {entity_name}_transformation
    For example: entity = project_task then the dag_id/python file name has to be project_task_transformation
    """
    return f"{entity}{DAG_ID_SUFFIX}"


def _resolve_schedule(raw: str) -> str | CronDataIntervalTimetable | None:
    """Maps the literal string "none" (any case) to Python None (unscheduled,
    manually/externally triggered only). "@once" is passed through as-is
    (OnceTimetable -- a genuine one-off with no periodicity to restore).
    Anything else (a preset like "@hourly" or a raw cron expression) is
    converted to a CronDataIntervalTimetable so scheduled runs get a real,
    non-zero-width [previous tick, this tick) data interval -- Airflow's
    default resolution for a bare schedule string is CronTriggerTimetable,
    which instead sets data_interval_start == data_interval_end == the
    trigger time, unusable as a bronze-read window."""
    normalized = raw.strip()
    if normalized.lower() == "none":
        return None
    if normalized == "@once":
        return normalized
    cron_expr = cron_presets.get(normalized, normalized)
    return CronDataIntervalTimetable(cron_expr, timezone="UTC")


def _get_airflow_api_token(base_url: str, login: str, password: str) -> str:
    response = requests.post(
        f"{base_url}{AIRFLOW_API_TOKEN_PATH}",
        json={"username": login, "password": password},
        timeout=AIRFLOW_API_TIMEOUT_SECONDS,
    )
    response.raise_for_status()
    return response.json()["access_token"]


def _ensure_target_dag_ready(dag_id: str) -> bool:
    """
    Checks whether `dag_id` is registered via Airflow's stable REST API
    (GET /api/v2/dags/{dag_id}). 
    If it exists but is paused (every entity DAG starts paused --
    Airflow's own `dags_are_paused_at_creation` default -- and nothing
    un-pauses it otherwise), auto-unpauses it via PATCH so the trigger that
    follows actually runs instead of sitting in `queued` until its
    execution_timeout.

    Returns False if the DAG isn't registered at all (caller skips this
    entity). Any failure other than 404 (bad/expired creds, network error,
    5xx) is a real problem, not "doesn't exist" -- raised so the calling
    task fails loudly and retries instead of silently skipping a valid
    entity.
    """
    conn = BaseHook.get_connection(AIRFLOW_API_CONN_ID)
    base_url = conn.host.rstrip("/")
    token = _get_airflow_api_token(base_url, conn.login, conn.password)
    headers = {"Authorization": f"Bearer {token}"}
    dag_path = f"{base_url}{AIRFLOW_API_DAG_PATH_TEMPLATE.format(dag_id=dag_id)}"

    response = requests.get(dag_path, headers=headers, timeout=AIRFLOW_API_TIMEOUT_SECONDS)
    if response.status_code == 404:
        return False
    response.raise_for_status()

    if response.json().get("is_paused"):
        log.warning("DAG '%s' is paused; auto-unpausing so its trigger can run.", dag_id)
        patch_response = requests.patch(
            dag_path, headers=headers, json={"is_paused": False},
            timeout=AIRFLOW_API_TIMEOUT_SECONDS,
        )
        patch_response.raise_for_status()

    return True


default_args = {
    "owner": "data-platform",
    "retries": 1,
    "retry_delay": timedelta(minutes=5),
}

with DAG(
    dag_id="bronze_to_silver_orchestrator",
    description=(
        "Sequentially triggers per-entity bronze-to-silver transformation DAGs "
        "for this DAG's own scheduled data interval."
    ),
    # Read at parse time, same as ENTITY_ORDER_VARIABLE, so cadence can be
    # changed via the Airflow UI/CLI without a code deploy. Accepts a cron
    # expression ("0 * * * *"), a preset ("@hourly", "@daily", ...), or the
    # literal string "None" to make the DAG unscheduled (manually/externally
    # triggered only). Takes effect on the next time the scheduler/dag-processor
    # reparses this file ([scheduler] min_file_process_interval), not instantly.
    schedule=_resolve_schedule(Variable.get(SCHEDULE_VARIABLE, default_var=DEFAULT_SCHEDULE)),
    start_date=pendulum.datetime(2024, 1, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    default_args=default_args,
    tags=["bronze-to-silver", "orchestrator"],
) as dag:

    entity_order = Variable.get(
        ENTITY_ORDER_VARIABLE,
        deserialize_json=True,
        default_var=[],
    )

    if not entity_order:
        log.warning(
            "Airflow Variable '%s' is missing or empty at parse time; "
            "'%s' will have zero trigger tasks until it is populated.",
            ENTITY_ORDER_VARIABLE, dag.dag_id,
        )

    @task
    def resolve_time_window(**context) -> dict:
        """Manual/backfill run (or a one-off @once schedule) may use the
        window override variables; a normal recurring scheduled run always
        uses this run's data_interval, so a forgotten override never silently
        freezes the next scheduled run's window."""
        dag_run = context["dag_run"]
        configured_schedule = Variable.get(SCHEDULE_VARIABLE, default_var=DEFAULT_SCHEDULE).strip().lower()
        override_eligible = dag_run.run_type != DagRunType.SCHEDULED or configured_schedule == "@once"

        start_time = None
        end_time = None

        if not override_eligible:
            log.info(
                "run_type='%s' with schedule='%s': a normal scheduled run always "
                "uses this run's data_interval; window override variables (if any "
                "are set) are ignored.",
                dag_run.run_type, configured_schedule,
            )
        else:
            start_override = Variable.get(WINDOW_START_OVERRIDE_VARIABLE, default_var="").strip()
            end_override = Variable.get(WINDOW_END_OVERRIDE_VARIABLE, default_var="").strip()

            if start_override and end_override:
                log.info(
                    "run_type='%s': using manual window override [%s, %s)",
                    dag_run.run_type, start_override, end_override,
                )
                start_time, end_time = start_override, end_override
            elif start_override or end_override:
                log.warning(
                    "Only one of '%s'/'%s' is set; ignoring the partial override and "
                    "falling back to this run's data_interval.",
                    WINDOW_START_OVERRIDE_VARIABLE, WINDOW_END_OVERRIDE_VARIABLE,
                )

        if start_time is None:
            start_time = context["data_interval_start"].to_iso8601_string()
            end_time = context["data_interval_end"].to_iso8601_string()

        if start_time == end_time:
            raise AirflowFailException(
                f"Resolved window is zero-width ({start_time}). This schedule "
                f"('{configured_schedule}') has no inherent periodicity (schedule "
                "is 'None' or '@once') and no valid override was set -- set both "
                f"'{WINDOW_START_OVERRIDE_VARIABLE}' and '{WINDOW_END_OVERRIDE_VARIABLE}' "
                "before triggering."
            )

        return {"start_time": start_time, "end_time": end_time}

    time_window = resolve_time_window()

    previous_task = None
    for entity in entity_order:
        target_dag_id = _dag_id_for_entity(entity)

        @task.short_circuit(
            task_id=f"check_{entity}_dag_exists",
            trigger_rule="all_done",
            ignore_downstream_trigger_rules=False,
        )
        def check_dag_exists(target_dag_id: str = target_dag_id, **context) -> bool:
            log.info(
                "Entity '%s' -> dag_id '%s': trigger window will be [%s, %s)",
                target_dag_id.removesuffix(DAG_ID_SUFFIX),
                target_dag_id,
                context["data_interval_start"],
                context["data_interval_end"],
            )
            ready = _ensure_target_dag_ready(target_dag_id)
            if not ready:
                log.warning(
                    "DAG '%s' is not registered yet; skipping this entity and "
                    "continuing with the rest of the order list.",
                    target_dag_id,
                )
            return ready

        check_task = check_dag_exists()

        trigger_task = TriggerDagRunOperator(
            task_id=f"trigger_{entity}",
            trigger_dag_id=target_dag_id,
            conf=time_window,
            wait_for_completion=True,
            poke_interval=30,
            execution_timeout=timedelta(hours=2),
            allowed_states=["success"],
            failed_states=["failed"],
            reset_dag_run=True,
            trigger_run_id=f"orchestrator__{{{{ dag_run.run_id }}}}__{entity}",
        )

        check_task >> trigger_task
        if previous_task is not None:
            previous_task >> check_task
        previous_task = trigger_task
