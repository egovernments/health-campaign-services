"""
silver_to_gold_mart_refresh.py

Rebuilds the gold marts after silver: refreshes every refreshable materialized
view in the ClickHouse Connection's database, ONE AT A TIME, dependencies first.

Why one at a time
    The mart views are `REFRESH EVERY 80 YEAR` (ClickHouse_ddl/08-15), so they
    never refresh on their own. Firing them all at once got the ClickHouse pod
    evicted on memory, so they have to be refreshed serially, by hand -- this
    DAG does that.

When it runs
    Scheduled on the SILVER_TABLES_ASSET Asset, which
    bronze_to_silver_task_orchestrator emits from its silver_run_finished task
    once every entity task has finished, passed or failed. So it runs after every
    bronze-to-silver run, scheduled or manual. That is safe because each mart is
    a full rebuild from the current silver tables: an entity that failed just
    leaves its silver table at its previous state. It can also be triggered by
    hand.

What it refreshes
    No database or view names are written here. At run time it reads
    system.view_refreshes for currentDatabase() -- the database of the
    clickhouse_default Connection (its Schema field) -- so marts added later are
    picked up automatically.

Order
    View B depends on view A when B's definition mentions A's name or A's TO
    target table (e.g. mv_dm_campaign_hierarchy reads dm_targets_base, which
    mv_dm_targets_base writes). Views are refreshed in dependency order; among
    views that are ready at the same time, alphabetically. A dependency cycle
    fails the run before anything is refreshed.

Failure handling
    For each view: SYSTEM REFRESH VIEW, then poll system.view_refreshes until
    that refresh has finished. If a view fails, every view that depends on it
    (directly or not) is skipped, the independent views still run, and the task
    fails at the end listing the failed and skipped views. If a view is still
    running after the per-view timeout, the run stops there, so the next view
    never overlaps with it.

Variables (optional)
    mart_refresh_view_timeout_minutes   max wait per view; default 60
    mart_refresh_poll_seconds           status poll interval; default 10

This file mentions airflow and dag, as safe-mode discovery requires.
"""
from __future__ import annotations

import logging
import os
import re
import sys
import time
from datetime import timedelta

import pendulum
from airflow.decorators import task
from airflow.exceptions import AirflowFailException
from airflow.models import DAG, Variable
from airflow.sdk import Asset

try:
    from kubernetes.client import models as k8s
except ImportError:  # local runs: no KubernetesExecutor, nothing to override
    k8s = None

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

log = logging.getLogger(__name__)

DAG_ID = "silver_to_gold_mart_refresh"

# Must match SILVER_TABLES_ASSET in sql_based_dags/bronze_to_silver_task_orchestrator.py.
SILVER_TABLES_ASSET = Asset(name="hcm_analytics_silver_tables")

VIEW_TIMEOUT_VARIABLE = "mart_refresh_view_timeout_minutes"
DEFAULT_VIEW_TIMEOUT_MINUTES = 60
POLL_SECONDS_VARIABLE = "mart_refresh_poll_seconds"
DEFAULT_POLL_SECONDS = 10

# view_refreshes.status values meaning a refresh is still in progress.
IN_PROGRESS_STATUSES = {"Running", "RunningOnAnotherReplica", "WaitingForDependencies", "Scheduling"}

# Same small task pod as the bronze-to-silver orchestrator: this task only sends
# commands and polls; the refresh itself runs inside ClickHouse.
TASK_POD_REQUESTS = {"cpu": "100m", "memory": "256Mi"}
TASK_POD_LIMITS = {"cpu": "500m", "memory": "512Mi"}

_TO_TARGET = re.compile(r"\bTO\s+((?:`[^`]+`|\w+)(?:\.(?:`[^`]+`|\w+))?)", re.IGNORECASE)


