"""
FastAPI wrapper around Airflow 3 REST API.

Provides endpoints for:
  - Listing DAGs
  - Triggering a DAG run (with optional conf)
  - Checking DAG run status
  - Listing recent DAG runs

Airflow 3 uses session-based auth, so we login first to get a session cookie,
then use that for subsequent API calls.

Environment variables:
  AIRFLOW_BASE_URL  - e.g. http://localhost:8080
  AIRFLOW_USERNAME  - Airflow UI username (default: admin)
  AIRFLOW_PASSWORD  - Airflow UI password (default: admin)
  ALLOWED_ORIGINS   - comma-separated CORS origins (default: http://localhost:3000)
"""

import json
import logging
import os
import re
import uuid
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from typing import Any

import httpx
import psycopg2
import psycopg2.extras
from fastapi import FastAPI, HTTPException, Query
from fastapi.concurrency import run_in_threadpool
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from pydantic import BaseModel
from typing import Optional, Dict, Any

LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
logging.basicConfig(level=LOG_LEVEL, format="%(asctime)s %(levelname)s %(name)s %(message)s")
logger = logging.getLogger(__name__)

class RequestInfoModel(BaseModel):
    apiId: Optional[str]
    ver: Optional[str]
    ts: Optional[str]
    action: Optional[str]
    did: Optional[str]
    key: Optional[str]
    msgId: Optional[str]
    authToken: Optional[str]
    correlationId: Optional[str]
    userInfo: Optional[Dict[str, Any]]

class ReportsMetadataRequest(BaseModel):
    RequestInfo: Optional[RequestInfoModel]
    tenantId: str
    campaignIdentifier: Optional[str] = None
    reportName: Optional[str] = None
    triggerFrequency: Optional[str] = None
    triggeredDate: Optional[str] = None  # dd-MM-yyyy; returns reports triggered on that day (tenant tz)

class ReportStatusRequest(BaseModel):
    RequestInfo: Optional[RequestInfoModel] = None
    tenantId: str
    campaignIdentifier: Optional[str] = None
    reportName: Optional[str] = None
    triggerFrequency: Optional[str] = None
    dagRunId: Optional[str] = None
    latestOnly: bool = True

class ReportsInProgressRequest(BaseModel):
    RequestInfo: Optional[RequestInfoModel] = None
    tenantId: str
    campaignIdentifier: Optional[str] = None
    reportName: Optional[str] = None
    triggeredDate: Optional[str] = None  # dd-MM-yyyy; returns reports triggered on that day (tenant tz)

class CheckExistingCustomReportRequest(BaseModel):
    RequestInfo: Optional[RequestInfoModel] = None
    tenantId: str
    campaignIdentifier: str
    reportName: str
    # Same "DD-MM-YYYY HH:MM:SS+ZZZZ" format the UI sends as customReportStartTime/
    # customReportEndTime when triggering - required so the range computed here
    # matches exactly what the DAG/pod eventually store in reportRange.
    customStartDate: str
    customEndDate: str

# --------------- Config ---------------

AIRFLOW_BASE_URL = os.getenv("AIRFLOW_BASE_URL", "http://localhost:8080")
AIRFLOW_USERNAME = os.getenv("AIRFLOW_USERNAME", "admin")
AIRFLOW_PASSWORD = os.getenv("AIRFLOW_PASSWORD", "admin")
ALLOWED_ORIGINS = os.getenv("ALLOWED_ORIGINS", "http://localhost:3000").split(",")

AIRFLOW_BASE_PATH = os.getenv("AIRFLOW_BASE_PATH", "/airflow")
AIRFLOW_API = f"{AIRFLOW_BASE_URL}{AIRFLOW_BASE_PATH}/api/v2"
AIRFLOW_TOKEN_URL = f"{AIRFLOW_BASE_URL}{AIRFLOW_BASE_PATH}/auth/token"

DB_HOST = os.getenv("DB_HOST", "localhost")
DB_PORT = os.getenv("DB_PORT", "5432")
DB_NAME = os.getenv("DB_NAME", "")
DB_USERNAME = os.getenv("DB_USERNAME", "")
DB_PASSWORD = os.getenv("DB_PASSWORD", "")
IS_CENTRAL_INSTANCE_ENABLED = os.getenv("IS_CENTRAL_INSTANCE_ENABLED", "false").lower() == "true"

# Resolve unqualified table names against the same schema migrate.py writes to.
# Mirrors migrate.py's DB_SCHEMAS convention (first schema in the list, default health),
# so a single DB_SCHEMAS=health makes both the migration and these reads use health.
DB_SCHEMA = (os.getenv("DB_SCHEMAS", "").split(",")[0].strip() or "health")

# Internal cluster-local URL for the dashboard-analytics service (e.g.
# http://dashboard-analytics.egov:8080) - sourced from the egov-service-host configmap,
# same pattern as EGOV_MDMS_HOST. No auth token needed: getChartV2 only requires
# headers.tenantId, not a valid user session, when called service-to-service.
DASHBOARD_ANALYTICS_HOST = os.getenv("EGOV_DASHBOARD_ANALYTICS_HOST", "").rstrip("/")

# In central-instance mode the tenantId is used as a schema *identifier* in the table
# name. Identifiers can't be bound parameters, so a request-supplied tenantId interpolated
# into SQL is an injection vector. Mirror services-common MultiStateInstanceUtil: each
# dot-separated segment must be a valid Postgres identifier (start with a letter/underscore,
# then alphanumeric/underscore, <=63 chars) - no quotes, spaces, semicolons, hyphens or
# comment markers can pass.
_TENANT_SEGMENT = r"[A-Za-z_][A-Za-z0-9_]{0,62}"
_SAFE_TENANT_RE = re.compile(rf"^{_TENANT_SEGMENT}(\.{_TENANT_SEGMENT})*$")


def _reports_metadata_table(tenant_id: str) -> str:
    """Resolve the REPORTS_METADATA table name, guarding the schema identifier.

    Non-central: plain REPORTS_METADATA (search_path=DB_SCHEMA). Central: schema-qualified
    with the tenantId, which is validated against a strict allowlist first so it can be
    safely interpolated (identifiers cannot be passed as bound parameters)."""
    if not IS_CENTRAL_INSTANCE_ENABLED:
        return "REPORTS_METADATA"
    if not tenant_id or not _SAFE_TENANT_RE.match(tenant_id):
        raise HTTPException(status_code=400, detail=f"Invalid tenantId: {tenant_id!r}")
    return f"{tenant_id}.REPORTS_METADATA"


