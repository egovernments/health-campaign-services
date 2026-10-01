"""
error_tracer.py — daily eGov platform error-tracer counts for the INTERNAL Slack post.

Replaces the manual "check the tracer every day" step with an aggregate that rides
along on the daily report: how many error records landed for this tenant today, and
how they break down by errorCode.

INTERNAL ONLY. The line this module produces is attached via
cfg["slack_internal_extra"], which notify.py appends only to the main-channel post —
never to the partner post (the partner post reuses the same slack_text, so anything
routed through slack_text itself would reach partners too).

Index/field shape taken from the verified recon-recovery error tracer
(D:\\DST\\scripts\\recon-recovery\\core\\chain.py + core/es_client.py), NOT guessed:
  - TRACER_INDEX = "egov-tracer-error-details" — GLOBAL, not tenant-prefixed, so a
    tenant filter is mandatory or the count spans every campaign on the cluster.
  - Tenant is NOT a top-level field. It lives inside the raw request body, so the
    tracer scopes by phrase match on Data.apiDetails.requestBody for '"tenantId":"xx"'
    — the same filter chain.py's fallback scroll uses.
  - Documents are Data.-wrapped and the time field is Data.@timestamp (ISO). The
    known "Data-wrapped vs flat @timestamp" trap silently returns 0 on the wrong
    path, which is why the path is pinned here rather than derived.
  - Error code is Data.errors[].errorCode; `errors` is an ARRAY and may contain a
    null element (chain.py guards the same way), so a doc can contribute to more
    than one bucket. Bucket counts therefore need not sum to the document total —
    both numbers are reported as what they are, never reconciled by fudging.

DELIBERATELY NOT INTERPRETED: this prints the codes the cluster actually reported,
aggregated. It does not translate them into severity or "lost dose" language — the
Sep-2026 tracer RCA established that the same code means different things in
different contexts (NON_EXISTENT_ENTITY was retry-storm noise with 29/29 records
persisted), so a severity claim computed from the code alone would be wrong as
often as right. Note the analyst shorthand NEE/IREI/NERE never appears in the data;
the stored codes are NON_EXISTENT_ENTITY, INVALID_RELATED_ENTITY_ID and
NON_EXISTENT_RELATED_ENTITY.

ALSO NOT A HEALTH GUARANTEE: the most damaging variant found in that RCA emits no
tracer error at all. A zero here means "nothing was reported", never "nothing went
wrong", and the zero-case wording says exactly that.

KNOWN UNDERCOUNT — tracer indexing is not real-time (5h+ lag observed during the
Chad SMC beneficiary-id RCA). A report generated at 17:00 will therefore miss part
of the same day's errors; the count is a same-day indicator, and the true daily
figure only settles later. Do not treat a low number as a closed day.
"""
import logging
import os
from datetime import datetime, timedelta, timezone

import requests
import urllib3

urllib3.disable_warnings()
log = logging.getLogger(__name__)

# Global (un-prefixed) tracer index — override per deployment if a cluster renames it.
TRACER_INDEX_DEFAULT = "egov-tracer-error-details"

# Off by default: enabling it adds one ES query and one Slack block to the internal
# post. FALSE keeps every existing byte of output identical.
ERROR_TRACER_DEFAULT = "FALSE"

# Distinct errorCode buckets to list. Anything beyond this is reported as an
# explicit "+N more" rather than silently dropped (terms-agg truncation is a
# standing gotcha in this codebase).
_MAX_CODES = 15

# Endpoints listed alongside the codes (which API is producing the errors is more
# actionable than the code alone). Kept short — this is a pointer, not a report.
_MAX_URLS = 3

# Neutral one-line gloss per known errorCode, so nobody has to decode the constant.
# DELIBERATELY LITERAL RESTATEMENTS, NOT DIAGNOSES: the Sep-2026 RCA proved the same
# code means different things run to run (NON_EXISTENT_RELATED_ENTITY was mostly
# double-coded overlap whose doses persisted). Anything here that implied severity
# or data loss would be wrong as often as right. Unknown codes print bare — an
# unglossed code is honest, an invented gloss is not.
# Codes that normally mean "the record is already saved" — the offline app
# re-sending after a sync retry. Split out of the headline so a few hundred
# benign retries cannot bury the handful of errors that need a human.
#
# NOT suppressed, only separated, and deliberately so: duplicates on the
# project-beneficiary endpoint are the duplicate-enrolment signal the Sep-2026
# RCA asked to be monitored (the PB-duplicate cascade was the mechanism behind
# the first verified lost dose). They stay visible on the endpoint line.
_RETRY_CODES = {"DUPLICATE_ENTITY"}

