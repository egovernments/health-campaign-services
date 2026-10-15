"""
Slack notifications for the hcm_data_healer DAG: per-tenant run summary, task
failure alerts and the run-timeout alert.

Results post to each window's MDMS slackChannels (none = not posted). Alerts (failures,
invalid entries, run notes) go only to SLACK_ALERT_CHANNEL. Env: SLACK_TOKEN,
SLACK_ALERT_CHANNEL (no token = no post).
Posting never raises, and each response is checked for "ok". Text is redacted
(long digit runs and emails masked) because errors can echo beneficiary data.
"""

import logging
import os
import re

import requests

log = logging.getLogger(__name__)

GREEN, AMBER, RED, GREY = "#2EB67D", "#ECB22E", "#E01E5A", "#5B6B7B"
STATUS = {  # status -> (display label, colour)
    "COMPLETED": ("Completed", GREEN),
    "COMPLETED_WITH_FAILURES": ("Completed with errors", AMBER),
    "CHECKED_ONLY": ("Analysis only", AMBER),
    "FAILED": ("Failed", RED),
}
_DIGITS = re.compile(r"\d{6,}")
_EMAIL = re.compile(r"[\w.+-]+@[\w-]+\.[\w.]+")
_MARK = "[already-alerted] "


class AlreadyAlerted(RuntimeError):
    """Raised after a task already posted to Slack, so the failure callback stays quiet.

    Sets both a message marker and a process-level flag, because Airflow does not
    always pass the exception to the callback."""

    def __init__(self, message):
        global _alerted_in_process
        _alerted_in_process = True
        super().__init__(_MARK + str(message))


_alerted_in_process = False


def _consume_alerted_flag():
    global _alerted_in_process
    was, _alerted_in_process = _alerted_in_process, False
    return was


def redact(text, limit=300):
    s = _EMAIL.sub("<email>", _DIGITS.sub("<num>", str(text or "")))
    s = s.replace(_MARK, "")
    return s if len(s) <= limit else s[:limit] + "..."


# Block Kit helpers
def _header(text):
    return {"type": "header", "text": {"type": "plain_text", "text": text[:150]}}


def _fields(pairs):
    return {"type": "section", "fields": [
        {"type": "mrkdwn", "text": f"*{k}*\n{v}"} for k, v in pairs[:10]]}


def _text(md):
    return {"type": "section", "text": {"type": "mrkdwn", "text": md[:2900]}}


def _context(md):
    return {"type": "context", "elements": [{"type": "mrkdwn", "text": md[:2900]}]}


def _divider():
    return {"type": "divider"}


def _n(value):
    return f"{int(value or 0):,}"


def _channels(value):
    """Normalise a comma-separated string or list of channels to a deduplicated list."""
    items = value if isinstance(value, (list, tuple)) else str(value or "").split(",")
    out = []
    for c in items:
        c = str(c).strip()
        if c and c not in out:
            out.append(c)
    return out


def alert_channels(cfg=None):
    """Return the alert channel(s) from SLACK_ALERT_CHANNEL. Alerts never fall back to prod."""
    return _channels((cfg or {}).get("SLACK_ALERT_CHANNEL") if cfg is not None
                     else os.getenv("SLACK_ALERT_CHANNEL", ""))


def _post(text, color, blocks, channels, token=None):
    """Post once per channel; return {channel: ts} for accepted posts."""
    token = (token or os.getenv("SLACK_TOKEN", "")).strip()
    channels = _channels(channels)
    if not token or not channels:
        log.info("[slack] no SLACK_TOKEN or no channel for this message - not posted")
        return {}
    url = "https://slack.com/api/chat.postMessage"
    posted = {}
    for channel in channels:
        payload = {"channel": channel, "text": text,
                   "attachments": [{"color": color, "blocks": blocks[:50]}]}
        try:
            r = requests.post(url, headers={"Authorization": f"Bearer {token}"}, json=payload, timeout=15)
            body = r.json() if r.content else {}
            if body.get("ok"):
                posted[channel] = body.get("ts", "")
                log.info(f"[slack] posted to {channel}: {text[:80]}")
            else:
                log.error(f"[slack] {channel} rejected: http={r.status_code} error={body.get('error')!r}")
        except Exception as exc:                                  # noqa: BLE001
            log.error(f"[slack] {channel} post failed: {type(exc).__name__}")
    return posted


