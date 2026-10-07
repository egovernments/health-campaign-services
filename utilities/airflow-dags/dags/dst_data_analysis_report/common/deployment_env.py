"""Per-deployment-group configuration: which sheet tab to read and which
environment (ES credentials, index-prefix convention, Slack defaults) applies.

Groups exist because tenants are grouped BY CREDENTIAL SET, not by country:
all tenants sharing one ES login live on one sheet tab and form one group.

Configuration sources, resolved at TASK RUNTIME (never at DAG-parse time):
  - Airflow Variable "dst_groups": JSON list, e.g.
        [{"name": "nigeria_states", "sheet_tab": "Nigeria States",
          "env": {"ES_INDEX_PREFIX": null}},
         {"name": "togo", "sheet_tab": "togo",
          "env": {"ES_INDEX_PREFIX": "", "CDD_ROLE": "COMMUNITY_DISTRIBUTOR"}}]
    In "env": a null value REMOVES the variable (tenant-prefixed indices need
    ES_INDEX_PREFIX absent); "" sets it present-and-empty (Togo's un-prefixed
    cluster). This distinction is load-bearing — see pipeline/config.py.
  - Airflow Variable "dst_secrets_<name>": CREDENTIALS ONLY for that group
    (ES_USER, ES_PASS, SLACK_TOKEN, GROQ_API_KEY), set via the Airflow UI,
    never in git.

    Do NOT put routing or behaviour here - GOOGLE_SHEET_ID, ES_URL,
    ES_INDEX_PREFIX, GOOGLE_DRIVE_FOLDER_ID, GROQ_MODEL and the like belong to
    the deployment environment. This blob silently overrides the environment,
    so a forgotten entry redirects real work: one stale Variable simultaneously
    pointed run history at the PRODUCTION sheet, pinned a decommissioned LLM
    model, and switched the ES index convention - none of it visible until the
    override logging below was added.
  - With no dst_groups Variable, a single default group falls back to the
    process environment (.env) — local runs work with zero Airflow setup.
"""
import json
import logging
import os
from contextlib import contextmanager

log = logging.getLogger(__name__)


def _default_group():
    return {"name": "default",
            "sheet_tab": os.getenv("GOOGLE_SHEET_TAB", "Sheet1"),
            "env": {}}


def _get_airflow_variable(name):
    """Read an Airflow Variable from either execution context.

    Airflow 3 forbids direct metadata-DB access from task code, so inside a
    task only the Task SDK path works; outside tasks (CLI, dag-processor)
    only the ORM path works. Try both; return "" when unset everywhere.
    """
    sdk_error = orm_error = None
    try:
        from airflow.sdk import Variable as SdkVariable
        value = SdkVariable.get(name, default=None)
        if value is not None:
            return str(value)
    except Exception as e:                                        # noqa: BLE001
        sdk_error = e
    try:
        from airflow.models import Variable as OrmVariable
        return OrmVariable.get(name, default_var="") or ""
    except Exception as e:                                        # noqa: BLE001
        orm_error = e

    # Outside Airflow altogether - local run.py, the JupyterHub boxes - both
    # paths error BY DESIGN and the legacy env-driven path is correct. Only
    # treat a failed read as fatal when Airflow is actually the one running us.
    under_airflow = any(os.getenv(k) for k in
                        ("AIRFLOW_CTX_DAG_ID", "AIRFLOW_HOME",
                         "AIRFLOW__CORE__EXECUTOR"))
    if not under_airflow:
        log.info(f"Airflow Variable {name!r} not readable outside Airflow "
                 f"(expected) - using process environment")
        return ""

    # BOTH paths errored under Airflow. That is NOT the same as "unset", and
    # conflating them is how a 2-second API-server hiccup silently re-routed a
    # whole tick: is_configured() went False, the default group's sheet_tab fell
    # back to "Sheet1", dst_config.apply() became a no-op so GOOGLE_CREDENTIALS_PATH
    # was never set, and the run completed successfully against a DIFFERENT
    # deployment. The entire configuration now lives in one Variable, so an
    # unreachable metadata service must be loud and fatal, not a fallback.
    raise RuntimeError(
        f"Airflow Variable {name!r} could not be READ (this is not the same as "
        f"it being unset). The whole deployment configuration lives in this "
        f"Variable, so continuing would silently run against default or stale "
        f"settings. SDK path: {sdk_error}. ORM path: {orm_error}.")


