"""Run lifecycle events to Kafka — the MDMS-mode audit path (zero database).

Adapted from the platform's common/kafka_status.py: same conventions (lazy
singleton producer, never raises, tenant-prefixed topic on central instances),
our fields (mode, slot, Drive folder instead of FileStore id). Consumed by egov-persister via one platform-side config YAML
into a dst_report_metadata table — producers hold no DB credentials.

Env: KAFKA_BROKER (required to enable — silently skipped otherwise),
DST_RUNS_TOPIC (default save-dst-report-metadata),
IS_CENTRAL_INSTANCE_ENABLED (tenant-prefixes the topic when "true").
"""
import datetime
import json
import logging
import os
import uuid

log = logging.getLogger(__name__)

DEFAULT_TOPIC = "save-dst-report-metadata"

_producer = None
_producer_init_failed = False


def _get_producer():
    global _producer, _producer_init_failed
    if _producer is not None or _producer_init_failed:
        return _producer
    broker = os.getenv("KAFKA_BROKER", "").strip()
    if not broker:
        # Was a SILENT return - an empty/absent KAFKA_BROKER looked identical in
        # the logs to a healthy run, so mdms-mode history quietly fell back to
        # the sheet with no clue why. Say it plainly.
        log.warning("[kafka] KAFKA_BROKER is not set - run events cannot be "
                    "published; history falls back to the Run Log tab")
        _producer_init_failed = True
        return None
    log.info(f"[kafka] connecting to broker {broker} ...")
    try:
        from kafka import KafkaProducer
        _producer = KafkaProducer(
            bootstrap_servers=broker,
            value_serializer=lambda v: json.dumps(v, default=str).encode("utf-8"))
        log.info(f"[kafka] producer connected to {broker}")
    except Exception:
        log.exception("[kafka] producer init failed — run events will be skipped")
        _producer_init_failed = True
        _producer = None
    return _producer


def _topic_for(tenant_id):
    base = os.getenv("DST_RUNS_TOPIC", DEFAULT_TOPIC)
    central = os.getenv("IS_CENTRAL_INSTANCE_ENABLED", "false").lower() == "true"
    return f"{tenant_id}-{base}" if central and tenant_id else base


def push_run_event(status, conf, dag_run_id, step_failed="",
                   drive_folder_url="", day=""):
    """Publish one lifecycle event for a campaign run. Never raises —
    a Kafka hiccup must not fail a DAG task.

    Field set deliberately carries no constants: report_name, trigger_frequency
    and dag_name were the same string in every row, and status_order was 40 in
    every row because only terminal events are emitted. What IS recorded is
    what actually varies per run — including cycle_index, without which two
    cycles of one campaign are indistinguishable (campaign_identifier is not
    unique per cycle), and mode, which now also carries "cumulative".
    """
    producer = _get_producer()
    if producer is None:
        log.info(f"[kafka] KAFKA_BROKER not set — skipping event {status}")
        return False

    row = conf.get("row") or {}
    tenant_id = conf.get("tenant", "")
    now = datetime.datetime.now(datetime.timezone.utc)
    event = {
        "event_id": str(uuid.uuid4()),
        "tenant_id": tenant_id,
        "state_name": conf.get("state_name", ""),
        "campaign_identifier": (row.get("campaign_number")
                                or row.get("project_type_id") or ""),
        # campaign_identifier is NOT unique per cycle - Chad C1 and C2 share
        # CMP-2026-06-27-000416 - so the cycle is part of the audit key too
        "cycle_index": str(row.get("cycle_index", "") or ""),
        "deployment_group": (conf.get("group") or {}).get("name", ""),
        # both | internal | partner | cumulative
        "mode": conf.get("mode", "both"),
        "day": str(day),
        "slot_date": conf.get("slot_date", ""),
        "slot_time": conf.get("slot_time", ""),
        "dag_run_id": dag_run_id,
        "status": status,
        # which stage died; the exception text stays in the Airflow task log,
        # reachable via dag_run_id
        "step_failed": step_failed or "",
        # the campaign's Drive FOLDER, not one file: a run publishes six
        # artifacts (2 docs, 3 sheets, chart) plus checkpoints in temp/
        "drive_folder_url": drive_folder_url or "",
        "timestamp_ms": int(now.timestamp() * 1000),
    }
    topic = _topic_for(tenant_id)
    try:
        # .get() on the future, NOT just flush(): flush() raises only on
        # timeout, while per-record failures (unknown topic with auto-create
        # disabled, authorization, record-too-large) are delivered to the
        # future. Without this, a rejected event returned True, record_outcome
        # set recorded="kafka" and SKIPPED the Run Log fallback - so the audit
        # trail was silently discarded and dst_report_metadata stayed empty.
        meta = producer.send(topic, value=event).get(timeout=10)
        log.info(f"[kafka] pushed {status} for {tenant_id} -> {topic} "
                 f"(partition {meta.partition}, offset {meta.offset})")
        return True
    except Exception:
        log.exception(f"[kafka] failed to push {status} to {topic} (non-fatal)")
        return False