# Run summary
RUN_STATUS = {False: ("Completed", GREEN), True: ("Completed with issues", AMBER)}


def _campaign_name(r):
    return f"{r['campaign_name']} ({r['tenant']})" if r.get("campaign_name") else f"Tenant {r['tenant']}"


# Alerts. Each alert has a code (documented in ALERTS.md), a severity and two short lines:
# what happened and what to do. Raw reasons go in a small context line for the DST team.
CRITICAL, WARNING, NOTICE, INFO = "CRITICAL", "WARNING", "NOTICE", "INFO"
_RANK = {CRITICAL: 0, WARNING: 1, NOTICE: 2, INFO: 3}
_RETRY = "None now; it is retried in the next run."

# code -> (severity, what happened, what to do)
ALERTS = {
    "HL-UNRECOVERABLE": (INFO, "{n} have a link that could not be completed; no copy was found in the "
                               "error tracer.", "None."),
    "HL-REJECTED": (WARNING, "{n} could not be repaired: {why}.",
                    "DST team to review them in dst_healer_record; they are retried in the next run."),
    "HL-PUSH-OFF": (INFO, "Check-only run: repairs are switched off (HEALER_PUSH_ENABLED=false).",
                    "None if intended; set HEALER_PUSH_ENABLED to true to repair."),
    "HL-NO-DATA": (WARNING, "No records changed: a data source returned no records for this window.",
                   "DST team to check index names, dates and connections in the healer settings."),
    "HL-GAP-LIMIT": (WARNING, "No records changed: differences exceed the safety limit, which indicates "
                              "an incomplete read.",
                     "DST team to review the task log and re-run, with a shorter window if needed."),
    "HL-READ-FAILED": (NOTICE, "Not processed: the data comparison did not complete.", _RETRY),
    "HL-SERVICE": (NOTICE, "Not processed: a database, Elasticsearch or HCM service did not respond.", _RETRY),
    "HL-AUDIT": (CRITICAL, "No records changed: the audit log (Kafka) is unavailable.",
                 "DST team to check KAFKA_BROKER and the audit persister."),
    "HL-CONFIG": (CRITICAL, "The healer settings could not be read.",
                  "DST team to check the Airflow Variable hcm_data_healer_config."),
    "HL-MDMS": (CRITICAL, "The campaign list could not be read from MDMS.",
                "DST team to check MDMS_URL and the MDMS service."),
    "HL-ENTRY": (WARNING, "Campaign entry not processed: {why}.", "{action}"),
    "HL-NOT-DUE": (INFO, "Campaign entry not due yet: the last 3 days are held back while phones sync.",
                   "None; it starts automatically."),
    "HL-STOPPED": (WARNING, "Repair stopped part-way; completed repairs are kept. Cause: {why}.",
                   "DST team to review the task log; the rest is retried in the next run."),
    "HL-UNFINISHED": (WARNING, "{n} did not finish.", "See the Task failure alert for this run."),
    "HL-TASK": (WARNING, "{what}", "{action}"),
    "HL-TIMEOUT": (NOTICE, "The run exceeded its time limit; unfinished windows were stopped.",
                   "None now; retried in the next run. If it repeats, shorten the windows."),
    "HL-UNKNOWN": (WARNING, "Not processed: an unexpected error occurred.", "DST team to review the task log."),
}

# raw reason phrase (lower case) -> code; first match wins
_CODE_OF = [
    ("switched to false during the run", "HL-PUSH-OFF"), ("healer_push_enabled", "HL-PUSH-OFF"),
    ("one side empty", "HL-NO-DATA"), ("exceed the limit", "HL-GAP-LIMIT"), ("% limit", "HL-GAP-LIMIT"),
    ("counts unreadable", "HL-READ-FAILED"), ("reconciliation", "HL-READ-FAILED"),
    ("error-tracer", "HL-READ-FAILED"), ("auditerror", "HL-AUDIT"), ("kafka", "HL-AUDIT"),
    ("config unreadable", "HL-CONFIG"), ("variable", "HL-CONFIG"), ("settling period", "HL-NOT-DUE"),
    ("mdms", "HL-MDMS"), ("timeout", "HL-SERVICE"), ("timed out", "HL-SERVICE"),
    ("connection", "HL-SERVICE"),
]

