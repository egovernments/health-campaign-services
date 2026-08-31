"""Builds the environment a DST pod needs, from this deployment's configuration.

All three DST DAGs launch pods from the same image, so they all need the same
env payload. Kept here rather than repeated per DAG: the key list is the
contract between the DAGs and main.py, and three copies of it would drift.

No Airflow import at module level - dst_common ships inside the pod image, which
has no Airflow.
"""
import json
import logging

log = logging.getLogger(__name__)

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
    "MDMS_URL", "MDMS_API_PREFIX", "MDMS_TENANT_ID",
    "DST_LOOKBACK_MINUTES",
)


def build_pod_env(group=None, extra=None):
    """Resolve the deployment configuration into a flat env dict for a pod.

    Called from a TASK, never at parse time: the DAG processor re-parses every
    ~30s, and a parse-time read would hit the metadata DB that often and bake
    credentials into the parsed DAG.

    The pod receives credentials as env values, carried in the calling task's
    XCom. That is the same exposure as the dst_config Variable itself - anyone
    who can read one can read the other - so it adds no new surface. Removing it
    would need a Kubernetes Secret, i.e. cluster access this deployment lacks.
    """
    from dst_common import dst_config
    from dst_common.deployment_env import group_environment

    env = {}
    with dst_config.apply():
        with group_environment(group or {"name": "default", "env": {}}):
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

    for key, value in (extra or {}).items():
        env[key] = value if isinstance(value, str) else json.dumps(value)

    log.info(f"pod env prepared: {len(env)} key(s); credentials "
             f"{'included' if 'DST_GOOGLE_CREDENTIALS_JSON' in env else 'ABSENT'}; "
             f"ES_URL {'set' if env.get('ES_URL') else 'MISSING'}")
    return env
