"""dst_campaign_run — runs ONE campaign report in its own pod. Trigger-only.

Triggered by dst_campaign_scheduler with the campaign's sheet row in
dag_run.conf, so this DAG never reads config itself.

WHY A POD rather than an in-process import
------------------------------------------
The pipeline needs gspread, pandas, openpyxl, matplotlib and the Google client
libraries. Importing it inside the Airflow worker means those must exist in the
SHARED Airflow image, which on a hosted Airflow we cannot change - a real deploy
failed exactly that way with "No module named 'gspread'".

In a pod the dependencies live in OUR image
(utilities/campaign-data-analysis-report/Dockerfile), so changing one is a
rebuild plus an Airflow Variable edit: no Helm, no cluster access. Same model as
the platform's hcm_dynamic_campaigns DAG and its hcm-custom-reports image, and
this DAG follows that file's pod configuration deliberately.

    build_pod_env  ->  run_campaign (pod)  ->  finalize_run

build_pod_env resolves this deployment's configuration; the pod's main.py applies
it, runs the campaign and writes the outcome marker, which finalize_run records
exactly as before.

Image tag comes from the Airflow Variable DST_REPORT_IMAGE, set from the UI after
the GitHub Actions build. DAG-only changes need no rebuild - git-sync picks those
up in about a minute.

Run history still follows the universal DST_MODE flag:
  sheet mode — one row on the sheet's Run Log tab
  mdms mode  — one Kafka lifecycle event (platform persister owns the DB write)
"""
import json
import logging
import os
from datetime import datetime, timedelta, timezone

try:
    from airflow.sdk import dag, task
except ImportError:
    from airflow.decorators import dag, task

from airflow.models import Variable
from airflow.providers.cncf.kubernetes.operators.pod import KubernetesPodOperator
from kubernetes.client import models as k8s_models

# Airflow puts DAGS_FOLDER on sys.path, not the directory holding this file.
# With DAGS_FOLDER at the synced repo root, `from dst_common import ...` cannot
# resolve - eGov's own DAGs fail the same way with `common`. Two lines here beat
# a PYTHONPATH the hosted Airflow gives us no way to set.
import os as _os, sys as _sys
_sys.path.insert(0, _os.path.dirname(_os.path.abspath(__file__)))

from dst_common import dst_config
from dst_common.alerts import notify_slack_on_failure
from dst_common.deployment_env import group_environment, mdms_enabled

log = logging.getLogger(__name__)

EXECUTE_TASK_ID = "run_campaign"
BUILD_ENV_TASK_ID = "build_pod_env"

# Set via: Admin -> Variables -> Add Variable (Key: DST_REPORT_IMAGE)
# Value:   egovio/campaign-data-analysis-report:airflow-analysis-<sha>
# Read at PARSE time because the operator needs it before construction, so an
# unset Variable is a DAG import error - the intended loud failure, since without
# an image there is nothing to run.
REPORT_IMAGE = Variable.get("DST_REPORT_IMAGE", default_var=None)
if not REPORT_IMAGE:
    raise ValueError("DST_REPORT_IMAGE Airflow Variable is required. Set it via "
                     "Admin -> Variables in the Airflow UI.")

K8S_NAMESPACE = os.getenv("K8S_NAMESPACE", "airflow")

# A campaign scroll holds a day of task documents plus the name maps in memory.
# Limits sized from hcm_dynamic_campaigns, which handles comparable volumes.
CONTAINER_RESOURCES = k8s_models.V1ResourceRequirements(
    requests={"memory": "512Mi", "cpu": "100m"},
    limits={"memory": "6Gi", "cpu": "2"},
)

