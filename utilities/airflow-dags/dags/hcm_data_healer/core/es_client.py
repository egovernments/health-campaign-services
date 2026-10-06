"""
Elasticsearch client for the reconciliation scan and tracer flagging.

- load_ids / fetch_details: per-entity index reads for the reconciliation check.
- flag_tracer_processed: mark egov-tracer docs Data.isProcessed=true.

Tracer payload recovery lives in core/chain.py.
"""

import base64
import os
import time
import warnings
from concurrent.futures import ThreadPoolExecutor

import pandas as pd
import requests
from requests.adapters import HTTPAdapter

from hcm_data_healer.core.utils.ids import pack

warnings.filterwarnings("ignore", message="Unverified HTTPS request is being made.*")

TRACER_INDEX = "egov-tracer-error-details"

# Id-only scroll pages are small, so a large page size is cheap.
ID_SCROLL_SIZE = 10000


class ESClient:
    def __init__(self, base_url, username, password, verify_ssl=False, batch_size=5000):
        self.base = base_url.rstrip("/")
        token = base64.b64encode(f"{username}:{password}".encode()).decode()
        self.headers = {"Content-Type": "application/json", "Authorization": f"Basic {token}"}
        self.verify = bool(verify_ssl)
        self.batch = int(batch_size)
        self.scroll_url = f"{self.base}/_search/scroll"
        # Keep-alive session avoids a TLS handshake per page; pool sized for
        # the chain phase's concurrent scrolls.
        self.session = requests.Session()
        adapter = HTTPAdapter(pool_connections=20, pool_maxsize=20)
        self.session.mount("http://", adapter)
        self.session.mount("https://", adapter)
        self.session.headers.update(self.headers)

    # Low-level helpers
    def _post(self, url, payload, timeout=120):
        last = None
        for attempt in range(3):
            if attempt:
                time.sleep(2 ** attempt * 5)       # 10s, then 20s
            try:
                r = self.session.post(url, json=payload, verify=self.verify, timeout=timeout)
                if r.status_code == 200:
                    return r.json()
                last = f"HTTP {r.status_code}: {r.text[:200]}"
            except Exception as exc:
                last = str(exc)
        raise RuntimeError(f"ES call failed after 3 attempts: {url} ({last})")

    def _scroll(self, search_url, query, callback):
        """Pass every hit page to `callback`; a truthy return stops early.

        Raises if any shard fails, since partial results would create false gaps."""
        sid = None
        try:
            while True:
                data = (self._post(search_url + "?scroll=10m", query) if sid is None
                        else self._post(self.scroll_url, {"scroll": "10m", "scroll_id": sid}))
                sid = data.get("_scroll_id")
                # Shard failures still return HTTP 200, just with fewer hits.
                failed = (data.get("_shards") or {}).get("failed", 0)
                if failed:
                    raise RuntimeError(f"ES scroll on {search_url} had {failed} failed shard(s) "
                                       f"- results incomplete, refusing to use them")
                hits = data.get("hits", {}).get("hits", [])
                if not hits:
                    break
                if callback(hits):
                    break
        finally:
            if sid:
                try:
                    self.session.delete(self.scroll_url, json={"scroll_id": sid},
                                        verify=self.verify, timeout=10)
                except Exception:
                    pass

    def _sliced_scroll(self, search_url, query, callback, slices=4):
        """Run the scroll as N parallel slices. The callback must be thread-safe."""
        if slices <= 1:
            return self._scroll(search_url, query, callback)

        def one(i):
            q = dict(query)
            q["slice"] = {"id": i, "max": slices}
            self._scroll(search_url, q, callback)

        with ThreadPoolExecutor(max_workers=slices) as ex:
            for f in [ex.submit(one, i) for i in range(slices)]:
                f.result()                        # re-raise slice failures

    @staticmethod
    def _extract(source, field_path):
        val = source
        for key in field_path.split("."):
            val = val.get(key) if isinstance(val, dict) else None
            if val is None:
                return None
        return val

    def _date_filter(self, entity, gte_iso, lte_iso, gte_ms, lte_ms):
        field = entity["es_date_field"]
        mode = entity.get("es_date_mode", "iso")
        if mode == "ms":            # epoch ms
            return {"range": {field: {"gte": gte_ms, "lte": lte_ms}}}
        if mode == "date":          # YYYY-MM-DD string
            return {"range": {field: {"gte": gte_iso[:10], "lte": lte_iso[:10]}}}
        return {"range": {field: {"gte": gte_iso, "lte": lte_iso}}}   # ISO timestamp

    # Reconciliation: id set
    def load_ids(self, entity, gte_iso, lte_iso, gte_ms, lte_ms, progress=None):
        url = f"{self.base}/{entity['es_index']}/_search"
        # Match the DB population: same window, tenant, not deleted.
        filters = [self._date_filter(entity, gte_iso, lte_iso, gte_ms, lte_ms)]
        tenant_field = entity.get("es_tenant_field")
        if tenant_field:
            filters.append({"term": {tenant_field: entity["tenant"]}})
        deleted_field = entity.get("es_deleted_field")
        if deleted_field:
            filters.append({"term": {deleted_field: False}})
        filters += entity["extra_es_filter"]
        query = {
            "size": ID_SCROLL_SIZE,
            "query": {"bool": {"filter": filters}},
            "_source": [entity["es_id_field"]],
        }
        ids = set()
        field = entity["es_id_field"]
        counter = {"n": 0}

        def cb(hits):
            for hit in hits:
                val = self._extract(hit.get("_source", {}), field)
                if val:
                    ids.add(pack(val))   # compact key, see utils/ids.py
            counter["n"] += len(hits)
            if progress:
                progress(counter["n"], len(ids))

        self._scroll(url, query, cb)
        return ids

    # Reconciliation: full documents for the given ids
    def fetch_details(self, entity, ids, progress=None):
        ids = [i for i in ids if i]
        if not ids:
            return pd.DataFrame()
        url = f"{self.base}/{entity['es_index']}/_search"
        id_field = entity["es_id_field"]
        rows = []
        for i in range(0, len(ids), 3000):
            chunk = ids[i:i + 3000]
            q = {
                "size": len(chunk),
                "_source": True,
                "query": {"bool": {"filter": [{"terms": {f"{id_field}.keyword": chunk}}]}},
            }
            data = self._post(url, q)
            for hit in data.get("hits", {}).get("hits", []):
                rows.append(hit.get("_source", {}))
            if progress:
                progress(min(i + 3000, len(ids)), len(ids))
        return pd.json_normalize(rows) if rows else pd.DataFrame()

    # Tracer flagging (the ES credential needs update rights)
    def flag_tracer_processed(self, doc_ids, progress=None):
        """Set Data.isProcessed=true on tracer docs matched by ES _id or Data.uuid.

        Returns the number updated (0 for empty input). Uses conflicts=proceed
        and no forced refresh.
        """
        ids = [str(d) for d in (doc_ids or []) if d]
        if not ids:
            return 0
        url = f"{self.base}/{TRACER_INDEX}/_update_by_query"
        script = {"source": "ctx._source.Data.isProcessed = true", "lang": "painless"}
        updated = 0
        for i in range(0, len(ids), 500):
            batch = ids[i:i + 500]
            # Try Data.uuid.keyword first; on HTTP 400 (no keyword subfield) fall
            # back to a mapping-agnostic match_phrase query.
            queries = [
                {"bool": {"should": [
                    {"ids": {"values": batch}},
                    {"terms": {"Data.uuid.keyword": batch}},
                ], "minimum_should_match": 1}},
                {"bool": {"should": [{"ids": {"values": batch}}]
                          + [{"match_phrase": {"Data.uuid": u}} for u in batch],
                          "minimum_should_match": 1}},
            ]
            ok, last = False, ""
            for q in queries:
                r = self.session.post(url, params={"conflicts": "proceed"},
                                      json={"script": script, "query": q},
                                      verify=self.verify, timeout=300)
                if r.status_code in (401, 403):
                    # Read-only credential; the caller treats this as non-fatal.
                    raise RuntimeError(
                        f"ES credential lacks write/update access to {TRACER_INDEX} "
                        f"(HTTP {r.status_code}) - isProcessed not set. Use an ES user "
                        f"with update rights on the tracer index.")
                if r.status_code == 200:
                    body = r.json()
                    updated += body.get("updated", 0)
                    fails = body.get("failures") or []
                    if fails:
                        raise RuntimeError(
                            f"_update_by_query reported {len(fails)} failure(s); "
                            f"first: {str(fails[0])[:200]}")
                    ok = True
                    break
                last = f"HTTP {r.status_code}: {r.text[:200]}"
                if r.status_code != 400:
                    break        # only a 400 warrants the fallback query
            if not ok:
                raise RuntimeError(f"flag_tracer_processed failed: {last}")
            if progress:
                progress(min(i + 500, len(ids)), len(ids))
        return updated

    def test_connection(self):
        try:
            r = self.session.get(f"{self.base}/_cluster/health",
                                 verify=self.verify, timeout=30)
            if r.status_code == 200:
                return True, f"ES OK ({r.json().get('status', '?')})"
            return False, f"ES HTTP {r.status_code}: {r.text[:150]}"
        except Exception as exc:
            return False, f"ES connection failed: {exc}"
