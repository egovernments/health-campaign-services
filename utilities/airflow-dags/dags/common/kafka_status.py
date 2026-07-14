"""
common/kafka_status.py

Shared helper for pushing report-generation lifecycle/status events to Kafka
from the Airflow DAGs (hcm_campaign_scheduler, hcm_dynamic_campaigns).

Uses the same event schema and tenant-topic-prefixing convention as
hcm-custom-reports/main.py's push_report_status, so all lifecycle events for
a given campaign/report/frequency land on one topic (CUSTOM_REPORTS_AUTOMATION_TOPIC,
i.e. save-hcm-report-metadata -> REPORTS_METADATA) regardless of which component
(scheduler DAG, processor DAG, or the report pod) produced them.

Required env vars:
    KAFKA_BROKER          - Kafka bootstrap servers
Optional:
    CUSTOM_REPORTS_AUTOMATION_TOPIC (default: "save-hcm-report-metadata")
    TENANT_ID                       (default: "dev")
    IS_CENTRAL_INSTANCE_ENABLED     (default: "false")
"""
from __future__ import annotations

import datetime
import json
import logging
import os
import uuid

logger = logging.getLogger("airflow.task")

CUSTOM_REPORTS_AUTOMATION_TOPIC = os.getenv("CUSTOM_REPORTS_AUTOMATION_TOPIC", "save-hcm-report-metadata")
KAFKA_BROKER = os.getenv("KAFKA_BROKER")
TENANT_ID_DEFAULT = os.getenv("TENANT_ID", "dev")
IS_CENTRAL_INSTANCE_ENABLED = os.getenv("IS_CENTRAL_INSTANCE_ENABLED", "false").lower() == "true"

# Mirrors STATUS_ORDER in hcm-custom-reports/main.py - keep the two in sync.
STATUS_ORDER = {
    "SCHEDULED": 10,
    "TRIGGERED": 20,
    "SKIPPED": 20,
    "POD_STARTED": 30,
    "REPORT_GENERATION_STARTED": 31,
    "ZIP_STARTED": 32,
    "FILESTORE_UPLOAD_STARTED": 33,
    "POD_INFRA_FAILED": 40,
    "ENV_VALIDATION_FAILED": 40,
    "REPORT_GENERATION_FAILED": 40,
    "OUTPUT_NOT_FOUND_FAILED": 40,
    "ZIP_FAILED": 40,
    "FILESTORE_UPLOAD_FAILED": 40,
    "REPORT_COMPLETED": 40,
}

_producer = None
_producer_init_failed = False


def _get_producer():
    """Lazily construct a module-level KafkaProducer. Returns None if unavailable."""
    global _producer, _producer_init_failed
    if _producer is not None or _producer_init_failed:
        return _producer
    try:
        from kafka import KafkaProducer

        _producer = KafkaProducer(
            bootstrap_servers=KAFKA_BROKER,
            value_serializer=lambda v: json.dumps(v).encode("utf-8"),
        )
    except Exception:
        logger.exception("[KAFKA] Failed to initialize KafkaProducer for status events")
        _producer_init_failed = True
        _producer = None
    return _producer


def _topic_for(base_topic, tenant_id):
    return f"{tenant_id}-{base_topic}" if IS_CENTRAL_INSTANCE_ENABLED and tenant_id else base_topic


def push_status_event(status, campaign, dag_id, dag_run_id, error_message=None,
                       report_triggered_time_ms=None, report_triggered_time=None):
    """
    Push one lifecycle status event for a campaign+report+frequency combination.

    Args:
        status (str): one of the keys in STATUS_ORDER.
        campaign (dict): matched-campaign dict as produced by hcm_campaign_scheduler
            (must have campaignIdentifier, identifierType, reportName, triggerFrequency,
            triggerTime, tenantId; may have startDate/endDate/reportStartTime/reportEndTime).
        dag_id (str): DAG producing this event (e.g. "hcm_campaign_scheduler").
        dag_run_id (str): the DAG run id this event belongs to.
        error_message (str, optional): human-readable reason, used for SKIPPED/FAILED statuses.
        report_triggered_time_ms / report_triggered_time (optional): the actual wall-clock
            moment this specific run was triggered (distinct from campaign["triggerTime"],
            which is just the MDMS-configured time-of-day, or whatever the requester sent
            for a CUSTOM report). Callers should pass the same value for every event
            belonging to one run so it reads consistently across all its status rows.

    Never raises - a Kafka hiccup must not fail a DAG task.
    """
    producer = _get_producer()
    if producer is None:
        logger.warning("[KAFKA] Skipping status event %s (producer unavailable)", status)
        return

    tenant_id = campaign.get("tenantId", TENANT_ID_DEFAULT)
    now_dt = datetime.datetime.now(datetime.timezone.utc)
    event = {
        "event_id": str(uuid.uuid4()),
        "tenant_id": tenant_id,
        "campaign_identifier": campaign.get("campaignIdentifier"),
        "identifier_type": campaign.get("identifierType"),
        "report_name": campaign.get("reportName"),
        "trigger_frequency": campaign.get("triggerFrequency"),
        "trigger_time": campaign.get("triggerTime"),
        "report_triggered_time_ms": report_triggered_time_ms,
        "report_triggered_time": report_triggered_time,
        "dag_run_id": dag_run_id,
        "dag_name": dag_id,
        "status": status,
        "status_order": STATUS_ORDER.get(status, 0),
        "error_message": error_message,
        "error_type": None,
        "file_store_id": None,
        "file_size_bytes": None,
        "row_count": None,
        "report_dates": None,
        "report_generation_time_seconds": None,
        # Epoch millis is what's actually bound into the DB (plain number -> BIGINT,
        # no JDBC string-to-timestamp cast risk); ISO string kept for readability only.
        "timestamp_ms": int(now_dt.timestamp() * 1000),
        "timestamp": now_dt.isoformat(),
    }

    topic = _topic_for(CUSTOM_REPORTS_AUTOMATION_TOPIC, tenant_id)
    try:
        producer.send(topic, value=event)
        producer.flush(timeout=10)
        logger.info("[KAFKA] Pushed status=%s for %s/%s to topic %s",
                    status, event["campaign_identifier"], event["report_name"], topic)
    except Exception:
        logger.exception("[KAFKA] Failed to push status event %s to topic %s", status, topic)
