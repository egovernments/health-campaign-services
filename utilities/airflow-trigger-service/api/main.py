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

import os
from contextlib import asynccontextmanager
from typing import Any

import httpx
import psycopg2
import psycopg2.extras
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from pydantic import BaseModel
from typing import Optional, Dict, Any

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


def _get_db_conn():
    return psycopg2.connect(
        host=DB_HOST, port=DB_PORT, dbname=DB_NAME,
        user=DB_USERNAME, password=DB_PASSWORD,
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
        return _cached_token

    async with httpx.AsyncClient() as client:
        resp = await client.post(
            AIRFLOW_TOKEN_URL,
            json={"username": AIRFLOW_USERNAME, "password": AIRFLOW_PASSWORD},
            timeout=30,
        )
    if resp.status_code not in (200, 201):
        raise HTTPException(
            status_code=resp.status_code,
            detail=f"Airflow login failed: {resp.text}",
        )
    token_data = resp.json()
    _cached_token = token_data.get("access_token")
    return _cached_token


async def _auth_headers() -> dict[str, str]:
    token = await _get_token()
    return {"Authorization": f"Bearer {token}"}


async def _invalidate_token():
    global _cached_token
    _cached_token = None


# --------------- Helpers ---------------

async def _airflow_get(path: str) -> dict:
    headers = await _auth_headers()
    async with httpx.AsyncClient() as client:
        resp = await client.get(
            f"{AIRFLOW_API}{path}",
            headers=headers,
            timeout=30,
        )
    # If 401/403, token may have expired — retry once
    if resp.status_code in (401, 403):
        await _invalidate_token()
        headers = await _auth_headers()
        async with httpx.AsyncClient() as client:
            resp = await client.get(
                f"{AIRFLOW_API}{path}",
                headers=headers,
                timeout=30,
            )
    if resp.status_code != 200:
        raise HTTPException(status_code=resp.status_code, detail=resp.text)
    return resp.json()


async def _airflow_post(path: str, body: dict) -> dict:
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
        raise HTTPException(status_code=resp.status_code, detail=resp.text)
    return resp.json()


# --------------- Models ---------------

class TriggerRequest(BaseModel):
    dag_id: str
    conf: dict[str, Any] | None = None
    logical_date: str | None = None
    note: str | None = None


# --------------- Endpoints ---------------

@app.get("/airflow-trigger-api/api/dags")
async def list_dags():
    """Return all DAGs visible in Airflow."""
    data = await _airflow_get("/dags?limit=100")
    return [
        {
            "dag_id": d["dag_id"],
            "description": d.get("description"),
            "is_paused": d["is_paused"],
            "schedule_interval": d.get("schedule_interval"),
            "tags": [t["name"] for t in d.get("tags", [])],
        }
        for d in data.get("dags", [])
    ]


@app.post("/airflow-trigger-api/api/dags/trigger")
async def trigger_dag(req: TriggerRequest):
    """Trigger a DAG run with optional conf payload."""
    body: dict[str, Any] = {}
    if req.conf:
        body["conf"] = req.conf
    if req.logical_date:
        body["logical_date"] = req.logical_date
    if req.note:
        body["note"] = req.note

    result = await _airflow_post(f"/dags/{req.dag_id}/dagRuns", body)
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
    data = await _airflow_get(
        f"/dags/{dag_id}/dagRuns?limit={limit}&order_by=-start_date"
    )
    return [
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


@app.get("/airflow-trigger-api/api/dags/{dag_id}/runs/{dag_run_id}")
async def get_dag_run(dag_id: str, dag_run_id: str):
    """Get status of a specific DAG run."""
    return await _airflow_get(f"/dags/{dag_id}/dagRuns/{dag_run_id}")


@app.get("/airflow-trigger-api/api/dags/{dag_id}/runs/{dag_run_id}/tasks")
async def list_task_instances(dag_id: str, dag_run_id: str):
    """List task instances for a DAG run (to see per-task status)."""
    data = await _airflow_get(
        f"/dags/{dag_id}/dagRuns/{dag_run_id}/taskInstances"
    )
    return [
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

@app.post("/airflow-trigger-api/api/reports-metadata")
async def search_reports_metadata(req: ReportsMetadataRequest):
    tenant_id = req.tenantId

    if IS_CENTRAL_INSTANCE_ENABLED:
        table = f"{tenant_id}.REPORTS_METADATA"
    else:
        table = "REPORTS_METADATA"

    query = f"SELECT * FROM {table} WHERE tenantId = %s"
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

    query += " ORDER BY createdTime DESC"

    try:
        conn = _get_db_conn()
        cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
        cur.execute(query, params)
        rows = cur.fetchall()
        cur.close()
        conn.close()
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

    return {
        "ResponseInfo": req.RequestInfo,
        "data": rows
    }


@app.get("/health")
async def health():
    return {"status": "ok"}