_CODE_GLOSS = {
    "UPLOAD_ERROR_FROM_APP":        "app reported it could not upload the record",
    "NON_EXISTENT_ENTITY":          "server had no matching record",
    "NON_EXISTENT_RELATED_ENTITY":  "a linked record was not found",
    "INVALID_RELATED_ENTITY_ID":    "a linked record id was rejected",
    "INVALID_ID":                   "id was rejected or could not be issued",
}


def _enabled(cfg):
    val = (str(cfg.get("error_tracer", "") or "").strip()
           or os.getenv("DST_ERROR_TRACER", "").strip()
           or ERROR_TRACER_DEFAULT)
    return str(val).upper() != "FALSE"


def _tracer_index():
    return os.getenv("DST_TRACER_INDEX", "").strip() or TRACER_INDEX_DEFAULT


def _tracer_es(cfg):
    """
    Tracer connection. The tracer index does NOT always live on the same cluster
    as the campaign indices — verified: ba/so/zf/ch tracer data sits on one
    cluster while the chad ITN tracer sits on another. So the URL and auth are
    independently overridable; unset, they fall back to the campaign cluster.
    Credentials come from the environment only — never hardcoded here.
    """
    url = os.getenv("DST_TRACER_ES_URL", "").strip() or cfg.get("es_url")
    user = os.getenv("DST_TRACER_ES_USER", "").strip()
    pwd = os.getenv("DST_TRACER_ES_PASS", "")
    auth = (user, pwd) if user else cfg.get("es_auth")
    return url, auth


def _aggregate(cfg, gte, lte):
    """
    Return (total_error_docs, [(errorCode, count), ...], truncated_count) for this
    tenant between the ISO bounds gte/lte, or None when the query could not be run
    — None means "not measured" and is rendered as such, never as a zero.
    """
    tenant = str(cfg.get("tenant", "")).strip()
    if not tenant:
        log.warning("[error_tracer] no tenant in cfg — skipping")
        return None

    body = {
        "size": 0,
        "query": {"bool": {"filter": [
            # Tenant lives inside the raw request body on this index (see docstring).
            {"match_phrase": {"Data.apiDetails.requestBody": f'"tenantId":"{tenant}"'}},
            {"range": {"Data.@timestamp": {"gte": gte, "lte": lte}}},
        ]}},
        "aggs": {
            "by_code": {"terms": {
                "field": "Data.errors.errorCode.keyword",
                "size": _MAX_CODES,
            }},
            # Documents carrying NO errorCode at all, counted directly rather than
            # derived as (total - sum of buckets): that subtraction is distorted by
            # docs holding two codes, which land in two buckets each. Same query,
            # so this costs nothing.
            "no_code": {"missing": {"field": "Data.errors.errorCode.keyword"}},
            # Which endpoint is producing them, and the dominant code within each —
            # the cross-tab is what turns "312 errors" into somewhere to look.
            "by_url": {
                "terms": {"field": "Data.apiDetails.url.keyword", "size": _MAX_URLS},
                "aggs": {"top_code": {"terms": {
                    "field": "Data.errors.errorCode.keyword", "size": 1,
                }}},
            },
        },
    }
    url, auth = _tracer_es(cfg)
    try:
        r = requests.post(
            f"{url}/{_tracer_index()}/_search",
            json=body, auth=auth, verify=False, timeout=60,
        )
        r.raise_for_status()
        data = r.json()
    except Exception as e:
        # Non-fatal by design: a tracer outage must never take down the daily report.
        log.warning(f"[error_tracer] query failed (non-fatal): {e}")
        return None

    total = (data.get("hits", {}).get("total") or {}).get("value", 0)
    aggs = data.get("aggregations", {}) or {}
    agg = aggs.get("by_code") or {}
    buckets = [(b.get("key") or "UNSPECIFIED", b.get("doc_count", 0))
               for b in agg.get("buckets", [])]

    url_agg = aggs.get("by_url") or {}
    urls = []
    for b in url_agg.get("buckets", []):
        top = ((b.get("top_code") or {}).get("buckets") or [{}])[0].get("key") or ""
        urls.append((b.get("key") or "", b.get("doc_count", 0), top))

    # Endpoints beyond the top _MAX_URLS. Reported, never dropped silently —
    # showing 3 of 10 without saying so misrepresents where the errors are.
    url_other = int(url_agg.get("sum_other_doc_count", 0) or 0)

    no_code = int((aggs.get("no_code") or {}).get("doc_count", 0) or 0)

    return (total, buckets, int(agg.get("sum_other_doc_count", 0) or 0),
            urls, url_other, no_code)


