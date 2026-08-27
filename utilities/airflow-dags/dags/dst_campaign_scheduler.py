"""dst_campaign_scheduler — decides, every 5 minutes, which campaign reports are due.

Stateless by design: each tick re-reads the Google Sheet, so a sheet edit is
live within 5 minutes and there is no cached schedule to invalidate. The sheet
is only ever read INSIDE tasks — never at DAG-parse time (the dag-processor
re-executes this file's top level every ~30 seconds).

Tick flow:
  list_deployment_groups     Airflow Variable dst_groups (or env default)
  find_due_campaigns (xN)    read the group's sheet tab, match slots against
                             the wall-clock lookback window, apply the
                             retime guard
  collect_due_campaigns      merge every group's due list
  trigger_campaign_run (xM)  one dst_campaign_run per due slot, campaign row
                             in conf, deterministic run id (duplicate slot
                             fires are skipped, never doubled)
"""
import logging
import os
from datetime import datetime, timedelta, timezone

try:
    from airflow.sdk import dag, task
except ImportError:
    from airflow.decorators import dag, task
try:
    from airflow.providers.standard.operators.trigger_dagrun import TriggerDagRunOperator
except ImportError:
    from airflow.operators.trigger_dagrun import TriggerDagRunOperator

# Airflow puts DAGS_FOLDER on sys.path, not the directory holding this file.
# With DAGS_FOLDER at the synced repo root, `from dst_common import ...` cannot
# resolve - eGov's own DAGs fail the same way with `common`. Two lines here beat
# a PYTHONPATH the hosted Airflow gives us no way to set.
import os as _os, sys as _sys
_sys.path.insert(0, _os.path.dirname(_os.path.abspath(__file__)))

from dst_common import dst_config
from dst_common.alerts import notify_slack_on_failure
from dst_common.deployment_env import (group_environment, load_deployment_groups,
                                   mdms_enabled)
from dst_common.run_history import build_retime_guard
from dst_common.slots import find_due_slots

log = logging.getLogger(__name__)

# Read INSIDE the task, never here. The dag-processor re-executes this module's
# top level every ~30 seconds, long before any Airflow Variable has been
# applied, so a value supplied via dst_config could never reach a module-level
# read — it would silently keep this default forever.
DEFAULT_LOOKBACK_MINUTES = "60"


@dag(
    dag_id="dst_campaign_scheduler",
    description="Reads the campaign config sheet every 5 minutes and triggers due report runs",
    schedule="*/5 * * * *",
    start_date=datetime(2026, 1, 1, tzinfo=timezone.utc),
    catchup=False,
    max_active_runs=1,
    tags=["dst", "reporting"],
    default_args={"retries": 1,
                  "retry_delay": timedelta(minutes=1),
                  "on_failure_callback": notify_slack_on_failure},
    doc_md=__doc__,
)
def dst_campaign_scheduler():

    @task
    def list_deployment_groups():
        groups = load_deployment_groups()
        log.info(f"deployment groups: {[g['name'] for g in groups]}")
        return groups

    @task(execution_timeout=timedelta(minutes=4))
    def find_due_campaigns(group):
        """Read one group's campaign rows and return the slots due right now.

        Config source follows DST_MDMS_ENABLED: false (default) reads the
        Google Sheet tab directly; true reads the mirror maintained by the
        dst_config_sync listener DAG — with a per-tick fallback to the sheet
        when MDMS is unreachable, so scheduling never stops for an MDMS outage
        (the two sources are identical by construction).

        Wall-clock window (now - lookback, now]: Airflow 3's CronTriggerTimetable
        has a zero-width data interval, so the tick's own timestamps are useless
        for windowing. The lookback also gives downtime catch-up.
        """
        from pipeline import config

        # dst_config wraps the WHOLE body: mdms_enabled() is read outside the
        # per-group context and would otherwise never see Variable-supplied
        # configuration.
        with dst_config.apply():
            lookback = int(os.getenv("DST_LOOKBACK_MINUTES",
                                     DEFAULT_LOOKBACK_MINUTES))
            use_mdms = mdms_enabled()
            with group_environment(group):
                if use_mdms:
                    from pipeline.mdms import get_active_rows_from_mdms
                    try:
                        rows = get_active_rows_from_mdms(group)
                    except ValueError as e:
                        # A CONFIGURATION error, not an outage. mdms.py raises
                        # ValueError for "DST_MDMS_ENABLED=true but MDMS_URL is
                        # not set", and catching it as an outage put the
                        # deployment in a permanent hybrid state: every tick
                        # logged one warning and quietly read the sheet, the
                        # sync DAG no-opped, history fell back to the sheet, and
                        # the deployment still reported itself as MDMS mode.
                        # mdms_enabled() refuses to guess for exactly this
                        # reason; swallowing the error here defeated that.
                        raise
                    except Exception as e:
                        log.warning(f"[{group['name']}] MDMS unreachable — falling "
                                    f"back to the sheet for this tick: {e}")
                        rows = config.get_active_rows()
                else:
                    rows = config.get_active_rows()
                # sheet mode: retime guard reads today's Run Log rows (one sheet
                # read per tick). mdms mode: no guard — zero sheet/DB access on
                # the scheduling path; run-id dedup still prevents duplicates.
                guard = None if use_mdms else build_retime_guard()

            now = datetime.now(timezone.utc)
            due = find_due_slots(group, rows, now, lookback,
                                 has_report_since=guard)
            log.info(f"[{group['name']}] {len(rows)} rows -> {len(due)} due slot(s) "
                     f"in the last {lookback} min")
            for item in due:
                log.info(f"  due: {item['trigger_run_id']}")
            return due

    @task
    def collect_due_campaigns(per_group_due):
        return [item for group_due in per_group_due for item in group_due]

    groups = list_deployment_groups()
    due_per_group = find_due_campaigns.expand(group=groups)
    all_due = collect_due_campaigns(due_per_group)

    # `conf` is a TEMPLATE FIELD on TriggerDagRunOperator and Airflow renders it
    # recursively, so every sheet cell was evaluated as Jinja: "{{" in a campaign
    # name killed the tick, and "{{ var.value.dst_config }}" would render the
    # service-account private key into dag_run.conf and the UI.
    # Emptying template_fields is the fix - conf is data, not a template.
    class _UntemplatedTriggerDagRunOperator(TriggerDagRunOperator):
        """TriggerDagRunOperator that treats conf as DATA, never as a template."""
        template_fields = ()

    _UntemplatedTriggerDagRunOperator.partial(
        task_id="trigger_campaign_run",
        trigger_dag_id="dst_campaign_run",
        skip_when_already_exists=True,
        reset_dag_run=False,
    ).expand_kwargs(all_due)


dst_campaign_scheduler()
