"""dst_config_sync — the sheet listener: mirrors the Google Sheet into MDMS,
one-way, every 10 minutes.

The sheet stays the only human surface (tabs = deployment groups, rows =
tenant campaigns). Per group: read the tab, diff against the MDMS mirror,
then create new campaigns, update edited ones, deactivate removed ones.
The sheet always wins — a manual MDMS edit is overwritten on the next tick.

Safety rules (encoded and unit-tested in pipeline/mdms.py::plan_sync):
  - invalid row -> rejected with reasons; its existing MDMS entry stays as
    last-known-good (a typo must not kill a running campaign's config)
  - empty sheet read -> never deactivates anything (transient-read guard)
  - duplicate identities -> first row wins, rest rejected with an alert
  - another tab's entries are never touched

Runs ONLY when DST_MDMS_ENABLED=true. Sheet mode touches MDMS nowhere: config
is read straight from the tab and run history is written to the Run Log tab, so
mirroring rows into MDMS would be pure waste — a Sheets read and a set of MDMS
writes every 10 minutes for a mirror nothing reads. The DAG therefore skips
itself in sheet mode rather than relying on MDMS_URL being unset, and can ship
to every deployment unchanged.
"""
import logging
from datetime import datetime, timedelta, timezone

try:
    from airflow.sdk import dag, task
except ImportError:
    from airflow.decorators import dag, task

# Airflow puts DAGS_FOLDER on sys.path, not the directory holding this file.
# With DAGS_FOLDER at the synced repo root, `from dst_common import ...` cannot
# resolve - eGov's own DAGs fail the same way with `common`. Two lines here beat
# a PYTHONPATH the hosted Airflow gives us no way to set.
import os as _os, sys as _sys
_sys.path.insert(0, _os.path.dirname(_os.path.abspath(__file__)))

from dst_common import dst_config
from dst_common.alerts import notify_slack_on_failure, send_slack_warning
from dst_common.deployment_env import (group_environment, load_deployment_groups,
                                   mdms_enabled)

try:
    from airflow.exceptions import AirflowSkipException
except ImportError:  # pragma: no cover - Airflow always provides this
    class AirflowSkipException(Exception):
        pass

log = logging.getLogger(__name__)


@dag(
    dag_id="dst_config_sync",
    description="Sheet listener: mirrors campaign config rows into MDMS (one-way)",
    schedule="*/10 * * * *",
    start_date=datetime(2026, 1, 1, tzinfo=timezone.utc),
    catchup=False,
    max_active_runs=1,
    tags=["dst", "config"],
    default_args={"retries": 1,
                  "retry_delay": timedelta(minutes=2),
                  "on_failure_callback": notify_slack_on_failure},
    doc_md=__doc__,
)
def dst_config_sync():

    @task
    def list_groups():
        """Skips the whole tick in sheet mode, so the UI shows plainly that
        no MDMS traffic happened rather than a green run that did nothing."""
        with dst_config.apply():
            if not mdms_enabled():
                raise AirflowSkipException(
                    "DST_MDMS_ENABLED is false — sheet mode involves MDMS nowhere")
            return load_deployment_groups()

    @task(execution_timeout=timedelta(minutes=5))
    def sync_group_to_mdms(group):
        """Read one tab, plan the diff, apply it to MDMS. Returns the counts
        so the sync history is visible in the Airflow UI per group."""
        from pipeline import config
        from pipeline.mdms import sync_rows_to_mdms

        with dst_config.apply():
            if not mdms_enabled():
                raise AirflowSkipException("DST_MDMS_ENABLED is false — nothing to sync")

            with group_environment(group):
                rows = config.get_active_rows()
                counts = sync_rows_to_mdms(group, rows)

        if counts and counts.get("skip_deactivation"):
            send_slack_warning(
                f"DST CONFIG SYNC [{group['name']}]: the sheet tab read back EMPTY, "
                f"so deactivation was skipped. Campaigns removed from the sheet are "
                f"still ACTIVE in MDMS and will keep producing reports.",
                group_name=group["name"])
        if counts is None:
            log.info(f"[{group['name']}] MDMS_URL not set — nothing to sync")
        elif counts.get("rejected"):
            details = "\n".join(counts.get("rejected_details", []))
            send_slack_warning(
                f"DST CONFIG SYNC [{group['name']}]: {counts['rejected']} sheet "
                f"row(s) REJECTED and not synced to MDMS - fix the sheet:\n{details}")
        return counts

    sync_group_to_mdms.expand(group=list_groups())


dst_config_sync()
