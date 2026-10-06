"""hcm_data_healer - daily repair of missing HCM data for active MDMS campaign windows.

Runs at 02:00 UTC for each active window in MDMS (airflow-configs.dst-data-healer-config):
analyze (DB vs ES + error-tracer chain, read-only) -> guard -> push -> audit -> Slack.

Guardrails: the newest 3 days are never healed (settle period); existence checks fail
closed; no push when the analysis is degraded, one side is empty, or gaps exceed
MAX_GAP_PCT / MAX_GAP_RECORDS; no push without a working audit path; writes never retry.
HEALER_PUSH_ENABLED=false stops pushing (re-checked between entities).

Manual trigger with `tenant` heals just that window. Config: Airflow Variable
hcm_data_healer_config (see hcm_data_healer/airflow_env.py KEYS).
"""
import logging
import os
import shutil
import tempfile
from datetime import datetime, timedelta, timezone

try:
    from airflow.sdk import Param, dag, task
except ImportError:
    from airflow.decorators import dag, task
    from airflow.models.param import Param

from airflow.utils.trigger_rule import TriggerRule

# Airflow puts DAGS_FOLDER on sys.path, not the directory holding this file.
import os as _os, sys as _sys
_here = _os.path.dirname(_os.path.abspath(__file__))
if _here not in _sys.path:
    _sys.path.insert(0, _here)

from hcm_data_healer.airflow_env import healer_environment, live_push_block, push_enabled
from hcm_data_healer.notify import AlreadyAlerted, run_timeout_alert, task_failure_alert

log = logging.getLogger(__name__)

EXECUTION_TIMEOUT = timedelta(hours=8)


def manual_target(params):
    """Build one window from manual-trigger params, applying the settle rule."""
    from hcm_data_healer.mdms import to_target
    t = to_target({"startDate": params.get("start_date"), "endDate": params.get("end_date"),
                   "errorTracer": "YES" if params.get("run_chain", True) else "NO",
                   "campaignName": "manual run"}, str(params["tenant"]).strip())
    if t["error"] or t["skip"]:
        raise ValueError(f"manual run not possible: {t['error'] or t['skip']}")
    return t


def degraded(stages, run_chain):
    """Return why the analysis must not drive a push, or '' if it is clean."""
    if stages.get("reconciliation") != "ok":
        return f"reconciliation {stages.get('reconciliation')}"
    if run_chain and stages.get("chain") != "ok":
        return f"error-tracer {stages.get('chain')}"
    return ""


# Per-entity gap limits; above either, the push is refused.
MAX_GAP_PCT = 5
MAX_GAP_RECORDS = 20000


def gap_guard(recon_rows, chain_totals):
    """Return why the push is refused because counts look like a broken read, or ''.

    A wrong index or bad date field returns 0 hits without an error, making every
    record look missing."""
    pct, cap = MAX_GAP_PCT, MAX_GAP_RECORDS
    for r in recon_rows:
        try:
            db, es = int(r["DB ids"]), int(r["ES ids"])
            gaps = int(r["Ghost (ES not in DB)"]) + int(r["Index-failed (DB not in ES)"])
        except (KeyError, TypeError, ValueError):
            return f"{r.get('Entity')}: counts unreadable"
        if (db == 0) != (es == 0):
            return f"{r['Entity']}: DB has {db:,} and ES has {es:,} - one side empty"
        total = max(db, es)
        if gaps > cap:
            return f"{r['Entity']}: {gaps:,} gaps exceed the limit of {cap:,}"
        if total and gaps > 100 and gaps / total * 100 > pct:
            return (f"{r['Entity']}: {gaps:,} gaps = {gaps / total * 100:.1f}% of {total:,} "
                    f"records, over the {pct}% limit")
    if chain_totals.get("chain_recoverable", 0) > cap:
        return f"chain: {chain_totals['chain_recoverable']:,} recoverable exceed the limit of {cap:,}"
    return ""


def found_counts(totals):
    return {"ghosts_found": totals["ghosts"],
            "index_failed_found": totals["index_failed"],
            "chain_missing": totals["chain_missing"],
            "chain_recoverable": totals["chain_recoverable"],
            "unrecoverable": totals["chain_unrecoverable"]}


