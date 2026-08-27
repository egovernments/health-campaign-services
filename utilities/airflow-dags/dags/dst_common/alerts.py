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
    from dst_common import dst_config

    token = (dst_config.resolved("SLACK_TOKEN", "") or "").strip()
    if token or not group_name:
        return token
    try:
        from dst_common.deployment_env import _load_group_secrets
        return str(_load_group_secrets(group_name).get("SLACK_TOKEN", "")).strip()
    except Exception as e:
        log.warning(f"[alerts] could not read group secrets for a token: {e}")
        return ""


def _post(channel, text, token, blocks=None):
    """Post and CHECK the result. Slack answers 200 with {"ok": false} for
    invalid_auth / channel_not_found / not_in_channel, so an unchecked post
    reports success while delivering nothing."""
    payload = {"channel": channel, "text": text}
    if blocks:
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


def alert_channel(row=None):
    """Where operational alerts go. A dedicated ops channel wins; the
    campaign's reporting channel is only a last resort."""
    from dst_common import dst_config

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
        detail = f"{type(exception).__name__}: {exception}" if exception else "Failure"
        message = (f"DST report failed - {state} | {campaign} | slot {slot}\n"
                   f"{detail}")

        blocks = [
            {"type": "header",
             "text": {"type": "plain_text", "text": f"DST report failed - {state}"[:150]}},
            {"type": "section", "fields": [
                {"type": "mrkdwn", "text": f"*Campaign*\n{campaign}"},
                {"type": "mrkdwn", "text": f"*Tenant*\n`{tenant}`"},
                {"type": "mrkdwn", "text": f"*Slot*\n{slot}"},
                {"type": "mrkdwn", "text": f"*Failed at*\n`{task_id}`"},
            ]},
            {"type": "section",
             "text": {"type": "mrkdwn", "text": f"```{detail[:900]}```"}},
            {"type": "context", "elements": [
                {"type": "mrkdwn", "text": f"dag `{dag_id}`  |  run `{run_id}`"}]},
        ]
        log.error(message)

        if not token or not channel:
            log.warning("[alerts] SLACK_TOKEN/channel not set — alert logged only")
            return
        _post(channel, message, token, blocks=blocks)
    except Exception as e:
        log.warning(f"[alerts] failure alert could not be sent: {e}")


def send_slack_warning(text, channel=None, group_name=""):
    """Post a warning to Slack (campaign channel or SLACK_CHANNEL fallback).
    Never raises. Used e.g. by the config sync to nag about rejected rows
    every tick until a human fixes the sheet."""
    try:
        # group_name matters: the rejected-rows nag is raised outside
        # group_environment, so without it a token held in dst_secrets_<group>
        # is invisible and the warning silently degrades to a log line
        token = _slack_token(group_name)
        channel = channel or alert_channel()
        log.warning(text)
        if not token or not channel:
            return
        _post(channel, text, token)
    except Exception as e:
        log.warning(f"[alerts] warning could not be sent: {e}")
