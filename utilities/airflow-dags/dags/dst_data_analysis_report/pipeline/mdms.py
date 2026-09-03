"""MDMS as the machine-read campaign-config store, mirrored one-way from the
Google Sheet.

The sheet stays the only human surface (tabs = deployment groups, rows =
tenant campaigns); the dst_config_sync DAG mirrors each tab into MDMS, and the
scheduler reads MDMS instead of the sheet (DST_MDMS_ENABLED=true). Same read
pattern as the platform's hcm_campaign_scheduler, including internal calls
with a dummy authToken (auth is enforced at the gateway, which in-cluster
service-to-service traffic never crosses).

Tenancy mapping: one MDMS root tenant per environment; every entry carries
deploymentGroup (= sheet tab) and its campaign tenant inside data.row, so one
search serves all of a group's tenants — exactly how a sheet tab works.

Sync design: plan_sync() is PURE (sheet rows + existing entries -> actions);
apply_sync() does the HTTP. Every edge case lives in the plan and is
unit-testable without a network.

Entry data shape (top-level fields drive schema uniqueness):
    {"rowIdentity": "<tenant>::<campaign key>::<cycle>::<campaign_start>",
     "deploymentGroup": "<sheet tab / group name>",
     ...every sheet column as its own top-level field, values as strings...}

FLAT on purpose (HCM-style, like airflow-configs.campaign-report-config): each
column is visible and queryable in MDMS instead of hiding inside a nested
object. The two bookkeeping keys above are the ENVELOPE; entry_row() strips
them to recover the sheet row. New sheet columns still flow with no schema
change - the schema does not forbid additionalProperties.

Env: MDMS_URL (in-cluster base URL, required to enable),
MDMS_API_PREFIX (/mdms-v2/v2 via the gateway, /egov-mdms-service/v2
direct — governs BOTH reads and writes), MDMS_SEARCH_ENDPOINT (read path
override only), MDMS_TENANT_ID, MDMS_AUTH_TOKEN
(default "" — internal calls need none), DST_MDMS_SCHEMA_CODE, MDMS_LIMIT.
"""
import logging
import os

import requests

log = logging.getLogger(__name__)

DEFAULT_SCHEMA_CODE = "airflow-configs.dst-campaign-report-config"


def _api_prefix():
    """Path prefix for the MDMS v2 API, WITHOUT a trailing slash.

    Two deployment shapes exist:
      - through the DIGIT gateway:  /mdms-v2/v2          (the default)
      - direct to the service:      /egov-mdms-service/v2
    The read path was configurable via MDMS_SEARCH_ENDPOINT while the write path
    hardcoded "/mdms-v2/v2", so pointing a deployment at a direct service URL
    fixed searches and left every create/update 404ing. One key governs both.

    Verified live 2026-08-24 against mdms-v2 on :8094 (context path /mdms-v2, v2
    controller with _create/{schemaCode}), where /mdms-v2/v2 is correct.
    """
    return os.getenv("MDMS_API_PREFIX", "/mdms-v2/v2").rstrip("/")


def _base_url():
    return os.getenv("MDMS_URL", "").strip().rstrip("/")


# The two bookkeeping fields the sync adds on top of the sheet columns. Row
# reconstruction is "everything except these", so a new sheet column needs no
# code change here.
_ENVELOPE_KEYS = ("rowIdentity", "deploymentGroup")


def entry_row(data):
    """The sheet row inside a FLAT mdms entry: data minus the envelope keys."""
    return {k: v for k, v in (data or {}).items() if k not in _ENVELOPE_KEYS}


def _schema_code():
    return os.getenv("DST_MDMS_SCHEMA_CODE", DEFAULT_SCHEMA_CODE)


def _mdms_tenant(group=None):
    """One MDMS root tenant per environment; a group may override it."""
    if group and str(group.get("mdms_tenant", "")).strip():
        return str(group["mdms_tenant"]).strip()
    return os.getenv("MDMS_TENANT_ID", os.getenv("TENANT_ID", "dev"))