def _get_db_conn():
    logger.debug("Opening DB connection to %s:%s/%s", DB_HOST, DB_PORT, DB_NAME)
    return psycopg2.connect(
        host=DB_HOST, port=DB_PORT, dbname=DB_NAME,
        user=DB_USERNAME, password=DB_PASSWORD,
        options=f"-c search_path={DB_SCHEMA}",
    )


app = FastAPI(title="Airflow DAG Trigger API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_methods=["*"],
    allow_headers=["*"],
)


# --------------- Auth ---------------

_cached_token: str | None = None


async def _get_token() -> str:
    """Login to Airflow via POST /auth/token (OAuth2 password grant)."""
    global _cached_token
    if _cached_token:
        logger.debug("Using cached Airflow token")
        return _cached_token

    logger.debug("Requesting new Airflow token from %s", AIRFLOW_TOKEN_URL)
    async with httpx.AsyncClient() as client:
        resp = await client.post(
            AIRFLOW_TOKEN_URL,
            json={"username": AIRFLOW_USERNAME, "password": AIRFLOW_PASSWORD},
            timeout=30,
        )
    if resp.status_code not in (200, 201):
        logger.error("Airflow login failed: HTTP %s %s", resp.status_code, resp.text)
        raise HTTPException(
            status_code=resp.status_code,
            detail=f"Airflow login failed: {resp.text}",
        )
    token_data = resp.json()
    _cached_token = token_data.get("access_token")
    logger.info("Obtained new Airflow token")
    return _cached_token


async def _auth_headers() -> dict[str, str]:
    token = await _get_token()
    return {"Authorization": f"Bearer {token}"}


async def _invalidate_token():
    global _cached_token
    logger.warning("Invalidating cached Airflow token")
    _cached_token = None


# --------------- Helpers ---------------

async def _airflow_get(path: str) -> dict:
    logger.debug("Airflow GET %s", path)
    headers = await _auth_headers()
    async with httpx.AsyncClient() as client:
        resp = await client.get(
            f"{AIRFLOW_API}{path}",
            headers=headers,
            timeout=30,
        )
    # If 401/403, token may have expired — retry once
    if resp.status_code in (401, 403):
        logger.warning("Airflow GET %s got HTTP %s - retrying with a fresh token", path, resp.status_code)
        await _invalidate_token()
        headers = await _auth_headers()
        async with httpx.AsyncClient() as client:
            resp = await client.get(
                f"{AIRFLOW_API}{path}",
                headers=headers,
                timeout=30,
            )
    if resp.status_code != 200:
        logger.error("Airflow GET %s failed: HTTP %s %s", path, resp.status_code, resp.text)
        raise HTTPException(status_code=resp.status_code, detail=resp.text)
    return resp.json()


async def _airflow_post(path: str, body: dict) -> dict:
    logger.debug("Airflow POST %s body=%s", path, body)
    headers = await _auth_headers()
    async with httpx.AsyncClient() as client:
        resp = await client.post(
            f"{AIRFLOW_API}{path}",
            json=body,
            headers=headers,
            timeout=30,
        )
    # If 401/403, token may have expired — retry once
    if resp.status_code in (401, 403):
        logger.warning("Airflow POST %s got HTTP %s - retrying with a fresh token", path, resp.status_code)
        await _invalidate_token()
        headers = await _auth_headers()
        async with httpx.AsyncClient() as client:
            resp = await client.post(
                f"{AIRFLOW_API}{path}",
                json=body,
                headers=headers,
                timeout=30,
            )
    if resp.status_code not in (200, 201):
        logger.error("Airflow POST %s failed: HTTP %s %s", path, resp.status_code, resp.text)
        raise HTTPException(status_code=resp.status_code, detail=resp.text)
    return resp.json()


# --------------- Models ---------------

class TriggerRequest(BaseModel):
    dag_id: str
    conf: dict[str, Any] | None = None
    logical_date: str | None = None
    note: str | None = None
    locale: str | None = None
    RequestInfo: Optional[RequestInfoModel] = None


# --------------- Report status enrichment ---------------
# Mirrors STATUS_ORDER in hcm-custom-reports/main.py / kafka_status.py - keep in sync.

TERMINAL_STATUSES = {
    "REPORT_COMPLETED", "SKIPPED",
    "POD_INFRA_FAILED", "ENV_VALIDATION_FAILED", "REPORT_GENERATION_FAILED",
    "OUTPUT_NOT_FOUND_FAILED", "ZIP_FAILED", "FILESTORE_UPLOAD_FAILED",
}
FAILED_STATUSES = TERMINAL_STATUSES - {"REPORT_COMPLETED", "SKIPPED"}
# Pushed directly by this API (trigger_dag), not by the DAGs/pod - see
# _insert_triggered_on_ui_rows. Not part of kafka_status.py's/hcm-custom-reports'
# STATUS_ORDER mirrors since neither of those ever emits it.
TRIGGERED_ON_UI_STATUS_ORDER = 15  # between SCHEDULED(10) and TRIGGERED(20)
STAGE_PROGRESS_PERCENT = {
    "TRIGGERED_ON_UI": 12,
    "SCHEDULED": 10,
    "TRIGGERED": 25,
    "POD_STARTED": 40,
    "REPORT_GENERATION_STARTED": 55,
    "ZIP_STARTED": 75,
    "FILESTORE_UPLOAD_STARTED": 90,
    "REPORT_COMPLETED": 100,
    # Failed rows keep the percent of the stage they failed at, so the UI can render
    # a progress bar that stops (in red) where things actually broke.
    "POD_INFRA_FAILED": 30,
    "ENV_VALIDATION_FAILED": 30,
    "REPORT_GENERATION_FAILED": 55,
    "OUTPUT_NOT_FOUND_FAILED": 60,
    "ZIP_FAILED": 75,
    "FILESTORE_UPLOAD_FAILED": 90,
}


