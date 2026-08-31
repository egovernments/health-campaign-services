"""dst_campaign_scheduler — decides, every 5 minutes, which campaign reports are due.

Stateless by design: each tick re-reads the campaign config, so an edit is live
within 5 minutes and there is no cached schedule to invalidate. Config is only
ever read INSIDE tasks — never at DAG-parse time (the dag-processor re-executes
this file's top level every ~30 seconds).

Tick flow:
  list_deployment_groups     Airflow Variable dst_groups (or env default)
  build_find_due_envs        one pod env payload per group
  find_due_campaigns (xN)    POD: read the group's config, match slots against
                             the wall-clock lookback window, apply the retime
                             guard
  collect_due_campaigns      merge every group's due list
  trigger_campaign_run (xM)  one dst_campaign_run per due slot, campaign row in
                             conf, deterministic run id (duplicate slot fires
                             are skipped, never doubled)

WHY find_due_campaigns IS A POD
-------------------------------
Reading the campaign config needs gspread, which is not in the shared Airflow
image and — per the platform's own report-automation guide — should not be: a
new image is built when "Python dependencies (requirements.txt)" change, and the
tag goes into an Airflow Variable. So the dependency lives in OUR image and this
task runs there, exactly as hcm_dynamic_campaigns runs its report pods.

In the worker this task failed on every tick with "No module named 'gspread'",
before it could trigger anything, so no campaign report ran at all.
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

from airflow.models import Variable
from airflow.providers.cncf.kubernetes.operators.pod import KubernetesPodOperator
from kubernetes.client import models as k8s_models

# Airflow puts DAGS_FOLDER on sys.path, not the directory holding this file.
# With DAGS_FOLDER at the synced repo root, `from dst_common import ...` cannot
# resolve - eGov's own DAGs fail the same way with `common`. Two lines here beat
# a PYTHONPATH the hosted Airflow gives us no way to set.
import os as _os, sys as _sys
_sys.path.insert(0, _os.path.dirname(_os.path.abspath(__file__)))

from dst_common.alerts import notify_slack_on_failure
from dst_common.deployment_env import load_deployment_groups
from dst_common.pod_env import build_pod_env

log = logging.getLogger(__name__)

# Set via: Admin -> Variables (Key: DST_REPORT_IMAGE)
# Value:   egovio/campaign-data-analysis-report:airflow-analysis-<sha>
# Read at PARSE time because the operator needs it before construction, so an
# unset Variable is a DAG import error - the intended loud failure, since without
# an image there is nothing to run.
REPORT_IMAGE = Variable.get("DST_REPORT_IMAGE", default_var=None)
if not REPORT_IMAGE:
    raise ValueError("DST_REPORT_IMAGE Airflow Variable is required. Set it via "
                     "Admin -> Variables in the Airflow UI.")

K8S_NAMESPACE = os.getenv("K8S_NAMESPACE", "airflow")

# This pod reads a config sheet and a day of Run Log rows - small next to a
# campaign extract, so a fraction of dst_campaign_run's allowance.
FIND_DUE_RESOURCES = k8s_models.V1ResourceRequirements(
    requests={"memory": "256Mi", "cpu": "100m"},
    limits={"memory": "1Gi", "cpu": "1"},
)


class _UntemplatedPodOperator(KubernetesPodOperator):
    """KubernetesPodOperator that treats env_vars as DATA, never as a template.

    env_vars is a template field and Airflow renders template fields
    recursively, so a config value containing "{{" would be evaluated as Jinja.
    Everything here is already resolved - and includes the service-account key -
    so rendering it can only misfire. Same guard, same reason, as
    _UntemplatedTriggerDagRunOperator below.
    """
    template_fields = ()


@dag(
    dag_id="dst_campaign_scheduler",
    description="Reads the campaign config every 5 minutes and triggers due report runs",
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

    @task
    def build_find_due_envs(groups):
        """One env payload per group, for the mapped pods below.

        Resolved in the worker because only the worker can read the dst_config
        Variable; the pod receives the answer, never the lookup.
        """
        import json
        return [build_pod_env(g, {"DST_POD_MODE": "find_due",
                                  "DST_GROUP": json.dumps(g)})
                for g in groups]

    # One pod per deployment group. do_xcom_push returns the due list that
    # main.py's find_due mode writes to /airflow/xcom/return.json.
    find_due_campaigns = _UntemplatedPodOperator.partial(
        task_id="find_due_campaigns",
        name="dst-find-due",
        namespace=K8S_NAMESPACE,
        image=REPORT_IMAGE,
        # No node_selector: the image is a multi-arch manifest (amd64+arm64), and
        # pinning an arch left pods Pending forever on the arm64 UAT cluster.
        container_resources=FIND_DUE_RESOURCES,
        security_context=k8s_models.V1PodSecurityContext(
            run_as_user=1000, run_as_group=1000, fs_group=1000),
        do_xcom_push=True,
        get_logs=True,
        log_events_on_failure=True,     # surfaces ImagePullBackOff, OOMKilled
        in_cluster=True,
        startup_timeout_seconds=300,
        labels={"app": "dst-find-due", "managed-by": "airflow"},
        # A tick that cannot finish before the next one starts is useless, and
        # max_active_runs=1 means a slow tick blocks the following ones.
        execution_timeout=timedelta(minutes=4),
    ).expand(env_vars=build_find_due_envs(list_deployment_groups()))

    @task
    def collect_due_campaigns(per_group_due):
        # A pod that produced nothing yields None rather than an empty list.
        return [item for group_due in (per_group_due or [])
                for item in (group_due or [])]

    all_due = collect_due_campaigns(find_due_campaigns.output)

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
