"""
DIGIT HCM API client for the push phase.

Search, create and update calls with error classification, retry/backoff and
duplicate detection. Calls go to in-cluster services; roles travel in
RequestInfo.userInfo.
"""

import json
import logging
import os
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone

import requests
from requests.adapters import HTTPAdapter

log = logging.getLogger(__name__)

API_HDRS = {"Content-Type": "application/json"}
DUPLICATE_CODES = {"DUPLICATE_ENTITY", "ALREADY_EXISTS", "RECORD_ALREADY_EXISTS"}


class StopRun(Exception):
    """Raised on auth failure so the caller can halt the whole run."""


class ExistenceCheckFailed(Exception):
    """Search could not confirm existence; callers must not create those ids."""


# Roles sent inline in userInfo (in-cluster call, no gateway).
PUSH_ROLES = ("SYSTEM_ADMINISTRATOR", "SUPERUSER")


def _roles(tenant):
    return [{"code": c, "name": c.replace("_", " ").title(), "tenantId": tenant} for c in PUSH_ROLES]


class APIClient:
    def __init__(self, tenant, auth_token, user_uuid, options=None, verify_ssl=False, pool_size=10):
        self.tenant = tenant
        self.auth_token = auth_token
        self.user_uuid = user_uuid
        self.verify = bool(verify_ssl)
        opts = options or {}
        self.max_retries = int(opts.get("max_retries", 3))
        self.retry_delay = int(opts.get("retry_delay", 2))
        self.search_batch = int(opts.get("search_batch", 50))
        # Keep-alive session: one connection per service instead of one per call.
        # pool_size must cover the push workers so threads do not wait for a connection.
        self.session = requests.Session()
        adapter = HTTPAdapter(pool_connections=10, pool_maxsize=max(int(pool_size), 10))
        self.session.mount("http://", adapter)
        self.session.mount("https://", adapter)

    def req_info(self):
        # No gateway in-cluster, so services trust userInfo; authToken only if set.
        info = {
            "apiId": "data-healer",
            "ver": "1.0",
            "ts": int(datetime.now(timezone.utc).timestamp() * 1000),
            "msgId": f"recon-{datetime.now().strftime('%Y%m%d%H%M%S%f')}",
            "userInfo": {
                "id": 0,
                "tenantId": self.tenant,
                "uuid": self.user_uuid,
                "userName": "data-healer",
                "name": "Recon Recovery",
                "type": "EMPLOYEE",
                "mobileNumber": "9999999999",
                "roles": _roles(self.tenant),
            },
        }
        if self.auth_token:
            info["authToken"] = self.auth_token
        return info

    # Existence checks
    def check_exists(self, entity, ids, workers=1, progress=None):
        """Return the clientReferenceIds that already exist, via the search API.

        Chunks of search_batch ids run on up to `workers` threads (read-only calls);
        progress(done, total) is called after each chunk.
        Fails closed: any non-200 or exception raises ExistenceCheckFailed."""
        ids = list(ids)
        chunks = [ids[i:i + self.search_batch] for i in range(0, len(ids), self.search_batch)]

        def one(n, chunk):
            payload = {"RequestInfo": self.req_info(),
                       entity["search_key"]: {"tenantId": self.tenant, "clientReferenceId": chunk}}
            try:
                r = self.session.post(entity["search_url"], json=payload,
                                      params={"tenantId": self.tenant, "limit": self.search_batch, "offset": 0},
                                      headers=API_HDRS, verify=self.verify, timeout=60)
                if r.status_code in (401, 403):
                    raise StopRun(f"Auth failure on search HTTP {r.status_code}")
                if r.status_code != 200:
                    raise ExistenceCheckFailed(f"{entity['name']} search HTTP {r.status_code}: "
                                               f"{r.text[:200]}")
                return {rec.get("clientReferenceId") for rec in (r.json().get(entity["resp_key"]) or [])
                        if rec.get("clientReferenceId")}
            except (StopRun, ExistenceCheckFailed):
                raise
            except Exception as exc:
                raise ExistenceCheckFailed(f"{entity['name']} search chunk {n} failed: {exc}") from exc

        found = set()
        if workers <= 1:
            for n, chunk in enumerate(chunks, 1):
                found |= one(n, chunk)
                if progress:
                    progress(min(n * self.search_batch, len(ids)), len(ids))
            return found
        with ThreadPoolExecutor(max_workers=workers) as pool:
            futures = [pool.submit(one, n, chunk) for n, chunk in enumerate(chunks, 1)]
            try:
                done = 0
                for fut in as_completed(futures):
                    found |= fut.result()          # first error raises; finally cancels the rest
                    done += 1
                    if progress:
                        progress(min(done * self.search_batch, len(ids)), len(ids))
            finally:
                for fut in futures:
                    fut.cancel()
        return found

    def search_one(self, entity, crid):
        """Return the full record dict for one clientReferenceId, or None."""
        payload = {"RequestInfo": self.req_info(),
                   entity["search_key"]: {"tenantId": self.tenant, "clientReferenceId": [crid]}}
        try:
            r = self.session.post(entity["search_url"], json=payload,
                                  params={"tenantId": self.tenant, "limit": 1, "offset": 0},
                                  headers=API_HDRS, verify=self.verify, timeout=60)
            if r.status_code in (401, 403):
                raise StopRun(f"Auth failure HTTP {r.status_code}")
            if r.status_code == 200:
                records = r.json().get(entity["resp_key"]) or []
                return records[0] if records else None
        except StopRun:
            raise
        except Exception as exc:
            log.warning(f"search_one error for {crid}: {exc}")
        return None

    def search_many(self, entity, crids):
        """Return {clientReferenceId: full record} for a batch of ids in one call.

        Ids not returned are absent from the dict. Raises on a non-200 answer so the
        caller can record why; StopRun on auth failure."""
        crids = list(crids)
        payload = {"RequestInfo": self.req_info(),
                   entity["search_key"]: {"tenantId": self.tenant, "clientReferenceId": crids}}
        r = self.session.post(entity["search_url"], json=payload,
                              params={"tenantId": self.tenant, "limit": len(crids), "offset": 0},
                              headers=API_HDRS, verify=self.verify, timeout=60)
        if r.status_code in (401, 403):
            raise StopRun(f"Auth failure on search HTTP {r.status_code}")
        if r.status_code != 200:
            raise RuntimeError(f"{entity['name']} search HTTP {r.status_code}: {r.text[:200]}")
        return {rec["clientReferenceId"]: rec
                for rec in (r.json().get(entity["resp_key"]) or [])
                if rec.get("clientReferenceId")}

    # Error classification: returns (should_retry, is_duplicate, message)
    @staticmethod
    def classify_error(http_status, resp_text):
        digit_errors = []
        try:
            body = json.loads(resp_text)
            digit_errors = body.get("Errors") or body.get("errors") or []
        except Exception:
            pass
        if digit_errors:
            msg = "; ".join(f"{e.get('code', '?')}: {e.get('message', e.get('description', ''))}"
                            for e in digit_errors)
        else:
            msg = resp_text[:4000]

        if http_status == 409:
            return False, True, msg
        if any(e.get("code", "").upper() in DUPLICATE_CODES for e in digit_errors):
            return False, True, msg
        if http_status in (401, 403):
            raise StopRun(f"Auth failure HTTP {http_status}: {msg}")
        if http_status == 429 or http_status >= 500:
            return True, False, msg
        if 400 <= http_status < 500:
            return False, False, msg
        if digit_errors:
            return False, False, msg
        return False, False, ""

    # Create / update with retry and exponential backoff
    def _write(self, url, body_key, record, ok_status="PUSHED", retry_read_timeout=True):
        """retry_read_timeout=False (creates): a read timeout means HCM may already have the
        record, so it is not re-sent; it is FAILED and the next run's existence check decides.
        A connect timeout never reached HCM and is always retried."""
        record["hasErrors"] = False
        payload = {"RequestInfo": self.req_info(), body_key: record}
        delay = self.retry_delay
        last_err = ""
        for attempt in range(1, self.max_retries + 1):
            try:
                r = self.session.post(url, json=payload, headers=API_HDRS,
                                      verify=self.verify, timeout=60)
                should_retry, is_dup, err = self.classify_error(r.status_code, r.text)
                if r.status_code in (200, 201, 202) and not err:
                    return ok_status, r.status_code, ""
                if is_dup:
                    return "SKIPPED", r.status_code, err
                if should_retry and attempt < self.max_retries:
                    last_err = err
                    time.sleep(delay); delay *= 2
                    continue
                return "FAILED", r.status_code, err
            except StopRun:
                raise
            except requests.exceptions.ConnectTimeout:
                last_err = "Connect timed out"
                if attempt < self.max_retries:
                    time.sleep(delay); delay *= 2
            except requests.exceptions.Timeout:
                if not retry_read_timeout:
                    return "FAILED", 0, ("Request timed out after it was sent - not re-sent, "
                                         "HCM may have it; the next run's existence check decides")
                last_err = "Request timed out"
                if attempt < self.max_retries:
                    time.sleep(delay); delay *= 2
            except requests.exceptions.ConnectionError as exc:
                last_err = f"Connection error: {str(exc)[:200]}"
                if attempt < self.max_retries:
                    time.sleep(delay); delay *= 2
            except Exception as exc:
                return "FAILED", 0, f"Unexpected error: {str(exc)[:200]}"
        return "FAILED", 0, f"Max retries exceeded. Last error: {last_err}"

    def create(self, entity, record):
        return self._write(entity["create_url"], entity["create_key"], record, ok_status="PUSHED",
                           retry_read_timeout=False)

    def update(self, entity, record):
        return self._write(entity["update_url"], entity["update_key"], record, ok_status="UPDATED")
