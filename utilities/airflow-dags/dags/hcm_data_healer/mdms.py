"""
Reads the campaign windows to heal from MDMS (schema airflow-configs.dst-data-healer-config).

MDMS is read once, under one tenant:
  TENANT_ID, e.g. ba (central) or taraba.
  central (IS_CENTRAL_INSTANCE_ENABLED=true): each entry heals the tenant in its `tenant`
    column (blank = TENANT_ID), e.g. so -> so-*-index-v1, so.*
  otherwise: TENANT_ID is also the tenant healed.
An entry runs only when data.active is YES and MDMS isActive is true. Fields (strings): rowIdentity, tenant, campaignName,
startDate, endDate (blank = settle cutoff), errorTracer (blank = YES), slackChannels, active.
Calls are in-cluster with an empty authToken. Env: MDMS_URL, plus tenancy keys.
"""

import logging
import os
import re
from datetime import datetime, timedelta, timezone

import requests

log = logging.getLogger(__name__)

SCHEMA_CODE = "airflow-configs.dst-data-healer-config"
API_PREFIX = "/mdms-v2/v2"
PAGE_SIZE = 500
MAX_PAGES = 50                 # guard against a build that ignores offset
SETTLE_DAYS = 3                # newest days still in flight: never healed
MAX_OPEN_WINDOW_DAYS = 120     # a window with no endDate may not run longer


def _search_url():
    base = os.getenv("MDMS_URL", "").strip().rstrip("/")
    if not base:
        raise ValueError("MDMS_URL is not set - nothing to read the heal list from")
    return f"{base}{API_PREFIX}/_search"


def is_central():
    return os.getenv("IS_CENTRAL_INSTANCE_ENABLED", "false").strip().lower() == "true"


def mdms_tenant():
    """The one MDMS tenant (TENANT_ID)."""
    tenant = os.getenv("TENANT_ID", "").strip().lower()
    if not tenant:
        raise ValueError("TENANT_ID is not set")
    if "," in tenant:
        raise ValueError("TENANT_ID must be one tenant (e.g. ba), not a list")
    return tenant


# a campaign tenant code: it becomes an index prefix and a DB schema name
_TENANT = re.compile(r"^[a-z][a-z0-9_]*$")


def _request_info(tenant):
    # No token needed in-cluster; userInfo.uuid is set because some MDMS builds require it.
    return {"apiId": "data-healer", "msgId": "data-healer-read", "authToken": "",
            "userInfo": {"id": 1, "uuid": "data-healer", "type": "SYSTEM",
                         "roles": [], "tenantId": tenant}}


def search_entries(tenant):
    """Return all healer-schema entries for `tenant`, paginated.

    Stops on a repeated page (MDMS ignoring offset) and raises after MAX_PAGES."""
    url, limit, max_pages = _search_url(), PAGE_SIZE, MAX_PAGES
    entries, offset, seen_ids = [], 0, set()
    for _ in range(max_pages):
        r = requests.post(url, json={
            "RequestInfo": _request_info(tenant),
            "MdmsCriteria": {"tenantId": tenant, "schemaCode": SCHEMA_CODE,
                             "limit": limit, "offset": offset},
        }, timeout=60)
        r.raise_for_status()
        page = r.json().get("mdms", []) or []
        ids = {e.get("id") for e in page}
        if page and ids <= seen_ids:
            log.warning(f"[mdms] {tenant}: page at offset {offset} repeats earlier entries "
                        f"- MDMS ignores offset; stopping pagination")
            return entries
        seen_ids |= ids
        entries.extend(page)
        if len(page) < limit:
            return entries
        offset += limit
    raise RuntimeError(f"MDMS search for {tenant} exceeded {max_pages} pages "
                       f"- refusing a possibly endless listing")


def parse_date(value):
    """Normalise a date to YYYY-MM-DD; '' when blank, ValueError when unreadable."""
    s = str(value or "").strip()
    if not s:
        return ""
    for fmt in ("%Y-%m-%d", "%d/%m/%Y", "%d-%m-%Y", "%Y/%m/%d"):
        try:
            return datetime.strptime(s, fmt).date().isoformat()
        except ValueError:
            continue
    raise ValueError(f"unreadable date {s!r} (use YYYY-MM-DD)")


def yes(value, default):
    s = str(value or "").strip().upper()
    return default if not s else s in ("YES", "Y", "TRUE", "1")


# Slack channel ids (e.g. C0BDXCWCC3Z), not channel names.
_CHANNEL_ID = re.compile(r"^[CGD](?=[A-Z0-9]*\d)[A-Z0-9]{8,}$")


def parse_channels(value):
    """Split a comma-separated list of Slack channel ids, deduplicated in order.

    Raises on anything that is not a channel id, so typos are reported."""
    out = []
    for part in str(value or "").replace(";", ",").split(","):
        cid = part.strip()
        if not cid:
            continue
        if not _CHANNEL_ID.match(cid):
            raise ValueError(f"Slack channel {part.strip()!r} is not a valid channel ID "
                             f"(expected format: C0BDXCWCC3Z)")
        if cid not in out:
            out.append(cid)
    return out