def _request_info():
    """RequestInfo for an internal service-to-service MDMS call.

    userInfo.uuid is REQUIRED for writes, not optional: MDMS's
    enrichAuditDetails populates createdBy/lastModifiedBy from it and rejects the
    request outright without it —
        NullCheckException: User uuid present inside UserInfo being sent to
        enrichAuditDetails method must not be null
    With only {"id": 1} every create and update returned 400. This was invisible
    until a real MDMS v2 was available to call (2026-08-24); a v1 service has no
    write API to fail against.

    authToken stays empty by default because internal calls bypass the gateway —
    the platform's own scheduler does the same.
    """
    uuid_value = os.getenv("MDMS_USER_UUID", "dst-automation").strip() or "dst-automation"
    return {"apiId": "dst-automation", "msgId": "dst-config-sync",
            "authToken": os.getenv("MDMS_AUTH_TOKEN", ""),
            "userInfo": {"id": 1, "uuid": uuid_value, "type": "SYSTEM",
                         "roles": [], "tenantId": _mdms_tenant()}}


def row_identity(row):
    """Stable identity of a campaign across edits: tenant + campaign + cycle + start.

    Editing times/dates/channels keeps the identity (-> update); a genuinely
    new campaign or a new cycle is a new identity (-> create). Falls back to
    project_type_id for non-admin tenants, then state_name.

    cycle_index and campaign_start are part of the key because a campaign
    number is NOT unique per cycle: on the production sheet Chad cycles 1 and 2
    both carry CMP-2026-06-27-000416, as do Bauchi, Kogi, Nasarawa, Oyo, FCT,
    Plateau, Borno, Kebbi, Sokoto and Zamfara. With tenant::campaign alone,
    plan_sync's first-row-wins rule rejected every ACTIVE row in favour of an
    older archived one, so mdms mode ran nothing at all. Cycle alone fixes
    Nigeria; campaign_start is needed for Togo and Taraba, whose rows differ
    only by date.
    """
    tenant = str(row.get("tenant", "")).strip().lower()
    campaign_key = (str(row.get("campaign_number", "")).strip()
                    or str(row.get("project_type_id", "")).strip()
                    or str(row.get("state_name", "")).strip().lower())
    # '2' and '02' are the same cycle - normalise before keying
    cycle = str(row.get("cycle_index", "")).strip().lstrip("0") or "-"
    start = str(row.get("campaign_start", "")).strip() or "-"
    return f"{tenant}::{campaign_key}::{cycle}::{start}"


def protection_key(row):
    """Looser key used ONLY to decide what must not be deactivated.

    The full identity includes campaign_start, so a typo in that cell produces a
    NEW identity and the live entry looks removed from the sheet - which would
    deactivate a running campaign for a single mistyped character, exactly what
    the reject-and-keep rule exists to prevent. Matching on tenant+campaign+cycle
    keeps the entry alive while the typo is rejected.
    """
    return "::".join(row_identity(row).split("::")[:3])


def normalize_row(row):
    return {str(k).strip(): str(v).strip() for k, v in row.items()}


def validate_row(row):
    """Pre-flight checks a bad sheet edit must not get past. Returns a list
    of problems (empty = valid). MDMS schema validation is the second net."""
    from dst_data_analysis_report.pipeline.config import _parse_date
    from dst_data_analysis_report.pipeline.schedule_utils import parse_report_times

    problems = []
    if not str(row.get("tenant", "")).strip():
        problems.append("tenant is empty")
    if not str(row.get("state_name", "")).strip():
        problems.append("state_name is empty")
    for field in ("campaign_start", "campaign_end"):
        if not _parse_date(row.get(field, "")):
            problems.append(f"{field} unparseable: {row.get(field)!r}")
    if not (parse_report_times(row.get("report_times", ""))
            or parse_report_times(row.get("partner_report_times", ""))):
        problems.append(
            f"no valid time in report_times {row.get('report_times')!r} "
            f"or partner_report_times {row.get('partner_report_times')!r}")
    # Optional, but a typo here means the cumulative report silently never runs
    mopup = str(row.get("mopup_end_date", "")).strip()
    if mopup and not _parse_date(mopup):
        problems.append(f"mopup_end_date unparseable: {mopup!r}")
    return problems


