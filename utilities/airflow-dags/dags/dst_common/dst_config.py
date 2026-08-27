"""ONE Airflow Variable holding this deployment's entire configuration.

Motivation: on a hosted Airflow we do not own the pods, the Helm values or the
image, so there is no way to set a process environment variable or mount a file.
The only writable surface is Admin -> Variables. Everything the pipeline needs
therefore has to be reachable from a Variable, including the Google service
account, which is normally a file on disk.

Variable name: dst_config    Shape:

    {
      "sheet_tab": "Nigeria States",
      "env": {
        "ES_URL": "https://elasticsearch-data.es-cluster-v8:9200",
        "ES_INDEX_PREFIX": null,          # null REMOVES -> tenant-prefixed
        "GOOGLE_SHEET_ID": "1MspTor...",
        "GOOGLE_DRIVE_FOLDER_ID": "0AO9...",
        "DST_TARGET_FOLDER_ID": "1IyH...",
        "GROQ_MODEL": "openai/gpt-oss-120b",
        "SLACK_CHANNEL": "C0...",
        "DST_ALERT_CHANNEL": "C0...",
        "DST_MDMS_ENABLED": "false",
        "DST_LOOKBACK_MINUTES": "60",
        "CDD_ROLE": "DISTRIBUTOR",        # COMMUNITY_DISTRIBUTOR on Togo
        "DST_DUP_MATRIX": "FALSE"         # TRUE on the Chad ITN deployment
      },
      "secrets": {                        # credentials ONLY
        "ES_USER": "", "ES_PASS": "",
        "SLACK_TOKEN": "xoxb-...",
        "GROQ_API_KEY": "gsk_..."
      },
      "google_credentials_json": { ...service account JSON... }
    }

Why env and secrets are separate keys inside ONE object rather than two
Variables: rotating a token must be a single edit in a single place. Keeping the
two sub-dicts distinct preserves the property that matters — routing lives
somewhere readable and reviewable, credentials live somewhere that is never
logged — while the operator still only ever opens one Variable.

Applied at the TOP of every task, not merely inside group_environment. Several
consumers deliberately run outside the per-group context and would otherwise
never see this config at all:
  - mdms_enabled()                dst_campaign_scheduler / finalize_run
  - push_run_event()              run_history.record_outcome
  - alert_channel()               on_failure_callback, send_slack_warning
The failure callback runs AFTER the env is restored, so alerts.py resolves this
config directly rather than relying on os.environ.

Precedence, lowest to highest: process env  <  env  <  secrets. Secrets winning
is deliberate (a rotated credential must beat a stale deployment value) but it
also means a routing key placed in "secrets" silently overrides the deployment —
the exact class of incident that once pointed run history at the PRODUCTION
sheet and pinned a decommissioned LLM model. Routing keys found in "secrets" are
logged loudly by name.

Absent Variable = legacy behaviour (dst_groups + dst_secrets_<name> + process
env), so local runs and the JupyterHub boxes are unaffected.
"""
import json
import logging
import os
import stat
import tempfile
from contextlib import contextmanager

log = logging.getLogger(__name__)

VARIABLE_NAME = "dst_config"

# Keys that decide WHICH environment we read from and write to. Never
# credentials — if one of these turns up in "secrets" it is a misconfiguration
# worth shouting about, not a preference.
ROUTING_KEYS = ("GOOGLE_SHEET_ID", "GOOGLE_SHEET_TAB", "ES_URL",
                "ES_INDEX_PREFIX", "GOOGLE_DRIVE_FOLDER_ID",
                "DST_TARGET_FOLDER_ID", "GOOGLE_RUNLOG_TAB",
                "DST_MDMS_ENABLED", "MDMS_URL", "KAFKA_BROKER",
                "DST_RUNS_TOPIC")

_cache = None
_cache_loaded = False


def _read_variable(name):
    """Airflow 3 forbids ORM access inside tasks and the Task SDK is absent
    outside them, so both paths are tried. Shared with deployment_env."""
    from dst_common.deployment_env import _get_airflow_variable
    return _get_airflow_variable(name)


def load(refresh=False):
    """Parsed dst_config, or None when the Variable is not set.

    Cached per process: a task may enter the config context more than once and
    each miss is a metadata-DB round trip. A parse failure returns None and
    logs, rather than raising — the legacy path still works, and an unusable
    Variable should not take the whole deployment down silently at import time.
    """
    global _cache, _cache_loaded
    if _cache_loaded and not refresh:
        return _cache

    # A read FAILURE must not be cached: _cache_loaded was set before the
    # content was even validated, so one transient failure pinned this process
    # to "not configured" for its whole life - every later task in that worker
    # ran unconfigured. An unreadable Variable now propagates (see
    # deployment_env._get_airflow_variable); only a genuinely absent or
    # malformed one is cached as None.
    raw = _read_variable(VARIABLE_NAME)
    _cache_loaded = True
    if not (raw or "").strip():
        _cache = None
        return None
    try:
        parsed = json.loads(raw)
    except ValueError as e:
        log.error(f"Variable '{VARIABLE_NAME}' is not valid JSON — falling back "
                  f"to dst_groups/process env: {e}", exc_info=True)
        _cache = None
        return None
    if not isinstance(parsed, dict):
        log.error(f"Variable '{VARIABLE_NAME}' must be a JSON object, got "
                  f"{type(parsed).__name__} — ignoring it")
        _cache = None
        return None
    _cache = parsed
    return _cache


def is_configured():
    return load() is not None


def sheet_tab(default="Sheet1"):
    cfg = load() or {}
    return str(cfg.get("sheet_tab") or default)


