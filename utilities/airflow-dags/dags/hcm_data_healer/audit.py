"""
Audit trail for the hcm_data_healer DAG, published via Kafka to egov-persister.

Tables (platform/dst_healer_audit.sql): dst_healer_run (one row per window, upserted
from RUNNING to final status), dst_healer_record (one row per problem record) and
the dst_healer_report view joining them.

Topics are not tenant-prefixed; an unlisted topic is acked by Kafka but never persisted.
Ids are deterministic (uuid5), so re-publishing never duplicates rows. A publish
failure raises AuditError, since this is the only record of a heal. Env: KAFKA_BROKER.
"""

import glob
import json
import logging
import os
import re
import uuid
from datetime import datetime, timezone

import pandas as pd

log = logging.getLogger(__name__)

RUN_TOPIC = "save-dst-healer-run"
RECORD_TOPIC = "save-dst-healer-record"
_NS = uuid.UUID("5b0f6a3e-2f7c-4d8e-9a51-6f3c1d2e7b90")   # fixed so ids are stable across runs

# internal case -> issue label stored in the audit
ISSUE = {"CREATE": "MISSING_IN_DB", "UPDATE": "MISSING_IN_ES",
         "CHAIN_A": "MEMBER_WITHOUT_HOUSEHOLD", "CHAIN_B": "HOUSEHOLD_WITHOUT_MEMBERS",
         "CHAIN_C": "MEMBER_WITHOUT_INDIVIDUAL", "CHAIN_D": "HOUSEHOLD_NOT_ENROLLED",
         "CHAIN_E": "BENEFICIARY_WITHOUT_TASK", "CHAIN_F": "HOUSEHOLD_NOT_ENROLLED_NO_MEMBERS"}
CHAIN_GAP_ENTITY = {"A": "Household", "B": "HouseholdMember", "C": "Individual",
                    "D": "ProjectBeneficiary", "E": "ProjectTask", "F": "ProjectBeneficiary"}

# push status -> (outcome, action taken, plain reason)
OUTCOME = {
    "PUSHED": ("CREATED", "CREATE", "Created in HCM (accepted by the API; persisted asynchronously)"),
    "UPDATED": ("REINDEXED", "UPDATE", "Re-sent to HCM so it is indexed again"),
    "SKIPPED_EXISTS": ("ALREADY_EXISTS", "NONE", "Already in HCM (search API or DB, deleted rows included) - not created again"),
    "SKIPPED": ("ALREADY_EXISTS", "CREATE", "HCM answered that the record already exists"),
    "FAILED": ("FAILED", "CREATE", "HCM rejected the write"),
    "NOT_FOUND_IN_API": ("FAILED", "UPDATE", "Not returned by the HCM search API, so it could not be re-sent"),
    "NO_PAYLOAD": ("FAILED", "NONE", "No source copy of the record was available to rebuild it"),
    "NO_CLIENT_AUDIT": ("SKIPPED_NO_CLIENT_AUDIT", "NONE",
                        "Source record has no clientAuditDetails; pushing it would create new mismatches"),
    "SKIPPED_NOT_A_CREATE": ("SKIPPED_NOT_A_CREATE", "NONE",
                             "The only copy is from a failed update/delete or a deleted payload; not re-created"),
    "NOT_IN_TRACER": ("UNRECOVERABLE", "NONE",
                      "No copy exists in ES, the DB or the error tracer; must be re-entered in the field"),
    "PROCESSED_STILL_MISSING": ("PREVIOUS_PUSH_NOT_SAVED", "NONE",
                                "Recovered and pushed by an earlier run, but still not in the system"),
    "DETECTED": ("DETECTED_ONLY", "NONE", "Found; nothing written because the push was skipped for this run"),
    "NOT_ATTEMPTED": ("NOT_ATTEMPTED", "NONE", "Found; the push stopped before reaching this record"),
}
OUTCOME["SKIPPED_NOT_CREATE"] = OUTCOME["SKIPPED_NOT_A_CREATE"]

_producer = None