def _positive_int_variable(name: str, default: int) -> int:
    raw = Variable.get(name, default_var=default)
    try:
        value = int(raw)
    except (TypeError, ValueError):
        value = 0
    if value <= 0:
        log.warning("Variable '%s' = %r is not a positive integer; using %d.", name, raw, default)
        return default
    return value


def _task_pod_executor_config() -> dict:
    if k8s is None:
        return {}
    resources = k8s.V1ResourceRequirements(requests=TASK_POD_REQUESTS, limits=TASK_POD_LIMITS)
    return {"pod_override": k8s.V1Pod(spec=k8s.V1PodSpec(containers=[k8s.V1Container(name="base", resources=resources)]))}


def _quote_identifier(name: str) -> str:
    return "`" + name.replace("\\", "\\\\").replace("`", "\\`") + "`"


def _to_target(create_query: str) -> str | None:
    """Table name (no database) of the view's TO clause, or None if it has none."""
    match = _TO_TARGET.search(create_query)
    if not match:
        return None
    return match.group(1).split(".")[-1].strip("`")


def _mentions(query: str, name: str) -> bool:
    return re.search(rf"(?<![\w`]){re.escape(name)}(?![\w`])|`{re.escape(name)}`", query) is not None


def _dependencies(views: dict[str, str]) -> dict[str, set[str]]:
    """{view: views it reads from}, from each view's CREATE query text."""
    targets = {view: _to_target(query) for view, query in views.items()}
    deps: dict[str, set[str]] = {}
    for view, query in views.items():
        deps[view] = {
            other
            for other in views
            if other != view
            and (_mentions(query, other) or (targets[other] and targets[other] != targets[view]
                                             and _mentions(query, targets[other])))
        }
    return deps


def _refresh_order(deps: dict[str, set[str]]) -> list[str]:
    """Dependencies first; alphabetical among views ready at the same time."""
    remaining = {view: set(d) for view, d in deps.items()}
    order: list[str] = []
    while remaining:
        ready = sorted(view for view, d in remaining.items() if not d)
        if not ready:
            raise AirflowFailException(f"Dependency cycle between refreshable views: {sorted(remaining)}")
        for view in ready:
            order.append(view)
            del remaining[view]
        for d in remaining.values():
            d.difference_update(ready)
    return order


def _status(client, view: str, start: str) -> dict | None:
    rows = client.query(
        """
        SELECT
            status,
            ifNull(last_refresh_time >= {start:DateTime}, false) AS attempted,
            ifNull(last_success_time >= {start:DateTime}, false) AS succeeded,
            exception
        FROM system.view_refreshes
        WHERE database = currentDatabase() AND view = {view:String}
        """,
        parameters={"view": view, "start": start},
    ).result_rows
    if not rows:
        return None
    status, attempted, succeeded, exception = rows[0]
    return {"status": status, "attempted": bool(attempted), "succeeded": bool(succeeded), "exception": exception or ""}


def _wait_until_idle(client, view: str, deadline: float, poll_seconds: int) -> None:
    """A refresh already in progress (e.g. started by hand) finishes first, so
    the one triggered next can be told apart from it."""
    while True:
        state = _status(client, view, "1970-01-01 00:00:00")
        if state is None or state["status"] not in IN_PROGRESS_STATUSES:
            return
        if time.monotonic() > deadline:
            raise AirflowFailException(f"{view}: an earlier refresh is still running after the timeout; stopping.")
        log.info("%s: an earlier refresh is still %s; waiting", view, state["status"])
        time.sleep(poll_seconds)