# invalid MDMS entry: phrase -> (why, action)
_ENTRY = [
    ("overlaps", "its dates overlap another active entry for this tenant",
     "Change the dates in MDMS or set active to NO on one entry."),
    ("startdate is empty", "no start date", "Add startDate in the MDMS entry."),
    ("is before startdate", "end date is before start date", "Correct the dates in the MDMS entry."),
    ("no enddate and the window", "no end date and the window is too long",
     "Set endDate in the MDMS entry, or set active to NO if the campaign has ended."),
    ("does not match tenant_id", "tenant differs from this healer's TENANT_ID",
     "Correct the tenant in the MDMS entry."),
    ("not a valid tenant code", "invalid tenant code", "Correct the tenant in the MDMS entry."),
    ("unreadable date", "a date is not in YYYY-MM-DD format", "Correct the date in the MDMS entry."),
    ("slack channel", "invalid Slack channel ID", "Correct slackChannels in the MDMS entry."),
]

# HCM rejection code or reason (lower case) -> plain cause
_REJECTION = [
    ("no source copy", "with no copy to rebuild from"),
    ("not returned by the hcm search", "not found by the HCM search"),
    ("duplicate", "already in HCM"), ("already", "already in HCM"),
    ("not_found", "missing a linked record in HCM"), ("link", "missing a linked record in HCM"),
    ("reference", "missing a linked record in HCM"), ("tenant", "for the wrong tenant"),
    ("invalid", "failing HCM validation"), ("validation", "failing HCM validation"),
]

# record type behind each unrecoverable chain gap
MISSING_KIND = {
    "MEMBER_WITHOUT_HOUSEHOLD": "members without their household", "CHAIN_A": "members without their household",
    "HOUSEHOLD_WITHOUT_MEMBERS": "households without members", "CHAIN_B": "households without members",
    "MEMBER_WITHOUT_INDIVIDUAL": "members without their person record",
    "CHAIN_C": "members without their person record",
    "HOUSEHOLD_NOT_ENROLLED": "households without an enrolment", "CHAIN_D": "households without an enrolment",
    "BENEFICIARY_WITHOUT_TASK": "beneficiaries without a delivery record",
    "CHAIN_E": "beneficiaries without a delivery record",
    "HOUSEHOLD_NOT_ENROLLED_NO_MEMBERS": "households without an enrolment",
    "CHAIN_F": "households without an enrolment",
}


def code_of(reason):
    """Return the alert code for a raw reason or error text."""
    s = str(reason or "").lower()
    return next((code for phrase, code in _CODE_OF if phrase in s), "HL-UNKNOWN")


def _entry_problem(reason):
    s = str(reason or "").lower()
    return next(((why, action) for phrase, why, action in _ENTRY if phrase in s),
                ("the entry is not valid", "Correct the MDMS entry."))


def _rejection_cause(code):
    s = str(code).lower()
    return next((cause for phrase, cause in _REJECTION if phrase in s), "refused by HCM")


def alert(code, title, details="", **fill):
    """Return (severity, mrkdwn) for one alert: severity + title, what happened, what to do."""
    severity, what, action = ALERTS[code]
    what, action = what.format(**fill), action.format(**fill)
    lines = [f"*{severity}*  {title}", what, f"*Action:* {action}"]
    tech = f"{code}" + (f" | {redact(details, 160)}" if details else "") + " | see ALERTS.md"
    return severity, "\n".join(lines) + f"\n_{tech}_"