class AuditError(RuntimeError):
    """An audit event could not be delivered to Kafka."""


def now_ms():
    return int(datetime.now(timezone.utc).timestamp() * 1000)


def _id(*parts):
    return str(uuid.uuid5(_NS, "::".join(str(p) for p in parts)))


def _clean(value, limit=None):
    """Convert NaN/None to '' and strip, optionally truncating."""
    if value is None or (isinstance(value, float) and pd.isna(value)):
        return ""
    s = str(value).strip()
    if s.lower() == "nan":
        return ""
    return s[:limit] if limit else s


def _json(value):
    """Parse payload text for the JSONB column; None if absent, wrapped if unparseable."""
    if isinstance(value, (dict, list)):
        return value
    text = _clean(value)
    if not text:
        return None
    try:
        return json.loads(text)
    except ValueError:
        return {"unparsed": text[:2000]}


# under Kafka's default 1 MB message limit
AUDIT_MAX_BYTES = 900000


def run_topic():
    return RUN_TOPIC


def record_topic():
    return RECORD_TOPIC


def _get_producer():
    global _producer
    if _producer is None:
        broker = os.getenv("KAFKA_BROKER", "").strip()
        if not broker:
            raise AuditError("KAFKA_BROKER is not set - the audit trail cannot be written")
        from kafka import KafkaProducer
        _producer = KafkaProducer(
            bootstrap_servers=[b.strip() for b in broker.split(",") if b.strip()],
            value_serializer=lambda v: json.dumps(v, default=str).encode("utf-8"),
            acks="all", retries=3, request_timeout_ms=30000)
    return _producer


def _send(topic, event):
    """Send and wait for the broker ack; per-record failures only surface on the future."""
    try:
        return _get_producer().send(topic, value=event).get(timeout=30)
    except AuditError:
        raise
    except Exception as exc:
        raise AuditError(f"Kafka publish to {topic} failed: {type(exc).__name__}: {exc}") from exc


# Run row
def run_id_of(dag_run_id, target):
    return f"{dag_run_id}::{target['tenant']}::{target['start_date']}"


def run_event(status, run_id, dag_run_id, target, end_date, started_ms, counts=None,
              failed_step="", reason=""):
    """Build the run row event; counts use the DAG's keys (ghosts_found, created, ...)."""
    c = counts or {}
    finished = status != "RUNNING"

    def n(key):
        return c.get(key) if finished else None

    found = (sum(int(c.get(k) or 0) for k in ("ghosts_found", "index_failed_found", "chain_missing"))
             if finished and c else None)
    return {"dag_run_id": dag_run_id, "tenant_id": target["tenant"],
            "campaign_name": target.get("campaign_name", ""),
            "window_start": target["start_date"], "window_end": end_date,
            "error_tracer": bool(target.get("run_chain", True)),
            "status": status, "failed_step": failed_step, "reason": _clean(reason, 500),
            "found_total": found, "created": n("created"), "reindexed": n("reindexed"),
            "already_present": n("skipped"), "failed": n("failed"), "unrecoverable": n("unrecoverable"),
            "started_at_ms": started_ms, "finished_at_ms": now_ms() if finished else None}


def publish_run(event):
    meta = _send(run_topic(), event)
    log.info(f"[audit] run {event['status']} {event['dag_run_id']} {event['tenant_id']}:{event['window_start']} "
             f"(partition {getattr(meta, 'partition', '?')}, offset {getattr(meta, 'offset', '?')})")


# Record rows
def _latest(folder, pattern):
    files = sorted(glob.glob(os.path.join(folder or "", pattern)))
    return files[-1] if files else None


def _source(r):
    """Map a tracer CSV row to the source_* columns."""
    return {"source_error_code": _clean(r.get("Error_Code"), 128),
            "source_error_message": _clean(r.get("Error_Message"), 1000),
            "source_error_time": _clean(r.get("Timestamp"), 40),
            "source_api_url": _clean(r.get("API_URL"), 300),
            "source_doc_id": _clean(r.get("Error_Doc_UUID"), 128)}


