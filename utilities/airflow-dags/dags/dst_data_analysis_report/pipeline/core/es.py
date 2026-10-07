"""Elasticsearch access helpers shared by all pipeline modules."""
import logging
import os
import threading
import time

import requests
import urllib3

urllib3.disable_warnings()
log = logging.getLogger(__name__)

TIMEOUT = 120

# ES load guard. Every ES request in the pipeline goes through post() so a
# small cluster (Taraba) can be protected without touching any query:
#   DST_ES_MAX_CONCURRENT  max ES requests in flight per run (unset = no cap;
#                          the name lookups otherwise fire 8 threads at once)
#   DST_ES_THROTTLE_MS     pause after every ES request (unset = 0)
#   DST_ES_PAGE_SIZE       docs per scroll page / ids per terms lookup
#                          (unset = 5000; read when analyze is imported, which
#                          happens inside the run, after dst_config.apply())
# The first two are read per call, so dst_config.apply() setting them before
# the run is enough. 429/503 (ES saying it is overloaded) is always retried with back-off
# instead of failing the task, because an Airflow retry re-runs the WHOLE
# extract and multiplies the load it was meant to relieve.
_RETRY_STATUSES = {429, 503}
_BACKOFF_SECONDS = (10, 30, 60)
_gate_lock = threading.Lock()
_gate = {"size": None, "sem": None}


def _env_int(name):
    try:
        return max(int(os.getenv(name, "").strip() or 0), 0)
    except ValueError:
        log.warning(f"{name}={os.getenv(name)!r} is not a whole number — ignored")
        return 0


def page_size(default):
    """DST_ES_PAGE_SIZE if set (clamped to 100..10000, ES's window), else default."""
    size = _env_int("DST_ES_PAGE_SIZE")
    return min(max(size, 100), 10000) if size else default


def _semaphore():
    size = _env_int("DST_ES_MAX_CONCURRENT")
    if not size:
        return None
    with _gate_lock:
        if _gate["size"] != size:
            _gate["size"], _gate["sem"] = size, threading.BoundedSemaphore(size)
        return _gate["sem"]


def post(url, **kwargs):
    """requests.post for ES, behind the load guard (cap, throttle, back-off)."""
    sem = _semaphore()
    pause = _env_int("DST_ES_THROTTLE_MS") / 1000
    for attempt in range(len(_BACKOFF_SECONDS) + 1):
        if sem:
            sem.acquire()
        try:
            r = requests.post(url, **kwargs)
        finally:
            if sem:
                sem.release()
        if pause:
            time.sleep(pause)
        if r.status_code not in _RETRY_STATUSES or attempt == len(_BACKOFF_SECONDS):
            return r
        wait = _BACKOFF_SECONDS[attempt]
        log.warning(f"ES returned {r.status_code} (overloaded) — backing off "
                    f"{wait}s before retry {attempt + 1}/{len(_BACKOFF_SECONDS)}")
        time.sleep(wait)


def _release_scroll(url, sid, auth):
    """Release a scroll context, never masking the real failure.

    An unguarded requests.delete in a `finally` REPLACES the exception that is
    already propagating: if the cluster went away mid-scroll, the useful message
    (401, index_not_found, a mapping error) is lost and the caller sees a bare
    ConnectionError instead. That also erases the HTTP status campaign_runner
    reads to decide fail-fast versus retry, so a permanent 404 gets retried
    twice as though it were transient.
    """
    try:
        requests.delete(f"{url}/_search/scroll",
                        json={"scroll_id": sid}, auth=auth, verify=False,
                        timeout=30)
    except Exception as e:                                        # noqa: BLE001
        log.warning(f"could not release the scroll context (harmless; it "
                    f"expires on its own in 10m): {e}")


def scroll_all(url, index, query, auth, label):
    """Scroll an index and return every hit. Use scroll_batches for very large datasets."""
    hits = []
    sid = None
    try:
        r = post(f"{url}/{index}/_search?scroll=10m",
                          json=query, auth=auth, verify=False, timeout=TIMEOUT)
        r.raise_for_status()
        data = r.json()
        sid = data["_scroll_id"]
        batch = data["hits"]["hits"]
        hits.extend(batch)
        total = data["hits"]["total"]["value"]
        log.info(f"  {label}: ~{total:,} docs, fetching ...")
        while batch:
            r = post(f"{url}/_search/scroll",
                              json={"scroll": "10m", "scroll_id": sid},
                              auth=auth, verify=False, timeout=TIMEOUT)
            r.raise_for_status()
            data = r.json()
            sid = data["_scroll_id"]
            batch = data["hits"]["hits"]
            hits.extend(batch)
            if len(hits) % 50_000 < len(batch):
                log.info(f"  {label}: {len(hits):,} / ~{total:,} fetched ...")
    finally:
        if sid:
            _release_scroll(url, sid, auth)
    log.info(f"  {label}: {len(hits):,} docs fetched")
    return hits


def scroll_batches(url, index, query, auth, label):
    """Yield one scroll page at a time without accumulating hits in memory."""
    sid = None
    try:
        r = post(f"{url}/{index}/_search?scroll=10m",
                          json=query, auth=auth, verify=False, timeout=TIMEOUT)
        r.raise_for_status()
        data = r.json()
        sid = data["_scroll_id"]
        batch = data["hits"]["hits"]
        total = data["hits"]["total"]["value"]
        log.info(f"  {label}: ~{total:,} docs, streaming in batches ...")
        processed = 0
        while batch:
            yield batch
            processed += len(batch)
            if processed % 100_000 < len(batch):
                log.info(f"  {label}: {processed:,} / ~{total:,} streamed ...")
            r = post(f"{url}/_search/scroll",
                              json={"scroll": "10m", "scroll_id": sid},
                              auth=auth, verify=False, timeout=TIMEOUT)
            r.raise_for_status()
            data = r.json()
            sid = data["_scroll_id"]
            batch = data["hits"]["hits"]
    finally:
        if sid:
            _release_scroll(url, sid, auth)


def composite_agg(url, index, query_must, agg_sources, auth):
    """Paginate a composite aggregation and return all buckets."""
    buckets = []
    after = None
    while True:
        agg_body = {"size": 1000, "sources": agg_sources}
        if after:
            agg_body["after"] = after
        q = {
            "size": 0,
            "query": {"bool": {"must": query_must}},
            "aggs": {"combo": {"composite": agg_body}},
        }
        r = post(f"{url}/{index}/_search",
                          json=q, auth=auth, verify=False, timeout=TIMEOUT)
        r.raise_for_status()
        data = r.json()
        page = data["aggregations"]["combo"]["buckets"]
        buckets.extend(page)
        after = data["aggregations"]["combo"].get("after_key")
        if not after:
            break
    return buckets