def load_deployment_groups():
    """Return the configured groups, or the env-driven default group."""
    from dst_data_analysis_report.common import dst_config

    raw = _get_airflow_variable("dst_groups")
    if not raw.strip():
        # dst_config is the single-Variable deployment: one credential set, one
        # sheet tab, tenants as ROWS. dst_groups only earns its keep when a
        # second credential set (Taraba, Togo) joins the same Airflow.
        if dst_config.is_configured():
            group = dst_config.as_group()
            log.info(f"dst_config Variable in use — single deployment group "
                     f"{group['name']!r} on tab {group['sheet_tab']!r}")
            return [group]
        log.info("dst_groups Variable not set — using the env-driven default group")
        return [_default_group()]

    groups = json.loads(raw)
    for group in groups:
        if not group.get("name") or not group.get("sheet_tab"):
            raise ValueError(f"dst_groups entry missing name/sheet_tab: {group}")
    return groups


def _load_group_secrets(group_name):
    from dst_data_analysis_report.common import dst_config

    raw = _get_airflow_variable(f"dst_secrets_{group_name}")
    if not raw.strip():
        if dst_config.is_configured():
            # Not a misconfiguration: dst_config carries the credentials and has
            # already applied them. Warning here would cry wolf every task.
            log.info(f"dst_secrets_{group_name} unset — credentials come from "
                     f"the dst_config Variable")
            return {}
        log.warning(f"secrets Variable 'dst_secrets_{group_name}' is empty — "
                    f"tasks will rely on the process environment")
        return {}
    return json.loads(raw)


@contextmanager
def group_environment(group):
    """Apply one group's sheet tab, env overrides and secrets to os.environ,
    restoring the previous state afterwards — so one group's credentials can
    never leak into another group's task."""
    overrides = dict(group.get("env") or {})
    overrides["GOOGLE_SHEET_TAB"] = group.get("sheet_tab") or "Sheet1"
    if group.get("name") and group["name"] != "default":
        overrides.update(_load_group_secrets(group["name"]))

    # A group's secrets Variable SILENTLY replaces deployment configuration.
    # A stale one pinned GROQ_MODEL to a decommissioned model, ES_INDEX_PREFIX
    # to the wrong convention and GOOGLE_SHEET_ID to PRODUCTION - three real
    # incidents from one forgotten Variable, none visible in any log. Anything
    # a group changes is now stated up front. Values are never logged: keys
    # only, since this blob holds credentials.
    changed = [k for k, v in overrides.items()
               if str(os.environ.get(k)) != str(v) and k in os.environ]
    if changed:
        log.warning(f"[{group.get('name')}] group config REPLACES the deployment "
                    f"environment for: {', '.join(sorted(changed))}")
    routing = sorted(k for k in changed
                     if k in ("GOOGLE_SHEET_ID", "ES_URL", "ES_INDEX_PREFIX",
                              "GOOGLE_DRIVE_FOLDER_ID"))
    if routing:
        log.warning(f"[{group.get('name')}] those include ROUTING keys "
                    f"({', '.join(routing)}) - this group writes and reads "
                    f"somewhere other than the deployment default")

    snapshot = {key: os.environ.get(key) for key in overrides}
    try:
        for key, value in overrides.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = str(value)
        yield
    finally:
        for key, previous in snapshot.items():
            if previous is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = previous


def mdms_enabled():
    """The ONE deployment-wide switch: is this an MDMS-backed deployment?

        DST_MDMS_ENABLED=false (default)  purely Google Sheet — config read from
                                          the tab, run history on the Run Log tab.
        DST_MDMS_ENABLED=true             platform-integrated — config from the
                                          MDMS mirror, run history as Kafka
                                          lifecycle events for the persister.

    Boolean rather than a mode string on purpose: the value space is two, so a
    typo cannot silently select a working-but-wrong mode. An unparseable value
    RAISES instead of defaulting, because the two modes read config from
    different places and write history to different places — quietly guessing
    is worse than a loud failure the operator can see in the task log.

    Either way the sheet is the fallback: if MDMS cannot be read the scheduler
    reads the tab for that tick, and if Kafka cannot be written the outcome is
    appended to the Run Log tab. A deployment with no MDMS write access still
    works end to end.

    Set once per deployment in the environment — never per group or per DAG.
    DST_MODE (sheet|mdms) is still honoured for deployments not yet migrated.
    """
    raw = os.getenv("DST_MDMS_ENABLED")
    if raw is None:
        legacy = (os.getenv("DST_MODE") or "").strip().lower()
        if legacy in ("mdms", "sheet"):
            log.info(f"using legacy DST_MODE={legacy}; prefer DST_MDMS_ENABLED")
            return legacy == "mdms"
        if legacy:
            raise ValueError(
                f"DST_MODE={legacy!r} is not 'sheet' or 'mdms'. Set "
                f"DST_MDMS_ENABLED=true|false instead.")
        return False

    value = str(raw).strip().lower()
    if value in ("true", "1", "yes", "y", "on"):
        return True
    if value in ("false", "0", "no", "n", "off", ""):
        return False
    raise ValueError(
        f"DST_MDMS_ENABLED={raw!r} is not a boolean. Use true or false — "
        f"refusing to guess, because the two modes read config from different "
        f"places and write run history to different places.")
