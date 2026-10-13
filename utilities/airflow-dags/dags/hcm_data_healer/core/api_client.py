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
from datetime import datetime, timezone

import requests

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
    def __init__(self, tenant, auth_token, user_uuid, options=None, verify_ssl=False):
        self.tenant = tenant
        self.auth_token = auth_token
        self.user_uuid = user_uuid
        self.verify = bool(verify_ssl)
        opts = options or {}
        self.max_retries = int(opts.get("max_retries", 3))
        self.retry_delay = int(opts.get("retry_delay", 2))
        self.search_batch = int(opts.get("search_batch", 50))

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
    def check_exists(self, entity, ids):
        """Return the clientReferenceIds that already exist, via the search API.

        Fails closed: any non-200 or exception raises ExistenceCheckFailed."""
        found = set()
        ids = list(ids)
        for i in range(0, len(ids), self.search_batch):
            chunk = ids[i:i + self.search_batch]
            payload = {"RequestInfo": self.req_info(),
                       entity["search_key"]: {"tenantId": self.tenant, "clientReferenceId": chunk}}
            try:
                r = requests.post(entity["search_url"], json=payload,
                                  params={"tenantId": self.tenant, "limit": self.search_batch, "offset": 0},
                                  headers=API_HDRS, verify=self.verify, timeout=60)
                if r.status_code in (401, 403):
                    raise StopRun(f"Auth failure on search HTTP {r.status_code}")
                if r.status_code != 200:
                    raise ExistenceCheckFailed(f"{entity['name']} search HTTP {r.status_code}: "
                                               f"{r.text[:200]}")
                for rec in (r.json().get(entity["resp_key"]) or []):
                    crid = rec.get("clientReferenceId", "")
                    if crid:
                        found.add(crid)
            except (StopRun, ExistenceCheckFailed):
                raise
            except Exception as exc:
                raise ExistenceCheckFailed(f"{entity['name']} search chunk "
                                           f"{i // self.search_batch + 1} failed: {exc}") from exc
        return found

    def search_one(self, entity, crid):
        """Return the full record dict for one clientReferenceId, or None."""
        payload = {"RequestInfo": self.req_info(),
                   entity["search_key"]: {"tenantId": self.tenant, "clientReferenceId": [crid]}}
        try:
            r = requests.post(entity["search_url"], json=payload,
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
    def _write(self, url, body_key, record, ok_status="PUSHED"):
        record["hasErrors"] = False
        payload = {"RequestInfo": self.req_info(), body_key: record}
        delay = self.retry_delay
        last_err = ""
        for attempt in range(1, self.max_retries + 1):
            try:
                r = requests.post(url, json=payload, headers=API_HDRS,
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
            except requests.exceptions.Timeout:
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
        return self._write(entity["create_url"], entity["create_key"], record, ok_status="PUSHED")

    def update(self, entity, record):
        return self._write(entity["update_url"], entity["update_key"], record, ok_status="UPDATED")
