"""
Configuration for the hcm_data_healer DAG.

All settings come from one Airflow Variable, "hcm_data_healer_config" (a JSON
object). They are applied when a task runs and removed when it ends.

A null value unsets a key; "" sets it to empty. Unknown or missing keys fail
the run at the start. The allowed keys are listed in KEYS below.
"""

import difflib
import json
import logging
import os
from contextlib import contextmanager

log = logging.getLogger(__name__)

CONFIG_VARIABLE = "hcm_data_healer_config"

# key -> (required, description). Unlisted keys are rejected.
KEYS = {
    # Elasticsearch
    "ES_BASE_URL": (True, "ES endpoint, e.g. https://elasticsearch-data.es-cluster-v8:9200"),
    "ES_USERNAME": (True, "ES user (read all indices; update on the tracer index)"),
    "ES_PASSWORD": (True, "ES password"),
    "ES_INDEX_PREFIX": (False, 'null/absent = "<tenant>-" prefixed; "" = un-prefixed cluster'),
    # Postgres (read-only)
    "DB_HOST": (True, "Postgres host"), "DB_PORT": (False, "default 5432"),
    "DB_NAME": (True, "database"), "DB_USER": (True, "read-only user"),
    "DB_PASSWORD": (True, "password"), "DB_SSLMODE": (False, "default require"),
    # HCM services (push target, in-cluster)
    "HCM_SERVICE_TEMPLATE": (False, "default http://{service}.egov:8080"),
    # MDMS (what to heal) + tenancy
    "MDMS_URL": (True, "MDMS v2 base URL, e.g. http://mdms-v2.egov:8080"),
    "IS_CENTRAL_INSTANCE_ENABLED": (False, "true = central cluster: tenant taken from each MDMS entry"),
    "TENANT_ID": (True, "the MDMS tenant, e.g. ba (central) or taraba; also the tenant "
                        "healed when not central"),
    # audit
    "KAFKA_BROKER": (True, "Kafka bootstrap servers, comma separated"),
    # Slack
    "SLACK_TOKEN": (False, "bot token; without it nothing is posted"),
    "SLACK_CHANNEL": (False, "ops channel(s), comma separated: full daily report, crash alerts, "
                              "and windows with no slackChannels of their own"),
    "HEALER_AIRFLOW_BASE_URL": (False, "Airflow UI base URL for the 'View run' link in Slack"),
    # emergency stop
    "HEALER_PUSH_ENABLED": (False, "false = emergency stop: analyze + audit only"),
}


class ConfigError(ValueError):
    """The deployment config is unreadable, misspelled or incomplete."""


def get_variable(name):
    """Read an Airflow Variable via the Task SDK, falling back to the ORM.

    Returns "" when unset or when Airflow is not installed. Raises ConfigError
    when Airflow is present but the read fails (fail closed)."""
    errors = []
    try:
        from airflow.sdk import Variable
        value = Variable.get(name, default=None)
        return "" if value is None else str(value)
    except ImportError:
        pass
    except Exception as exc:                                      # noqa: BLE001
        errors.append(f"task-sdk: {type(exc).__name__}: {exc}")
    try:
        from airflow.models import Variable
        value = Variable.get(name, default_var=None)
        return "" if value is None else str(value)
    except ImportError:
        if not errors:
            return ""                                             # no Airflow: local run
    except Exception as exc:                                      # noqa: BLE001
        errors.append(f"orm: {type(exc).__name__}: {exc}")
    raise ConfigError(f"could not read Airflow Variable {name!r}: {'; '.join(errors)}")


def load_config():
    """Return the parsed config Variable, or {} when unset."""
    raw = get_variable(CONFIG_VARIABLE).strip()
    if not raw:
        return {}
    try:
        value = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise ConfigError(f"Airflow Variable {CONFIG_VARIABLE!r} is not valid JSON: {exc}") from None
    if not isinstance(value, dict):
        raise ConfigError(f"Airflow Variable {CONFIG_VARIABLE!r} must be a JSON object")
    bad = [k for k, v in value.items() if isinstance(v, (dict, list))]
    if bad:
        raise ConfigError(f"{CONFIG_VARIABLE}: values must be strings or null, not JSON: "
                          f"{', '.join(sorted(bad))}")
    return value


def preflight():
    """Validate the config, raising one error that lists every problem. Call inside healer_environment()."""
    config = load_config()
    problems = []
    for key in sorted(config):
        if key not in KEYS and not key.startswith("HCM_SVC_"):
            hint = difflib.get_close_matches(key, list(KEYS), n=1)
            problems.append(f"unknown key {key}" + (f" (did you mean {hint[0]}?)" if hint else ""))
    missing = [k for k, (req, _) in KEYS.items() if req and not os.getenv(k, "").strip()]
    if missing:
        problems.append("missing " + ", ".join(missing))
    if "," in os.getenv("TENANT_ID", ""):
        problems.append("TENANT_ID must be one tenant (e.g. ba), not a list")
    if problems:
        raise ConfigError(f"{CONFIG_VARIABLE}: " + "; ".join(problems))
    if not (os.getenv("SLACK_TOKEN", "").strip() and os.getenv("SLACK_CHANNEL", "").strip()):
        log.warning("[config] SLACK_TOKEN/SLACK_CHANNEL not set - no summaries or alerts will be posted")


def push_enabled():
    """Return False when the emergency stop is set in the current environment."""
    return os.getenv("HEALER_PUSH_ENABLED", "true").strip().lower() not in ("false", "0", "no")


def live_push_block():
    """Re-read the Variable mid-push; return a reason to stop, or ''.

    An unreadable Variable stops the push (fail closed)."""
    try:
        value = load_config().get("HEALER_PUSH_ENABLED", os.getenv("HEALER_PUSH_ENABLED", "true"))
    except ConfigError as exc:
        return f"config unreadable mid-push, stopping to be safe: {exc}"
    if str(value or "true").strip().lower() in ("false", "0", "no"):
        return "HEALER_PUSH_ENABLED switched to false during the run"
    return ""


@contextmanager
def healer_environment():
    """Overlay the config Variable onto os.environ for the duration of a task."""
    overlay = load_config()
    if overlay:
        log.info(f"[config] {CONFIG_VARIABLE} sets: {', '.join(sorted(overlay))}")
    saved = {k: os.environ.get(k) for k in overlay}
    try:
        for key, value in overlay.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = str(value)
        yield
    finally:
        for key, value in saved.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = value