@dag(
    dag_id="hcm_data_healer",
    description="Daily: per MDMS campaign window, finds missing HCM data (DB vs ES + error tracer), "
                "pushes the fix, audits every record to the DB, posts to Slack",
    schedule="0 2 * * *",                # 02:00 UTC daily
    start_date=datetime(2026, 1, 1, tzinfo=timezone.utc),
    catchup=False,
    max_active_runs=1,
    max_active_tasks=4,
    # Must end before the next 02:00 run; a timed-out run alerts via the DAG callback.
    dagrun_timeout=timedelta(hours=23),
    on_failure_callback=run_timeout_alert,
    default_args={"on_failure_callback": task_failure_alert, "retries": 0},
    tags=["dst", "data-healer"],
    params={
        "tenant": Param("", type="string",
                        description="Blank = every active MDMS entry. Set = heal just this tenant window"),
        "start_date": Param("", type="string", description="YYYY-MM-DD (manual run only)"),
        "end_date": Param("", type="string",
                          description="YYYY-MM-DD; blank or later than the settle cutoff = the cutoff"),
        "run_chain": Param(True, type="boolean", description="Include error-tracer chain recovery"),
    },
    doc_md=__doc__,
)
def hcm_data_healer():

    @task
    def get_campaigns_from_mdms(params=None, dag_run=None, ti=None):
        """Return the windows to heal; the full roster goes to XCom key "roster"."""
        from hcm_data_healer import mdms
        from hcm_data_healer.airflow_env import preflight
        # Trigger conf overrides Params; not every trigger path merges conf into params.
        params = {**(params or {}), **((getattr(dag_run, "conf", None) or {}) if dag_run else {})}
        with healer_environment():
            preflight()
            if str(params.get("tenant") or "").strip():
                target = manual_target(params)
                log.info(f"[healer] manual run: {target['tenant']}:{target['start_date']}"
                         f"..{target['end_date']}")
                plan = {"heal": [target], "roster": {"tenants": [target["tenant"]], "heal": 1,
                                                     "inactive": 0, "skipped": [], "invalid": []}}
            else:
                plan = mdms.plan()
        ti.xcom_push(key="roster", value=plan["roster"])
        return plan["heal"]

    @task(execution_timeout=EXECUTION_TIMEOUT,
          map_index_template="{{ task.op_kwargs['target']['tenant'] }}:"
                             "{{ task.op_kwargs['target']['start_date'] }}")
    def heal_campaign(target, dag_run=None, ti=None):
        """Analyze -> guard -> push -> audit -> alert, for one campaign window."""
        from hcm_data_healer import audit, notify
        from hcm_data_healer.core import runner

        dag_run_id = dag_run.run_id if dag_run else "local"
        run_id = audit.run_id_of(dag_run_id, target)
        end_date = target["end_date"]
        tag = f"[healer {target['tenant']}:{target['start_date']}]"

        def say(msg):
            log.info(f"{tag} {msg}")

        result = {"tenant": target["tenant"], "start_date": target["start_date"],
                  "campaign_name": target.get("campaign_name", ""),
                  "end_date": end_date, "run_id": run_id, "status": "FAILED",
                  "counts": {}, "recovered_by_error": {}, "unrecoverable_by_issue": {},
                  "step_failed": "", "reason": "",
                  "slack_channels": target.get("slack_channels") or []}
        alerted = {"slack": False}

        def finish(status, counts=None, tally=None, step_failed="", reason=""):
            result.update(status=status, counts=counts or {}, step_failed=step_failed,
                          reason=reason,
                          recovered_by_error=(tally or {}).get("recovered_by_error", {}),
                          unrecoverable_by_issue=(tally or {}).get("unrecoverable_by_issue", {}),
                          failed_by_reason=(tally or {}).get("failed_by_reason", {}),
                          by_category=(tally or {}).get("by_category", {}),
                          duration_min=max(round((audit.now_ms() - started_ms) / 60000), 1))
            # Pushed before any failure so the summary covers it and the failure callback stays quiet.
            if ti is not None:
                ti.xcom_push(key="result", value=dict(result))
                alerted["slack"] = True
            audit.publish_run(audit.run_event(status, run_id, dag_run_id, target, end_date, started_ms,
                                              counts, step_failed, notify.redact(reason, 500)))

        with healer_environment():
            # Audit first: if Kafka is down this raises before anything is written.
            started_ms = audit.now_ms()
            audit.publish_run(audit.run_event("RUNNING", run_id, dag_run_id, target, end_date, started_ms))
            say(f"RUNNING run_id={run_id} window {target['start_date']}..{end_date}")

            own_dir = True
            output_root = tempfile.mkdtemp(prefix=f"healer_{target['tenant']}_")
            step = "analyze"
            try:
                cfg = runner.make_context(target["tenant"], target["start_date"], end_date,
                                          flag_processed=True)
                # Read-only, so one retry is safe.
                try:
                    analysis = runner.analyze(cfg, output_root, run_chain=target["run_chain"],
                                              log_fn=say)
                except Exception as exc:                          # noqa: BLE001
                    say(f"analyze failed ({type(exc).__name__}: {exc}); retrying once")
                    analysis = runner.analyze(cfg, output_root, run_chain=target["run_chain"],
                                              log_fn=say)
                counts = found_counts(analysis["totals"])

                step = "guard"
                why_not = (degraded(analysis["stages"], target["run_chain"])
                           or gap_guard(analysis["reconciliation"], counts)
                           or ("" if push_enabled() else "HEALER_PUSH_ENABLED=false"))
                auditor = audit.RecordAuditor(run_id, target["tenant"], analysis["recon_dir"],
                                              analysis["chain_dir"], dag_run_id=dag_run_id,
                                              window_start=target["start_date"])
                pushed = None
                if why_not:
                    say(f"push SKIPPED - {why_not}")
                else:
                    step = "push"
                    pushed = runner.push(cfg, analysis["recon_dir"], analysis["chain_dir"],
                                         output_root, record_sink=auditor.on_push_batch,
                                         should_stop=live_push_block, log_fn=say)
                    counts.update({"created": pushed["pushed"], "reindexed": pushed["updated"],
                                   "skipped": pushed["skipped"], "failed": pushed["failed"]})

                step = "audit"
                push_state = "skipped" if pushed is None else (
                    "stopped" if pushed["aborted"] else "done")
                tally = audit.tally(auditor.finish(push_state))
            except BaseException as exc:                          # incl. task timeout
                try:
                    finish("FAILED", step_failed=step, reason=f"{type(exc).__name__}: {exc}")
                except BaseException:                             # noqa: BLE001
                    log.exception(f"{tag} could not record the FAILED event")
                if alerted["slack"]:
                    raise AlreadyAlerted(f"{tag} {step} failed: {type(exc).__name__}: {exc}") from exc
                raise
            finally:
                if own_dir:
                    shutil.rmtree(output_root, ignore_errors=True)

            if pushed is None:
                finish("CHECKED_ONLY", counts, tally, step_failed="guard", reason=why_not)
            elif pushed["aborted"]:
                finish("FAILED", counts, tally, step_failed="push",
                       reason=f"push stopped before finishing: {pushed['abort_reason']}")
                raise (AlreadyAlerted if alerted["slack"] else RuntimeError)(
                    f"{tag} push stopped: {pushed['abort_reason']}")
            elif pushed["failed"]:
                finish("COMPLETED_WITH_FAILURES", counts, tally)
            else:
                finish("COMPLETED", counts, tally)
            say(f"{result['status']} {counts}")

        if result["status"] == "COMPLETED_WITH_FAILURES":
            # Good records are already pushed; fail the task so the failures get reviewed.
            raise (AlreadyAlerted if alerted["slack"] else RuntimeError)(
                f"{tag} {pushed['failed']} record(s) failed to push - "
                f"see dst_healer_record where dag_run_id = '{dag_run_id}'")
        return result

    @task(trigger_rule=TriggerRule.ALL_DONE)
    def post_daily_summary(results, ti=None, dag_run=None):
        """Post the run summary; fail the run if anything upstream failed.

        Results are read from the "result" XCom, so failed windows are still counted."""
        from hcm_data_healer import notify
        roster = ti.xcom_pull(task_ids="get_campaigns_from_mdms", key="roster")
        if roster is None:
            # Planning failed (already alerted); without this the run would end green.
            raise RuntimeError("get_campaigns_from_mdms failed - nothing was healed tonight")
        pushed = ti.xcom_pull(task_ids="heal_campaign", key="result") or []
        if isinstance(pushed, dict):
            pushed = [pushed]
        done = [r for r in pushed if r] or [r for r in (results or []) if r]
        crashed = max(roster.get("heal", 0) - len(done), 0)
        with healer_environment():
            started = getattr(dag_run, "start_date", None)
            minutes = (max(round((datetime.now(timezone.utc) - started).total_seconds() / 60), 1)
                       if started else None)
            posted = notify.run_summary(datetime.now(timezone.utc).date().isoformat(), roster, done,
                                        crashed, dag_run_id=getattr(dag_run, "run_id", ""),
                                        run_minutes=minutes)
        bad = [r for r in done if r["status"] in ("FAILED", "COMPLETED_WITH_FAILURES")]
        if crashed or bad or roster.get("invalid"):
            raise (AlreadyAlerted if posted else RuntimeError)(
                f"{crashed} window(s) did not finish, {len(bad)} finished with errors, "
                f"{len(roster.get('invalid', []))} invalid MDMS entr(ies) - see Slack and dst_healer_report")
        return {"healed": len(done)}

    windows = get_campaigns_from_mdms()
    post_daily_summary(heal_campaign.expand(target=windows))


hcm_data_healer()