def reason_alert(title, reason, failed=False):
    """Alert for a window or entry that stopped for a raw reason."""
    raw = str(reason or "")
    if raw.startswith("push stopped before finishing"):
        inner = code_of(raw.split(":", 1)[-1])
        why = ALERTS[inner][1].split(": ", 1)[-1].rstrip(".") if inner != "HL-UNKNOWN" else "unexpected error"
        return alert("HL-STOPPED", title, raw, why=why[0].lower() + why[1:])
    code = code_of(raw)
    if code == "HL-UNKNOWN" and not failed:
        why, action = _entry_problem(raw)
        return alert("HL-ENTRY", title, raw, why=why, action=action)
    return alert(code, title, raw)


def unrecoverable_split(r):
    """Return e.g. '86 beneficiaries without a delivery record, 5 households without members'."""
    kinds = {}
    for issue, v in (r.get("unrecoverable_by_issue") or {}).items():
        kind = MISSING_KIND.get(issue, "other links")
        kinds[kind] = kinds.get(kind, 0) + int(v or 0)
    return ", ".join(f"{_n(v)} {k}" for k, v in sorted(kinds.items(), key=lambda kv: -kv[1]))


def _window(r):
    return f"{_campaign_name(r)} | {r.get('start_date') or '?'} to {r.get('end_date') or '?'}"


def _detail_lines(results, roster, crashed):
    """Build (severity, text) alerts for every issue that needs attention; INFO is dropped."""
    items = []
    for r in results:
        title = _window(r)
        reasons = r.get("failed_by_reason") or {}
        if reasons:
            top = sorted(reasons.items(), key=lambda kv: -kv[1])[:3]
            why = ", ".join(f"{_n(v)} {_rejection_cause(k)}" for k, v in top)
            items.append(alert("HL-REJECTED", title, ", ".join(f"{k} {_n(v)}" for k, v in top),
                               n=_plural(sum(reasons.values()), "record"), why=why))
        unrec = int((r.get("counts") or {}).get("unrecoverable") or 0)
        if unrec:
            items.append(alert("HL-UNRECOVERABLE", title, unrecoverable_split(r),
                               n=_plural(unrec, "record")))
        if r["status"] in ("CHECKED_ONLY", "FAILED") and r.get("reason"):
            items.append(reason_alert(title, r["reason"], failed=r["status"] == "FAILED"))
    for i in roster.get("invalid", []):
        items.append(reason_alert(f"Campaign entry {i['entry'] or i['tenant']}", i["reason"]))
    for sk in roster.get("skipped", []):
        items.append(reason_alert(f"Campaign entry {sk['entry']}", sk["reason"]))
    if crashed:
        items.append(alert("HL-UNFINISHED", "Campaign windows", n=_plural(crashed, "campaign window")))
    return sorted((it for it in items if it[0] != INFO), key=lambda it: _RANK[it[0]])


def run_link(dag_run_id):
    base = os.getenv("HEALER_AIRFLOW_BASE_URL", "").strip().rstrip("/")
    if not base or not dag_run_id:
        return ""
    from urllib.parse import quote
    return f"<{base}/dags/hcm_data_healer/runs/{quote(dag_run_id, safe='')}|View run>"


def _plural(n, word):
    return f"{_n(n)} {word}{'' if int(n or 0) == 1 else 's'}"


def _codes_text(codes):
    return ", ".join(f"{k} {_n(v)}" if len(codes) > 1 else k
                     for k, v in sorted(codes.items(), key=lambda kv: -kv[1])[:3])


def _no_changes_reason(r):
    """Return a plain reason for a CHECKED_ONLY result, else ''."""
    if r["status"] != "CHECKED_ONLY":
        return ""
    code = code_of(r.get("reason"))
    return ALERTS[code][1].split(": ", 1)[-1].rstrip(".") if code != "HL-UNKNOWN" else "the run stopped early"


