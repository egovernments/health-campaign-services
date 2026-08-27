"""Elasticsearch access helpers shared by all pipeline modules."""
import logging

import requests
import urllib3

urllib3.disable_warnings()
log = logging.getLogger(__name__)

TIMEOUT = 120


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
        r = requests.post(f"{url}/{index}/_search?scroll=10m",
                          json=query, auth=auth, verify=False, timeout=TIMEOUT)
        r.raise_for_status()
        data = r.json()
        sid = data["_scroll_id"]
        batch = data["hits"]["hits"]
        hits.extend(batch)
        total = data["hits"]["total"]["value"]
        log.info(f"  {label}: ~{total:,} docs, fetching ...")
        while batch:
            r = requests.post(f"{url}/_search/scroll",
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
        r = requests.post(f"{url}/{index}/_search?scroll=10m",
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
            r = requests.post(f"{url}/_search/scroll",
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
        r = requests.post(f"{url}/{index}/_search",
                          json=q, auth=auth, verify=False, timeout=TIMEOUT)
        r.raise_for_status()
        data = r.json()
        page = data["aggregations"]["combo"]["buckets"]
        buckets.extend(page)
        after = data["aggregations"]["combo"].get("after_key")
        if not after:
            break
    return buckets