def _enrich_status_row(row: dict, now_ms: int) -> dict:
    """Adds UI-facing computed fields to a REPORTS_METADATA row without touching storage."""
    status = row.get("status")
    triggered_ms = row.get("reporttriggeredtimems")
    event_ms = row.get("eventtimestampms")
    is_terminal = status in TERMINAL_STATUSES

    row["isTerminal"] = is_terminal
    row["isFailed"] = status in FAILED_STATUSES
    # "Report time" - the report script's own execution time (existing column, relabeled).
    row["reportTimeSeconds"] = row.get("reportgenerationtimeseconds")
    # "Processing time" - total wall-clock from trigger to completion. Only meaningful
    # once a run has actually completed.
    row["processingTimeSeconds"] = (
        round((event_ms - triggered_ms) / 1000, 2)
        if status == "REPORT_COMPLETED" and triggered_ms is not None and event_ms is not None
        else None
    )
    # How long a still-running attempt has been going, computed server-side so the
    # client's clock skew can never affect it. None once terminal.
    row["elapsedSeconds"] = (
        round((now_ms - triggered_ms) / 1000, 2)
        if not is_terminal and triggered_ms is not None
        else None
    )
    # Estimates computed once at trigger time (trigger_dag) and threaded through
    # Airflow's conf -> env vars -> every subsequent Kafka event (same "stamp once,
    # carry through" pattern as reportTriggeredTimeMs), so every row for a run carries
    # the same value - relabeled here from the native lowercase-folded DB columns.
    row["expectedRows"] = row.get("expectedrows")
    row["expectedGenerationTimeSeconds"] = row.get("expectedgenerationtimeseconds")
    # How long after trigger this specific event happened - a per-event timeline value,
    # distinct from elapsedSeconds (which is "now", recomputed on every poll).
    row["secondsSinceTriggered"] = row.get("secondssincetriggered")

    # Progress: prefer a live estimate (elapsed / expected time) over the static
    # per-stage percentages, since it ticks up continuously instead of jumping between
    # fixed values - capped short of 100% so only the real REPORT_COMPLETED status ever
    # shows a full bar. Falls back to the stage-based percent when no estimate exists
    # (report type has no *_estimate chart, or the live/historical lookup came back null).
    if not is_terminal and row["expectedGenerationTimeSeconds"] and row["elapsedSeconds"] is not None:
        row["progressPercent"] = min(95, round((row["elapsedSeconds"] / float(row["expectedGenerationTimeSeconds"])) * 100))
    else:
        row["progressPercent"] = STAGE_PROGRESS_PERCENT.get(status, 0)
    return row


# reportName -> visualizationCode of the matching *_estimate chart config in
# ChartApiConfig.json. Each config's aggrQuery already encodes the specific
# aggregation (cardinality/count/filter) appropriate for that report - this API
# only needs to know which chart to call and where to read the number back from.
REPORT_ESTIMATE_CHART_KEYS = {
    "user_sync_status": "user_sync_status_estimate",
    "spaq_approved_hf": "spaq_approved_hf_estimate",
    "hf_stock_summary": "hf_stock_summary_estimate",
    "smc_referral_report": "smc_referral_report_estimate",
    "beneficiary_visit_list": "beneficiary_visit_list_estimate",
    "daily_hf_summary_report": "daily_hf_summary_report_estimate",
    "cumulative_daily_summary_form": "cumulative_daily_summary_form_estimate",
}
ESTIMATE_RESULT_KEY = "estimatedCount"  # matches the "select" key in every *_estimate chart config
EXPECTED_GENERATION_TIME_BUFFER_SECONDS = 180  # 3 min
EXPECTED_GENERATION_TIME_DEFAULT_SECONDS = 600  # 10 min, used when no historical data exists yet
# Minimum gap between two triggers for the same campaign+report+CUSTOM-range before a
# regeneration is allowed once the previous run completed successfully. Measured from
# when the previous run was triggered (reportTriggeredTimeMs), not when it finished -
# this bounds "how often can this exact range be re-requested," independent of how
# long generation itself took. Does not apply to a FAILED previous run (that already
# allows immediate retry, unchanged) or to a still-in-progress one (already blocked
# outright, unrelated to this cooldown).
CUSTOM_REPORT_RETRY_COOLDOWN_SECONDS = int(os.getenv("CUSTOM_REPORT_RETRY_COOLDOWN_SECONDS", "3600"))
DASHBOARD_ANALYTICS_TIMEOUT_SECONDS = 8

# reportName -> override: an int (seconds) replaces the global default above for that report;
# "NEVER" (or JSON null) means the report can never be regenerated for the same exact range
# once a run has completed successfully - not merely a longer cooldown. Reports not listed here
# fall back to CUSTOM_REPORT_RETRY_COOLDOWN_SECONDS. Overridable in full via
# CUSTOM_REPORT_RETRY_COOLDOWN_OVERRIDES (JSON object) without a code change - only the defaults
# below need a redeploy to adjust.
DEFAULT_CUSTOM_REPORT_RETRY_COOLDOWN_OVERRIDES: dict[str, Any] = {
    "beneficiary_visit_list": "NEVER",
}
try:
    CUSTOM_REPORT_RETRY_COOLDOWN_OVERRIDES = (
        json.loads(os.environ["CUSTOM_REPORT_RETRY_COOLDOWN_OVERRIDES"])
        if os.getenv("CUSTOM_REPORT_RETRY_COOLDOWN_OVERRIDES")
        else DEFAULT_CUSTOM_REPORT_RETRY_COOLDOWN_OVERRIDES
    )
except (json.JSONDecodeError, TypeError):
    logger.exception("Invalid CUSTOM_REPORT_RETRY_COOLDOWN_OVERRIDES - using defaults")
    CUSTOM_REPORT_RETRY_COOLDOWN_OVERRIDES = DEFAULT_CUSTOM_REPORT_RETRY_COOLDOWN_OVERRIDES


def _get_retry_cooldown_seconds(report_name: str) -> int | None:
    """None means "never allow regeneration for the same range" (permanent block)."""
    if report_name not in CUSTOM_REPORT_RETRY_COOLDOWN_OVERRIDES:
        return CUSTOM_REPORT_RETRY_COOLDOWN_SECONDS
    override = CUSTOM_REPORT_RETRY_COOLDOWN_OVERRIDES[report_name]
    if override is None or (isinstance(override, str) and override.strip().upper() == "NEVER"):
        return None
    return int(override)


def _parse_custom_range(start_str: str, end_str: str) -> tuple[datetime, datetime]:
    """Mirrors hcm_dynamic_campaigns.py's compute_range() CUSTOM branch exactly, so the
    range used here matches whatever the DAG/pod eventually use for generation.

    Keep the user-selected timezone (do NOT convert to UTC): the epoch is identical
    either way, but converting shifts the *displayed* date (e.g. midnight 13th IST
    becomes 12th 18:30 UTC), so the reportRange would show the wrong day vs what the
    user picked. The DAG's CUSTOM branch was fixed the same way."""
    try:
        start = datetime.strptime(start_str, "%d-%m-%Y %H:%M:%S%z")
        end = datetime.strptime(end_str, "%d-%m-%Y %H:%M:%S%z")
    except ValueError as e:
        raise HTTPException(status_code=400, detail=f"Invalid date format: {e}")
    return start, end