def _windows(day_iso):
    """
    Two comparable windows: today 00:00 -> now, and yesterday 00:00 -> the SAME
    clock time.

    Why not yesterday's full day: tracer indexing lags several hours, so today's
    count is always partial. Measured against a complete yesterday, every day
    would look like an improvement — a bias that would hide real spikes. Cutting
    yesterday at the same time of day removes most of that.

    Residual bias, stated rather than hidden: yesterday's window has since been
    fully indexed while today's is still filling in, so today still reads low.
    A spike is therefore trustworthy; an apparent drop may be lag, which is why
    the wording never claims improvement.
    """
    now = datetime.now(timezone.utc)
    clock = now.strftime("%H:%M:%S")
    try:
        prev_iso = (datetime.strptime(day_iso, "%Y-%m-%d").date() - timedelta(days=1)).isoformat()
    except ValueError:
        return (f"{day_iso}T00:00:00.000Z", f"{day_iso}T23:59:59.999Z"), None
    return (
        (f"{day_iso}T00:00:00.000Z", f"{day_iso}T{clock}.000Z"),
        (f"{prev_iso}T00:00:00.000Z", f"{prev_iso}T{clock}.000Z"),
    )


def _trend_phrase(total, prev):
    """Short inline trend, e.g. "up 6.5x vs yesterday (48)". "" when unmeasurable."""
    if prev is None:
        return ""
    if prev == 0:
        return "vs 0 yesterday"
    if total == 0:
        return f"vs {prev:,} yesterday"
    ratio = total / prev
    if ratio >= 1.15:
        return f"up {ratio:.1f}x vs yesterday ({prev:,})"
    if ratio <= 0.87:
        return f"down {prev / total:.1f}x vs yesterday ({prev:,})"
    return f"about the same as yesterday ({prev:,})"


def _cumulative_line(cfg, cum_records=None):
    """
    Campaign-to-date errors, against cumulative records when the caller supplies
    them. "" when the campaign start is unknown or the query fails — a missing
    cumulative line never costs the day's own numbers.

    Window runs campaign_start -> now, so unlike today's figure this one is
    effectively complete: the indexing lag only affects the last few hours of a
    span measured in weeks.
    """
    start = cfg.get("campaign_start")
    if not start:
        return ""
    start_iso = start.isoformat() if hasattr(start, "isoformat") else str(start)
    now_iso = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")

    res = _aggregate(cfg, f"{start_iso}T00:00:00.000Z", now_iso)
    if res is None:
        return ""
    cum_total = res[0]

    try:
        cum_rec = int(cum_records or 0)
    except (TypeError, ValueError):
        cum_rec = 0
    if cum_rec:
        return (f"Campaign to date: {cum_total:,} errors against "
                f"{cum_rec:,} records ({cum_total / cum_rec * 100:.1f}%)")
    return f"Campaign to date: {cum_total:,} errors"