def settle_cutoff(today=None):
    """Return the last day a heal may cover: today minus SETTLE_DAYS.

    Newer records may still be in flight; healing them would create duplicates."""
    today = today or datetime.now(timezone.utc).date()
    return (today - timedelta(days=SETTLE_DAYS)).isoformat()


def to_target(data, mdms_tenant, mdms_id="", today=None):
    """Build a heal target from entry data; sets 'error' (bad input) or 'skip' (not yet due)."""
    named = str(data.get("tenant") or "").strip().lower()
    target = {"tenant": (named if is_central() else "") or mdms_tenant,
              "campaign_name": str(data.get("campaignName") or "").strip(),
              "row_identity": str(data.get("rowIdentity") or "").strip(),
              "start_date": "", "end_date": "",
              "run_chain": yes(data.get("errorTracer"), default=True),
              "mdms_id": mdms_id, "error": "", "skip": "", "slack_channels": []}
    try:
        target["slack_channels"] = parse_channels(data.get("slackChannels"))
        if not is_central() and named and named != mdms_tenant:
            # single-tenant cluster: only TENANT_ID lives here
            raise ValueError(f"tenant {named!r} does not match TENANT_ID {mdms_tenant!r}")
        if not _TENANT.match(target["tenant"]):
            raise ValueError(f"tenant {target['tenant']!r} is not a valid tenant code (e.g. ba)")
        start = parse_date(data.get("startDate"))
        end = parse_date(data.get("endDate"))
        if not start:
            raise ValueError("startDate is empty")
        if end and end < start:
            raise ValueError(f"endDate {end} is before startDate {start}")
        target["start_date"] = start
        cutoff = settle_cutoff(today)
        open_days = MAX_OPEN_WINDOW_DAYS
        if not end and (datetime.fromisoformat(cutoff) - datetime.fromisoformat(start)).days > open_days:
            raise ValueError(f"no endDate and the window is over {open_days} days old "
                             f"- set endDate (or active=NO when the campaign is over)")
        # never past the settle cutoff
        target["end_date"] = min(end, cutoff) if end else cutoff
        if start > cutoff:
            target["skip"] = (f"The window starts on {start}; processing begins after the "
                              f"{SETTLE_DAYS}-day settling period")
    except ValueError as exc:
        target["error"] = str(exc)
    return target


def plan(today=None):
    """Return {"heal": [targets], "roster": {...}} for every entry under the MDMS tenant.

    The roster feeds the daily summary. Overlapping windows of one tenant are
    invalid, since two concurrent heals could both create the same records."""
    heal, skipped, invalid, inactive = [], [], [], 0
    root = mdms_tenant()
    try:
        entries = search_entries(root)
    except Exception as exc:                                      # noqa: BLE001
        log.error(f"[mdms] {root}: search failed: {exc}")
        invalid.append({"tenant": root, "entry": "", "reason": f"MDMS search failed: {exc}"[:300]})
        entries = []
    windows = {}                                                  # tenant -> accepted windows
    # Earliest first, so an overlap deterministically rejects the later entry.
    for entry in sorted(entries, key=lambda e: str((e.get("data") or {}).get("startDate") or "")):
        data = entry.get("data") or {}
        if entry.get("isActive", True) is False or not yes(data.get("active"), default=False):
            inactive += 1
            continue
        t = to_target(data, root, entry.get("id", ""), today)
        tenant = t["tenant"]
        label = t["row_identity"] or f"{tenant}::{data.get('startDate', '?')}"
        chans = t["slack_channels"]
        if t["error"]:
            invalid.append({"tenant": tenant, "entry": label, "reason": t["error"],
                            "slack_channels": chans})
        elif t["skip"]:
            skipped.append({"tenant": tenant, "entry": label, "reason": t["skip"],
                            "slack_channels": chans})
        else:
            mine = windows.setdefault(tenant, [])
            clash = next((w for w in mine if t["start_date"] <= w["end_date"]
                          and w["start_date"] <= t["end_date"]), None)
            if clash:
                invalid.append({"tenant": tenant, "entry": label, "slack_channels": chans,
                                "reason": f"overlaps {clash['start_date']}..{clash['end_date']}"
                                          f" - one active window per tenant"})
            else:
                mine.append(t)
                heal.append(t)
    tenants = sorted({x["tenant"] for x in heal + skipped + invalid}) or [root]
    roster = {"tenants": tenants, "heal": len(heal), "inactive": inactive,
              "skipped": skipped, "invalid": invalid}
    log.info(f"[mdms] plan: mdms tenant={root} tenants={','.join(tenants)} heal={len(heal)} inactive={inactive} "
             f"skipped={len(skipped)} invalid={len(invalid)} schema={SCHEMA_CODE}")
    return {"heal": heal, "roster": roster}