def plan_sync(sheet_rows, existing_entries, group_name):
    """Pure diff: what must change in MDMS to mirror the sheet tab.

    Returns {"create": [data], "update": [(entry, data)], "deactivate": [entry],
             "unchanged": int, "rejected": [(identity, problems)],
             "skip_deactivation": bool}

    Edge cases encoded here:
      - duplicate identity in the sheet -> first row wins, later ones rejected
      - invalid row -> rejected; an EXISTING entry for that identity is kept
        as last-known-good (a typo must not kill a running campaign's config)
      - identity vanished from the sheet -> deactivate its entry
      - empty sheet read -> deactivation SKIPPED entirely (a transient empty
        read must not wipe the mirror), flagged for alerting
      - previously deactivated identity returns -> update (reactivates)
      - active=FALSE rows still sync: that column is campaign data the
        scheduler interprets; MDMS isActive only means "row exists on sheet"
    """
    by_identity = {}
    for entry in existing_entries:
        data = entry.get("data") or {}
        if data.get("deploymentGroup") == group_name and data.get("rowIdentity"):
            by_identity[data["rowIdentity"]] = entry

    plan = {"create": [], "update": [], "deactivate": [],
            "unchanged": 0, "rejected": [], "skip_deactivation": False}
    seen = set()

    for raw in sheet_rows:
        row = normalize_row(raw)
        identity = row_identity(row)
        if identity in seen:
            plan["rejected"].append((identity, ["duplicate identity in sheet"]))
            continue
        seen.add(identity)

        problems = validate_row(row)
        if problems:
            plan["rejected"].append((identity, problems))
            continue

        # flat: the row IS the entry, plus the envelope (written last so a
        # hypothetical sheet column of the same name cannot shadow it)
        data = {**row, "rowIdentity": identity, "deploymentGroup": group_name}
        existing = by_identity.get(identity)
        if existing is None:
            plan["create"].append(data)
        elif (entry_row(existing.get("data")) != row
              or not existing.get("isActive", True)):
            plan["update"].append((existing, data))
        else:
            plan["unchanged"] += 1

    if not sheet_rows:
        plan["skip_deactivation"] = True
    else:
        # protected = every identity the sheet still mentions, INCLUDING rows that
        # failed validation (see protection_key)
        protected = {protection_key(normalize_row(r)) for r in sheet_rows}
        for identity, entry in by_identity.items():
            if identity in seen or entry.get("isActive", True) is False:
                continue
            if "::".join(identity.split("::")[:3]) in protected:
                log.warning(f"[mdms-sync] {identity} not matched this tick but the "
                            f"sheet still lists the campaign - keeping it active")
                continue
            plan["deactivate"].append(entry)
    return plan


def search_entries(group=None):
    """All entries of our schema for the group's MDMS tenant (paginated),
    optionally filtered to the group. Same call shape as the platform's
    fetch_campaigns_from_mdms."""
    # MDMS_SEARCH_ENDPOINT still wins when set explicitly (existing deployments)
    endpoint = os.getenv("MDMS_SEARCH_ENDPOINT", _api_prefix() + "/_search")
    url = _base_url() + "/" + endpoint.lstrip("/")
    limit = int(os.getenv("MDMS_LIMIT", "500"))
    entries, offset = [], 0
    while True:
        r = requests.post(url, json={
            "RequestInfo": _request_info(),
            "MdmsCriteria": {"tenantId": _mdms_tenant(group),
                             "schemaCode": _schema_code(),
                             "limit": limit, "offset": offset},
        }, timeout=60)
        r.raise_for_status()
        page = r.json().get("mdms", [])
        entries.extend(page)
        if len(page) < limit:
            break
        offset += limit
    if group is not None:
        entries = [e for e in entries
                   if (e.get("data") or {}).get("deploymentGroup") == group.get("name")]
    return entries