def _compute_custom_report_range(start_str: str, end_str: str) -> str:
    start, end = _parse_custom_range(start_str, end_str)
    return f"{start.strftime('%Y-%m-%d %H:%M:%S%z')}_{end.strftime('%Y-%m-%d %H:%M:%S%z')}"


async def _fetch_expected_rows(
    report_name: str, tenant_id: str, campaign_identifier: str, identifier_type: str,
    start_dt: datetime, end_dt: datetime,
) -> int | None:
    """Best-effort live row-count estimate via the dashboard-analytics service's
    getChartV2 API, which already has Elasticsearch access and a config-driven query
    framework (see the *_estimate entries in ChartApiConfig.json) - this API never
    needs its own ES credentials. Called service-to-service on the internal cluster
    URL, no auth token required. Returns None on any failure (missing config, network
    error, bad response shape) - callers treat that as "no estimate available", never
    as a reason to fail the trigger.
    """
    if not DASHBOARD_ANALYTICS_HOST:
        logger.debug("EGOV_DASHBOARD_ANALYTICS_HOST not configured - skipping expectedRows for %s", report_name)
        return None

    chart_key = REPORT_ESTIMATE_CHART_KEYS.get(report_name)
    if not chart_key:
        logger.debug("No estimate chart configured for reportName=%s - skipping expectedRows", report_name)
        return None

    filter_key = "projectTypeId" if identifier_type == "projectTypeId" else "campaignNumber"
    body = {
        "aggregationRequestDto": {
            "visualizationCode": chart_key,
            "visualizationType": "metric",
            "queryType": "",
            "requestDate": {
                "startDate": int(start_dt.timestamp() * 1000),
                "endDate": int(end_dt.timestamp() * 1000),
            },
            "filters": {filter_key: campaign_identifier},
            "moduleLevel": "",
            "aggregationFactors": None,
        },
        "headers": {"tenantId": tenant_id},
    }

    logger.debug("Calling getChartV2 chart=%s body=%s", chart_key, body)
    try:
        async with httpx.AsyncClient() as client:
            resp = await client.post(
                # dashboard-analytics sets server.servlet.context-path=/dashboard-analytics
                # (see its application.properties) - that prefix is part of the app itself,
                # not just an ingress/Zuul routing rule, so it's required even calling the
                # service directly on its cluster-internal host:port.
                f"{DASHBOARD_ANALYTICS_HOST}/dashboard-analytics/dashboard/getChartV2",
                json=body,
                timeout=DASHBOARD_ANALYTICS_TIMEOUT_SECONDS,
            )
        resp.raise_for_status()
        raw_response = resp.json()["responseData"]["customData"]["rawResponse"]
        value = raw_response.get(ESTIMATE_RESULT_KEY)
        if value is None:
            logger.warning("getChartV2(%s) returned no '%s' value: %s", chart_key, ESTIMATE_RESULT_KEY, raw_response)
            return None
        expected_rows = int(value)
        logger.info("expectedRows=%s for reportName=%s (chart=%s)", expected_rows, report_name, chart_key)
        return expected_rows
    except Exception:
        logger.exception("Failed to fetch expectedRows for reportName=%s via chart=%s - defaulting to null", report_name, chart_key)
        return None


def _fetch_expected_generation_time_seconds(
    tenant_id: str, report_name: str, trigger_frequency: str, expected_rows: int | None,
) -> float | None:
    """expectedGenerationTimeSeconds = sum(reportGenerationTimeSeconds) * expectedRows /
    sum(rowCount) + a 3-minute buffer, using historical REPORT_COMPLETED runs for the
    same reportName+triggerFrequency. Falls back to a flat 10-minute default when no
    historical data exists yet. Returns None when expectedRows itself is unknown,
    since the formula has no input to scale by in that case.
    """
    if expected_rows is None:
        logger.debug(
            "No expectedRows for reportName=%s triggerFrequency=%s - expectedGenerationTimeSeconds stays null",
            report_name, trigger_frequency,
        )
        return None

    table = _reports_metadata_table(tenant_id)
    try:
        conn = _get_db_conn()
        cur = conn.cursor()
        cur.execute(
            f"""
            SELECT SUM(reportGenerationTimeSeconds), SUM(rowCount)
            FROM {table}
            WHERE tenantId = %s AND reportName = %s AND triggerFrequency = %s AND status = 'REPORT_COMPLETED'
            """,
            (tenant_id, report_name, trigger_frequency),
        )
        total_time, total_rows = cur.fetchone()
        cur.close()
        conn.close()
    except Exception:
        logger.exception(
            "Failed to fetch historical generation-time stats for reportName=%s triggerFrequency=%s",
            report_name, trigger_frequency,
        )
        return None

    if not total_rows:
        logger.info(
            "No historical REPORT_COMPLETED data for reportName=%s triggerFrequency=%s - using %ss default",
            report_name, trigger_frequency, EXPECTED_GENERATION_TIME_DEFAULT_SECONDS,
        )
        return float(EXPECTED_GENERATION_TIME_DEFAULT_SECONDS)

    expected_seconds = round(
        float(total_time or 0) * expected_rows / float(total_rows) + EXPECTED_GENERATION_TIME_BUFFER_SECONDS, 2
    )
    logger.info(
        "expectedGenerationTimeSeconds=%s for reportName=%s triggerFrequency=%s "
        "(historical total_time=%s total_rows=%s, expectedRows=%s)",
        expected_seconds, report_name, trigger_frequency, total_time, total_rows, expected_rows,
    )
    return expected_seconds