def publish_records(rows):
    """Publish rows in batches under AUDIT_MAX_BYTES; return the count sent."""
    if not rows:
        return 0
    limit = AUDIT_MAX_BYTES
    batch, size, sent = [], 0, 0
    for row in rows:
        n = len(json.dumps(row, default=str).encode("utf-8")) + 2
        if batch and size + n > limit:
            _send(record_topic(), {"records": batch})
            sent += len(batch)
            batch, size = [], 0
        batch.append(row)
        size += n
    if batch:
        _send(record_topic(), {"records": batch})
        sent += len(batch)
    log.info(f"[audit] {sent:,} record row(s) -> {record_topic()}")
    return sent


class RecordAuditor:
    """Build and publish one dst_healer_record row per problem record.

    on_push_batch is the push's record_sink, so rows are published during the push.
    finish() adds rows for records found but not acted on.
    """

    def __init__(self, run_id, tenant, recon_dir, chain_dir, publish=True, dag_run_id="", window_start=""):
        # run_id only seeds record ids; stored keys are dag_run_id, tenant_id, window_start.
        self.run_id, self.tenant = run_id, tenant
        self.dag_run_id = dag_run_id or run_id.split("::")[0]
        self.window_start = window_start or (run_id.split("::") + ["", "", ""])[2]
        self.recon_dir, self.chain_dir = recon_dir, chain_dir
        self.publish = publish
        self.seen = set()            # (case, crid) already given a row
        self.rows = []               # everything built, for the tally
        self.tracer = {}             # (case, crid) -> {"source": {...}, "payload": text}
        for gap in CHAIN_GAP_ENTITY:
            path = _latest(chain_dir, f"{tenant}_chain_tracer_{gap}_*.csv") if chain_dir else None
            if path:
                for r in pd.read_csv(path, dtype=str).to_dict("records"):
                    crid = _clean(r.get("clientReferenceId"))
                    if crid:
                        self.tracer[(f"CHAIN_{gap}", crid)] = {"source": _source(r),
                                                               "payload": r.get("payload_json")}

    def _row(self, entity, case, crid, status, payload=None, source=None, http="", api_error="",
             found_in=None):
        outcome, action, reason = OUTCOME.get(status, (status, "NONE", status))
        if found_in is None:
            found_in = {"CREATE": "ELASTICSEARCH", "UPDATE": "DATABASE"}.get(case, "ERROR_TRACER")
        if outcome == "FAILED" and not _clean(http):
            action = "NONE"          # refused before any write
        if outcome == "FAILED" and api_error:
            reason = f"{reason}: {_clean(api_error, 600)}"
        return {"record_id": _id(self.run_id, case, crid), "dag_run_id": self.dag_run_id,
                "window_start": self.window_start,
                "tenant_id": self.tenant, "entity": entity, "client_reference_id": _clean(crid, 128),
                "issue": ISSUE.get(case, case), "found_in": found_in, "action": action,
                "outcome": outcome, "outcome_reason": _clean(reason, 1000), "payload": _json(payload),
                **(source or {"source_error_code": "", "source_error_message": "",
                              "source_error_time": "", "source_api_url": "", "source_doc_id": ""}),
                "api_status": _clean(http, 8), "api_response": _clean(api_error, 2000),
                "recorded_at_ms": now_ms()}

    def _emit(self, rows):
        self.rows.extend(rows)
        if self.publish:
            publish_records(rows)

    def on_push_batch(self, push_rows):
        out = []
        for r in push_rows:
            case, crid = r.get("Case", ""), _clean(r.get("clientReferenceId"))
            if (case, crid) in self.seen:
                continue
            self.seen.add((case, crid))
            info = self.tracer.get((case, crid)) or {}
            status = r.get("Status", "")
            ok = status in ("PUSHED", "UPDATED", "SKIPPED_EXISTS", "SKIPPED")
            api_error = r.get("Error") or ("" if ok else r.get("Response", ""))
            out.append(self._row(r.get("Entity", ""), case, crid, status,
                                 payload=r.get("Payload") or info.get("payload"),
                                 source=info.get("source"), http=r.get("HttpStatus", ""),
                                 api_error=api_error))
        self._emit(out)

    def finish(self, push_status):
        """Emit rows for unhandled records; push_status is 'skipped', 'stopped' or 'done'."""
        leftover = "DETECTED" if push_status == "skipped" else "NOT_ATTEMPTED"
        out = []

        def add(entity, case, crid, status, payload=None, source=None, found_in=None):
            if crid and (case, crid) not in self.seen:
                self.seen.add((case, crid))
                out.append(self._row(entity, case, crid, status, payload, source, found_in=found_in))

        if push_status != "done":
            from hcm_data_healer.core.entities import get_entities
            for e in get_entities(self.tenant):
                folder = os.path.join(self.recon_dir or "", e["folder"])
                for case, pattern in (("CREATE", f"{self.tenant}_ES_not_in_DB_*.csv"),
                                      ("UPDATE", f"{self.tenant}_DB_not_in_ES_*.csv")):
                    path = _latest(folder, pattern)
                    if path:
                        for crid in pd.read_csv(path, dtype=str)["clientReferenceId"].dropna():
                            add(e["name"], case, _clean(crid), leftover)
            for (case, crid), info in self.tracer.items():
                add(CHAIN_GAP_ENTITY[case[-1]], case, crid, leftover, info["payload"], info["source"])

        if self.chain_dir:
            for gap, entity in CHAIN_GAP_ENTITY.items():
                path = _latest(self.chain_dir, f"{self.tenant}_chain_unrec_{gap}_*.csv")
                if path:
                    df = pd.read_csv(path, dtype=str)
                    for crid in df.get("id_value", pd.Series(dtype=str)).dropna():
                        add(entity, f"CHAIN_{gap}", _clean(crid), "NOT_IN_TRACER", found_in="NOWHERE")
                path = _latest(self.chain_dir, f"{self.tenant}_chain_processed_{gap}_*.csv")
                if path:
                    for r in pd.read_csv(path, dtype=str).to_dict("records"):
                        add(entity, f"CHAIN_{gap}", _clean(r.get("clientReferenceId")),
                            "PROCESSED_STILL_MISSING", r.get("payload_json"), _source(r))
        self._emit(out)
        return self.rows