def category_lines(r):
    """Build the per-category lines (missing in DB, missing in ES, error tracer)."""
    cats = (r.get("by_category") or {})
    db, es, tr = (cats.get(k) or {"found": 0, "outcomes": {}, "failed_by_reason": {}} for k in ("db", "es", "tracer"))
    lines = []
    if r["status"] == "FAILED":
        return ["This campaign could not be processed tonight; the DST team has been alerted."]
    why = _no_changes_reason(r)
    if why:
        lines.append(f"No changes applied: {why}.")

    def part(cat, label, done_key, done_word):
        o = cat["outcomes"]
        if not cat["found"]:
            return f"{label}: 0 found"
        if why:
            return f"{label}: {_n(cat['found'])} found"
        bits = [f"{_n(o.get(done_key, 0))} {done_word}"]
        if o.get("ALREADY_EXISTS"):
            bits.append(f"{_n(o['ALREADY_EXISTS'])} already present")
        if o.get("FAILED"):
            bits.append(f"{_n(o['FAILED'])} could not be restored")
        skipped = sum(v for k, v in o.items() if k.startswith("SKIPPED"))
        if skipped:
            bits.append(f"{_n(skipped)} skipped")
        return f"{label}: {_n(cat['found'])} found - " + ", ".join(bits)

    lines.append(part(db, "Missing in DB", "CREATED", "restored"))
    lines.append(part(es, "Missing in ES", "REINDEXED", "re-indexed"))
    o = tr["outcomes"]
    if not tr["found"]:
        lines.append("Error tracer: 0 incomplete links")
    elif why:
        lines.append(f"Error tracer: {_n(tr['found'])} incomplete links found")
    else:
        bits = [f"{_n(o.get('CREATED', 0))} recovered",
                f"{_n(o.get('UNRECOVERABLE', 0) + o.get('PREVIOUS_PUSH_NOT_SAVED', 0))} with no copy found"]
        if o.get("FAILED"):
            bits.append(f"{_n(o['FAILED'])} could not be restored")
        lines.append(f"Error tracer: {_n(tr['found'])} incomplete links - " + ", ".join(bits))
    split = unrecoverable_split(r)
    if split:
        lines.append(f"No copy found for: {split}.")
    return lines


def tenant_message(tenant, windows, day, dag_run_id):
    """Return (fallback text, colour, lines) for one tenant's message."""
    issues_any = any(r["status"] != "COMPLETED" for r in windows)
    label, color = RUN_STATUS[issues_any]
    if any(r["status"] == "FAILED" for r in windows):
        label, color = "Failed", RED
    names = sorted({r.get("campaign_name") for r in windows if r.get("campaign_name")})
    lines = [f"*HCM Data Healer | Tenant: {tenant} | Campaign: {', '.join(names) or '-'}*"]
    for r in windows:
        found = sum(int((c or {}).get("found") or 0) for c in (r.get("by_category") or {}).values())
        if r["status"] == "COMPLETED" and not found:
            lines.append("No discrepancies found; no changes needed.")
        lines += category_lines(r)
        lines.append(f"Window: {r['start_date']} to {r['end_date']} ({int(r.get('duration_min') or 1)} mins)")
    link = run_link(dag_run_id)
    lines.append(f"DAG run: {dag_run_id or '-'}" + (f"  |  {link}" if link else ""))
    return f"HCM Data Healer | {tenant}", color, lines


