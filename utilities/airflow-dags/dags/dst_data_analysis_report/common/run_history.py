"""Sheet-backed retime guard for sheet mode — no database anywhere.

There is deliberately NO run lock in this system: duplicate fires of the same
slot are impossible via deterministic trigger run-ids, and the rare overlap of
two different slots for one tenant is accepted (worst case: one duplicate
Slack post), the same trade the platform's production report system makes.

The guard must be built inside group_environment (it reads the group's sheet).
mdms mode skips the guard entirely (zero sheet/DB access on the scheduling
path there).
"""
import logging


log = logging.getLogger(__name__)


def build_retime_guard():
    """One sheet read, then a pure closure for find_due_slots.

    has_report_since(tenant, mode, slot_dt, state_name, campaign_number,
    cycle_index) -> True when a SUCCESS row for this CAMPAIGN already exists
    today for a slot at or after this one, with a covering mode ("both" covers
    internal and partner). FAILED runs do not count, so a retimed slot may
    replace a failed report.

    Scoped by campaign, not just tenant: one tenant runs several campaigns
    (Bauchi SMC + ITN, Chad's three rows), and tenant-only matching let one
    campaign's success suppress another's slot.
    """
    from dst_data_analysis_report.pipeline.run_log import fetch_today_runs
    from dst_data_analysis_report.pipeline.schedule_utils import parse_report_times
    today_runs = fetch_today_runs()
    log.info(f"[retime-guard] {len(today_runs)} run(s) recorded today")

    def has_report_since(tenant, mode, slot_dt, state_name="",
                         campaign_number="", cycle_index=""):
        """Keyed on TENANT, which is what find_due_slots passes. It previously
        compared against the Run Log's State column while the caller passed the
        lowercase tenant, so the guard never matched anything and a retimed
        slot re-fired. Compares the recorded SLOT time, not the write time."""
        covering = {mode, "both"}
        slot_hhmm = slot_dt.strftime("%H:%M")

        def normalised(value):
            """Google Sheets strips a leading zero, so a slot written as 07:24 can
            read back as 7:24. These times were compared as STRINGS, and
            "7:24" > "07:24" lexically — so a single-digit hour sorted after every
            two-digit one and the guard's >= test silently inverted."""
            parsed = parse_report_times(value)
            return parsed[0] if parsed else str(value)
        def same_campaign(r):
            # rows written before the Tenant column existed (and every row
            # run.py writes) carry only State — fall back to it rather than
            # treating them as belonging to no campaign at all
            same_tenant = (r["tenant"] == tenant
                           or (not r["tenant"] and state_name
                               and r["state"] == state_name))
            if not same_tenant:
                return False
            # One tenant legitimately holds SEVERAL campaigns: Bauchi runs SMC
            # and ITN together, Chad has three rows. Matching on tenant alone
            # let an SMC success at 05:30 suppress the ITN 05:00 slot inside the
            # lookback window - a silently missing report, no failure, no alert.
            # An empty cell on a legacy row matches anything, so old rows keep
            # behaving exactly as before.
            if campaign_number and r.get("campaign")                     and r["campaign"] != campaign_number:
                return False
            if cycle_index and r.get("cycle") and r["cycle"] != cycle_index:
                return False
            return True
        return any(same_campaign(r) and r["status"] == "SUCCESS"
                   and r["mode"] in covering
                   and normalised(r["slot_time"]) >= slot_hhmm
                   for r in today_runs)

    return has_report_since