def _insert_triggered_on_ui_rows(conf: dict, dag_id: str, dag_run_id: str, triggered_dt: datetime) -> None:
    """Best-effort bootstrap row so the UI has something to show within milliseconds
    of the click, instead of waiting ~1-1.5 min for Airflow's scheduler to pick up
    the run and for build_payload to push its own TRIGGERED event.

    Written directly to REPORTS_METADATA - this API already holds a DB connection,
    and this is a one-off early signal, not the run's source of truth. Every later
    event for this dagRunId still flows through the normal Kafka -> persister
    pipeline exactly as before, and naturally supersedes this row once it arrives
    (higher statusOrder). Never raises - a failure here must not fail the trigger
    itself, since the DAG run has already been created in Airflow by this point.
    """
    matched_campaigns = conf.get("matched_campaigns")
    if not isinstance(matched_campaigns, list) or not matched_campaigns:
        logger.debug("No matched_campaigns in conf for dag_run_id=%s - nothing to bootstrap", dag_run_id)
        return

    triggered_ms = int(triggered_dt.timestamp() * 1000)
    triggered_iso = triggered_dt.isoformat()

    # One connection for the whole batch, reused across campaigns (was: a fresh connect
    # per row inside the loop). Best-effort: if even the connect fails, skip the bootstrap
    # entirely - the normal Kafka -> persister pipeline still records every later event.
    try:
        conn = _get_db_conn()
    except Exception:
        logger.exception("Failed to open DB connection for TRIGGERED_ON_UI rows, dag_run_id=%s", dag_run_id)
        return

    try:
        for c in matched_campaigns:
            if not isinstance(c, dict):
                continue
            tenant_id = c.get("tenantId")
            campaign_identifier = c.get("campaignIdentifier")
            report_name = c.get("reportName")
            trigger_frequency = c.get("triggerFrequency")
            if not tenant_id:
                logger.warning("Skipping TRIGGERED_ON_UI row - matched campaign has no tenantId (dag_run_id=%s)", dag_run_id)
                continue

            # expectedRows/expectedGenerationTimeSeconds were already computed by trigger_dag
            # (before Airflow was even called) so they could be stamped onto conf and threaded
            # through env vars into every subsequent Kafka event - just read them back here.
            expected_rows = c.get("expectedRows")
            expected_generation_time_seconds = c.get("expectedGenerationTimeSeconds")
            row_triggered_ms = c.get("reportTriggeredTimeMs", triggered_ms)
            seconds_since_triggered = round((triggered_ms - row_triggered_ms) / 1000, 2)

            report_range = None
            if (trigger_frequency or "").upper() == "CUSTOM":
                try:
                    start_dt, end_dt = _parse_custom_range(
                        c.get("customReportStartTime", ""), c.get("customReportEndTime", "")
                    )
                    report_range = f"{start_dt.strftime('%Y-%m-%d %H:%M:%S%z')}_{end_dt.strftime('%Y-%m-%d %H:%M:%S%z')}"
                except Exception:
                    logger.exception(
                        "Failed to compute report_range for campaignIdentifier=%s dag_run_id=%s",
                        campaign_identifier, dag_run_id,
                    )

            try:
                table = _reports_metadata_table(tenant_id)
                with conn.cursor() as cur:
                    cur.execute(
                        f"""
                        INSERT INTO {table} (
                            eventId, dagRunId, dagName, campaignIdentifier, identifierType, reportName,
                            triggerFrequency, triggerTime, tenantId, reportRange,
                            reportTriggeredTimeMs, reportTriggeredTime,
                            status, statusOrder, eventTimestamp, eventTimestampMs,
                            expectedRows, expectedGenerationTimeSeconds, secondsSinceTriggered
                        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                        ON CONFLICT (eventId) DO NOTHING
                        """,
                        (
                            str(uuid.uuid4()), dag_run_id, dag_id,
                            campaign_identifier, c.get("identifierType"), report_name,
                            trigger_frequency, c.get("triggerTime"), tenant_id, report_range,
                            row_triggered_ms, c.get("reportTriggeredTime", triggered_iso),
                            "TRIGGERED_ON_UI", TRIGGERED_ON_UI_STATUS_ORDER, triggered_iso, triggered_ms,
                            expected_rows, expected_generation_time_seconds, seconds_since_triggered,
                        ),
                    )
                conn.commit()
                logger.info(
                    "Inserted TRIGGERED_ON_UI row: campaignIdentifier=%s reportName=%s dag_run_id=%s "
                    "expectedRows=%s expectedGenerationTimeSeconds=%s",
                    campaign_identifier, report_name, dag_run_id, expected_rows, expected_generation_time_seconds,
                )
            except Exception:
                logger.exception(
                    "Failed to insert TRIGGERED_ON_UI row for campaignIdentifier=%s dag_run_id=%s",
                    campaign_identifier, dag_run_id,
                )
                conn.rollback()
    finally:
        conn.close()


# --------------- Endpoints ---------------

@app.get("/airflow-trigger-api/api/dags")
async def list_dags():
    """Return all DAGs visible in Airflow."""
    logger.info("list_dags called")
    data = await _airflow_get("/dags?limit=100")
    dags = [
        {
            "dag_id": d["dag_id"],
            "description": d.get("description"),
            "is_paused": d["is_paused"],
            "schedule_interval": d.get("schedule_interval"),
            "tags": [t["name"] for t in d.get("tags", [])],
        }
        for d in data.get("dags", [])
    ]
    logger.info("list_dags returning %d dags", len(dags))
    return dags