def run_summary(day, roster, results, crashed, dag_run_id="", run_minutes=None):
    """Post results to prod and every issue to the alert channel.

    Results: one message per tenant to the windows' MDMS slackChannels (none = not posted).
    Alerts (SLACK_ALERT_CHANNEL): failed windows, HCM rejections, unrecoverable records,
    guardrail holds and run notes.
    Returns True only if every post was accepted."""
    alerts = alert_channels()
    by_tenant = {}
    for r in results:
        by_tenant.setdefault(r["tenant"], []).append(r)
    all_ok = True
    for tenant, windows in by_tenant.items():
        ran = [r for r in windows if r["status"] != "FAILED"]
        if ran:
            text, color, lines = tenant_message(tenant, ran, day, dag_run_id)
            channels = _channels([c for r in ran for c in (r.get("slack_channels") or [])])
            if not channels:
                log.info(f"[slack] {tenant}: no slackChannels in MDMS - result not posted")
                continue
            posted = _post(text, color, [_text("\n".join(lines))], channels)
            all_ok &= len(posted) == len(channels)
    failed = [r for r in results if r["status"] == "FAILED"]
    notes = [reason_alert(_window(r), r.get("reason") or "", failed=True) for r in failed]
    # windows that ran but had rejections, unrecoverable records or a guardrail hold
    notes += _detail_lines([r for r in results if r["status"] != "FAILED"], roster, crashed)
    notes = sorted((x for x in notes if x[0] != INFO), key=lambda x: _RANK[x[0]])
    if notes:
        link = run_link(dag_run_id)
        footer = f"Run {dag_run_id or '-'}" + (f"  |  {link}" if link else "")
        worst = notes[0][0]
        color = {CRITICAL: RED, WARNING: AMBER}.get(worst, GREY)
        act = sum(1 for sev, _ in notes if sev in (CRITICAL, WARNING))
        summary = (f"{act} action{'s' if act != 1 else ''} needed" if act
                   else "no action needed now")
        blocks = [_header(f"HCM Data Healer | {summary} | {day}")]
        for _, note in notes:
            blocks += [_divider(), _text(note)]
        blocks += [_divider(), _context(footer)]
        posted = _post(f"HCM Data Healer | {summary} | {day}", color, blocks[:50], alerts)
        all_ok &= len(posted) == len(alerts)
    return bool(all_ok) and bool(by_tenant or notes)


def _cfg():
    """Read Slack settings from the Variable; callbacks run outside healer_environment()."""
    try:
        from hcm_data_healer.airflow_env import load_config
        return load_config()
    except Exception:                                             # noqa: BLE001
        return {}


def run_timeout_alert(context):
    """DAG-level failure callback; alerts only when the run timed out."""
    if "time" not in str(context.get("reason") or "").lower():
        return
    cfg = _cfg()
    run = getattr(context.get("dag_run"), "run_id", "?")
    link = run_link(run)
    _, text = alert("HL-TIMEOUT", "Healer run")
    blocks = [_header("HCM Data Healer | Run timed out"), _text(text),
              _context(f"Run {run}  |  unfinished rows stay RUNNING in dst_healer_run"
                       + (f"  |  {link}" if link else ""))]
    _post(f"HCM Data Healer | Run timed out | {run}", GREY, blocks, alert_channels(cfg),
          token=cfg.get("SLACK_TOKEN"))


TASK_WHAT = {
    "get_campaigns_from_mdms": "No campaign was processed: the campaign list could not be loaded.",
    "heal_campaign": "This campaign window was not processed; no records changed.",
    "post_daily_summary": "Repairs ran, but the daily results message failed.",
}


def task_failure_alert(context):
    """Task failure callback; skipped when the task already alerted."""
    exc = context.get("exception")
    if _consume_alerted_flag() or isinstance(exc, AlreadyAlerted) or _MARK in str(exc or ""):
        return
    cfg = _cfg()
    ti = context.get("task_instance") or context.get("ti")
    dag_run = context.get("dag_run")
    run = getattr(dag_run, "run_id", "?")
    label = getattr(ti, "rendered_map_index", None)
    task_id = getattr(ti, "task_id", "?")
    raw = f"{type(exc).__name__}: {exc}" if exc else "stopped without an error message (worker restart)"
    code = code_of(raw)
    title = f"Task {task_id}" + (f" | {label}" if label else "")
    if code == "HL-UNKNOWN":
        severity, text = alert("HL-TASK", title, raw, what=TASK_WHAT.get(task_id, "A healer task failed."),
                               action=_RETRY if task_id == "heal_campaign" else "DST team to review the task log.")
    else:
        severity, text = alert(code, title, raw)
    log_url = getattr(ti, "log_url", "")
    blocks = [_header(f"HCM Data Healer | Task failed | {task_id}"), _text(text),
              _context(f"Attempt {getattr(ti, 'try_number', '?')}  |  Run {run}"
                       + (f"  |  <{log_url}|Task log>" if log_url else ""))]
    color = {CRITICAL: RED, WARNING: AMBER}.get(severity, GREY)
    _post(f"HCM Data Healer | Task failed | {task_id} {label or ''}".strip(), color, blocks,
          alert_channels(cfg), token=cfg.get("SLACK_TOKEN"))