def _refresh_one(client, view: str, timeout_minutes: int, poll_seconds: int) -> tuple[bool, str]:
    """(succeeded, error). Raises AirflowFailException on timeout so no later
    view starts while this one may still be running."""
    deadline = time.monotonic() + timeout_minutes * 60
    _wait_until_idle(client, view, deadline, poll_seconds)

    start = client.query("SELECT toString(now())").result_rows[0][0]
    client.command(f"SYSTEM REFRESH VIEW {_quote_identifier(view)}")
    started = time.monotonic()

    while True:
        time.sleep(poll_seconds)
        state = _status(client, view, start)
        if state is None:
            return False, "view no longer listed in system.view_refreshes"
        if state["attempted"] and state["status"] not in IN_PROGRESS_STATUSES:
            if state["succeeded"]:
                log.info("%s: refreshed in %.0fs", view, time.monotonic() - started)
                return True, ""
            return False, state["exception"] or f"refresh ended without success (status {state['status']})"
        if time.monotonic() > deadline:
            raise AirflowFailException(
                f"{view}: refresh still {state['status']} after {timeout_minutes} min "
                f"('{VIEW_TIMEOUT_VARIABLE}'); stopping so the next view doesn't overlap with it."
            )


def _dependents(view: str, deps: dict[str, set[str]]) -> set[str]:
    """Every view that reads from `view`, directly or through other views."""
    found: set[str] = set()
    frontier = {view}
    while frontier:
        nxt = {v for v, d in deps.items() if d & frontier and v not in found}
        found |= nxt
        frontier = nxt
    return found


with DAG(
    dag_id=DAG_ID,
    description="Refreshes the gold mart materialized views one at a time, dependencies first, after silver.",
    schedule=[SILVER_TABLES_ASSET],
    start_date=pendulum.datetime(2024, 1, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    default_args={
        "owner": "data-platform",
        "retries": 0,
        "executor_config": _task_pod_executor_config(),
    },
    tags=["clickhouse-analytics", "clickhouse", "silver-to-gold", "marts"],
) as dag:

    @task(execution_timeout=timedelta(hours=12))
    def refresh_marts() -> dict:
        from clickhouse_utils import get_clickhouse_client

        timeout_minutes = _positive_int_variable(VIEW_TIMEOUT_VARIABLE, DEFAULT_VIEW_TIMEOUT_MINUTES)
        poll_seconds = _positive_int_variable(POLL_SECONDS_VARIABLE, DEFAULT_POLL_SECONDS)
        client = get_clickhouse_client()

        database = client.query("SELECT currentDatabase()").result_rows[0][0]
        rows = client.query(
            """
            SELECT r.view, t.create_table_query
            FROM system.view_refreshes AS r
            INNER JOIN system.tables AS t ON t.database = r.database AND t.name = r.view
            WHERE r.database = currentDatabase()
            """
        ).result_rows
        views = {view: query for view, query in rows}
        if not views:
            log.warning("No refreshable materialized views in database '%s'; nothing to do.", database)
            return {"database": database, "refreshed": [], "failed": {}, "skipped": []}

        deps = _dependencies(views)
        order = _refresh_order(deps)
        log.info("Database '%s': %d views, refresh order:", database, len(order))
        for position, view in enumerate(order, 1):
            log.info("  %2d. %s%s", position, view, f"  (after {', '.join(sorted(deps[view]))})" if deps[view] else "")

        refreshed: list[str] = []
        failed: dict[str, str] = {}
        skipped: set[str] = set()
        for position, view in enumerate(order, 1):
            if view in skipped:
                log.warning("[%d/%d] %s: skipped, it depends on a failed view", position, len(order), view)
                continue
            log.info("[%d/%d] %s: refreshing", position, len(order), view)
            ok, error = _refresh_one(client, view, timeout_minutes, poll_seconds)
            if ok:
                refreshed.append(view)
            else:
                log.error("[%d/%d] %s: FAILED: %s", position, len(order), view, error)
                failed[view] = error
                skipped |= _dependents(view, deps)

        log.info("refreshed %d, failed %d, skipped %d", len(refreshed), len(failed), len(skipped))
        if failed:
            raise AirflowFailException(
                f"Failed: {sorted(failed)}. Skipped (depend on a failed view): {sorted(skipped)}. "
                f"Errors: {failed}"
            )
        return {"database": database, "refreshed": refreshed, "failed": failed, "skipped": sorted(skipped)}

    refresh_marts()