def _write(action, body_mdms):
    url = f"{_base_url()}{_api_prefix()}/_{action}/{_schema_code()}"
    r = requests.post(url, json={"RequestInfo": _request_info(),
                                 "Mdms": body_mdms}, timeout=60)
    if r.status_code >= 400:
        # MDMS returns the actual reason in the body (a named validation error,
        # or which required field is missing). raise_for_status alone gave
        # "400 Client Error:  for url: ..." and nothing else, so the sync failure
        # that reached Slack named no cause at all.
        raise requests.HTTPError(
            f"MDMS {action} rejected with {r.status_code}: {r.text[:400]}",
            response=r)


def apply_sync(plan, group=None):
    """Execute a plan against MDMS. Continues past per-entry failures and
    raises one summary error at the end so the sync task alerts + retries
    (idempotent: a retry re-plans against the new mirror state)."""
    counts = {"created": 0, "updated": 0, "deactivated": 0,
              "unchanged": plan["unchanged"], "rejected": len(plan["rejected"])}
    failures = []

    for data in plan["create"]:
        try:
            _write("create", {"tenantId": _mdms_tenant(group),
                              "schemaCode": _schema_code(),
                              "data": data, "isActive": True})
            counts["created"] += 1
        except Exception as e:
            failures.append(f"create {data['rowIdentity']}: {e}")

    for existing, data in plan["update"]:
        try:
            _write("update", {**existing, "data": data, "isActive": True})
            counts["updated"] += 1
        except Exception as e:
            failures.append(f"update {data['rowIdentity']}: {e}")

    for entry in plan["deactivate"]:
        identity = (entry.get("data") or {}).get("rowIdentity", "?")
        try:
            _write("update", {**entry, "isActive": False})
            counts["deactivated"] += 1
        except Exception as e:
            failures.append(f"deactivate {identity}: {e}")

    for identity, problems in plan["rejected"]:
        log.warning(f"[mdms-sync] REJECTED {identity}: {'; '.join(problems)}")
    if plan["skip_deactivation"]:
        log.warning("[mdms-sync] sheet returned 0 rows — deactivation skipped "
                    "(a transient empty read must not wipe the mirror)")
    if failures:
        raise RuntimeError(f"MDMS sync had {len(failures)} failure(s): "
                           + " | ".join(failures[:5]))
    return counts


def sync_rows_to_mdms(group, sheet_rows):
    """One sync pass for one group (tab): read mirror, plan, apply."""
    if not _base_url():
        log.info("[mdms-sync] MDMS_URL not set — sync skipped")
        return None
    existing = search_entries(group)
    plan = plan_sync(sheet_rows, existing, group.get("name"))
    counts = apply_sync(plan, group)
    counts["skip_deactivation"] = plan["skip_deactivation"]
    counts["rejected_details"] = [f"{identity}: {'; '.join(problems)}"
                                  for identity, problems in plan["rejected"]]
    log.info(f"[mdms-sync] {group.get('name')}: {counts}")
    return counts


def get_active_rows_from_mdms(group):
    """The scheduler's MDMS read path (DST_MDMS_ENABLED=true): returns row
    dicts identical in shape to config.get_active_rows()."""
    if not _base_url():
        raise ValueError("DST_MDMS_ENABLED=true but MDMS_URL is not set")
    rows = [entry_row(e.get("data"))
            for e in search_entries(group) if e.get("isActive", True)]
    log.info(f"MDMS group '{group.get('name')}': {len(rows)} row(s) loaded")
    return rows
