"""Slack alerting for DAG failures — the team must hear about a dead report
before the stakeholders ask where it is.

Channel precedence: DST_ALERT_CHANNEL, then SLACK_CHANNEL, then the campaign's
own slack_channel as a last resort. A technical failure belongs in an ops
channel, NOT in the campaign channel where partners read the numbers — that
was the previous order and it would have posted tracebacks to stakeholders.
"""
import logging
import os

import requests

log = logging.getLogger(__name__)

# Severity colours for the attachment bar — the one bit of visual "styling"
# Slack allows. Kept muted (not pure #FF0000) so a channel of them is readable.
COLOR_FAILED = "#D64541"      # red   — a run died, nothing was delivered
COLOR_INCOMPLETE = "#E5A50A"  # amber — published but degraded / missing data
COLOR_INFO = "#5B6B7B"        # slate — routine notice (e.g. config sync)


def _slack_token(group_name=""):
    """Slack token for a callback.

    on_failure_callback runs AFTER the task body, so group_environment's finally
    has already restored os.environ — a token that lives only in
    dst_secrets_<group> is invisible here. That made the ENTIRE alerting system
    a no-op on any deployment following the documented secrets pattern. Fall
    back to reading the group's secrets Variable directly.
    """
    # dst_config.resolved reads the Variable directly, which matters here: this
    # runs AFTER group_environment restored os.environ, so a token that only
    # ever lived in a Variable is invisible to os.getenv.
    from dst_data_analysis_report.common import dst_config

    token = (dst_config.resolved("SLACK_TOKEN", "") or "").strip()
    if token or not group_name:
        return token
    try:
        from dst_data_analysis_report.common.deployment_env import _load_group_secrets
        return str(_load_group_secrets(group_name).get("SLACK_TOKEN", "")).strip()
    except Exception as e:
        log.warning(f"[alerts] could not read group secrets for a token: {e}")
        return ""


def _post(channel, text, token, blocks=None, color=None):
    """Post and CHECK the result. Slack answers 200 with {"ok": false} for
    invalid_auth / channel_not_found / not_in_channel, so an unchecked post
    reports success while delivering nothing.

    color is the nearest thing Slack has to CSS: an attachment color paints a
    coloured vertical bar down the left of the message (red = failed, amber =
    incomplete), so severity reads at a glance in a busy channel. When a colour
    is given the blocks go INSIDE the attachment (blocks at top level would
    render a second, bar-less copy)."""
    payload = {"channel": channel, "text": text}
    if blocks and color:
        payload["attachments"] = [{"color": color, "blocks": blocks}]
    elif blocks:
        # text stays as the notification/fallback line; blocks are the layout
        payload["blocks"] = blocks
    r = requests.post("https://slack.com/api/chat.postMessage",
                      headers={"Authorization": f"Bearer {token}"},
                      json=payload, timeout=10)
    body = {}
    try:
        body = r.json()
    except Exception:
        pass
    if not body.get("ok"):
        log.error(f"[alerts] Slack rejected the alert: http={r.status_code} "
                  f"error={body.get('error', r.text[:120])!r} channel={channel}")
        return False
    return True


def build_alert_blocks(header, lead, fields=None, detail_label=None,
                       detail=None, detail_code=False, context=None):
    """One layout for every DST alert, so failure / incomplete / config-sync
    all read the same: a header, a plain-English lead line, an optional 2-column
    field grid, a divider + labelled detail, and a muted context footer. Pair it
    with a colour in _post (red/amber/slate) for the severity bar."""
    blocks = [
        {"type": "header", "text": {"type": "plain_text", "text": header[:150]}},
        {"type": "section", "text": {"type": "mrkdwn", "text": lead}},
    ]
    if fields:
        blocks.append({"type": "section",
                       "fields": [{"type": "mrkdwn", "text": f}
                                  for f in fields[:10]]})
    if detail:
        blocks.append({"type": "divider"})
        body = f"```{detail[:2800]}```" if detail_code else detail[:2900]
        text = f"*{detail_label}*\n{body}" if detail_label else body
        blocks.append({"type": "section",
                       "text": {"type": "mrkdwn", "text": text}})
    if context:
        blocks.append({"type": "context",
                       "elements": [{"type": "mrkdwn", "text": context}]})
    return blocks