def tally(rows):
    """Summarise audit rows into the breakdowns used by the Slack report."""
    out = {"by_outcome": {}, "recovered_by_error": {}, "unrecoverable_by_issue": {}, "failed_by_reason": {},
           "by_category": {c: {"found": 0, "outcomes": {}, "failed_by_reason": {}} for c in ("db", "es", "tracer")}}
    for r in rows:
        cat = {"MISSING_IN_DB": "db", "MISSING_IN_ES": "es"}.get(r["issue"], "tracer")
        bucket = out["by_category"][cat]
        bucket["found"] += 1
        bucket["outcomes"][r["outcome"]] = bucket["outcomes"].get(r["outcome"], 0) + 1
        out["by_outcome"][r["outcome"]] = out["by_outcome"].get(r["outcome"], 0) + 1
        if r["outcome"] == "FAILED":
            # HCM error code if present, else a short reason
            m = re.search(r"\b([A-Z][A-Z0-9]+(?:_[A-Z0-9]+)+)\b", r.get("api_response") or "")
            key = m.group(1) if m else (r.get("api_response") or r.get("outcome_reason") or "unknown")[:60]
            out["failed_by_reason"][key] = out["failed_by_reason"].get(key, 0) + 1
            bucket["failed_by_reason"][key] = bucket["failed_by_reason"].get(key, 0) + 1
        if r["found_in"] == "ERROR_TRACER" and r["outcome"] == "CREATED":
            code = r["source_error_code"] or "(no code)"
            out["recovered_by_error"][code] = out["recovered_by_error"].get(code, 0) + 1
        if r["outcome"] == "UNRECOVERABLE":
            out["unrecoverable_by_issue"][r["issue"]] = out["unrecoverable_by_issue"].get(r["issue"], 0) + 1
    return out