# Configuration the pod needs. Routing first, then credentials. Anything absent
# is simply not passed, and the pipeline's own defaults apply.
POD_ENV_KEYS = (
    "ES_URL", "ES_INDEX_PREFIX", "ES_USER", "ES_PASS",
    "GOOGLE_SHEET_ID", "GOOGLE_SHEET_TAB", "GOOGLE_RUNLOG_TAB",
    "GOOGLE_DRIVE_FOLDER_ID", "DST_TARGET_FOLDER_ID",
    "SLACK_TOKEN", "SLACK_CHANNEL", "DST_ALERT_CHANNEL",
    "GROQ_API_KEY", "GROQ_MODEL", "GROQ_BASE_URL",
    "CDD_ROLE", "CDD_ROLE_ITN", "DST_DUP_MATRIX",
    "KAFKA_BROKER", "DST_RUNS_TOPIC", "TENANT_ID", "DST_MDMS_ENABLED",
)


@dag(
    dag_id="dst_campaign_run",
    description="Runs one campaign report in a pod: ES extract, Excels, Word docs, Drive upload, Slack post",
    schedule=None,
    start_date=datetime(2026, 1, 1, tzinfo=timezone.utc),
    catchup=False,
    max_active_runs=16,
    # max_consecutive_failed_dag_runs deliberately unset: one DAG serves every
    # tenant, so 3 failures from one bad sheet cell auto-paused the whole fleet.
    # dagrun_timeout must exceed the pod's worst case (3 x 60m + 2 x 3m = 186m),
    # or the run is killed mid-retry and finalize_run never writes the audit row.
    dagrun_timeout=timedelta(minutes=200),
    tags=["dst", "reporting"],
    default_args={"on_failure_callback": notify_slack_on_failure},
    doc_md=__doc__,
)
def dst_campaign_run():

    @task
    def build_pod_env(dag_run=None):
        """Resolve this deployment's configuration into the pod's env vars.

        In a TASK, not at parse time: the DAG processor re-parses every 30s, and
        a parse-time read would hit the metadata DB that often and bake
        credentials into the parsed DAG.

        The pod receives credentials as env values, held in this task's XCom.
        That is the same exposure as the dst_config Variable itself - anyone who
        can read one can read the other - so it adds no new surface. Removing it
        would need a Kubernetes Secret, i.e. cluster access this deployment does
        not have.
        """
        conf = dag_run.conf or {}
        if not conf.get("row"):
            raise ValueError("dag_run.conf is missing 'row' — this DAG must be "
                             "triggered by dst_campaign_scheduler, or manually "
                             "with a full conf payload")

        env = {}
        with dst_config.apply():
            with group_environment(conf.get("group") or {"name": "default",
                                                         "env": {}}):
                for key in POD_ENV_KEYS:
                    value = dst_config.resolved(key)
                    if value not in (None, ""):
                        env[key] = str(value)

            # The service account travels as JSON and the pod writes its own 0600
            # file. GOOGLE_CREDENTIALS_PATH would be meaningless here - that path
            # exists only in the Airflow worker.
            cfg = dst_config.load() or {}
            creds = cfg.get("google_credentials_json")
            if creds:
                env["DST_GOOGLE_CREDENTIALS_JSON"] = (
                    creds if isinstance(creds, str) else json.dumps(creds))

        env["DST_CAMPAIGN_ROW"] = json.dumps(conf["row"])
        env["DST_RUN_MODE"] = conf.get("mode", "both")
        log.info(f"pod env prepared: {len(env)} key(s); credentials "
                 f"{'included' if 'DST_GOOGLE_CREDENTIALS_JSON' in env else 'ABSENT'}; "
                 f"ES_URL {'set' if env.get('ES_URL') else 'MISSING'}")
        return env

    # One JSON env var rather than thirty templated ones: less to render, and
    # main.py applies it to os.environ in a single step.
    run_campaign = KubernetesPodOperator(
        task_id=EXECUTE_TASK_ID,
        name="dst-campaign-report",
        namespace=K8S_NAMESPACE,
        image=REPORT_IMAGE,
        # No node_selector: the image is a multi-arch manifest (amd64+arm64), and
        # pinning an arch left pods Pending forever on the arm64 UAT cluster.

        # No cmds/arguments - the Dockerfile ENTRYPOINT runs main.py.
        env_vars={
            "DST_POD_CONFIG": "{{ ti.xcom_pull(task_ids='" + BUILD_ENV_TASK_ID + "') | tojson }}",
        },
        container_resources=CONTAINER_RESOURCES,
        security_context=k8s_models.V1PodSecurityContext(
            run_as_user=1000, run_as_group=1000, fs_group=1000),

        do_xcom_push=True,              # the outcome marker main.py writes
        get_logs=True,                  # pod stdout into the Airflow task log
        log_events_on_failure=True,     # surfaces ImagePullBackOff, OOMKilled
        is_delete_operator_pod=False,   # keep it after failure so events survive
        in_cluster=True,
        startup_timeout_seconds=600,
        labels={"app": "dst-campaign-report", "managed-by": "airflow"},

        retries=2,
        retry_delay=timedelta(minutes=3),
        execution_timeout=timedelta(minutes=60),
    )

    # on_failure_callback disabled here: this task re-raises only as bookkeeping,
    # and inheriting the callback posted TWO Slack alerts per failure.
    # retries=0 is load-bearing: finalize_run always raises on a failed run and is
    # not idempotent (append, fresh event_id, Slack warning), so an inherited
    # default_task_retries=1 would double every failed campaign's audit rows.
    @task(trigger_rule="all_done", on_failure_callback=None, retries=0)
    def finalize_run(dag_run=None, ti=None):
        """Always runs. Records every REAL report attempt on the channel the
        universal DST_MODE flag selects (Run Log tab in sheet mode, Kafka event
        in mdms mode). Routine no-ops record nothing. A raw None from xcom_pull
        means the pod genuinely failed — recorded, then re-raised so the DAG run
        is marked failed (finalize is the leaf task; swallowing the failure would
        blind anything watching DAG-run state)."""
        from dst_common.run_history import record_outcome

        conf = dag_run.conf or {}
        marker = ti.xcom_pull(task_ids=EXECUTE_TASK_ID)
        # KubernetesPodOperator returns whatever the pod wrote to
        # /airflow/xcom/return.json. A pod that died before writing it yields
        # None - exactly the "genuinely failed" signal. Some provider versions
        # hand it back as a string.
        if isinstance(marker, str):
            try:
                marker = json.loads(marker)
            except ValueError:
                log.warning("[finalize] pod XCom was not JSON — treating the run "
                            "as failed")
                marker = None
        if not isinstance(marker, dict):
            marker = None

        if marker is not None and marker.get("ok") is None:
            log.info(f"routine no-op ({marker.get('reason')}) — nothing recorded")
            return

        # Resolve the flag defensively: mdms_enabled() RAISES on a bad value, and
        # a typo must not destroy the very audit row this task exists to write. An
        # unusable flag falls back to the sheet, which needs no extra
        # infrastructure, and the misconfiguration still surfaces in the log and
        # in the Slack alert from the failed pod task.
        with dst_config.apply():
            try:
                use_mdms = mdms_enabled()
            except ValueError as e:
                log.error(f"[finalize] {e} — recording to the Run Log tab instead",
                          exc_info=True)
                use_mdms = False

            # The pod cannot push a second XCom key, so its failure text rides on
            # the marker itself (main.py puts it under "error").
            result = record_outcome(conf, dag_run.run_id, marker,
                                    use_mdms, group_environment,
                                    error_detail=str((marker or {}).get("error", ""))[:900])
        log.info(f"[finalize] outcome recorded via {result['recorded']}")

        if result["failed"]:
            raise RuntimeError("the campaign pod failed — outcome recorded; "
                               "re-raising so the DAG run is marked failed")

    build_pod_env() >> run_campaign >> finalize_run()


dst_campaign_run()