def resolved(key, default=None):
    """One value, with the same precedence apply() uses.

    For consumers that run outside the applied context — notably the Slack
    failure callback, which fires after os.environ has been restored.
    """
    cfg = load()
    if cfg:
        for section in ("secrets", "env"):
            block = cfg.get(section) or {}
            if key in block and block[key] is not None:
                return str(block[key])
    value = os.getenv(key)
    return value if value is not None else default


def _overrides():
    """env then secrets, flattened, with the routing-in-secrets warning."""
    cfg = load() or {}
    env = dict(cfg.get("env") or {})
    secrets = dict(cfg.get("secrets") or {})

    misplaced = sorted(k for k in secrets if k in ROUTING_KEYS)
    if misplaced:
        log.warning(f"[dst_config] ROUTING keys found in 'secrets': "
                    f"{', '.join(misplaced)} — these override the deployment "
                    f"and are never logged with their values. Move them to "
                    f"'env' so a wrong one is visible.")
    overlap = sorted(set(env) & set(secrets))
    if overlap:
        log.info(f"[dst_config] 'secrets' takes precedence over 'env' for: "
                 f"{', '.join(overlap)}")

    merged = env
    merged.update(secrets)
    return merged


def _write_credentials_file(payload):
    """Materialise the service account JSON and return its path.

    The pipeline authenticates to Sheets and Drive exclusively with this
    service account (pipeline/notify.py and pipeline/core/drive.py both call
    config._resolve_creds_path; drive_token.json is no longer used anywhere),
    so this single file is all that is needed.

    Written 0600 under the system temp dir, never inside the repo — the repo is
    a git working copy and the deploy target is a PUBLIC repository.
    """
    if isinstance(payload, str):
        try:
            payload = json.loads(payload)
        except ValueError:
            log.error("[dst_config] google_credentials_json is a string but not "
                      "valid JSON — ignoring it", exc_info=True)
            return None
    if not isinstance(payload, dict) or not payload.get("private_key"):
        log.error("[dst_config] google_credentials_json is not a service "
                  "account object (no private_key) — ignoring it")
        return None

    # Per-PROCESS path, not a fixed one. This file is deleted in apply()'s
    # finally, and with max_active_runs=16 two campaign tasks can share a
    # container: on a fixed path the first task to finish deleted the key out
    # from under a longer-running one, whose next Drive call then fell back to
    # the repo-root credential.json (absent on a clean deploy -> FileNotFoundError,
    # which is a DATA_ERROR, so no retry and the slot's run id already consumed;
    # or present on a dev box -> silently a DIFFERENT service account).
    #
    # mkdtemp also closes two smaller holes: makedirs(exist_ok=True) would have
    # accepted a pre-existing directory owned by someone else on a shared /tmp,
    # and it creates with 0700 so there is no window where the parent is
    # world-traversable.
    directory = tempfile.mkdtemp(prefix=f"dst-credentials-{os.getpid()}-")
    path = os.path.join(directory, "credential.json")
    # O_EXCL + 0600 at CREATE time: open() then chmod() left a window in which
    # the private key existed at the umask default (often world-readable).
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    fd = os.open(path, flags, stat.S_IRUSR | stat.S_IWUSR)
    try:
        handle = os.fdopen(fd, "w", encoding="utf-8")
    except BaseException:
        os.close(fd)
        raise
    with handle:
        json.dump(payload, handle)
    log.info(f"[dst_config] service account materialised from the Variable -> "
             f"{path} (client_email={payload.get('client_email', '?')})")
    return path


@contextmanager
def apply():
    """Apply dst_config to os.environ for the duration of the block.

    A no-op when the Variable is unset, so wrapping a task in this is always
    safe. os.environ is restored afterwards — including deleting the
    materialised credentials file, so a private key never outlives the task
    that needed it.
    """
    cfg = load()
    if not cfg:
        yield False
        return

    overrides = _overrides()
    tab = cfg.get("sheet_tab")
    if tab:
        overrides["GOOGLE_SHEET_TAB"] = tab

    creds_path = None
    if cfg.get("google_credentials_json"):
        creds_path = _write_credentials_file(cfg["google_credentials_json"])
        if creds_path:
            overrides["GOOGLE_CREDENTIALS_PATH"] = creds_path

    replaced = sorted(k for k, v in overrides.items()
                      if k in os.environ and str(os.environ.get(k)) != str(v))
    if replaced:
        log.info(f"[dst_config] replaces the process environment for: "
                 f"{', '.join(replaced)}")
    log.info(f"[dst_config] applied {len(overrides)} key(s) from Variable "
             f"'{VARIABLE_NAME}'")

    snapshot = {key: os.environ.get(key) for key in overrides}
    try:
        for key, value in overrides.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = str(value)
        yield True
    finally:
        for key, previous in snapshot.items():
            if previous is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = previous
        if creds_path:
            # Remove the file AND its private directory. A private key must not
            # outlive the task that needed it. (On SIGKILL neither runs - that
            # is why the directory is per-process and 0700, so a leftover is at
            # least not readable by other users.)
            try:
                os.remove(creds_path)
            except OSError:
                pass
            try:
                os.rmdir(os.path.dirname(creds_path))
            except OSError:
                pass


def as_group():
    """dst_config expressed as a single deployment group.

    The Bauchi central instance is ONE credential set serving every tenant on
    the Nigeria States tab — tenants are rows, not groups — so the group list
    collapses to one entry. The multi-group path (dst_groups) still exists for
    the day Taraba or Togo, which need their own ES logins, are moved onto the
    same Airflow.
    """
    cfg = load() or {}
    env = dict(cfg.get("env") or {})
    return {"name": str(cfg.get("name") or "dst"),
            "sheet_tab": sheet_tab(),
            "env": env}