@app.post("/airflow-trigger-api/api/dags/trigger")
async def trigger_dag(req: TriggerRequest):
    """Trigger a DAG run with optional conf payload."""
    logger.info("trigger_dag called: dag_id=%s logical_date=%s", req.dag_id, req.logical_date)
    # The actual moment this HTTP request arrived - captured before Airflow ever
    # sees it, so it reflects the true "user clicked" time rather than whenever
    # Airflow's scheduler eventually picks up the run.
    triggered_dt = datetime.now(timezone.utc)

    body: dict[str, Any] = {}
    if req.conf:
        # Locale = the user's selected app locale, resolved exactly like excel-ingestion:
        # explicit body `locale` first, else RequestInfo.msgId ("<ts>|<locale>"), else the
        # DAG/worker env default downstream. Stamped onto conf so it threads through
        # Airflow conf -> build_payload -> worker env LOCALE -> localization (falls back to
        # env for no-msgId / scheduler-triggered runs).
        _locale = req.locale
        if not _locale and req.RequestInfo and req.RequestInfo.msgId and "|" in req.RequestInfo.msgId:
            _locale = req.RequestInfo.msgId.split("|", 1)[1].strip() or None
        if _locale:
            req.conf.setdefault("locale", _locale)
        matched_campaigns = req.conf.get("matched_campaigns")
        if isinstance(matched_campaigns, list):
            # build_payload already prefers a pre-set reportTriggeredTimeMs/Time over
            # its own dag_run.start_date fallback - stamping it here (instead of
            # leaving it for build_payload to default) means every later lifecycle
            # event for this run reports the true click time, not ~1-1.5 min later.
            triggered_ms = int(triggered_dt.timestamp() * 1000)
            triggered_iso = triggered_dt.isoformat()
            for c in matched_campaigns:
                if not isinstance(c, dict):
                    continue
                c.setdefault("reportTriggeredTimeMs", triggered_ms)
                c.setdefault("reportTriggeredTime", triggered_iso)

                # expectedRows/expectedGenerationTimeSeconds: computed here (BEFORE
                # Airflow ever sees this conf), only feasible for CUSTOM since that's the
                # only frequency with a known date range at trigger time. Stamping it here
                # (not just on this API's own TRIGGERED_ON_UI row) means it flows through
                # to Airflow's conf -> build_payload's env vars -> every subsequent Kafka
                # event, so the estimate survives the run's whole lifecycle.
                if (c.get("triggerFrequency") or "").upper() == "CUSTOM" and "expectedRows" not in c:
                    try:
                        start_dt, end_dt = _parse_custom_range(
                            c.get("customReportStartTime", ""), c.get("customReportEndTime", "")
                        )
                        expected_rows = await _fetch_expected_rows(
                            c.get("reportName"), c.get("tenantId"), c.get("campaignIdentifier"),
                            c.get("identifierType"), start_dt, end_dt,
                        )
                        c["expectedRows"] = expected_rows
                        c["expectedGenerationTimeSeconds"] = await run_in_threadpool(
                            _fetch_expected_generation_time_seconds,
                            c.get("tenantId"), c.get("reportName"), c.get("triggerFrequency"), expected_rows,
                        )
                    except Exception:
                        logger.exception(
                            "Failed to compute expectedRows/expectedGenerationTimeSeconds for campaignIdentifier=%s",
                            c.get("campaignIdentifier"),
                        )
                        c["expectedRows"] = None
                        c["expectedGenerationTimeSeconds"] = None
        body["conf"] = req.conf
    if req.logical_date:
        body["logical_date"] = req.logical_date
    if req.note:
        body["note"] = req.note

    result = await _airflow_post(f"/dags/{req.dag_id}/dagRuns", body)
    logger.info("Airflow dag_run created: dag_id=%s dag_run_id=%s state=%s", result["dag_id"], result["dag_run_id"], result["state"])

    if req.dag_id == "hcm_dynamic_campaigns" and req.conf:
        # Bootstrap row is best-effort and the DAG run is already created, so nothing here -
        # not even an unexpected error - may turn a successful trigger into a 500.
        try:
            await run_in_threadpool(_insert_triggered_on_ui_rows, req.conf, req.dag_id, result["dag_run_id"], triggered_dt)
        except Exception:
            logger.exception("TRIGGERED_ON_UI bootstrap failed for dag_run_id=%s - trigger already succeeded, continuing", result["dag_run_id"])

    return {
        "dag_id": result["dag_id"],
        "dag_run_id": result["dag_run_id"],
        "state": result["state"],
        "logical_date": result["logical_date"],
        "start_date": result.get("start_date"),
    }


@app.get("/airflow-trigger-api/api/dags/{dag_id}/runs")
async def list_dag_runs(dag_id: str, limit: int = 10):
    """List recent runs for a DAG, newest first."""
    logger.info("list_dag_runs called: dag_id=%s limit=%s", dag_id, limit)
    data = await _airflow_get(
        f"/dags/{dag_id}/dagRuns?limit={limit}&order_by=-start_date"
    )
    runs = [
        {
            "dag_run_id": r["dag_run_id"],
            "state": r["state"],
            "logical_date": r["logical_date"],
            "start_date": r.get("start_date"),
            "end_date": r.get("end_date"),
            "conf": r.get("conf", {}),
            "note": r.get("note"),
        }
        for r in data.get("dag_runs", [])
    ]
    logger.info("list_dag_runs returning %d runs for dag_id=%s", len(runs), dag_id)
    return runs


@app.get("/airflow-trigger-api/api/dags/{dag_id}/runs/{dag_run_id}")
async def get_dag_run(dag_id: str, dag_run_id: str):
    """Get status of a specific DAG run."""
    logger.info("get_dag_run called: dag_id=%s dag_run_id=%s", dag_id, dag_run_id)
    return await _airflow_get(f"/dags/{dag_id}/dagRuns/{dag_run_id}")


@app.get("/airflow-trigger-api/api/dags/{dag_id}/runs/{dag_run_id}/tasks")
async def list_task_instances(dag_id: str, dag_run_id: str):
    """List task instances for a DAG run (to see per-task status)."""
    logger.info("list_task_instances called: dag_id=%s dag_run_id=%s", dag_id, dag_run_id)
    data = await _airflow_get(
        f"/dags/{dag_id}/dagRuns/{dag_run_id}/taskInstances"
    )
    tasks = [
        {
            "task_id": t["task_id"],
            "state": t.get("state"),
            "start_date": t.get("start_date"),
            "end_date": t.get("end_date"),
            "duration": t.get("duration"),
            "try_number": t.get("try_number"),
        }
        for t in data.get("task_instances", [])
    ]
    logger.info("list_task_instances returning %d tasks for dag_run_id=%s", len(tasks), dag_run_id)
    return tasks

# Filter by "triggered on this calendar day": interpret the date in the tenant timezone
# (default +05:30) and bound reportTriggeredTimeMs to that day. Configurable for other tenants.
REPORT_QUERY_TZ_OFFSET_MINUTES = int(os.getenv("REPORT_QUERY_TZ_OFFSET_MINUTES", "330"))

def _triggered_day_bounds_ms(date_str):
    """'dd-MM-yyyy' -> (start_ms, end_ms) epoch-millis spanning that whole day in the
    configured tenant timezone. Raises ValueError on a bad format."""
    tz = timezone(timedelta(minutes=REPORT_QUERY_TZ_OFFSET_MINUTES))
    day_start = datetime.strptime(date_str, "%d-%m-%Y").replace(tzinfo=tz)
    start_ms = int(day_start.timestamp() * 1000)
    end_ms = int((day_start + timedelta(days=1)).timestamp() * 1000) - 1
    return start_ms, end_ms


def _latest_per_run_subquery(table: str, where_clause: str) -> str:
    """Windowed 'latest event per run' source: one row per run (partitioned by the run's
    identity), ranked by the producer's logical ordering (statusOrder, then eventTimestampMs)
    so it's correct even under out-of-order Kafka delivery. Wrap with an outer
    `WHERE rn = 1 [...]` to select/filter the latest row. Shared by report-status and
    reports-in-progress so the ranking definition can't drift between them."""
    return f"""
        SELECT * FROM (
            SELECT *, ROW_NUMBER() OVER (
                PARTITION BY tenantId, campaignIdentifier, reportName, triggerFrequency, dagRunId
                ORDER BY statusOrder DESC, eventTimestampMs DESC
            ) AS rn
            FROM {table}
            WHERE {where_clause}
        ) ranked
    """


