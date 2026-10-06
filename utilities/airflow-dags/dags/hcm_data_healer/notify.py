"""
Slack notifications for the hcm_data_healer DAG: per-tenant run summary, task
failure alerts and the run-timeout alert.

Windows post to their MDMS slackChannels, else the ops channel (SLACK_CHANNEL);
alerts go to the ops channel. Env: SLACK_TOKEN, SLACK_CHANNEL (no token = no post).
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


def ops_channels(cfg=None):
    """Return the ops channel(s) from SLACK_CHANNEL."""
    return _channels((cfg or {}).get("SLACK_CHANNEL") if cfg is not None else os.getenv("SLACK_CHANNEL", ""))


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


def _detail_lines(results, roster, crashed):
    """Build run-notes lines for configuration problems and unfinished work."""
    lines = []
    for r in results:
        reasons = r.get("failed_by_reason") or {}
        if reasons:
            lines.append(f"- {_campaign_name(r)}: rejected by HCM - " + ", ".join(
                f"{k} {_n(v)}" for k, v in sorted(reasons.items(), key=lambda kv: -kv[1])[:3]))
        if int((r.get("counts") or {}).get("unrecoverable") or 0):
            lines.append(f"- {_campaign_name(r)}: {_n(r['counts']['unrecoverable'])} records have no "
                         f"source copy and need re-capture in the field")
        if r["status"] in ("CHECKED_ONLY", "FAILED") and r.get("reason"):
            lines.append(f"- {_campaign_name(r)}: {redact(r['reason'], 160)}")
    for i in roster.get("invalid", []):
        lines.append(f"- Configuration {i['entry'] or i['tenant']} not processed: {redact(i['reason'], 140)}")
    for s in roster.get("skipped", []):
        lines.append(f"- Configuration {s['entry']} not yet due: {s['reason']}")
    if crashed:
        lines.append(f"- {crashed} campaign(s) did not finish; see the task failure alert")
    return lines


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
    reason = str(r.get("reason") or "")
    if "HEALER_PUSH_ENABLED" in reason:
        return "updates are paused by configuration"
    if "gaps" in reason:
        return "the number of discrepancies exceeded the safety limit (likely an incomplete data read)"
    return "part of the data could not be read"


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
            return f"{label}: none"
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
        lines.append("Error tracer: no missing links")
    elif why:
        lines.append(f"Error tracer: {_n(tr['found'])} missing links found")
    else:
        bits = [f"{_n(o.get('CREATED', 0))} recovered",
                f"{_n(o.get('UNRECOVERABLE', 0) + o.get('PREVIOUS_PUSH_NOT_SAVED', 0))} unrecoverable"]
        if o.get("FAILED"):
            bits.append(f"{_n(o['FAILED'])} could not be restored")
        lines.append(f"Error tracer: {_n(tr['found'])} missing links - " + ", ".join(bits))
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
        body = None
        found = sum(int((c or {}).get("found") or 0) for c in (r.get("by_category") or {}).values())
        if r["status"] == "COMPLETED" and not found:
            body = "No discrepancies found; no changes needed."
        lines += [body] if body else category_lines(r)
        lines.append(f"Window: {r['start_date']} to {r['end_date']} ({int(r.get('duration_min') or 1)} mins)")
    link = run_link(dag_run_id)
    lines.append(f"DAG run: {dag_run_id or '-'}" + (f"  |  {link}" if link else ""))
    return f"HCM Data Healer | {tenant}", color, lines


def run_summary(day, roster, results, crashed, dag_run_id="", run_minutes=None):
    """Post one message per tenant, plus a run-notes message to ops when needed.

    Returns True only if every post was accepted."""
    ops = ops_channels()
    by_tenant = {}
    for r in results:
        by_tenant.setdefault(r["tenant"], []).append(r)
    all_ok = True
    for tenant, windows in by_tenant.items():
        text, color, lines = tenant_message(tenant, windows, day, dag_run_id)
        channels = _channels([c for r in windows for c in (r.get("slack_channels") or [])] or ops)
        posted = _post(text, color, [_text("\n".join(lines))], channels)
        all_ok &= len(posted) == len(channels)
    notes = _detail_lines([], roster, crashed)
    if notes:
        link = run_link(dag_run_id)
        footer = f"DAG run: {dag_run_id or '-'}" + (f"  |  {link}" if link else "")
        posted = _post(f"HCM Data Healer | run notes | {day}", AMBER,
                       [_text(f"*HCM Data Healer | Run notes | {day}*\n" + "\n".join(notes) + f"\n{footer}")], ops)
        all_ok &= len(posted) == len(ops)
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
    blocks = [_header("HCM Data Healer | Run timed out"),
              _text("The run exceeded its time limit. Campaign windows still in progress were stopped and "
                    "are retried in the next run."),
              _context(f"Technical details: dag_run_id={run}  |  unfinished rows stay RUNNING in dst_healer_run")]
    _post(f"Data Healer run timed out: {run}", RED, blocks, ops_channels(cfg), token=cfg.get("SLACK_TOKEN"))


def task_failure_alert(context):
    """Task failure callback; skipped when the task already alerted."""
    exc = context.get("exception")
    if _consume_alerted_flag() or isinstance(exc, AlreadyAlerted) or _MARK in str(exc or ""):
        return
    cfg = _cfg()
    ti = context.get("task_instance") or context.get("ti")
    dag_run = context.get("dag_run")
    label = getattr(ti, "rendered_map_index", None)
    task_id = getattr(ti, "task_id", "?")
    what = {"get_campaigns_from_mdms": "The campaign configuration could not be read from MDMS. No campaign "
                                       "was processed in this run.",
            "heal_campaign": "A campaign window could not be processed. It is retried in the next run.",
            "post_daily_summary": "The daily run summary could not be completed."}.get(task_id, "A task failed.")
    blocks = [_header("HCM Data Healer | Task failure"),
              _fields([("Task", task_id), ("Campaign window", label or "-"),
                       ("Attempt", str(getattr(ti, "try_number", "?")))]),
              _text(what)]
    log_url = getattr(ti, "log_url", "")
    tech = [f"error={type(exc).__name__ if exc else 'unknown'}: {redact(exc) or 'no message (task may have been killed)'}",
            f"dag_run_id={getattr(dag_run, 'run_id', '?')}"]
    blocks.append(_context("Technical details: " + "  |  ".join(tech)
                           + (f"  |  <{log_url}|task log>" if log_url else "")))
    _post(f"Data Healer task failed: {task_id} {label or ''}".strip(), RED, blocks, ops_channels(cfg),
          token=cfg.get("SLACK_TOKEN"))