def tracer_summary_block(cfg, records=None, cum_records=None):
    """
    Deterministic Slack block for the internal post — returns "" when disabled.

    `records` = the day's submitted-record count from the report, used only as the
    denominator of the headline rate so the number reads in proportion at a glance
    ("312 errors against 4,348 records") instead of as a bare count nobody can size.

    SCOPE HONESTY: the two numbers come from different populations. `records` counts
    this campaign's task documents that DID land; the tracer counts API errors for
    the whole tenant, which can include other entities and other campaigns on the
    same tenant. So the rate is an indicator, not a subset percentage — hence the
    trailing scope line, and hence "against" rather than "of".

    Deliberately NOT routed through the LLM narrative: these are the numbers most
    likely to be editorialised into a false alarm, and the exact code names must
    survive verbatim for anyone cross-checking against the tracer itself.
    """
    if not _enabled(cfg):
        return ""

    day = cfg.get("extract_date")
    day_iso = day.isoformat() if hasattr(day, "isoformat") else str(day)
    tenant = str(cfg.get("tenant", "")).strip()
    today_win, prev_win = _windows(day_iso)
    res = _aggregate(cfg, *today_win)
    if res is None:
        return f"*Error tracer ({tenant}):* not measured (query failed)."

    # Yesterday is a second, independent query: a failure here costs the trend
    # line only, never the day's own numbers.
    prev_total = None
    if prev_win:
        prev_res = _aggregate(cfg, *prev_win)
        if prev_res is not None:
            prev_total = prev_res[0]
    trend = _trend_phrase(res[0], prev_total)

    total, buckets, other, urls, url_other, no_code = res
    if not total:
        # Never phrase a zero as an all-clear: the most damaging failure mode found
        # in the Sep-2026 RCA raises no tracer error at all, so this says what was
        # reported, not that nothing went wrong. Always one line.
        # "logged", not "no errors occurred": the silent failure mode raises
        # nothing at all. One word carries the caveat, so no sentence is needed.
        tail = f" ({trend})" if trend else ""
        return f"*Error tracer ({tenant}):* nothing logged today{tail}."

    try:
        rec = int(records or 0)
    except (TypeError, ValueError):
        rec = 0
    # Name the denominator: a bare "7.2% of 4,348" leaves the reader guessing what
    # 4,348 counts. It is the campaign's submitted records for the day, the same
    # figure the summary paragraph above already quotes.
    rate = f" ({total / rec * 100:.1f}% of {rec:,} records submitted)" if rec else ""
    tail = f" — {trend}" if trend else ""

    # Retry duplicates split out of the headline. On real Kogi data these were
    # 435 of 602 — benign re-sends burying the 3 errors that needed a look.
    dup = sum(n for code, n in buckets if code in _RETRY_CODES)
    split = f" — {dup:,} duplicate re-sends, {total - dup:,} other" if dup else ""

    # ONE standard shape every day, quiet or not: headline, cumulative, codes,
    # endpoints. No quiet/spike variants — a block that changes form is one the
    # reader has to re-parse each time.
    lines = [f"*Error tracer ({tenant}):* {total:,} errors today{rate}{split}{tail}"]

    # Campaign to date, so the day's number has a scale to sit against — the same
    # cumulative framing the summary paragraph above uses for coverage.
    cum = _cumulative_line(cfg, cum_records)
    if cum:
        lines.append(cum)

    # All codes on one line. The reconciliation gap (docs with no parsed
    # errorCode) is shown as "N uncoded" rather than dropped — the counts
    # genuinely do not sum to the total, and hiding that would make the
    # arithmetic look clean when it is not. Codes can also overlap on a single
    # doc (NON_EXISTENT_RELATED_ENTITY and INVALID_RELATED_ENTITY_ID are often
    # double-coded), so these must never be added together.
    parts = [f"{code} {n:,}" for code, n in buckets]
    if other:
        parts.append(f"+{other:,} other types")
    if no_code:
        parts.append(f"{no_code:,} with no code")
    if parts:
        lines.append("Codes: " + ", ".join(parts))

    # Which endpoints produced them, each with its dominant code — says where to
    # look AND under what. "mostly" is deliberate: the sub-aggregation returns the
    # TOP code only, so an endpoint with mixed codes would otherwise read as if
    # every one of its errors were that single code.
    url_parts = [f"{url} {n:,}" + (f" (mostly {top})" if top else "")
                 for url, n, top in urls if url]
    if url_other:
        url_parts.append(f"+{url_other:,} on other endpoints")
    if url_parts:
        lines.append("Endpoints: " + ", ".join(url_parts))

    return "\n".join(lines)


def attach(cfg, records=None, cum_records=None):
    """
    Compute the block and hang it on cfg for notify.py's internal-only branch.
    Safe to call unconditionally: disabled or failing, it leaves cfg untouched
    except for an explicit "not measured" note.
    """
    try:
        block = tracer_summary_block(cfg, records=records, cum_records=cum_records)
    except Exception as e:
        log.warning(f"[error_tracer] block build failed (non-fatal): {e}")
        return
    if block:
        cfg["slack_internal_extra"] = block
        log.info(f"[error_tracer] internal Slack block attached ({len(block)} chars)")
