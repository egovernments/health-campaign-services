"""dst_campaign_run — executes one campaign report end to end. Trigger-only.

Triggered by dst_campaign_scheduler with the campaign's sheet row in
dag_run.conf, so this DAG never reads config itself. The whole pipeline chain
(analyze -> cdd_sync -> report -> notify) runs inside ONE task: intermediate
files stay on that task's local disk (pod-local on Kubernetes), and every
durable artifact — reports, Excels, checkpoints — is published to Google Drive
before the task ends. Checkpoints upload even on failure, so a dead run stays
debuggable from any machine (see pipeline/README.md).

No database anywhere, in any mode. No run lock either: duplicate fires of the
same slot are impossible via deterministic trigger run-ids, and the rare
overlap of two different slots for one tenant is accepted — the same trade
the platform's production report system makes.

Run history follows the universal DST_MODE flag:
  sheet mode — one row on the sheet's Run Log tab
  mdms mode  — one Kafka lifecycle event (platform persister owns the DB write)

Task chain:
  execute_campaign_pipeline  the pipeline chain; data errors fail fast with no
                             retry, infrastructure errors retry
  finalize_run               ALL_DONE: records the outcome, then re-raises on
                             failure so the DAG run itself is marked failed
"""
import logging
from datetime import datetime, timedelta, timezone

try:
    from airflow.sdk import dag, task
except ImportError:
    from airflow.decorators import dag, task

from dst_common import dst_config
from dst_common.alerts import notify_slack_on_failure
from dst_common.campaign_runner import execute_campaign
from dst_common.deployment_env import group_environment, mdms_enabled
from dst_common.dst_kafka_status import push_run_event

log = logging.getLogger(__name__)

EXECUTE_TASK_ID = "execute_campaign_pipeline"


@dag(
    dag_id="dst_campaign_run",
    description="Runs one campaign report: ES extract, Excels, Word docs, Drive upload, Slack post",
    schedule=None,
    start_date=datetime(2026, 1, 1, tzinfo=timezone.utc),
    catchup=False,
    max_active_runs=16,
    # max_consecutive_failed_dag_runs deliberately unset: one DAG serves every
    # tenant, so 3 failures from one bad sheet cell auto-paused the whole fleet.
    # dagrun_timeout must exceed the task's worst case (3 x 60m + 2 x 3m = 186m),
    # or the run is killed mid-retry and finalize_run never writes the audit row.
    dagrun_timeout=timedelta(minutes=200),
    tags=["dst", "reporting"],
    default_args={"on_failure_callback": notify_slack_on_failure},
    doc_md=__doc__,
)
def dst_campaign_run():

    @task(retries=2, retry_delay=timedelta(minutes=3),
          execution_timeout=timedelta(minutes=60))
    def execute_campaign_pipeline(dag_run=None, ti=None):
        """Run the whole pipeline chain for this campaign row (see
        common/campaign_runner.py for stage and error semantics).

        On failure the exception text is pushed to XCom under "failure" BEFORE
        re-raising. A raising task pushes no return value, so finalize_run had
        nothing to write and the Run Log said only "see task log" — useless to
        anyone without Airflow access, which on a hosted deployment is everyone.
        """
        conf = dag_run.conf or {}
        if not conf.get("row"):
            raise ValueError("dag_run.conf is missing 'row' — this DAG must be "
                             "triggered by dst_campaign_scheduler, or manually "
                             "with a full conf payload")
        try:
            with dst_config.apply():
                with group_environment(conf.get("group") or {"name": "default",
                                                             "env": {}}):
                    return execute_campaign(conf["row"], conf.get("mode", "both"))
        except Exception as e:
            if ti is not None:
                try:
                    ti.xcom_push(key="failure", value=f"{type(e).__name__}: {e}"[:900])
                except Exception:            # never mask the real error
                    pass
            raise

    # on_failure_callback disabled here: this task re-raises only as bookkeeping,
    # and inheriting the callback posted TWO Slack alerts per failure.
    # retries=0 is load-bearing: finalize_run always raises on a failed run and is
    # not idempotent (append, fresh event_id, Slack warning), so an inherited
    # default_task_retries=1 would double every failed campaign's audit rows.
    @task(trigger_rule="all_done", on_failure_callback=None, retries=0)
    def finalize_run(dag_run=None, ti=None):
        """Always runs. Records every REAL report attempt on the channel the
        universal DST_MODE flag selects (Run Log tab in sheet mode, Kafka event
        in mdms mode). Routine no-ops record nothing. A raw None from
        xcom_pull means the execute task genuinely failed — recorded, then
        re-raised so the DAG run is marked failed (finalize is the leaf task;
        swallowing the failure would blind max_consecutive_failed_dag_runs)."""
        from dst_common.run_history import record_outcome

        conf = dag_run.conf or {}
        marker = ti.xcom_pull(task_ids=EXECUTE_TASK_ID)
        if marker is not None and marker.get("ok") is None:
            log.info(f"routine no-op ({marker.get('reason')}) — nothing recorded")
            return

        # Resolve the flag defensively: mdms_enabled() RAISES on a bad value,
        # and a typo must not destroy the very audit row this task exists to
        # write. An unusable flag falls back to the sheet, which needs no
        # extra infrastructure, and the misconfiguration still surfaces in the
        # log and in the Slack alert from the failed execute task.
        # mdms_enabled(), push_run_event() and send_slack_warning() all read
        # configuration outside the per-group context, so the whole recording
        # step runs inside the dst_config environment.
        with dst_config.apply():
            try:
                use_mdms = mdms_enabled()
            except ValueError as e:
                log.error(f"[finalize] {e} — recording to the Run Log tab instead", exc_info=True)
                use_mdms = False

            result = record_outcome(conf, dag_run.run_id, marker,
                                    use_mdms, group_environment,
                                    error_detail=ti.xcom_pull(
                                        task_ids=EXECUTE_TASK_ID, key="failure") or "")
        log.info(f"[finalize] outcome recorded via {result['recorded']}")
        failed = result["failed"]

        if failed:
            raise RuntimeError("execute_campaign_pipeline failed — outcome "
                               "recorded; re-raising so the DAG run is marked failed")

    execute_campaign_pipeline() >> finalize_run()


dst_campaign_run()