def record_outcome(conf, dag_run_id, marker, use_mdms, group_environment,
                   error_detail=""):
    """Record one run's outcome on the channel the deployment flag selects.

    Kept out of the DAG file so it can be exercised without an Airflow
    scheduler. Returns a summary of what was written:
        {"recorded": "kafka"|"sheet"|"none", "failed": bool,
         "step_failed": str, "drive_folder_url": str}

    A marker of None means the execute task genuinely failed. Sheet mode writes
    the Run Log tab; mdms mode publishes to Kafka and FALLS BACK to the Run Log
    tab if the publish does not land, so a broker outage cannot silently erase
    the audit trail.
    """
    from dst_data_analysis_report.common.alerts import send_slack_warning
    from dst_data_analysis_report.common.dst_kafka_status import push_run_event

    row = conf.get("row") or {}
    group = conf.get("group") or {"name": "default", "env": {}}
    mode = conf.get("mode", "both")

    failed = marker is None
    stages = {} if failed else marker.get("stages", {})
    degraded = any(str(v).startswith("degraded") for v in stages.values())
    step_failed = next((name for name, outcome in stages.items()
                        if str(outcome).startswith("failed")), "")
    if failed and not step_failed:
        step_failed = "execute_campaign_pipeline"
    # The Error column is the audit trail a human actually reads, so it carries
    # the REAL text. Two things were wrong here:
    #   - on failure it said only "see task log", which is useless to anyone
    #     without Airflow access (on a hosted deployment, everyone);
    #   - on degradation it said "cdd_sync degraded" unconditionally, so a run
    #     degraded by a failed Drive upload or an ungenerated narrative was
    #     reported as a cdd_sync problem — naming the wrong culprit is worse
    #     than naming none.
    degraded_text = "; ".join(f"{name}: {outcome}"
                              for name, outcome in stages.items()
                              if str(outcome).startswith("degraded"))
    if failed:
        error = error_detail or (
            f"execute_campaign_pipeline failed and pushed no detail — see the "
            f"task log for run {dag_run_id}")
    elif degraded:
        error = degraded_text
    else:
        error = ""
    drive_folder_url = "" if failed else marker.get("drive_folder_url", "")
    drive_link = "" if failed else marker.get("drive_link", "")
    day = "" if failed else marker.get("day", "")

    # A degraded run SUCCEEDS - Airflow is green and on_failure_callback never
    # fires - so without this the only trace was a spreadsheet cell nobody
    # reads, while a report with missing sync numbers or a placeholder
    # narrative had already gone to the partner channel.
    if degraded and not failed:
        # Written for whoever is on call, not for whoever wrote the pipeline. The
        # previous version read "cdd_sync (degraded: produced no sync workbook)":
        # a module name, an internal artifact, and no statement of impact, cause
        # or action. Name the campaign, say what is missing from the report, and
        # say what to do.
        problems = "\n".join(
            "  - " + str(outcome).replace("degraded:", "").strip()
            for outcome in stages.values() if str(outcome).startswith("degraded"))
        campaign = row.get("campaign_name") or row.get("campaign_number") or "?"
        where = " ".join(x for x in (conf.get("slot_date", ""),
                                     conf.get("slot_time", "")) if x)
        day_part = f" / Day {day}" if day else ""
        # Slack mrkdwn: a scannable structure (bold headers, one issue per line)
        # reads better than a run-on paragraph when triaging several at once.
        state = conf.get("state_name", "?")
        head = f"{campaign}" + (f" · Day {day}" if day else "")
        from dst_data_analysis_report.common.alerts import (build_alert_blocks,
                                                            COLOR_INCOMPLETE)
        slot_field = f"*Slot*\n{where or '-'}  ({mode})"
        blocks = build_alert_blocks(
            header=f"Report incomplete — {state}",
            lead=f"*{head}* was published and sent to Slack, "
                 f"but it is missing data.",
            fields=[slot_field, f"*Campaign*\n{campaign}"],
            detail_label="What is missing", detail=problems,
            context="Before you share it: review the report, fix the cause "
                    "above, then re-run the slot if the numbers matter.")
        send_slack_warning(
            f"Report incomplete — {state} · {head} — published but missing data",
            group_name=(group or {}).get("name", ""),
            blocks=blocks, color=COLOR_INCOMPLETE)

    published = False
    if use_mdms:
        published = push_run_event(
            "REPORT_FAILED" if failed else "REPORT_COMPLETED",
            conf, dag_run_id, step_failed=step_failed,
            drive_folder_url=drive_folder_url, day=day)
        if not published:
            log.warning("[finalize] Kafka publish did not land — falling back "
                        "to the Run Log tab so the outcome is not lost")

    recorded = "kafka" if published else "none"
    if not published:
        with group_environment(group):
            from dst_data_analysis_report.pipeline.run_log import append_run_log
            ok = append_run_log(
                conf.get("state_name", ""), row.get("campaign_name", ""), day,
                "FAILED" if failed else "SUCCESS",
                step_failed=step_failed, error=error,
                drive_link=drive_folder_url or drive_link, mode=mode,
                tenant=conf.get("tenant", ""),
                cycle_index=row.get("cycle_index", ""),
                slot_date=conf.get("slot_date", ""),
                slot_time=conf.get("slot_time", ""),
                dag_run_id=dag_run_id)
        recorded = "sheet" if ok else "none"

    return {"recorded": recorded, "failed": failed,
            "step_failed": step_failed, "drive_folder_url": drive_folder_url}