@app.post("/airflow-trigger-api/api/reports-metadata")
def search_reports_metadata(req: ReportsMetadataRequest):
    logger.info(
        "search_reports_metadata called: tenantId=%s campaignIdentifier=%s reportName=%s triggerFrequency=%s triggeredDate=%s",
        req.tenantId, req.campaignIdentifier, req.reportName, req.triggerFrequency, req.triggeredDate,
    )
    tenant_id = req.tenantId

    table = _reports_metadata_table(tenant_id)

    # REPORTS_METADATA is now append-only (one row per status event, not just terminal
    # outcomes) - filtering to REPORT_COMPLETED reproduces the original "one row per
    # successful, downloadable report" shape this endpoint has always returned, since
    # REPORT_COMPLETED is emitted exactly once per run. Failed attempts simply won't
    # appear here anymore (they're still fully visible via /report-status).
    # Rows written before this column existed have status IS NULL - the only success/
    # failure signal available for those is whether fileStoreId was ever populated
    # (the pre-existing persister mapping never included a status column, and a FAILED
    # push always left file_store_id at its "" default) - so those legacy rows are
    # included here precisely when they have a real fileStoreId, to avoid every
    # historical successful report silently disappearing the moment this ships.
    query = f"""SELECT * FROM {table} WHERE tenantId = %s AND (
        status = 'REPORT_COMPLETED'
        OR (status IS NULL AND fileStoreId IS NOT NULL AND fileStoreId <> '')
    )"""
    params: list[str] = [tenant_id]

    if req.campaignIdentifier:
        query += " AND campaignIdentifier = %s"
        params.append(req.campaignIdentifier)
    if req.reportName:
        query += " AND reportName = %s"
        params.append(req.reportName)
    if req.triggerFrequency:
        query += " AND triggerFrequency = %s"
        params.append(req.triggerFrequency)
    if req.triggeredDate:
        try:
            _from_ms, _to_ms = _triggered_day_bounds_ms(req.triggeredDate)
        except ValueError:
            raise HTTPException(status_code=400, detail="triggeredDate must be in dd-MM-yyyy format")
        query += " AND reportTriggeredTimeMs BETWEEN %s AND %s"
        params.extend([_from_ms, _to_ms])

    query += " ORDER BY createdTime DESC"

    try:
        conn = _get_db_conn()
        cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
        cur.execute(query, params)
        rows = cur.fetchall()
        cur.close()
        conn.close()
    except Exception:
        logger.exception("search_reports_metadata query failed")
        raise HTTPException(status_code=500, detail="Failed to fetch report metadata")

    now_ms = int(datetime.now(timezone.utc).timestamp() * 1000)
    data = [_enrich_status_row(row, now_ms) for row in rows]
    logger.info("search_reports_metadata returning %d rows", len(data))

    return {
        "ResponseInfo": req.RequestInfo,
        "data": data
    }


@app.post("/airflow-trigger-api/api/report-status")
def search_report_status(req: ReportStatusRequest):
    """Full lifecycle history / latest-state view - REPORT_STATUS_EVENTS was merged into
    REPORTS_METADATA (one topic, one table going forward), so this now reads from there."""
    logger.info(
        "search_report_status called: tenantId=%s campaignIdentifier=%s reportName=%s triggerFrequency=%s dagRunId=%s latestOnly=%s",
        req.tenantId, req.campaignIdentifier, req.reportName, req.triggerFrequency, req.dagRunId, req.latestOnly,
    )
    tenant_id = req.tenantId

    table = _reports_metadata_table(tenant_id)

    filters = ["tenantId = %s"]
    params: list[str] = [tenant_id]

    if req.campaignIdentifier:
        filters.append("campaignIdentifier = %s")
        params.append(req.campaignIdentifier)
    if req.reportName:
        filters.append("reportName = %s")
        params.append(req.reportName)
    if req.triggerFrequency:
        filters.append("triggerFrequency = %s")
        params.append(req.triggerFrequency)
    if req.dagRunId:
        filters.append("dagRunId = %s")
        params.append(req.dagRunId)

    where_clause = " AND ".join(filters)

    # latestOnly ranks by statusOrder/eventTimestampMs (the producer's own logical ordering,
    # as a plain number - not by insertion order or the display-only eventTimestamp string),
    # so this stays correct even if Kafka delivered events out of order.
    if req.latestOnly:
        query = _latest_per_run_subquery(table, where_clause) + " WHERE rn = 1 ORDER BY eventTimestampMs DESC"
    else:
        query = f"SELECT * FROM {table} WHERE {where_clause} ORDER BY eventTimestampMs ASC"

    try:
        conn = _get_db_conn()
        cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
        cur.execute(query, params)
        rows = cur.fetchall()
        cur.close()
        conn.close()
    except Exception:
        logger.exception("search_report_status query failed")
        raise HTTPException(status_code=500, detail="Failed to fetch report status")

    for row in rows:
        row.pop("rn", None)
    logger.info("search_report_status returning %d rows", len(rows))

    return {
        "ResponseInfo": req.RequestInfo,
        "data": rows
    }