def alert_channel(row=None):
    """Where operational alerts go. A dedicated ops channel wins; the
    campaign's reporting channel is only a last resort."""
    from dst_data_analysis_report.common import dst_config

    return ((dst_config.resolved("DST_ALERT_CHANNEL", "") or "").strip()
            or (dst_config.resolved("SLACK_CHANNEL", "") or "").strip()
            or str((row or {}).get("slack_channel", "")).strip())


def notify_slack_on_failure(context):
    """Airflow on_failure_callback: post what failed and why to Slack.
    Never raises — an alerting failure must not mask the original error."""
    try:
        ti = context.get("task_instance")
        exception = context.get("exception")
        conf = (context.get("dag_run").conf or {}) if context.get("dag_run") else {}
        row = conf.get("row") or {}

        state = row.get("state_name") or conf.get("state_name") or "?"
        group_name = (conf.get("group") or {}).get("name", "")
        channel = alert_channel(row)
        token = _slack_token(group_name)

        run_id = getattr(context.get("dag_run"), "run_id", "?")
        dag_id = getattr(ti, "dag_id", "?")
        task_id = getattr(ti, "task_id", "?")
        campaign = row.get("campaign_name") or "-"
        tenant = conf.get("tenant") or row.get("tenant") or "-"
        slot = " ".join(x for x in (conf.get("slot_date", ""),
                                    conf.get("slot_time", "")) if x) or "-"
        mode = conf.get("mode", "")
        if mode:
            slot = f"{slot}  ({mode})"
        detail = (f"{type(exception).__name__}: {exception}" if exception
                  else "No error detail was captured — see the task log via the "
                       "run id below.")
        message = (f"Report failed — {state} · {campaign} · slot {slot}\n"
                   f"{detail}")

        blocks = build_alert_blocks(
            header=f"Report failed — {state}",
            lead=f"*{campaign}* did not produce a report.\n"
                 f"Nothing was posted to any channel for this slot.",
            fields=[f"*Tenant*\n`{tenant}`", f"*Slot*\n{slot}",
                    f"*Failed at*\n`{task_id}`", f"*DAG*\n`{dag_id}`"],
            detail_label="What went wrong", detail=detail, detail_code=True,
            context=f"run `{run_id}` — open this run in Airflow for the "
                    f"full task log")
        log.error(message)

        if not token or not channel:
            log.warning("[alerts] SLACK_TOKEN/channel not set — alert logged only")
            return
        _post(channel, message, token, blocks=blocks, color=COLOR_FAILED)
    except Exception as e:
        log.warning(f"[alerts] failure alert could not be sent: {e}")


def send_slack_warning(text, channel=None, group_name="", blocks=None,
                       color=None):
    """Post a warning to Slack (campaign channel or SLACK_CHANNEL fallback).
    Never raises. Used e.g. by the config sync to nag about rejected rows
    every tick until a human fixes the sheet.

    text is always the fallback/notification line; pass blocks (from
    build_alert_blocks) and a color for the same styled, bar-coded layout the
    failure alert uses."""
    try:
        # group_name matters: the rejected-rows nag is raised outside
        # group_environment, so without it a token held in dst_secrets_<group>
        # is invisible and the warning silently degrades to a log line
        token = _slack_token(group_name)
        channel = channel or alert_channel()
        log.warning(text)
        if not token or not channel:
            return
        _post(channel, text, token, blocks=blocks, color=color)
    except Exception as e:
        log.warning(f"[alerts] warning could not be sent: {e}")
