"""
Configuration for the HCM Data Healer.

- Secrets and connection details: process environment (Airflow) or a local .env.
- Per-run inputs: DAG run conf, or the core.runner CLI.
- Operational defaults: the constants below.
"""

import os
from datetime import datetime

from dotenv import load_dotenv

# PROJECT_ROOT holds the local .env and output/; HEALER_ENV_FILE overrides the .env path.
PACKAGE_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROJECT_ROOT = os.path.dirname(os.path.dirname(PACKAGE_ROOT))
DEFAULT_ENV_PATH = os.getenv("HEALER_ENV_FILE", "").strip() or os.path.join(PROJECT_ROOT, ".env")

# Operational defaults
def _flag(name, default):
    return os.getenv(name, str(default)).strip().lower() in ("1", "true", "yes")


# In-cluster ES and HCM endpoints use self-signed certificates.
VERIFY_SSL = False
ES_BATCH_SIZE = 5000
DB_CONNECT_TIMEOUT = 60

PUSH_OPTIONS = {"max_retries": 3, "retry_delay": 2, "search_batch": 50}

# ProjectTask filter; blank reconciles all tasks.
DEFAULT_TASK_FILTER = {"status": "", "dose_index": ""}

# Push identity (RequestInfo.userInfo.uuid); records keep their source audit fields.
DEFAULT_USER_UUID = "f47ac10b-58cc-4372-a567-0e02b2c3d479"


def _dotenv_is_local_only():
    """Whether to read the local .env (skipped under Airflow).

    Under Airflow a stray `ES_INDEX_PREFIX=` in a .env would switch every tenant
    to un-prefixed indices, since an absent ES_INDEX_PREFIX is itself a setting.
    HEALER_LOAD_DOTENV forces the decision either way.
    """
    flag = os.getenv("HEALER_LOAD_DOTENV", "").strip().lower()
    if flag in ("0", "false", "no"):
        return False
    if flag in ("1", "true", "yes"):
        return True
    return not any(key in os.environ for key in
                   ("AIRFLOW_HOME", "AIRFLOW_CTX_DAG_ID", "AIRFLOW__CORE__EXECUTOR"))


def load_env(env_path=None):
    """Load the .env for local runs; does not override existing variables."""
    if _dotenv_is_local_only():
        load_dotenv(env_path or DEFAULT_ENV_PATH, override=False)


def index_prefix(tenant):
    """ES index prefix: ES_INDEX_PREFIX unset -> "<tenant>-"; "" -> none; else as given."""
    value = os.getenv("ES_INDEX_PREFIX")
    return f"{tenant}-" if value is None else value


def _check_date(label, value):
    try:
        return datetime.strptime(value, "%Y-%m-%d").date()
    except (TypeError, ValueError):
        raise ValueError(f"{label} {value!r} is not a YYYY-MM-DD date")


def _secret(env_name, required=True, label=None):
    value = os.getenv(env_name, "")
    if required and not str(value).strip():
        raise ValueError(f"Missing '{env_name}' in .env / environment"
                         + (f" ({label})" if label else ""))
    return value


def _shared_es_db():
    """Build ES and DB connection config from the environment."""
    es_cfg = {
        "base_url": _secret("ES_BASE_URL", label="ES base url"),
        "username": _secret("ES_USERNAME", label="ES username"),
        "password": _secret("ES_PASSWORD", label="ES password"),
        "verify_ssl": VERIFY_SSL,
        "batch_size": ES_BATCH_SIZE,
    }
    db_cfg = {
        "host": _secret("DB_HOST", label="DB host"),
        "port": int(_secret("DB_PORT", required=False) or 5432),
        "database": _secret("DB_NAME", label="DB name"),
        "user": _secret("DB_USER", label="DB user"),
        "password": _secret("DB_PASSWORD", label="DB password"),
        "sslmode": _secret("DB_SSLMODE", required=False) or "require",
        "connect_timeout": DB_CONNECT_TIMEOUT,
    }
    return es_cfg, db_cfg


def _service_config():
    """In-cluster service hosts: HCM_SERVICE_TEMPLATE plus HCM_SVC_<SERVICE> overrides."""
    template = os.getenv("HCM_SERVICE_TEMPLATE", "").strip() or "http://{service}.egov:8080"
    overrides = {}
    for k, v in os.environ.items():
        if k.startswith("HCM_SVC_") and str(v).strip():
            overrides[k[len("HCM_SVC_"):].lower()] = v.strip()
    return template, overrides


def build_campaign_context(tenant, user_uuid, start_date, end_date, task_filter=None,
                           flag_processed=False):
    """Build the run context from run inputs and the environment.

    flag_processed: after a successful push, mark source tracer docs
    Data.isProcessed=true (the ES credential needs update rights).
    """
    load_env()
    tenant = (tenant or "").strip()
    if not tenant:
        raise ValueError("tenant is empty")
    start = _check_date("start_date", (start_date or "").strip())
    end = _check_date("end_date", (end_date or "").strip())
    if start > end:
        raise ValueError(f"start_date {start} is after end_date {end}")
    es_cfg, db_cfg = _shared_es_db()
    template, overrides = _service_config()
    return {
        "name": tenant,
        "tenant": tenant,
        "es_index_prefix": index_prefix(tenant),
        "auth_token": "",                       # in-cluster calls need no token
        "user_uuid": (user_uuid or "").strip() or DEFAULT_USER_UUID,
        "es": es_cfg,
        "db": db_cfg,
        "task_filter": task_filter if task_filter is not None else dict(DEFAULT_TASK_FILTER),
        "push_options": dict(PUSH_OPTIONS),
        "start_date": start_date.strip(),
        "end_date": end_date.strip(),
        "service_template": template,
        "service_overrides": overrides,
        "flag_processed": bool(flag_processed),
        "hcm_verify_ssl": VERIFY_SSL,
    }