@app.post("/airflow-trigger-api/api/reports-in-progress")
def reports_in_progress(req: ReportsInProgressRequest):
    """Latest-status rows that are still in flight (not completed/failed/skipped) -
    drives the 'in progress' cards/badges on the reports UI."""
    logger.info(
        "reports_in_progress called: tenantId=%s campaignIdentifier=%s reportName=%s triggeredDate=%s",
        req.tenantId, req.campaignIdentifier, req.reportName, req.triggeredDate,
    )
    tenant_id = req.tenantId

    table = _reports_metadata_table(tenant_id)

    filters = ["tenantId = %s"]
    params: list[str] = [tenant_id]

    if req.campaignIdentifier:
        filters.append("campaignIdentifier = %s")
        params.append(req.campaignIdentifier)
    if req.reportName:
        filters.append("reportName = %s")
        params.append(req.reportName)
    if req.triggeredDate:
        try:
            _from_ms, _to_ms = _triggered_day_bounds_ms(req.triggeredDate)
        except ValueError:
            raise HTTPException(status_code=400, detail="triggeredDate must be in dd-MM-yyyy format")
        filters.append("reportTriggeredTimeMs BETWEEN %s AND %s")
        params.extend([_from_ms, _to_ms])

    where_clause = " AND ".join(filters)

    # "In flight" = the run's latest status is not terminal. Derive that from the in-file
    # TERMINAL_STATUSES set (which already includes SKIPPED and every *_FAILED) instead of a
    # magic `statusOrder < 40` threshold, so it can't silently drift from kafka_status.py's
    # ordering if a new in-progress stage is added. NULL-status legacy rows fall out of
    # NOT IN, which is correct - they were never real in-flight runs.
    terminal_statuses = tuple(sorted(TERMINAL_STATUSES))
    terminal_placeholders = ",".join(["%s"] * len(terminal_statuses))
    query = (
        _latest_per_run_subquery(table, where_clause)
        + f" WHERE rn = 1 AND status NOT IN ({terminal_placeholders}) ORDER BY eventTimestampMs DESC"
    )
    params = params + list(terminal_statuses)

    try:
        conn = _get_db_conn()
        cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
        cur.execute(query, params)
        rows = cur.fetchall()
        cur.close()
        conn.close()
    except Exception:
        logger.exception("reports_in_progress query failed")
        raise HTTPException(status_code=500, detail="Failed to fetch in-progress reports")

    now_ms = int(datetime.now(timezone.utc).timestamp() * 1000)
    data = []
    hidden_no_range = 0
    for row in rows:
        row.pop("rn", None)
        if (row.get("triggerfrequency") or "").upper() == "CUSTOM" and not row.get("reportrange"):
            hidden_no_range += 1
            logger.info(
                "reports_in_progress: hiding CUSTOM run with no reportRange - dagRunId=%s reportName=%s",
                row.get("dagrunid"), row.get("reportname"),
            )
            continue
        data.append(_enrich_status_row(row, now_ms))
    logger.info("reports_in_progress returning %d in-flight rows (%d with no reportRange hidden)", len(data), hidden_no_range)

    return {
        "ResponseInfo": req.RequestInfo,
        "data": data
    }


@app.post("/airflow-trigger-api/api/reports-check-existing")
def check_existing_custom_report(req: CheckExistingCustomReportRequest):
    """Pre-flight check before triggering a CUSTOM report: is there already a
    completed/in-progress/failed run for this exact campaign+report+date-range?

    Matches on the existing reportRange column (no new columns/migrations). For
    runs triggered through this API, trigger_dag's TRIGGERED_ON_UI bootstrap row
    already carries the range from the very first event, so this is reliable from
    the moment of trigger onward - not just from POD_STARTED as before.
    """
    logger.info(
        "check_existing_custom_report called: tenantId=%s campaignIdentifier=%s reportName=%s customStartDate=%s customEndDate=%s",
        req.tenantId, req.campaignIdentifier, req.reportName, req.customStartDate, req.customEndDate,
    )
    tenant_id = req.tenantId

    table = _reports_metadata_table(tenant_id)

    report_range = _compute_custom_report_range(req.customStartDate, req.customEndDate)
    logger.debug("check_existing_custom_report computed reportRange=%s", report_range)

    # expectedRows/expectedGenerationTimeSeconds are stamped onto matched_campaigns in
    # trigger_dag and threaded through every subsequent event, so every row of the
    # matched run already carries the same value directly - no carry-forward needed.
    query = f"""
        WITH matches AS (
            SELECT * FROM {table}
            WHERE tenantId = %s AND campaignIdentifier = %s AND reportName = %s
              AND triggerFrequency = 'CUSTOM' AND reportRange = %s AND status <> 'SKIPPED'
        ),
        latest_run AS (
            SELECT dagRunId FROM matches
            ORDER BY reportTriggeredTimeMs DESC NULLS LAST
            LIMIT 1
        )
        SELECT * FROM matches
        WHERE dagRunId = (SELECT dagRunId FROM latest_run)
        ORDER BY statusOrder DESC, eventTimestampMs DESC
        LIMIT 1
    """
    params = [tenant_id, req.campaignIdentifier, req.reportName, report_range]

    try:
        conn = _get_db_conn()
        cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
        cur.execute(query, params)
        row = cur.fetchone()
        cur.close()
        conn.close()
    except Exception:
        logger.exception("check_existing_custom_report query failed")
        raise HTTPException(status_code=500, detail="Failed to check existing report")

    if not row:
        logger.info("check_existing_custom_report: no existing run for reportRange=%s", report_range)
        return {"ResponseInfo": req.RequestInfo, "exists": False, "data": None}

    now_ms = int(datetime.now(timezone.utc).timestamp() * 1000)
    enriched = _enrich_status_row(row, now_ms)
    logger.info(
        "check_existing_custom_report: found existing run status=%s isTerminal=%s isFailed=%s for reportRange=%s",
        enriched.get("status"), enriched.get("isTerminal"), enriched.get("isFailed"), report_range,
    )

    # A completed (not failed) previous run for this exact range no longer blocks
    # regeneration forever - once CUSTOM_REPORT_RETRY_COOLDOWN_SECONDS has passed since
    # it was triggered, report back as if nothing exists at all, so the frontend's
    # existing "if (!existing) { trigger immediately }" path fires unchanged. Still
    # within the cooldown: stays blocked as before, but now carries how much longer -
    # ready for the UI to render once it has a place to show it.
    if enriched.get("isTerminal") and not enriched.get("isFailed"):
        cooldown_seconds = _get_retry_cooldown_seconds(req.reportName)
        if cooldown_seconds is None:
            enriched["retryBlocked"] = True
            logger.info(
                "check_existing_custom_report: reportName=%s is configured as no-retry - "
                "regeneration permanently blocked for reportRange=%s", req.reportName, report_range,
            )
        else:
            triggered_ms = enriched.get("reporttriggeredtimems")
            if triggered_ms is not None:
                remaining_seconds = cooldown_seconds - (now_ms - triggered_ms) / 1000
                if remaining_seconds <= 0:
                    logger.info(
                        "check_existing_custom_report: cooldown elapsed for reportRange=%s - allowing regeneration",
                        report_range,
                    )
                    return {"ResponseInfo": req.RequestInfo, "exists": False, "data": None}
                enriched["retryAvailableInSeconds"] = round(remaining_seconds)
                enriched["retryAvailableInMinutes"] = -(-round(remaining_seconds) // 60)  # ceil without importing math
                logger.info(
                    "check_existing_custom_report: still within cooldown for reportRange=%s - %s seconds remaining",
                    report_range, enriched["retryAvailableInSeconds"],
                )

    return {
        "ResponseInfo": req.RequestInfo,
        "exists": True,
        "data": enriched,
    }


@app.get("/health")
async def health():
    logger.debug("health check ping")
    return {"status": "ok"}
