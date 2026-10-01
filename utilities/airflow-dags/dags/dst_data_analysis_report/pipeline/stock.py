"""stock.py — OPTIONAL stock / supply-chain stage (self-contained add-on).

READING THIS FILE
    It is long because the fleet has four different stock data models, but you
    almost never need all of it. Start with whichever applies:

      "what does column X mean?"      -> NG_LEGS, the query catalogue
      "where does the arithmetic happen?" -> _ledger_maths (one function)
      "what query actually ran?"      -> set DST_STOCK_LOG_QUERY=TRUE
      Nigeria SMC ledger              -> _collect_smc_ng
      Chad / AZM ledger               -> _collect_smc
      ITN / LLIN                      -> _collect_itn
      per-day tab                     -> _collect_daily_flow
      per-CDD audit                   -> _collect_cdd_accountability*

    VOCABULARY — the ledger's own column titles, in snake_case. Nothing new is
    invented here: read a formula and you are reading the row it produces.
    Stock moving DOWN the chain is "sent"; stock coming BACK is "returned",
    and the two words are never mixed.

      variable                     ledger column
      ---------------------------  ------------------------------------------
      sent_by_state_to_hf          Sent by State to HF
      received_by_hf_from_state    Received by HF from State
      rejected_by_hf               Rejected by HF
      in_transit_state_to_hf       In Transit from State to HF

      sent_by_hf_to_cdd            (not printed — see below)
      accepted_by_cdd              (not printed — see below)
      rejected_by_cdd              Rejected by CDD
      in_transit_hf_to_cdd         In Transit from HF to CDD

      returned_by_cdd_to_hf        Returned by CDD to HF
      return_received_by_hf        Return Received by HF
      return_rejected_by_hf        Return Rejected by HF

      returned_by_hf_to_state      Returned by HF to State
      return_received_by_state     Return Received by State
      return_rejected_by_state     Return Rejected by State

    THE TWO THAT ARE NOT COLUMNS, and why. Both count handover RECORDS, in
    which a dose handed out, returned, and handed out again appears twice:
      sent_by_hf_to_cdd  ->  stock_given_to_cdds  ("Stock Given to CDDs")
      accepted_by_cdd    ->  received_by_cdd      ("Received by CDD")
    _ledger_maths removes what came back, so every PRINTED column counts each
    physical dose exactly once.

    THE ONE THING THAT CONFUSES EVERYONE: the issue records can add up to MORE
    than the facility ever received, because stock a CDD hands back gets issued
    again. The printed "given" column subtracts the returns so each physical
    dose is counted once. See _ledger_maths.


Airflow copy of the DST automation repo's pipeline/stock.py, kept in step
with it (only the imports differ). The ES scroll, the openpyxl styles and
the index-name derivation stay inlined so the two copies diff cleanly; the
docx helpers come from core.word. common/campaign_runner calls
stock.run(cfg) after cdd_sync (guarded, non-fatal, degrades the run on
failure), and report.py / report_itn.py call build_stock_section(doc, cfg,
...) right before their Conclusion when cfg["stock_data"] exists.

ON AIRFLOW the switches resolve, first match wins:
  1. the Google Sheet cell stock_report / itn_scanner (per campaign,
     reaching the task via the row in dag_run.conf — through MDMS in mdms
     mode, it is still the sheet's value)
  2. the dst_config Airflow Variable's env block (DST_STOCK_REPORT /
     DST_STOCK_ITN_SCANNER) — dst_config.apply() puts it in os.environ for
     the task, OVER the pod's own environment
  3. the pod / process environment (same keys)
  4. the in-code defaults below (stock ON, ITN scanner OFF)

Feature switches (first match wins):
  1. the sheet cell stock_report / itn_scanner         (per campaign)
  2. the DST_STOCK_REPORT / DST_STOCK_ITN_SCANNER env  (per deployment)
  3. STOCK_REPORT_DEFAULT = TRUE, STOCK_ITN_SCANNER_DEFAULT = FALSE below
Off = stock.run returns None immediately and no report output changes at all.

What it reports — the fleet has FOUR distinct stock data models and this
module detects which one the campaign's own documents follow:

1. SMC, Nigeria convention (auto-detected: additionalDetails.stockEntryType
   ISSUED/RETURNED x status ACCEPTED/REJECTED/IN_TRANSIT, both sides of every
   transfer recorded) — per-hop Sent / Accepted / Rejected triples at
   LGA x Health Facility x product grain, the exact shape of the fleet's
   existing NA/PL STOCK reports, plus balances.
2. SMC, Chad-like convention (eventType/reason, sender-side records only,
   upstream may be "Central Facility") — receipts at HF, HF dispatches to
   CDDs, CDD returns, damage/loss from BOTH the reason enum and Chad's
   additionalDetails.status. The "issued" leg auto-picks the side that
   actually carries the data (HF dispatches vs CDD receipts).
3. AZM (Kebbi convention) — same legs, but stock is BOTTLES of 30 doses:
   consumption = (SUCCESS + VISITED doses) / 30 and redose is not
   double-subtracted; bottle returns (unused/partial/wasted/empty) come from
   additionalDetails.
4. ITN/LLIN (Chad ITN model) — boundary-grain stock (bales, scans,
   received/returned/issued/wasted) merged with distribution from the task
   index (DISTRIBUTOR vs DISTRIBUTOR_REGISTRAR quantities, scanned/manual
   codes, duplicate bednet codes via scripted metrics).

Balances (canonical agg_stock_summary formulas):
    Stock at HF  = received-in + CDD-returns - issued-out - returned-upstream
                   - damaged - lost
    Stock at CDD = issued - used - returns  (never shown as a minus figure in
                   the doc: a negative means handovers were not recorded, and
                   the section says exactly that in plain words)

The Word section is written for three readers: the campaign manager (Supply
at a Glance), on-ground supervisors (Facilities to Restock First; Facilities
Not Recording Handovers), and the audit — always LAST — Stock Check per CDD.

Field facts this module relies on (do not "fix" them):
  - Data.facility* is always "me" and Data.transactingFacility* the
    counterparty — the transformer flips sender/receiver by direction.
  - Data.physicalCount is always a positive magnitude; direction comes only
    from eventType/reason.
  - On STOCK docs projectTypeId lives at Data.additionalDetails.projectTypeId
    (top-level on task docs). campaignNumber is top-level on both.
  - The stock date field is a per-deployment choice (DST_STOCK_DATE_FIELD):
    createdTime/dateOfEntry are epoch ms, @timestamp is ISO, taskDates is
    YYYY-MM-DD — the range clause must match the value format.

The date window is CUMULATIVE-TO-DATE, not the report day: a balance computed
from one day's movements is meaningless. With a campaign identifier the query
has no lower bound (pre-campaign pre-positioning belongs in the balance);
without one it falls back to campaign_start so a shared tenant cannot bleed a
previous campaign's stock into this report.

run(cfg) returns the workbook path on success and None on the no-op (flag
off, or zero stock documents matched).
"""
import json
import logging
import os
from collections import namedtuple
from datetime import datetime, timezone

import requests
import urllib3
from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side

urllib3.disable_warnings()
log = logging.getLogger(__name__)

# ── feature switches (in-code defaults, env overrides) ────────────────────────
# ON by default (user, 2026-10-01): a campaign gets a stock section unless its
# sheet cell stock_report says FALSE (or DST_STOCK_REPORT=FALSE).
STOCK_REPORT_DEFAULT = "TRUE"
# ITN: the NO-SCANNER ledger by default (user, 2026-10-01). A scanner campaign
# (Chad: bales, scans, codes) sets itn_scanner=TRUE on its sheet row.
STOCK_ITN_SCANNER_DEFAULT = "FALSE"
STOCK_DATE_FIELD_DEFAULT = "createdTime"


def _flag_on():
    val = (os.getenv("DST_STOCK_REPORT", "").strip() or STOCK_REPORT_DEFAULT)
    return val.strip().upper() in ("TRUE", "YES", "1", "Y", "ON")


def enabled(cfg):
    """Is the stock stage on for THIS campaign?

    The sheet's stock_report cell decides (config.build resolves it to
    True/False); a blank cell is None and defers to the deployment default
    (DST_STOCK_REPORT, then STOCK_REPORT_DEFAULT).
    """
    choice = cfg.get("stock_report")
    return _flag_on() if choice is None else bool(choice)


def _log_queries():
    """DST_STOCK_LOG_QUERY=TRUE prints every Elasticsearch request this module
    sends, ready to paste into Kibana or curl.

    Worth having: the queries are assembled from helpers, so reading the source
    tells you the SHAPE of a leg but not the body that actually ran. Every stock
    question so far ("why is issued larger than received?") was answered by
    looking at the real query, and reconstructing it by hand each time is both
    slow and a chance to reconstruct it WRONG — which is its own bug.
    """
    return os.getenv("DST_STOCK_LOG_QUERY", "").strip().upper() in (
        "TRUE", "YES", "1", "Y", "ON")


def _date_field(cfg):
    return (str(cfg.get("stock_date_field", "")).strip()
            or os.getenv("DST_STOCK_DATE_FIELD", "").strip()
            or STOCK_DATE_FIELD_DEFAULT)


ITN_DRUG_TYPES = {"ITN", "LLIN"}
_DEFAULT_BOUNDARY = {
    "smc": ["state", "lga", "ward", "healthFacility"],
    "itn": ["pays", "province", "district"],
}
# boundaryHierarchy keys differ per deployment (NG: state/lga/ward/
# healthFacility; Chad: country/province/district/HEALTHFACILITY). When no
# explicit override is configured, the levels are DETECTED from a probe doc
# and ordered by this list.
_LEVEL_ORDER = ["country", "state", "pays", "province", "region", "district",
                "lga", "county", "ward", "village", "healthFacility",
                "HEALTHFACILITY"]
_DAMAGED_REASONS = ["DAMAGED_IN_STORAGE", "DAMAGED_IN_TRANSIT"]
_LOST_REASONS = ["LOST_IN_STORAGE", "LOST_IN_TRANSIT"]
_TERMS_SIZE = 2000          # explicit on every terms agg — ES caps at 10 otherwise
_TIMEOUT = 120
AZM_DOSES_PER_BOTTLE = 30   # the Kebbi AZM reports' bottle conversion
_ADDL = "Data.additionalDetails"

_TASK_SUFFIX = "project-task-index-v1"


def _stock_index(cfg):
    """Derive the stock index name from the task index key, preserving the
    deployment's prefix convention without touching config.py."""
    task_index = cfg["ES_INDEX_TASK"]
    if not task_index.endswith(_TASK_SUFFIX):
        raise ValueError(f"unexpected task index name {task_index!r}")
    prefix = task_index[:-len(_TASK_SUFFIX)]
    return f"{prefix}stock-index-v1"


def _variant(cfg):
    return "itn" if cfg.get("drug_type") in ITN_DRUG_TYPES else "smc"


def _boundary_levels(cfg, probe_index=None):
    raw = (str(cfg.get("stock_boundary_levels", "")).strip()
           or os.getenv("DST_STOCK_BOUNDARY_LEVELS", "").strip())
    if raw:
        return [b.strip() for b in raw.split(",") if b.strip()]
    if probe_index:
        detected = _detect_boundary_levels(cfg, probe_index)
        if detected:
            log.info(f"  [stock] boundary levels detected from data: {detected}")
            return detected
    return _DEFAULT_BOUNDARY[_variant(cfg)]


def _detect_boundary_levels(cfg, index):
    """Read one matching doc's boundaryHierarchy and order its keys by the
    known hierarchy order. Returns None when nothing matched."""
    try:
        body = {"size": 1, "_source": ["Data.boundaryHierarchy"],
                "query": {"bool": {"must": _stock_must(cfg)}}}
        data = _search(cfg, index, body, "boundary probe")
        hits = data.get("hits", {}).get("hits", [])
        if not hits:
            return None
        keys = set((hits[0]["_source"].get("Data", {})
                    .get("boundaryHierarchy") or {}).keys())
        return [k for k in _LEVEL_ORDER if k in keys] or None
    except Exception as e:                                       # noqa: BLE001
        log.warning(f"  [stock] boundary probe failed (using defaults): {e}")
        return None


def _stock_xlsx(cfg):
    if cfg.get("stock_xlsx"):
        return cfg["stock_xlsx"]
    name = ("stock_cumulative.xlsx" if cfg.get("cumulative")
            else f"stock_day{cfg['DAY']}.xlsx")
    return os.path.join(cfg["out_dir"], name)


def _epoch_ms(iso_ts):
    return int(datetime.strptime(iso_ts, "%Y-%m-%dT%H:%M:%S.%fZ")
               .replace(tzinfo=timezone.utc).timestamp() * 1000)


# ── query building ────────────────────────────────────────────────────────────

def _campaign_filters(cfg):
    """Campaign-scope clauses for the STOCK indices. Unlike the task index
    (where the daily date range isolates the campaign), stock movements span
    the whole campaign and often precede it, so the identifier is applied
    whenever the sheet provides one. projectTypeId sits under
    additionalDetails on stock docs — top-level would silently match nothing."""
    filters = []
    if cfg.get("is_admin_console") and cfg.get("campaign_number"):
        filters.append({"term": {"Data.campaignNumber.keyword": cfg["campaign_number"]}})
    elif cfg.get("project_type_id"):
        # projectTypeId sits TOP-LEVEL on some deployments' stock docs (Chad)
        # and under additionalDetails on others — match either.
        pid = cfg["project_type_id"]
        filters.append({"bool": {"minimum_should_match": 1, "should": [
            {"term": {"Data.projectTypeId.keyword": pid}},
            {"term": {"Data.additionalDetails.projectTypeId.keyword": pid}},
        ]}})
    if cfg.get("cycle_index"):
        filters.append({"term": {
            "Data.additionalDetails.cycleIndex.keyword": cfg["cycle_index"]}})
    return filters


def _task_campaign_filters(cfg):
    """Campaign-scope clauses for the TASK index (top-level fields there)."""
    filters = []
    if cfg.get("is_admin_console") and cfg.get("campaign_number"):
        filters.append({"term": {"Data.campaignNumber.keyword": cfg["campaign_number"]}})
    elif cfg.get("project_type_id"):
        filters.append({"term": {"Data.projectTypeId.keyword": cfg["project_type_id"]}})
    elif cfg.get("project_type"):
        filters.append({"term": {"Data.projectType.keyword": cfg["project_type"]}})
    if cfg.get("cycle_index"):
        filters.append({"term": {
            "Data.additionalDetails.cycleIndex.keyword": cfg["cycle_index"]}})
    return filters


def _range_clause(field, gte_iso, lte_iso):
    """Range clause whose value format matches the field's convention.

    Either bound may be None. With both None there is nothing to constrain and
    the caller gets None back rather than an empty, always-true range.
    """
    bounds = {}
    if field == "taskDates":
        if gte_iso:
            bounds["gte"] = gte_iso[:10]
        if lte_iso:
            bounds["lte"] = lte_iso[:10]
    elif field == "@timestamp":
        if gte_iso:
            bounds["gte"] = gte_iso
        if lte_iso:
            bounds["lte"] = lte_iso
    else:                                   # createdTime / dateOfEntry: epoch ms
        if gte_iso:
            bounds["gte"] = _epoch_ms(gte_iso)
        if lte_iso:
            bounds["lte"] = _epoch_ms(lte_iso)
    return {"range": {f"Data.{field}": bounds}} if bounds else None


def _stock_must(cfg):
    """Campaign scope plus the date window for the STOCK indices.

    NO LOWER BOUND when a campaign identifier is present: stock pre-positioned
    before the campaign opened is real stock and belongs in the balance, and
    the campaign/cycle filter is what keeps another cycle out.

    NO UPPER BOUND EITHER ON A CUMULATIVE RUN (user, 2026-09-29). A daily report
    is a snapshot and stops at its own date so it stays reproducible. A
    cumulative report is the final reconciliation, and stock keeps moving after
    the last distribution day — CDDs hand leftovers back, facilities return to
    the State. run_cumulative sets LTE to the CAMPAIGN END, so bounding there
    would drop exactly those closing movements and report stock as still
    stranded with CDDs when it had in fact been returned. On Borno that would
    have lost a 142-dose issue on 09-08 and a 100-dose return on 09-12.

    Usage stays bounded (see _task_must): dosing ends with the campaign, stock
    movement does not.
    """
    filters = _campaign_filters(cfg)
    gte = None if filters else f"{cfg['campaign_start'].isoformat()}T00:00:00.000Z"
    lte = None if cfg.get("cumulative") else cfg["LTE"]
    clause = _range_clause(_date_field(cfg), gte, lte)
    return filters + ([clause] if clause else [])


def _task_must(cfg):
    gte = f"{cfg['campaign_start'].isoformat()}T00:00:00.000Z"
    return (_task_campaign_filters(cfg)
            + [_range_clause(cfg.get("task_date_field", "taskDates"),
                             gte, cfg["LTE"])])


# ── ES access (inlined — this tree has no pipeline.core) ──────────────────────

def _search(cfg, index, body, label):
    if _log_queries():
        log.info("  [stock] %s\nGET %s/_search\n%s",
                 label, index, json.dumps(body, indent=2, default=str))
    r = requests.post(f"{cfg['es_url']}/{index}/_search",
                      json=body, auth=cfg["es_auth"], verify=False,
                      timeout=_TIMEOUT)
    r.raise_for_status()
    log.info(f"  [stock] {label}: query ok")
    return r.json()


def _composite(cfg, index, must, sources, sub_aggs, label):
    """Paginated composite agg WITH sub-aggregations. missing_bucket keeps
    docs with an empty boundaryHierarchy — enrichment failures are silent and
    dropping those docs would silently understate every total."""
    buckets, after = [], None
    while True:
        comp = {"size": 1000, "sources": sources}
        if after:
            comp["after"] = after
        body = {"size": 0,
                "query": {"bool": {"must": must}},
                "aggs": {"combo": {"composite": comp, "aggs": sub_aggs}}}
        data = _search(cfg, index, body, label)
        page = data["aggregations"]["combo"]["buckets"]
        buckets.extend(page)
        after = data["aggregations"]["combo"].get("after_key")
        if not after:
            break
    log.info(f"  [stock] {label}: {len(buckets)} bucket(s)")
    return buckets


def _bsources(levels, extra=None):
    src = [{lvl: {"terms": {"field": f"Data.boundaryHierarchy.{lvl}.keyword",
                            "missing_bucket": True}}}
           for lvl in levels]
    for name, field in (extra or []):
        src.append({name: {"terms": {"field": field, "missing_bucket": True}}})
    return src


def _bkey(bucket_key, levels):
    return tuple(bucket_key.get(lvl) or "" for lvl in levels)


def _sum_agg(field="Data.physicalCount"):
    return {"qty": {"sum": {"field": field}}}


# ── SMC / AZM ─────────────────────────────────────────────────────────────────

def _smc_leg_composite(cfg, index, extra_must, levels, label):
    buckets = _composite(
        cfg, index, _stock_must(cfg) + extra_must,
        _bsources(levels, extra=[("product", "Data.productName.keyword")]),
        _sum_agg(), label)
    out = {}
    for b in buckets:
        key = _bkey(b["key"], levels) + (b["key"].get("product") or "",)
        out[key] = out.get(key, 0) + (b["qty"]["value"] or 0)
    return out


_ENTRY = f"{_ADDL}.stockEntryType.keyword"
_STATUS = f"{_ADDL}.status.keyword"


def _uses_entry_status(cfg, v1):
    """Nigeria encodes movements as additionalDetails.stockEntryType
    (ISSUED/RETURNED) x status (ACCEPTED/REJECTED/IN_TRANSIT) with BOTH sides
    of each transfer recorded; Chad uses eventType/reason with sender-side
    records only. Chad also carries stockEntryType but a different status
    vocabulary (IN_TRANSIT/RECEIVED/DAMAGED/LOST), so the probe requires
    ACCEPTED/REJECTED docs — and a meaningful SHARE of them, so a handful of
    stray docs from a mixed app version cannot flip the whole ledger."""
    body = {"size": 0, "track_total_hits": True,
            "query": {"bool": {"must": _stock_must(cfg)}},
            "aggs": {"acc_rej": {"filter": {"bool": {"must": [
                {"term": {_ENTRY: "ISSUED"}},
                {"terms": {_STATUS: ["ACCEPTED", "REJECTED"]}},
            ]}}}}}
    data = _search(cfg, v1, body, "convention probe")
    total = data.get("hits", {}).get("total", {}).get("value", 0)
    hits = data.get("aggregations", {}).get("acc_rej", {}).get("doc_count", 0)
    return hits >= 3 and total and (hits / total) >= 0.01


# ═══════════════════════════════════════════════════════════════════════════════
#  QUERY CATALOGUE — Nigeria convention
#
#  Every stock movement the ledger counts is defined ONCE, here. To answer
#  "what exactly counts as Stock Given to CDDs?", read the matching entry
#  below; you should never have to trace through the helper functions.
#
#  Each leg is identified by four things:
#    facility_types — whose book the document sits on. Data.facility* is always
#                     "me"; Data.transactingFacility* is the counterparty.
#    group_by       — the field holding the HEALTH FACILITY name, which differs
#                     by leg: on the state's and the CDD's own records the
#                     facility is the counterparty (transactingFacilityName);
#                     on the facility's own records it is itself (facilityName).
#    entry_type     — additionalDetails.stockEntryType, ISSUED or RETURNED.
#    extra          — any further constraints (see _ISSUE_TO_CDD_ONLY).
#
#  Every leg additionally carries the campaign + date scope from _stock_must(),
#  and each produces four sums: _sent (all statuses), _acc, _rej and _trans
#  (that status only). Set DST_STOCK_LOG_QUERY=TRUE to print the real request.
# ═══════════════════════════════════════════════════════════════════════════════

# Applied to the HF -> CDD issue leg ONLY. Without it that leg counted EVERY
# ISSUED record at a facility regardless of who received it, which is how
# "issued" could exceed "received" and drive Stock Left at HF negative.
#
# Written as EXCLUSIONS rather than "counterparty must be STAFF" on purpose: a
# must-clause would silently zero the column on any deployment whose documents
# do not carry transactingFacilityType or reason at all, whereas a must_not
# keeps documents that simply lack the field.
#
# Verified 2026-09-29 on so and bo: every HF ISSUED record already has a STAFF
# counterparty, so this currently removes nothing. It is a guard, not a fix.
_ISSUE_TO_CDD_ONLY = [{"bool": {"must_not": [
    # A return booked on the facility's own record can still carry
    # stockEntryType ISSUED; counted here it reads as fresh stock to a CDD.
    {"term": {"Data.reason.keyword": "RETURNED"}},
    # Dispatches to anywhere that is not a CDD: upstream, or another facility.
    # Real movements, but not stock given to CDDs.
    {"terms": {"Data.transactingFacilityType.keyword":
               ["State Facility", "Health Facility", "WAREHOUSE", "Warehouse"]}},
]}}]

_FACILITY_BOOK = ["Health Facility", "WAREHOUSE", "Warehouse"]

# Each leg names its own four measured quantities. The names ARE the ledger
# column titles in snake_case, so a formula reads like the row it produces:
# no decoding, and no separate glossary to keep in step.
_Leg = namedtuple("_Leg", "key column facility_types group_by entry_type extra "
                          "sent accepted rejected in_transit")

# Order = the stock journey: down to the facility, out to CDDs, and back again.
NG_LEGS = (
    _Leg(key="state",
         column="Sent by State to HF",
         facility_types=["State Facility"],
         group_by="transactingFacilityName",   # the facility is the counterparty
         entry_type="ISSUED",
         extra=(),
         sent="sent_by_state_to_hf",
         accepted="received_by_hf_from_state",
         rejected="rejected_by_hf",
         in_transit="in_transit_state_to_hf"),

    _Leg(key="issue",
         column="Stock Given to CDDs",
         facility_types=_FACILITY_BOOK,
         group_by="facilityName",              # the facility's own record
         entry_type="ISSUED",
         extra=_ISSUE_TO_CDD_ONLY,
         # NOT "stock given": this counts issue RECORDS, and a dose issued,
         # returned, then issued again appears twice. _ledger_maths turns it
         # into stock_given_to_cdds by removing what came back.
         sent="sent_by_hf_to_cdd",
         accepted="accepted_by_cdd",
         rejected="rejected_by_cdd",
         in_transit="in_transit_hf_to_cdd"),

    _Leg(key="cdd_return",
         column="Returned by CDD to HF",
         facility_types=["STAFF"],             # the CDD's own record
         group_by="transactingFacilityName",
         entry_type="RETURNED",
         extra=(),
         sent="returned_by_cdd_to_hf",
         accepted="return_received_by_hf",
         rejected="return_rejected_by_hf",
         in_transit="return_in_transit_to_hf"),

    _Leg(key="hf_return",
         column="Returned by HF to State",
         facility_types=_FACILITY_BOOK,
         group_by="facilityName",
         entry_type="RETURNED",
         extra=(),
         sent="returned_by_hf_to_state",
         accepted="return_received_by_state",
         rejected="return_rejected_by_state",
         in_transit="return_in_transit_to_state"),
)

# Every measured quantity, in journey order — derived from the legs so the two
# can never disagree.
_NG_METRICS = [name for leg in NG_LEGS
               for name in (leg.sent, leg.accepted, leg.rejected, leg.in_transit)]

# Which leg answers each metric, so a row can be assembled without guessing.
_METRIC_LEG = {name: leg.key for leg in NG_LEGS
               for name in (leg.sent, leg.accepted, leg.rejected, leg.in_transit)}


def _ng_branches(cfg, index, extra_sources=(), key_of=None):
    """Run every leg in the catalogue: {leg key: {(facility, product): sums}}.

    One request per leg. The legs differ in their filters (the issue leg alone
    carries the CDD-counterparty guard), so keeping them separate is what makes
    each request readable on its own and checkable against a hand-written query.
    """
    return {leg.key: _leg_branch(cfg, index, leg, extra_sources, key_of)
            for leg in NG_LEGS}


# The three transfer outcomes the ledger reports, and the metric suffix each
# becomes. A status outside this set is counted in "_sent" and reported by the
# truncation guard, never silently dropped.
_STATUS_SUFFIX = {"ACCEPTED": "acc", "REJECTED": "rej", "IN_TRANSIT": "trans"}


def _leg_query(cfg, leg, sources, after=None):
    """THE COMPLETE REQUEST for one leg. Nothing is added to it anywhere else —
    what you read here is what is sent, and it can be pasted into Kibana as-is
    (drop the `after` key on the first page).

        GET <tenant>-stock-index-v1/_search

    The aggregation mirrors the team's reference stock reports: break the
    documents down by stockEntryType, then by status, summing physicalCount at
    both levels. The entry-level sum is its OWN sum rather than the total of the
    status buckets, so a document carrying no status still counts as "sent"
    instead of quietly disappearing.
    """
    composite = {"size": 1000, "sources": sources}
    if after:
        composite["after"] = after

    return {
        "size": 0,
        "query": {
            "bool": {
                "must": [
                    # campaign number / project type, cycle, and the date
                    # window — see _stock_must for why there is usually no
                    # lower bound.
                    *_stock_must(cfg),

                    # whose book this leg sits on. Data.facility* is always
                    # "me"; Data.transactingFacility* is the counterparty.
                    {"terms": {"Data.facilityType.keyword":
                               list(leg.facility_types)}},

                    # leg-specific guard — only the facility -> CDD issue leg
                    # has one (see _ISSUE_TO_CDD_ONLY).
                    *leg.extra,
                ]
            }
        },
        "aggs": {
            "combo": {
                "composite": composite,
                "aggs": {
                    "entry_type": {
                        "terms": {"field": _ENTRY, "size": 20},
                        "aggs": {
                            # every record on this leg, whatever its status
                            "qty": {"sum": {"field": "Data.physicalCount"}},
                            "status": {
                                "terms": {"field": _STATUS, "size": 20},
                                "aggs": {
                                    "qty": {"sum": {"field": "Data.physicalCount"}}
                                },
                            },
                        },
                    }
                },
            }
        },
    }


def _read_entry_status(bucket, leg):
    """Read one leg's four metrics out of an entry/status bucket tree.

    Returns ({metric: quantity}, documents_outside_the_buckets).
    """
    by_status = {"ACCEPTED": leg.accepted,
                 "REJECTED": leg.rejected,
                 "IN_TRANSIT": leg.in_transit}

    vals, dropped = {}, 0
    entry_agg = bucket.get("entry_type") or {}
    dropped += entry_agg.get("sum_other_doc_count") or 0

    for entry_bucket in entry_agg.get("buckets", []):
        if entry_bucket.get("key") != leg.entry_type:
            continue                      # another leg reads that entry type
        vals[leg.sent] = entry_bucket["qty"]["value"] or 0

        status_agg = entry_bucket.get("status") or {}
        dropped += status_agg.get("sum_other_doc_count") or 0
        for status_bucket in status_agg.get("buckets", []):
            metric = by_status.get(status_bucket.get("key"))
            if metric:
                vals[metric] = status_bucket["qty"]["value"] or 0
    return vals, dropped


def _leg_branch(cfg, index, leg, extra_sources=(), key_of=None):
    """Run ONE leg and return {(facility, product[, day]): {metric: quantity}}.

    Keyed on the field holding the HEALTH FACILITY name for that leg: on the
    state's and the CDD's own records the facility is the counterparty
    (transactingFacilityName); on the facility's own records it is itself
    (facilityName). extra_sources / key_of let the daily tab add a day bucket
    without redefining any leg.
    """
    sources = list(extra_sources) + [
        {"hf": {"terms": {"field": f"Data.{leg.group_by}.keyword",
                          "missing_bucket": True}}},
        {"product": {"terms": {"field": "Data.productName.keyword",
                               "missing_bucket": True}}},
    ]

    out, dropped, after, pages = {}, 0, None, 0
    while True:                                  # composite paging
        body = _leg_query(cfg, leg, sources, after)
        agg = _search(cfg, index, body, leg.column)["aggregations"]["combo"]

        for bucket in agg["buckets"]:
            key = (key_of(bucket["key"]) if key_of else
                   (bucket["key"].get("hf") or "",
                    bucket["key"].get("product") or ""))
            vals, missed = _read_entry_status(bucket, leg)
            dropped += missed
            totals = out.setdefault(key, {})
            for name, qty in vals.items():
                totals[name] = totals.get(name, 0) + qty

        pages += 1
        after = agg.get("after_key")
        if not after:
            break

    log.info(f"  [stock] {leg.column}: {len(out)} row(s) over {pages} page(s)")
    if dropped:
        # Never a silent undercount: an unexpected entry type or status means
        # the app has started writing a value this ledger does not know about.
        log.warning("  [stock] %s: %s document(s) fell outside the "
                    "stockEntryType/status buckets — an unrecognised value is "
                    "present in the data and is NOT counted in this column.",
                    leg.column, f"{dropped:,}")
    return out


_LedgerRow = namedtuple(
    "_LedgerRow",
    "returned_by_cdds returned_upstream given_to_cdds received_by_cdds "
    "left_at_facility left_with_cdds")


def _ledger_maths(m, used, redosed):
    """Turn one facility+product's measured movements into the printed columns.

    `m` holds the raw sums, named <leg>_<outcome>: leg is state / iss / sret /
    hret (see NG_LEGS) and outcome is sent / acc / rej / trans. Everything below
    is arithmetic on those — no value here is fetched or guessed.

    WHO HOLDS STOCK IN TRANSIT: the receiver, on every leg. Stock sent to a CDD
    counts as theirs from dispatch; a return counts at the facility from
    dispatch; and a REJECTION hands accountability back to the sender. The one
    exception is State -> facility in transit, which sits on nobody's balance
    until the facility confirms it, and appears only in its own column.

    WHY "given" SUBTRACTS RETURNS: stock a CDD hands back can be issued again,
    so the issue records count some doses twice. Subtracting what came back
    leaves each physical dose counted exactly once — this is why "given" is
    smaller than the raw issue total, and the single most-asked question about
    this report.

    The columns always balance:
        used + redosed + left_with_cdds + left_at_facility
              + in transit to CDDs + confirmed upstream returns
        = received from the State
    """
    # Returns that STAYED with the facility (a rejected return bounces back to
    # the CDD, so it never lands on the facility's book).
    returns_kept_by_hf = (m["returned_by_cdd_to_hf"]
                          - m["return_rejected_by_hf"])

    # Returns that LEFT the facility for the State, likewise net of rejections.
    returns_sent_to_state = (m["returned_by_hf_to_state"]
                             - m["return_rejected_by_state"])

    # Each physical dose counted ONCE. The issue records count a dose twice
    # when it was handed out, returned, and handed out again — subtracting the
    # returns removes that second count. This is why the printed figure is
    # smaller than sent_by_hf_to_cdd, and it is the single most-asked
    # question about this report.
    stock_given_to_cdds = (m["sent_by_hf_to_cdd"]
                           - m["rejected_by_cdd"]
                           - returns_kept_by_hf)

    # Confirmed custody only — stock still travelling has its own column and
    # belongs to nobody's balance until the CDD confirms it.
    #
    # NOT the app's recorded accepted figure (accepted_by_cdd). That one counts
    # a returned-then-reissued dose on every trip, so it can exceed both this
    # column and the stock the facility ever received. The DAILY FLOW tab does
    # print the recorded figure, because there the returns come off the next
    # day's opening balance; a cumulative row has no next day, so it nets here.
    received_by_cdd = stock_given_to_cdds - m["in_transit_hf_to_cdd"]

    stock_left_at_hf = (m["received_by_hf_from_state"]
                        + returns_kept_by_hf
                        - m["sent_by_hf_to_cdd"]
                        + m["rejected_by_cdd"]
                        - returns_sent_to_state)

    # No returns term: they are already out of received_by_cdd, which comes
    # from stock_given_to_cdds. Subtracting them again would double-count.
    stock_left_with_cdds = received_by_cdd - (used + redosed)

    return _LedgerRow(returns_kept_by_hf, returns_sent_to_state,
                      stock_given_to_cdds, received_by_cdd,
                      stock_left_at_hf, stock_left_with_cdds)


def _collect_smc_ng(cfg, v1, task):
    """Nigeria-convention ledger: per hop Sent / Accepted / Rejected triples,
    the exact shape of the fleet's existing STOCK reports (NA/PL)."""
    # Every leg comes from the QUERY CATALOGUE above — the ledger and the daily
    # flow read the same definitions, so the two tabs cannot drift apart.
    branches = _ng_branches(cfg, v1)

    # consumption / redose / LGA lookup from the task index at the same grain
    tsources = [
        {"lga": {"terms": {"field": "Data.boundaryHierarchy.lga.keyword",
                           "missing_bucket": True}}},
        {"hf": {"terms": {
            "field": "Data.boundaryHierarchy.healthFacility.keyword",
            "missing_bucket": True}}},
        {"product": {"terms": {"field": "Data.productName.keyword",
                               "missing_bucket": True}}},
    ]
    consumed, redose, lga_map = {}, {}, {}
    for status, agg, sink in (
            ("ADMINISTRATION_SUCCESS", _sum_agg("Data.quantity"), consumed),
            ("VISITED", {}, redose)):
        for b in _composite(
                cfg, task,
                _task_must(cfg) + [{"term": {
                    "Data.administrationStatus.keyword": status}}],
                tsources, agg, f"task {status} (NG)"):
            key = (b["key"].get("hf") or "", b["key"].get("product") or "")
            val = (b["qty"]["value"] or 0) if "qty" in b else b["doc_count"]
            sink[key] = sink.get(key, 0) + val
            if b["key"].get("lga"):
                lga_map[key[0]] = b["key"]["lga"]

    # EVERY leg contributes rows, including hf_return: a facility that only
    # ever returned stock upstream still belongs in the ledger.
    all_keys = set(consumed) | set(redose)
    for per_key in branches.values():
        all_keys |= set(per_key)
    if not any(branches.values()):
        return None

    # Each leg reads ITS OWN result. Every leg is a separate request now, so
    # pointing two legs at one branch silently zeroes the second — that is how
    # the three "Returned by HF to State" columns read 0 while the daily tab,
    # which resolves legs correctly, showed the real 583 / 295.
    branch_of = {leg.key: branches[leg.key] for leg in NG_LEGS}

    rows = []
    # Raw handover volume is NOT a printed column (it exceeds Received whenever
    # returned stock goes out again, which reviewers read as an error) but the
    # report's give-back metrics still need the total.
    gross_issued = 0
    for key in sorted(all_keys):
        hf, product = key
        measured = {
            name: (branch_of[_METRIC_LEG[name]].get(key) or {}).get(name, 0)
            for name in _NG_METRICS
        }
        used = consumed.get(key, 0)
        redosed = redose.get(key, 0)

        # All the arithmetic lives in _ledger_maths — read that one function to
        # understand every computed column on this row.
        row = _ledger_maths(measured, used, redosed)
        con, red = used, redosed
        ret_in, ret_up = row.returned_by_cdds, row.returned_upstream
        net_given, cdd_received = row.given_to_cdds, row.received_by_cdds
        balance_hf, balance_cdd = row.left_at_facility, row.left_with_cdds
        vals = measured

        gross_issued += measured["sent_by_hf_to_cdd"]
        # Every printed column counts REAL doses and stays <= Received (the
        # conservation rule reviewers expect). The raw handover counters
        # (which exceed Received because returned stock goes out again) are
        # NOT printed — removed on feedback 2026-09-21 — but their gross sum
        # still feeds the report metrics via totals["issued"].
        # Column order rule (user, 2026-09-25): each balance CLOSES its own
        # block, so the row reads left-to-right with no cross-references —
        # CDD block: Given - rejected - in transit = Received by CDD, minus
        # used/redose = Stock Left with CDDs. THEN the return legs and the
        # facility balance. Returns must never sit between the CDD numbers
        # and the CDD balance (readers subtract them a second time).
        rows.append([lga_map.get(hf, ""), hf, product,
                     measured["sent_by_state_to_hf"],
                     measured["received_by_hf_from_state"],
                     measured["rejected_by_hf"],
                     measured["in_transit_state_to_hf"],
                     measured["sent_by_hf_to_cdd"],          # every handover
                     net_given,                              # real doses out
                     measured["rejected_by_cdd"],
                     measured["in_transit_hf_to_cdd"],
                     cdd_received,                           # confirmed doses
                     con, red,
                     balance_cdd,
                     measured["returned_by_cdd_to_hf"],
                     measured["return_received_by_hf"],
                     measured["return_rejected_by_hf"],
                     measured["returned_by_hf_to_state"],
                     measured["return_received_by_state"],
                     measured["return_rejected_by_state"],
                     balance_hf])

    headers = ["LGA", "Health Facility", "Product",
               "Sent by State to HF", "Received by HF from State",
               "Rejected by HF",
               "In Transit from State to HF (sent, not yet received)",
               "Sent by HF to CDD (all handovers, stock given again counted "
               "each time)",
               "Stock Given to CDDs (actual stock, returned stock not counted again)",
               "Rejected by CDD",
               "In Transit from HF to CDD (sent, not yet received)",
               "Received by CDD (actual stock, returned stock not counted again)",
               "Used by CDD (administered)",
               "Redose (repeat dose after the first was spat out/vomited)",
               "Stock Left with CDDs",
               "Returned by CDD to HF", "Return Received by HF",
               "Return Rejected by HF",
               "Returned by HF to State", "Return Received by State",
               "Return Rejected by State",
               "Stock Left at HF"]
    def col(title_starts_with):
        """Column position BY NAME. Using names rather than literal numbers
        means adding or moving a column cannot silently mis-address the totals
        or the report's index map — it raises instead."""
        for i, header in enumerate(headers):
            if header.startswith(title_starts_with):
                return i
        raise KeyError(f"no ledger column starting with {title_starts_with!r}")

    c_received_hf = col("Received by HF from State")
    c_rejected_hf = col("Rejected by HF")
    c_given = col("Stock Given to CDDs")
    c_rejected_cdd = col("Rejected by CDD")
    c_in_transit_cdd = col("In Transit from HF to CDD")
    c_used = col("Used by CDD")
    c_redose = col("Redose")
    c_left_cdd = col("Stock Left with CDDs")
    c_returned_cdd = col("Returned by CDD to HF")
    c_return_rejected_hf = col("Return Rejected by HF")
    c_returned_state = col("Returned by HF to State")
    c_return_rejected_state = col("Return Rejected by State")
    c_left_hf = col("Stock Left at HF")

    totals = {
        "received":  sum(r[c_received_hf] for r in rows),
        "issued":    gross_issued,
        # receiver-accountable: returns count from DISPATCH minus rejections
        # (sent - rejected on each leg), matching net_given/balance_hf so the
        # 6.1 reconciliation closes exactly
        "returned":  sum(r[c_returned_cdd] - r[c_return_rejected_hf]
                         for r in rows),
        "returned_upstream": sum(r[c_returned_state] - r[c_return_rejected_state]
                                 for r in rows),
        "rejected_in":  sum(r[c_rejected_hf] for r in rows),
        "rejected_out": sum(r[c_rejected_cdd] for r in rows),
        "consumed":  sum(r[c_used] for r in rows),
        "redose":    sum(r[c_redose] for r in rows),
        "damaged": 0, "lost": 0,
        "in_transit_out": sum(r[c_in_transit_cdd] for r in rows),
        "balance_hf":  sum(r[c_left_hf] for r in rows),
        "balance_cdd": sum(r[c_left_cdd] for r in rows),
    }
    rows.sort(key=lambda r: (r[0], r[1], r[2]))
    ix = {"lga": 0, "hf": 1, "product": 2,
          "received": c_received_hf, "issued": c_given,
          "consumed": c_used, "damaged": None, "lost": None,
          "bal_hf": c_left_hf, "bal_cdd": c_left_cdd}
    # DAILY FLOW tab (non-fatal): the chronological view that explains the
    # cumulative numbers — same columns, same formulas, per day.
    try:
        daily_rows = _collect_daily_flow(cfg, lga_map)
    except Exception as e:                                       # noqa: BLE001
        log.warning(f"  [stock] daily flow collection failed (tab skipped): {e}")
        daily_rows = []
    # daily tab = Date + the ledger columns, plus ONE daily-only column
    # ("Available with CDDs"). ONE daily-only semantic remains (user,
    # 2026-09-25): Received by CDD is the app's RECORDED accepted quantity for
    # that day. Stock Left with CDDs now subtracts the day's returns in the
    # same row, so the last day of this tab equals the ledger (fixed
    # 2026-09-29 — they previously disagreed by 15,537 doses).
    _recv_ix = headers.index("Received by CDD (actual stock, returned stock not counted again)")
    daily_headers = ["Date"] + headers
    daily_headers[_recv_ix + 1] = "Received by CDD (as recorded in the app)"
    daily_headers.insert(
        _recv_ix + 2,
        "Available with CDDs (yesterday's stock + received today)")
    return {"variant": "smc", "ng": True, "levels": ["LGA", "Health Facility"],
            "headers": headers, "rows": rows, "totals": totals, "ix": ix,
            "daily_rows": daily_rows, "daily_headers": daily_headers,
            "cdd_rows": _collect_cdd_accountability_ng(cfg, v1, task)}


# ── DAILY FLOW (the STOCK LEDGER's columns and formulas, per day) ─────────────

def _collect_daily_flow(cfg, lga_map):
    """DAILY FLOW tab: the STOCK LEDGER's exact columns plus Date, computed
    with the SAME formulas per day. Movement columns show that day's
    movements (net of that day's give-backs, so a facility's daily values
    SUM to its ledger row); the two Stock Left columns are RUNNING balances
    at the END of that day — a facility's last day equals its ledger row.
    Day buckets: Data.@timestamp for stock movements, taskDates for
    consumption (NG convention only)."""
    v1 = _stock_index(cfg)
    task = cfg["ES_INDEX_TASK"]

    def _day(ms):
        return datetime.fromtimestamp(
            ms / 1000, tz=timezone.utc).strftime("%Y-%m-%d")

    # Exactly the ledger's legs, with a day bucket added — same catalogue, same
    # filters, so the two tabs cannot drift apart.
    day_source = [{"day": {"date_histogram": {"field": "Data.@timestamp",
                                              "calendar_interval": "day"}}}]

    def _day_key(bucket_key):
        return (bucket_key.get("hf") or "",
                bucket_key.get("product") or "",
                _day(bucket_key["day"]))

    vals = {}
    for leg_key, per_key in _ng_branches(cfg, v1, day_source, _day_key).items():
        for key, metrics in per_key.items():
            totals = vals.setdefault(key, {})
            for name, qty in metrics.items():
                totals[name] = totals.get(name, 0) + qty

    # daily consumption / redose from the task index (taskDates day grain)
    tsources = [
        {"day": {"date_histogram": {"field": "Data.taskDates",
                                    "calendar_interval": "day"}}},
        {"hf": {"terms": {
            "field": "Data.boundaryHierarchy.healthFacility.keyword",
            "missing_bucket": True}}},
        {"product": {"terms": {"field": "Data.productName.keyword",
                               "missing_bucket": True}}},
    ]
    for status, agg, name in (
            ("ADMINISTRATION_SUCCESS", _sum_agg("Data.quantity"), "con"),
            ("VISITED", {}, "red")):
        for b in _composite(
                cfg, task,
                _task_must(cfg) + [{"term": {
                    "Data.administrationStatus.keyword": status}}],
                tsources, agg, f"daily task {status}"):
            key = (b["key"].get("hf") or "",
                   b["key"].get("product") or "", _day(b["key"]["day"]))
            m = vals.setdefault(key, {})
            val = (b["qty"]["value"] or 0) if "qty" in b else b["doc_count"]
            m[name] = m.get(name, 0) + val

    rows = []
    run = {}
    for hf, product, day in sorted(vals):
        g = vals[(hf, product, day)].get
        returns_kept_by_hf = (g("returned_by_cdd_to_hf", 0)
                              - g("return_rejected_by_hf", 0))
        returns_sent_to_state = (g("returned_by_hf_to_state", 0)
                                 - g("return_rejected_by_state", 0))
        # Same definition as the ledger's stock_given_to_cdds: issue records
        # less what the CDD rejected, less what came back and went out again.
        given = (g("sent_by_hf_to_cdd", 0)
                 - g("rejected_by_cdd", 0)
                 - returns_kept_by_hf)
        # SOURCE OF TRUTH (user, 2026-09-25): the daily "Received by CDD" is
        # the app's RECORDED accepted quantity for that day — no netting.
        cdd_recv = g("accepted_by_cdd", 0)
        con, red = g("con", 0), g("red", 0)
        ret_in, ret_up = returns_kept_by_hf, returns_sent_to_state
        r = run.setdefault((hf, product), {"carry": 0, "hf": 0})
        # The day's stock-card line:
        #   Available  = yesterday's closing stock + Received today
        #   Stock Left = Available - Used - Redose - Returned today
        #
        # The day's returns are subtracted IN THE SAME ROW (2026-09-29). They
        # used to come off the next day's Available instead, which left the
        # last day of this tab higher than the ledger by that day's returns —
        # a 15,537-dose disagreement across 88 facility/product rows. The
        # closing figure now matches Stock Left with CDDs in the ledger, and
        # Available is unaffected because it was already built on the closing
        # figure rather than the pre-returns one.
        available = r["carry"] + cdd_recv
        left_cdd = available - con - red - ret_in
        r["carry"] = left_cdd
        r["hf"] += (g("received_by_hf_from_state", 0)
                    + returns_kept_by_hf
                    - g("sent_by_hf_to_cdd", 0)
                    + g("rejected_by_cdd", 0)
                    - returns_sent_to_state)
        rows.append([day, lga_map.get(hf, ""), hf, product,
                     g("sent_by_state_to_hf", 0),
                     g("received_by_hf_from_state", 0),
                     g("rejected_by_hf", 0),
                     g("in_transit_state_to_hf", 0),
                     g("sent_by_hf_to_cdd", 0),      # every handover that day
                     given,
                     g("rejected_by_cdd", 0),
                     g("in_transit_hf_to_cdd", 0),
                     cdd_recv, available, con, red,
                     left_cdd,
                     g("returned_by_cdd_to_hf", 0),
                     g("return_received_by_hf", 0),
                     g("return_rejected_by_hf", 0),
                     g("returned_by_hf_to_state", 0),
                     g("return_received_by_state", 0),
                     g("return_rejected_by_state", 0),
                     r["hf"]])
    return rows


def _collect_cdd_accountability_ng(cfg, v1, task):
    """NG accountability: 'received' = HF-branch ISSUED+ACCEPTED grouped by
    the staff name (transactingFacilityName = the CDD username per the
    transformer's STAFF rule); returns from the STAFF branch."""
    sources = [{"user": {"terms": {"field": "Data.transactingFacilityName.keyword"}}},
               {"product": {"terms": {"field": "Data.productName.keyword",
                                      "missing_bucket": True}}}]
    received = {}
    for b in _composite(
            cfg, v1,
            _stock_must(cfg) + [
                {"terms": {"Data.facilityType.keyword":
                           ["Health Facility", "WAREHOUSE", "Warehouse"]}},
                {"term": {_ENTRY: "ISSUED"}},
                {"term": {_STATUS: "ACCEPTED"}}]
            # Same leg, same constraint as the ledger: without it a dispatch to
            # another facility becomes a row in the CDD tab keyed on that
            # FACILITY's name, listed as if it were a community distributor.
            + _ISSUE_TO_CDD_ONLY,
            sources, _sum_agg(), "NG accountability received"):
        received[(b["key"]["user"], b["key"].get("product") or "")] = \
            b["qty"]["value"] or 0

    rsources = [{"user": {"terms": {"field": "Data.facilityName.keyword"}}},
                {"product": {"terms": {"field": "Data.productName.keyword",
                                       "missing_bucket": True}}}]
    sub = {"acc": {"filter": {"term": {_STATUS: "ACCEPTED"}},
                   "aggs": _sum_agg()},
           "unused":  {"sum": {"field": f"{_ADDL}.unused_quantity"}},
           "partial": {"sum": {"field": f"{_ADDL}.partial_quantity"}},
           "wasted":  {"sum": {"field": f"{_ADDL}.wastedBlistersReturned"}},
           "empty":   {"sum": {"field": f"{_ADDL}.emptyBottlesReturned"}}}
    returned, extras = {}, {}
    for b in _composite(
            cfg, v1,
            _stock_must(cfg) + [{"term": {"Data.facilityType.keyword": "STAFF"}},
                                {"term": {_ENTRY: "RETURNED"}}],
            rsources, sub, "NG accountability returns"):
        key = (b["key"]["user"], b["key"].get("product") or "")
        returned[key] = b["acc"]["qty"]["value"] or 0
        extras[key] = [b["unused"]["value"] or 0, b["partial"]["value"] or 0,
                       b["wasted"]["value"] or 0, b["empty"]["value"] or 0]

    consumed = {}
    tsources = [{"user": {"terms": {"field": "Data.userName.keyword"}}},
                {"product": {"terms": {"field": "Data.productName.keyword",
                                       "missing_bucket": True}}}]
    for b in _composite(
            cfg, task,
            _task_must(cfg) + [{"term": {
                "Data.administrationStatus.keyword": "ADMINISTRATION_SUCCESS"}}],
            tsources, _sum_agg("Data.quantity"), "NG accountability consumption"):
        consumed[(b["key"]["user"], b["key"].get("product") or "")] = \
            b["qty"]["value"] or 0

    rows = []
    for key in set(received) | set(returned) | set(consumed):
        rec = received.get(key, 0)
        con = consumed.get(key, 0)
        ret = returned.get(key, 0)
        ex = extras.get(key, [0, 0, 0, 0])
        rows.append([key[0], key[1], rec, con, ret, rec - con - ret] + ex
                    + [_high_negative_flag(rec, con, ret)])
    rows.sort(key=lambda r: abs(r[5]), reverse=True)
    return rows


def _high_negative_flag(received, consumed, returned):
    """Highlight CDDs whose difference is a LARGE negative — they used well
    beyond the stock recorded to their login. Same noise floor as the report
    tables: at least 20 units and 5% of the larger of got/used."""
    diff = received - consumed - returned
    return ("CHECK: high negative difference"
            if diff <= -20 and abs(diff) >= 0.05 * max(received, consumed, 1)
            else "")


def _collect_smc(cfg):
    v1 = _stock_index(cfg)
    task = cfg["ES_INDEX_TASK"]
    if _uses_entry_status(cfg, v1):
        log.info("  [stock] stockEntryType/status ACCEPTED convention detected "
                 "— using the NG sent/accepted/rejected leg set")
        return _collect_smc_ng(cfg, v1, task)
    levels = _boundary_levels(cfg, probe_index=v1)

    # Legs are defined by the reporting facility's OWN records, so they hold
    # across deployments (NG records both sides of a transfer; Chad records
    # only the sender side and its upstream is "Central Facility" rather than
    # "State Facility"):
    #   received  - receipts AT the HF (RECEIVED+RECEIVED), any upstream
    #               counterparty except STAFF
    #   issued    - the HF's own dispatch to a CDD (DISPATCHED, no reason,
    #               counterparty STAFF)
    #   returned  - the CDD's return record (facilityType STAFF, RETURNED)
    #   returned_upstream - the HF's return record (facilityType HF, RETURNED,
    #               counterparty not STAFF)
    _not_staff_ttype = {"bool": {"must_not": [
        {"term": {"Data.transactingFacilityType.keyword": "STAFF"}}]}}

    state_to_hf = _smc_leg_composite(cfg, v1, [
        {"term": {"Data.eventType.keyword": "RECEIVED"}},
        {"term": {"Data.reason.keyword": "RECEIVED"}},
        {"term": {"Data.facilityType.keyword": "Health Facility"}},
        _not_staff_ttype,
    ], levels, "Received at HF")

    # Issued to CDDs — deployments record this on DIFFERENT sides:
    #   Chad: only the HF's dispatch (DISPATCHED, no reason, counterparty STAFF)
    #   Kebbi AZM-style: only the CDD's receipt (facilityType STAFF, RECEIVED)
    # Query both sides and use whichever carries the volume; when both sides
    # of the same movement are recorded, using ONE side avoids double counting.
    issued_hf_side = _smc_leg_composite(cfg, v1, [
        {"term": {"Data.eventType.keyword": "DISPATCHED"}},
        {"term": {"Data.facilityType.keyword": "Health Facility"}},
        {"term": {"Data.transactingFacilityType.keyword": "STAFF"}},
        {"bool": {"must_not": [{"exists": {"field": "Data.reason"}}]}},
    ], levels, "Issued to CDD (HF dispatches)")
    issued_cdd_side = _smc_leg_composite(cfg, v1, [
        {"term": {"Data.eventType.keyword": "RECEIVED"}},
        {"term": {"Data.reason.keyword": "RECEIVED"}},
        {"term": {"Data.facilityType.keyword": "STAFF"}},
    ], levels, "Issued to CDD (CDD receipts)")
    if sum(issued_cdd_side.values()) > sum(issued_hf_side.values()):
        hf_to_cdd = issued_cdd_side
        log.info("  [stock] issued leg counted from CDD-side receipts")
    else:
        hf_to_cdd = issued_hf_side
        log.info("  [stock] issued leg counted from HF-side dispatches")

    hf_to_state = _smc_leg_composite(cfg, v1, [
        {"term": {"Data.facilityType.keyword": "Health Facility"}},
        {"term": {"Data.reason.keyword": "RETURNED"}},
        _not_staff_ttype,
    ], levels, "Returned upstream")

    cdd_to_hf = _smc_leg_composite(cfg, v1, [
        {"term": {"Data.facilityType.keyword": "STAFF"}},
        {"term": {"Data.transactingFacilityType.keyword": "Health Facility"}},
        {"term": {"Data.reason.keyword": "RETURNED"}},
    ], levels, "Returned by CDD")

    # Damage/loss lives in TWO places depending on deployment: the reason
    # enum (DAMAGED_IN_*/LOST_IN_*) or Chad's additionalDetails.status
    # (DAMAGED/LOST). Count both — the conventions are mutually exclusive
    # in practice.
    def _merged(a, b):
        out = dict(a)
        for k, qty in b.items():
            out[k] = out.get(k, 0) + qty
        return out

    damaged = _merged(
        _smc_leg_composite(cfg, v1, [
            {"terms": {"Data.reason.keyword": _DAMAGED_REASONS}}],
            levels, "damaged (reason)"),
        _smc_leg_composite(cfg, v1, [
            {"term": {_STATUS: "DAMAGED"}}], levels, "damaged (status)"))
    lost = _merged(
        _smc_leg_composite(cfg, v1, [
            {"terms": {"Data.reason.keyword": _LOST_REASONS}}],
            levels, "lost (reason)"),
        _smc_leg_composite(cfg, v1, [
            {"term": {_STATUS: "LOST"}}], levels, "lost (status)"))

    def _task_leg(statuses, agg, label):
        buckets = _composite(
            cfg, task,
            _task_must(cfg) + [{"terms": {
                "Data.administrationStatus.keyword": statuses}}],
            _bsources(levels, extra=[("product", "Data.productName.keyword")]),
            agg, label)
        out = {}
        for b in buckets:
            key = _bkey(b["key"], levels) + (b["key"].get("product") or "",)
            val = (b["qty"]["value"] or 0) if "qty" in b else b["doc_count"]
            out[key] = out.get(key, 0) + val
        return out

    # AZM stock is BOTTLES (30 doses each) and a redose pours from the same
    # bottle, so consumption = (SUCCESS + VISITED doses) / 30 — the Kebbi AZM
    # report's convention. SPAQ/others: consumption = SUCCESS doses; redose is
    # a separate count subtracted from the CDD balance.
    is_azm = cfg.get("drug_type") == "AZM"
    consumed_statuses = (["ADMINISTRATION_SUCCESS", "VISITED"] if is_azm
                         else ["ADMINISTRATION_SUCCESS"])
    cdd_to_bnf = _task_leg(consumed_statuses, _sum_agg("Data.quantity"),
                           "CDD->BNF")
    if is_azm:
        cdd_to_bnf = {k: round(qty / AZM_DOSES_PER_BOTTLE, 2)
                      for k, qty in cdd_to_bnf.items()}
    redose = _task_leg(["VISITED"], {}, "redose")

    all_keys = (set(state_to_hf) | set(hf_to_state) | set(hf_to_cdd)
                | set(cdd_to_hf) | set(damaged) | set(lost)
                | set(cdd_to_bnf) | set(redose))
    if not (state_to_hf or hf_to_cdd or hf_to_state or cdd_to_hf):
        return None      # zero stock movement documents matched — the no-op

    rows = []
    gross_issued = 0
    for key in sorted(all_keys):
        s2h = state_to_hf.get(key, 0); h2s = hf_to_state.get(key, 0)
        h2c = hf_to_cdd.get(key, 0);   c2h = cdd_to_hf.get(key, 0)
        c2b = cdd_to_bnf.get(key, 0);  red = redose.get(key, 0)
        dam = damaged.get(key, 0);     los = lost.get(key, 0)
        # AZM: redose doses are already inside consumption (same bottle), so
        # only SPAQ-style products subtract redose from the CDD balance.
        cdd_bal = (h2c - (c2b + c2h) if is_azm
                   else h2c - (c2b + red + c2h))
        # Strict stock-journey order: received -> given -> used -> returns ->
        # shrinkage, computed outcomes (net + balances) last.
        gross_issued += h2c
        # Every printed column counts REAL doses (see the NG layout comment);
        # the gross handover counter feeds totals["issued"] only.
        # block-closing order (see NG comment): CDD block then facility block
        rows.append(list(key) + [
            s2h,                                    # Received
            h2c - c2h,                              # real doses out
            c2b, red,
            cdd_bal,                                # Stock Left with CDDs
            c2h,                                    # Returned by CDDs
            h2s,                                    # Returned to state
            dam, los,
            # canonical balance (agg_stock_summary): damage/loss is real
            # shrinkage, not stock in hand
            (s2h + c2h) - (h2s + h2c) - dam - los,  # Stock at HF
        ])

    unit = "bottles" if is_azm else "doses"
    _redose_txt = "" if is_azm else " - redose"
    headers = (list(levels) + ["Product",
               "Received by HF",
               "Stock Given to CDDs (actual stock, returned stock not counted again)",
               f"Used by CDDs ({unit})", "Redose",
               "Stock Left with CDDs",
               "Returned by CDDs to HF", "Returned by HF to State",
               "Damaged", "Lost",
               "Stock Left at HF"])
    n = len(levels) + 1
    totals = {
        "received":  sum(r[n] for r in rows),
        "issued":    gross_issued,
        "consumed":  sum(r[n + 2] for r in rows),
        "redose":    sum(r[n + 3] for r in rows),
        "returned":  sum(r[n + 5] for r in rows),
        "returned_upstream": sum(r[n + 6] for r in rows),
        "damaged":   sum(r[n + 7] for r in rows),
        "lost":      sum(r[n + 8] for r in rows),
        "balance_hf":  sum(r[n + 9] for r in rows),
        "balance_cdd": sum(r[n + 4] for r in rows),
    }
    # column index map for the audience-oriented report tables
    ix = {"lga": next((i for i, lvl in enumerate(levels)
                       if lvl.lower() in ("lga", "district")), None),
          "hf": len(levels) - 1, "product": len(levels),
          "received": n, "issued": n + 1,
          "consumed": n + 2, "damaged": n + 7, "lost": n + 8,
          "bal_hf": n + 9, "bal_cdd": n + 4}
    return {"variant": "smc", "levels": levels, "headers": headers,
            "rows": rows, "totals": totals, "ix": ix,
            "cdd_rows": _collect_cdd_accountability(cfg, v1, task)}


def _collect_cdd_accountability(cfg, v1_index, task_index):
    """Per user x product: received vs consumed vs returned (plus the
    unused/partial/wasted blister and empty-bottle quantities the SMC and AZM
    apps record under additionalDetails)."""
    sources = [{"user": {"terms": {"field": "Data.userName.keyword"}}},
               {"product": {"terms": {"field": "Data.productName.keyword",
                                      "missing_bucket": True}}}]
    sub = {
        "received": {"filter": {"bool": {"must": [
            {"term": {"Data.eventType.keyword": "RECEIVED"}},
            {"term": {"Data.reason.keyword": "RECEIVED"}}]}},
            "aggs": _sum_agg()},
        "returned": {"filter": {"term": {"Data.reason.keyword": "RETURNED"}},
                     "aggs": _sum_agg()},
        "unused":  {"sum": {"field": f"{_ADDL}.unused_quantity"}},
        "partial": {"sum": {"field": f"{_ADDL}.partial_quantity"}},
        "wasted":  {"sum": {"field": f"{_ADDL}.wastedBlistersReturned"}},
        "empty":   {"sum": {"field": f"{_ADDL}.emptyBottlesReturned"}},
    }
    buckets = _composite(
        cfg, v1_index,
        _stock_must(cfg) + [{"term": {"Data.facilityType.keyword": "STAFF"}}],
        sources, sub, "CDD accountability")

    is_azm = cfg.get("drug_type") == "AZM"
    consumed_statuses = (["ADMINISTRATION_SUCCESS", "VISITED"] if is_azm
                         else ["ADMINISTRATION_SUCCESS"])
    consumed = {}
    tsources = [{"user": {"terms": {"field": "Data.userName.keyword"}}},
                {"product": {"terms": {"field": "Data.productName.keyword",
                                       "missing_bucket": True}}}]
    for b in _composite(
            cfg, task_index,
            _task_must(cfg) + [{"terms": {
                "Data.administrationStatus.keyword": consumed_statuses}}],
            tsources, _sum_agg("Data.quantity"), "CDD consumption"):
        qty = b["qty"]["value"] or 0
        if is_azm:
            qty = round(qty / AZM_DOSES_PER_BOTTLE, 2)
        consumed[(b["key"]["user"], b["key"].get("product") or "")] = qty

    # Union with consumed-only users: a CDD who administered but has NO stock
    # documents at all must still appear (that is exactly the two-login case).
    stock_side = {}
    for b in buckets:
        key = (b["key"]["user"], b["key"].get("product") or "")
        stock_side[key] = [b["received"]["qty"]["value"] or 0,
                           b["returned"]["qty"]["value"] or 0,
                           b["unused"]["value"] or 0,
                           b["partial"]["value"] or 0,
                           b["wasted"]["value"] or 0,
                           b["empty"]["value"] or 0]

    rows = []
    for key in set(stock_side) | set(consumed):
        rec, ret, unused, partial, wasted, empty = stock_side.get(
            key, [0, 0, 0, 0, 0, 0])
        con = consumed.get(key, 0)
        rows.append([key[0], key[1], rec, con, ret, rec - con - ret,
                     unused, partial, wasted, empty,
                     _high_negative_flag(rec, con, ret)])
    rows.sort(key=lambda r: abs(r[5]), reverse=True)
    return rows


# ── ITN / LLIN ────────────────────────────────────────────────────────────────

_DUP_REDUCE = """
    Map m = new HashMap();
    for (s in states) {
        for (c in s) {
            m.put(c, m.getOrDefault(c, 0) + 1);
        }
    }
    int dup = 0;
    for (v in m.values()) {
        if (v > 1) dup += (v - 1);
    }
    return dup;
"""


def _dup_metric(lists):
    """Duplicate bednet-code counter over one or two additionalDetails list
    fields — copied from the proven Chad ITN report."""
    reads = "\n".join(
        f"""if (d.{fld} != null) {{ for (c in d.{fld}) {{ state.codes.add(c); }} }}"""
        for fld in lists)
    return {"scripted_metric": {
        "init_script": "state.codes = []",
        "map_script": f"""
            if (params._source.Data?.additionalDetails != null) {{
                def d = params._source.Data.additionalDetails;
                {reads}
            }}
        """,
        "combine_script": "return state.codes",
        "reduce_script": _DUP_REDUCE,
    }}


def _nested_boundary_aggs(levels, leaf_aggs):
    aggs = leaf_aggs
    for lvl in reversed(levels):
        aggs = {f"by_{lvl}": {
            "terms": {"field": f"Data.boundaryHierarchy.{lvl}.keyword",
                      "size": _TERMS_SIZE, "missing": "—"},
            "aggs": aggs}}
    return aggs


def _walk_buckets(agg_result, levels):
    def _rec(node, depth, prefix):
        lvl = levels[depth]
        for b in node[f"by_{lvl}"]["buckets"]:
            key = prefix + (b["key"],)
            if depth + 1 == len(levels):
                yield key, b
            else:
                yield from _rec(b, depth + 1, key)
    yield from _rec(agg_result["aggregations"], 0, ())


# ═══════════════════════════════════════════════════════════════════════════════
#  ITN — two stock models, chosen per campaign
#
#  SCANNER model (Chad ITN): bales + bale scans + bednet codes scanned, stock
#  as eventType/reason, sender-side records only. _collect_itn_scanner.
#
#  NO-SCANNER model (Borno ITN, 2026-10): no scans at all; stock follows the
#  NIGERIA convention (stockEntryType ISSUED/RETURNED x status ACCEPTED/
#  REJECTED/IN_TRANSIT, one record per transfer). Verified on bo
#  CMP-2026-09-21-000561 (4,213 docs, every bucket accounted for):
#      Central Facility -> Warehouse -> LGA Facility -> Distribution Hub
#          -> STAFF (distributor)
#  and returns back up the same chain. The ledger is the SMC one moved down a
#  level: LGA Facility plays the State, the Distribution Hub plays the HF and
#  the distributor plays the CDD — so the SAME _leg_query and _ledger_maths
#  are reused unchanged, only facility types and column titles differ.
#
#  Choice (first match wins):
#    1. sheet cell itn_scanner            TRUE / FALSE (blank = not set)
#    2. env DST_STOCK_ITN_SCANNER         TRUE / FALSE
#    3. STOCK_ITN_SCANNER_DEFAULT = FALSE -> no-scanner. Chad must set
#       itn_scanner=TRUE on its row. A mismatch with the data's own
#       convention is logged as a warning (see _itn_scanner_mode).
# ═══════════════════════════════════════════════════════════════════════════════

def _itn_scanner_mode(cfg, v1):
    # ONE sheet column, itn_scanner, drives every scanner/no-scanner choice
    # (stock model here, bednet code DQ in analyze_itn/report_itn).
    choice = cfg.get("itn_scanner")
    src = "sheet itn_scanner"
    if choice is None:
        raw = os.getenv("DST_STOCK_ITN_SCANNER", "").strip().upper()
        if raw:
            choice, src = raw in ("TRUE", "YES", "1", "Y", "ON"), "DST_STOCK_ITN_SCANNER"
        else:
            choice = STOCK_ITN_SCANNER_DEFAULT.upper() in ("TRUE", "YES", "1", "Y", "ON")
            src = "default"
    log.info(f"  [stock] ITN {'SCANNER' if choice else 'NO-SCANNER'} model "
             f"({src})")
    # The choice is the operator's, but a wrong one prints a ledger of zeros
    # (no-scanner on Chad data) or a scan table of zeros (scanner on Borno
    # data). Say so instead of letting it pass as a quiet stock month.
    try:
        ng_convention = _uses_entry_status(cfg, v1)
    except Exception as e:                                       # noqa: BLE001
        log.warning(f"  [stock] could not check the stock data convention: {e}")
    else:
        if bool(choice) == ng_convention:
            log.warning(
                f"  [stock] ITN {'SCANNER' if choice else 'NO-SCANNER'} model "
                f"chosen ({src}), but this campaign's stock records look "
                f"{'Nigeria-style (no scanner)' if ng_convention else 'scanner-style (Chad)'}"
                f" — set itn_scanner="
                f"{'FALSE' if ng_convention else 'TRUE'} on the sheet row if "
                f"the stock section comes out empty.")
    return bool(choice)


# Hub -> distributor issue leg guard, same idea as _ISSUE_TO_CDD_ONLY: written
# as exclusions so documents lacking the field are kept. On bo every hub ISSUED
# record has a STAFF counterparty (3,130 docs), so this removes nothing today.
_ISSUE_TO_DISTRIBUTOR_ONLY = [{"bool": {"must_not": [
    {"term": {"Data.reason.keyword": "RETURNED"}},
    {"terms": {"Data.transactingFacilityType.keyword":
               ["LGA Facility", "Distribution Hub", "Warehouse", "WAREHOUSE",
                "Central Facility", "State Facility"]}},
]}}]

_ITN_UPSTREAM = ["LGA Facility"]
_ITN_HUB = ["Distribution Hub"]

# Metric names are deliberately the SMC ones, so _ledger_maths runs unchanged:
#   state -> LGA Facility, hf -> Distribution Hub, cdd -> distributor.
# Only the printed column titles (in _collect_itn_ledger) say hub/distributor.
ITN_NG_LEGS = (
    _Leg(key="state", column="Sent by LGA Facility to Hub",
         facility_types=_ITN_UPSTREAM, group_by="transactingFacilityName",
         entry_type="ISSUED", extra=(),
         sent="sent_by_state_to_hf", accepted="received_by_hf_from_state",
         rejected="rejected_by_hf", in_transit="in_transit_state_to_hf"),
    _Leg(key="issue", column="Sent by Hub to Distributor",
         facility_types=_ITN_HUB, group_by="facilityName",
         entry_type="ISSUED", extra=_ISSUE_TO_DISTRIBUTOR_ONLY,
         sent="sent_by_hf_to_cdd", accepted="accepted_by_cdd",
         rejected="rejected_by_cdd", in_transit="in_transit_hf_to_cdd"),
    _Leg(key="cdd_return", column="Returned by Distributor to Hub",
         facility_types=["STAFF"], group_by="transactingFacilityName",
         entry_type="RETURNED", extra=(),
         sent="returned_by_cdd_to_hf", accepted="return_received_by_hf",
         rejected="return_rejected_by_hf", in_transit="return_in_transit_to_hf"),
    _Leg(key="hf_return", column="Returned by Hub to LGA Facility",
         facility_types=_ITN_HUB, group_by="facilityName",
         entry_type="RETURNED", extra=(),
         sent="returned_by_hf_to_state", accepted="return_received_by_state",
         rejected="return_rejected_by_state",
         in_transit="return_in_transit_to_state"),
)
_ITN_METRIC_LEG = {name: leg.key for leg in ITN_NG_LEGS
                   for name in (leg.sent, leg.accepted, leg.rejected, leg.in_transit)}

_ITN_LEDGER_HEADER_NOTES = {
    "Sent by LGA Facility to Hub": (
        "Every dispatch the LGA Facility recorded to this hub, whatever its "
        "status."),
    "Received by Hub from LGA Facility": (
        "Of Sent by LGA Facility to Hub, the part the hub accepted."),
    "Rejected by Hub": (
        "Of Sent by LGA Facility to Hub, the part the hub refused. It stays "
        "with the LGA Facility."),
    "In Transit from LGA Facility to Hub": (
        "Of Sent by LGA Facility to Hub, dispatched but not yet accepted or "
        "refused by the hub. It is on neither balance."),
    "Sent by Hub to Distributor": (
        "Every handover the hub recorded to a distributor, whatever its "
        "status. Bednets returned and given out again are counted on each "
        "trip. Bednets Given to Distributors removes the re-issues."),
    "Bednets Given to Distributors": (
        "= Sent by Hub to Distributor - Rejected by Distributor - (Returned "
        "by Distributor to Hub - Return Rejected by Hub)"),
    "Rejected by Distributor": (
        "Of Sent by Hub to Distributor, the part the distributor refused. It "
        "goes back to the hub."),
    "In Transit from Hub to Distributor": (
        "Of Sent by Hub to Distributor, handed over but not yet accepted or "
        "refused by the distributor. It is on neither balance."),
    "Received by Distributor": (
        "= Bednets Given to Distributors - In Transit from Hub to Distributor"),
    "Distributed to Households": (
        "Sum of bednets on successful distribution records, as recorded in "
        "the app (a record synced twice counts twice)."),
    "Bednets Left with Distributors": (
        "= Received by Distributor - Distributed to Households"),
    "Returned by Distributor to Hub": (
        "Every return the distributor recorded to this hub, whatever its "
        "status."),
    "Return Received by Hub": (
        "Of Returned by Distributor to Hub, the part the hub accepted back."),
    "Return Rejected by Hub": (
        "Of Returned by Distributor to Hub, the part the hub refused. It "
        "stays with the distributor."),
    "Returned by Hub to LGA Facility": (
        "Every return the hub recorded to the LGA Facility, whatever its "
        "status."),
    "Return Received by LGA Facility": (
        "Of Returned by Hub to LGA Facility, the part the LGA Facility "
        "accepted back."),
    "Return Rejected by LGA Facility": (
        "Of Returned by Hub to LGA Facility, the part the LGA Facility "
        "refused. It stays with the hub."),
    "Bednets Left at Hub": (
        "= Received by Hub from LGA Facility + (Returned by Distributor to "
        "Hub - Return Rejected by Hub) - Sent by Hub to Distributor + "
        "Rejected by Distributor - (Returned by Hub to LGA Facility - Return "
        "Rejected by LGA Facility)"),
}


def _hub_locations(cfg, v1):
    """{hub facilityName: (lga, ward, boundary hub name)} from the hub's OWN
    records. The ledger keys on facilityName ("Bulama Mustapha DH2") while
    task docs carry the boundary name ("Bulama Mustapha"); the hub's own
    records hold both, so this is the join — never a guess on the name."""
    buckets = _composite(
        cfg, v1,
        _stock_must(cfg) + [{"terms": {"Data.facilityType.keyword": _ITN_HUB}}],
        [{"hub": {"terms": {"field": "Data.facilityName.keyword"}}},
         {"lga": {"terms": {"field": "Data.boundaryHierarchy.lga.keyword",
                            "missing_bucket": True}}},
         {"ward": {"terms": {"field": "Data.boundaryHierarchy.ward.keyword",
                             "missing_bucket": True}}},
         {"bhub": {"terms": {
             "field": "Data.boundaryHierarchy.distributionHub.keyword",
             "missing_bucket": True}}}],
        {}, "hub locations")
    best = {}
    for b in buckets:
        k = b["key"]
        loc = (k.get("lga") or "", k.get("ward") or "", k.get("bhub") or "")
        prev = best.get(k["hub"])
        if prev is None or b["doc_count"] > prev[1]:
            best[k["hub"]] = (loc, b["doc_count"])
    return {hub: loc for hub, (loc, _) in best.items()}


def _itn_distributed_records(cfg, task, by_day=False):
    """Successful distribution records from the task index, summed per
    distributor (userName) and boundary hub (+ day when by_day).

    Returns [(user, bhub, lga, ward, day_or_None, bednets)]."""
    sources = [
        {"user": {"terms": {"field": "Data.userName.keyword",
                            "missing_bucket": True}}},
        {"bhub": {"terms": {
            "field": "Data.boundaryHierarchy.distributionHub.keyword",
            "missing_bucket": True}}},
        {"lga": {"terms": {"field": "Data.boundaryHierarchy.lga.keyword",
                           "missing_bucket": True}}},
        {"ward": {"terms": {"field": "Data.boundaryHierarchy.ward.keyword",
                            "missing_bucket": True}}},
    ]
    if by_day:
        sources.insert(0, {"day": {"date_histogram": {
            "field": "Data.taskDates", "calendar_interval": "day"}}})
    out = []
    for b in _composite(
            cfg, task,
            _task_must(cfg) + [{"term": {
                "Data.administrationStatus.keyword": "ADMINISTRATION_SUCCESS"}}],
            sources, _sum_agg("Data.quantity"),
            "daily task distributed (ITN)" if by_day else "task distributed (ITN)"):
        k = b["key"]
        day = (datetime.fromtimestamp(k["day"] / 1000, tz=timezone.utc)
               .strftime("%Y-%m-%d") if by_day else None)
        out.append((k.get("user") or "", k.get("bhub") or "",
                    k.get("lga") or "", k.get("ward") or "", day,
                    b["qty"]["value"] or 0))
    return out


def _distributor_home_hub(cfg, v1):
    """Which hubs supplied each distributor (hub ISSUED records name the
    distributor in transactingFacilityName). This is how distribution reaches
    the right hub when one boundary hub holds several hubs (bo: 'Bulama
    Abdullahi' holds both '... DH' and '... DH1').

    Returns (home, suppliers):
      home       {user: hub}             the LARGEST supplier — where the
                                         distributor is listed on the
                                         accountability tab
      suppliers  {user: {hub: bednets}}  every supplier, for the attributor
    """
    sent = {}
    for b in _composite(
            cfg, v1,
            _stock_must(cfg) + [{"terms": {"Data.facilityType.keyword": _ITN_HUB}},
                                {"term": {_ENTRY: "ISSUED"}}]
            + _ISSUE_TO_DISTRIBUTOR_ONLY,
            [{"user": {"terms": {"field": "Data.transactingFacilityName.keyword"}}},
             {"hub": {"terms": {"field": "Data.facilityName.keyword"}}}],
            _sum_agg(), "distributor home hub"):
        user, hub = b["key"]["user"], b["key"]["hub"]
        sent.setdefault(user, {})[hub] = b["qty"]["value"] or 0
    home = {user: max(sorted(by_hub), key=lambda h: by_hub[h])
            for user, by_hub in sent.items()}
    multi = sorted(u for u, by_hub in sent.items() if len(by_hub) > 1)
    if multi:
        # one line, not one per distributor (bo Day 5: 106 of 751)
        log.info(f"  [stock] {len(multi)} of {len(sent)} distributors were "
                 f"supplied by more than one hub; each distribution record "
                 f"goes to the supplier at that record's location, else the "
                 f"largest supplier (e.g. {', '.join(multi[:3])})")
    return home, sent


def _itn_attributor(locations, products_of, home, suppliers=None):
    """Build attribute(user, bhub) -> ledger key (hub, product) or None.

    1. by DISTRIBUTOR: a hub that supplied this distributor (exact even when
       a boundary hub holds several hubs). A distributor supplied by several
       hubs: the supplier whose boundary hub is where this distribution
       record was made; if location cannot separate them, the largest;
    2. by BOUNDARY HUB name: for distributors with no handover record, the
       hub whose own records carry that boundary name — the first one when
       several share it (logged, since that guess may be wrong);
    3. None: no hub on record -> the caller adds an orphan row.
    """
    def row_key(hub):
        prods = sorted(products_of.get(hub, []))
        if not prods:
            return None
        return (hub, "ITN" if "ITN" in prods else prods[0])

    by_bhub = {}
    for hub, (_lga, _ward, bhub) in sorted(locations.items()):
        if hub in products_of:
            by_bhub.setdefault(bhub, []).append(hub)
    warned = set()

    suppliers = suppliers or {}

    def attribute(user, bhub):
        by_hub = suppliers.get(user)
        if by_hub and len(by_hub) > 1:
            here = [h for h in by_hub
                    if (locations.get(h) or ("", "", ""))[2] == bhub]
            pool = here or list(by_hub)
            key = row_key(max(sorted(pool), key=lambda h: by_hub[h]))
            if key:
                return key, "distributor"
        if user in home:
            key = row_key(home[user])
            if key:
                return key, "distributor"
        hubs = by_bhub.get(bhub) or []
        if hubs:
            if len(hubs) > 1 and bhub not in warned:
                warned.add(bhub)
                log.warning(f"  [stock] boundary hub {bhub!r} holds hubs "
                            f"{hubs}; distribution by distributors with NO "
                            f"handover record attached to {hubs[0]!r}")
            return row_key(hubs[0]), "boundary"
        return None, "none"
    return attribute


def _collect_itn_ledger(cfg, v1, task):
    """NO-SCANNER ITN ledger (Borno): the SMC Nigeria ledger one level down."""
    branches = {leg.key: _leg_branch(cfg, v1, leg) for leg in ITN_NG_LEGS}
    if not any(branches.values()):
        return None
    locations = _hub_locations(cfg, v1)
    home, suppliers = _distributor_home_hub(cfg, v1)

    keys = set()
    for per_key in branches.values():
        keys |= set(per_key)
    products_of = {}
    for hub, product in keys:
        products_of.setdefault(hub, []).append(product)

    # Distribution -> hub row: by the distributor who handed it out (see
    # _itn_attributor). The SAME attributor serves the daily tab, so both tabs
    # put every bednet on the same row.
    attribute = _itn_attributor(locations, products_of, home, suppliers)
    used_at, orphan_locs, how = {}, {}, {"distributor": 0, "boundary": 0, "none": 0}
    records = _itn_distributed_records(cfg, task)
    # where each distributor worked, from their own records — the fallback
    # location on the accountability tab for distributors with no handover
    task_loc, _best = {}, {}
    for user, bhub, lga, ward, _day, qty in records:
        if qty >= _best.get(user, -1):
            _best[user], task_loc[user] = qty, (lga, ward, bhub)
    for user, bhub, lga, ward, _day, qty in records:
        key, via = attribute(user, bhub)
        if key is None:
            # Distributed, but no hub on record at all: the hub still belongs
            # on the ledger — its stock simply was not recorded in the app.
            key = (bhub or "(no hub on record)", "ITN")
            keys.add(key)
            orphan_locs.setdefault(key[0], (lga, ward, bhub))
        how[via] += qty
        used_at[key] = used_at.get(key, 0) + qty
    log.info(f"  [stock] distribution attributed: {how['distributor']:,.0f} "
             f"bednets by distributor, {how['boundary']:,.0f} by boundary hub "
             f"name, {how['none']:,.0f} to hubs with no stock record")
    if orphan_locs:
        log.warning(f"  [stock] {len(orphan_locs)} hub(s) distributed bednets "
                    f"but have no stock record of their own (shown with zero "
                    f"stock movements)")

    rows, gross_issued = [], 0
    for key in sorted(keys):
        hub, product = key
        lga, ward, _ = locations.get(hub) or orphan_locs.get(hub) or ("", "", "")
        m = {name: (branches[_ITN_METRIC_LEG[name]].get(key) or {}).get(name, 0)
             for name in _ITN_METRIC_LEG}
        used = used_at.get(key, 0)
        r = _ledger_maths(m, used, 0)
        gross_issued += m["sent_by_hf_to_cdd"]
        rows.append([lga, ward, hub, product,
                     m["sent_by_state_to_hf"], m["received_by_hf_from_state"],
                     m["rejected_by_hf"], m["in_transit_state_to_hf"],
                     m["sent_by_hf_to_cdd"], r.given_to_cdds,
                     m["rejected_by_cdd"], m["in_transit_hf_to_cdd"],
                     r.received_by_cdds, used, r.left_with_cdds,
                     m["returned_by_cdd_to_hf"], m["return_received_by_hf"],
                     m["return_rejected_by_hf"],
                     m["returned_by_hf_to_state"], m["return_received_by_state"],
                     m["return_rejected_by_state"],
                     r.left_at_facility])

    headers = ["LGA", "Ward", "Distribution Hub", "Product",
               "Sent by LGA Facility to Hub", "Received by Hub from LGA Facility",
               "Rejected by Hub",
               "In Transit from LGA Facility to Hub (sent, not yet received)",
               "Sent by Hub to Distributor (all handovers, bednets given again "
               "counted each time)",
               "Bednets Given to Distributors (actual bednets, returned bednets "
               "not counted again)",
               "Rejected by Distributor",
               "In Transit from Hub to Distributor (sent, not yet received)",
               "Received by Distributor (actual bednets, returned bednets not "
               "counted again)",
               "Distributed to Households (as recorded)",
               "Bednets Left with Distributors",
               "Returned by Distributor to Hub", "Return Received by Hub",
               "Return Rejected by Hub",
               "Returned by Hub to LGA Facility", "Return Received by LGA Facility",
               "Return Rejected by LGA Facility",
               "Bednets Left at Hub"]

    def col(prefix):
        for i, h in enumerate(headers):
            if h.startswith(prefix):
                return i
        raise KeyError(f"no ITN ledger column starting with {prefix!r}")

    c = {name: col(prefix) for name, prefix in (
        ("received", "Received by Hub from LGA Facility"),
        ("rej_in", "Rejected by Hub"), ("given", "Bednets Given"),
        ("rej_out", "Rejected by Distributor"),
        ("transit_out", "In Transit from Hub"),
        ("transit_in", "In Transit from LGA"),
        ("used", "Distributed to Households"),
        ("left_dist", "Bednets Left with Distributors"),
        ("ret", "Returned by Distributor to Hub"),
        ("ret_rej", "Return Rejected by Hub"),
        ("ret_up", "Returned by Hub to LGA Facility"),
        ("ret_up_rej", "Return Rejected by LGA Facility"),
        ("left_hub", "Bednets Left at Hub"))}

    def tot(name):
        return sum(r[c[name]] for r in rows)

    totals = {
        "received": tot("received"), "issued": gross_issued,
        "given": tot("given"),
        "returned": tot("ret") - tot("ret_rej"),
        "returned_upstream": tot("ret_up") - tot("ret_up_rej"),
        "rejected_in": tot("rej_in"), "rejected_out": tot("rej_out"),
        "in_transit_in": tot("transit_in"), "in_transit_out": tot("transit_out"),
        "consumed": tot("used"), "redose": 0, "damaged": 0, "lost": 0,
        "balance_hf": tot("left_hub"), "balance_cdd": tot("left_dist"),
    }
    rows.sort(key=lambda r: (r[0], r[1], r[2], r[3]))
    ix = {"lga": 0, "hf": 2, "product": 3, "received": c["received"],
          "issued": c["given"], "consumed": c["used"], "damaged": None,
          "lost": None, "bal_hf": c["left_hub"], "bal_cdd": c["left_dist"]}
    loc_of = {**{h: (l, w) for h, (l, w, _) in locations.items()},
              **{h: (l, w) for h, (l, w, _) in orphan_locs.items()}}
    # DAILY FLOW and DISTRIBUTOR ACCOUNTABILITY tabs — ITN-only functions, each
    # non-fatal: a failure drops that tab, never the ledger.
    try:
        daily_rows = _collect_itn_daily_flow(cfg, v1, task, attribute, loc_of)
    except Exception as e:                                       # noqa: BLE001
        log.warning(f"  [stock] ITN daily flow failed (tab skipped): {e}")
        daily_rows = []
    try:
        dist_rows = _collect_distributor_accountability(
            cfg, v1, task, home=home, loc_of=loc_of, task_loc=task_loc)
    except Exception as e:                                       # noqa: BLE001
        log.warning(f"  [stock] distributor accountability failed (tab "
                    f"skipped): {e}")
        dist_rows = []
    recv_ix = col("Received by Distributor")
    daily_headers = ["Date"] + headers
    daily_headers[recv_ix + 1] = "Received by Distributor (as recorded in the app)"
    daily_headers.insert(
        recv_ix + 2,
        "Available with Distributors (yesterday's stock + received today)")
    return {"variant": "itn_ledger", "ng": True,
            "levels": ["LGA", "Ward", "Distribution Hub"],
            "headers": headers, "rows": rows, "totals": totals, "ix": ix,
            "daily_rows": daily_rows, "daily_headers": daily_headers,
            "cdd_rows": dist_rows}


def _collect_itn_daily_flow(cfg, v1, task, attribute, loc_of):
    """ITN DAILY FLOW: the ITN ledger's columns plus Date, per day, with the
    SAME formulas as the ledger (and as the SMC daily tab — kept separate so
    SMC is never touched). Movement columns are that day's movements; the two
    Bednets Left columns are running balances at the END of the day, so a
    hub's last day equals its ledger row.

    Day buckets: Data.@timestamp for stock (device clock — a record stamped in
    the wrong year appears on that wrong day, which is the honest place for
    it), taskDates for distribution."""
    def _day(ms):
        return datetime.fromtimestamp(ms / 1000, tz=timezone.utc).strftime("%Y-%m-%d")

    day_source = [{"day": {"date_histogram": {"field": "Data.@timestamp",
                                              "calendar_interval": "day"}}}]

    def _day_key(bucket_key):
        return (bucket_key.get("hf") or "", bucket_key.get("product") or "",
                _day(bucket_key["day"]))

    vals = {}
    for leg in ITN_NG_LEGS:
        for key, metrics in _leg_branch(cfg, v1, leg, day_source, _day_key).items():
            totals = vals.setdefault(key, {})
            for name, qty in metrics.items():
                totals[name] = totals.get(name, 0) + qty

    # daily distribution, attached by the SAME attributor as the ledger
    for user, bhub, _lga, _ward, day, qty in _itn_distributed_records(
            cfg, task, by_day=True):
        target, _via = attribute(user, bhub)
        if target is None:
            target = (bhub or "(no hub on record)", "ITN")   # ledger orphan row
        m = vals.setdefault(target + (day,), {})
        m["con"] = m.get("con", 0) + qty

    rows, run = [], {}
    for hub, product, day in sorted(vals):
        g = vals[(hub, product, day)].get
        returns_kept_by_hub = (g("returned_by_cdd_to_hf", 0)
                               - g("return_rejected_by_hf", 0))
        returns_sent_up = (g("returned_by_hf_to_state", 0)
                           - g("return_rejected_by_state", 0))
        given = (g("sent_by_hf_to_cdd", 0) - g("rejected_by_cdd", 0)
                 - returns_kept_by_hub)
        # As on the SMC daily tab: Received is the app's RECORDED accepted
        # quantity for that day; the day's returns come off the same row.
        recv = g("accepted_by_cdd", 0)
        con = g("con", 0)
        r = run.setdefault((hub, product), {"carry": 0, "hub": 0})
        available = r["carry"] + recv
        left_dist = available - con - returns_kept_by_hub
        r["carry"] = left_dist
        r["hub"] += (g("received_by_hf_from_state", 0) + returns_kept_by_hub
                     - g("sent_by_hf_to_cdd", 0) + g("rejected_by_cdd", 0)
                     - returns_sent_up)
        lga, ward = loc_of.get(hub, ("", ""))
        rows.append([day, lga, ward, hub, product,
                     g("sent_by_state_to_hf", 0),
                     g("received_by_hf_from_state", 0),
                     g("rejected_by_hf", 0),
                     g("in_transit_state_to_hf", 0),
                     g("sent_by_hf_to_cdd", 0),
                     given,
                     g("rejected_by_cdd", 0),
                     g("in_transit_hf_to_cdd", 0),
                     recv, available, con,
                     left_dist,
                     g("returned_by_cdd_to_hf", 0),
                     g("return_received_by_hf", 0),
                     g("return_rejected_by_hf", 0),
                     g("returned_by_hf_to_state", 0),
                     g("return_received_by_state", 0),
                     g("return_rejected_by_state", 0),
                     r["hub"]])
    return rows


_DIST_HEADERS = ["LGA", "Ward", "Distribution Hub", "Distributor (user)",
                 "Product", "Received", "Distributed", "Returned",
                 "Difference (= received - distributed - returned)"]


def _dist_flagged(row):
    """The red rows (ITN distributors AND SMC CDDs): handed out / used MORE
    than recorded as given to them — the only gap that counts against a
    clean record. Every accountability builder puts the flag at index 10."""
    return bool(row[10]) if len(row) > 10 else False


def _dist_display_rows(rows):
    """Accountability rows in TAB order: location first, then the numbers,
    then the flag (index 9, drives the red fill, not written as a column)."""
    return [[r[11], r[12], r[13], r[0], r[1], r[2], r[3], r[4], r[5], r[10]]
            for r in rows]


def _collect_distributor_accountability(cfg, v1, task, home=None, loc_of=None,
                                        task_loc=None):
    """Per-distributor stock check (ITN): what each distributor accepted from
    a hub, distributed to households, and returned. Same shape as the SMC CDD
    accountability rows so the Word audit table reads them unchanged:
    [user, product, received, distributed, returned, difference,
     0, 0, 0, 0, flag, lga, ward, hub] — the four zeros are SMC blister/bottle
    columns that do not exist for bednets (not written to the ITN tab).

    WHERE A DISTRIBUTOR BELONGS: the hub that supplied them (home, the same
    rule that attributes their distribution on the ledger), with that hub's
    LGA/Ward (loc_of). A distributor with no handover record falls back to the
    boundary LGA/Ward/hub of their own distribution records (task_loc).

    Keys: the hub's ISSUED record names the distributor in
    transactingFacilityName; the distributor's own RETURNED record in
    facilityName; distribution records in userName. Product is the stock
    product name; distribution records are attributed to the distributor's
    stock product (bednets are one product)."""
    sources = [{"user": {"terms": {"field": "Data.transactingFacilityName.keyword"}}},
               {"product": {"terms": {"field": "Data.productName.keyword",
                                      "missing_bucket": True}}}]
    received = {}
    for b in _composite(
            cfg, v1,
            _stock_must(cfg) + [
                {"terms": {"Data.facilityType.keyword": _ITN_HUB}},
                {"term": {_ENTRY: "ISSUED"}},
                {"term": {_STATUS: "ACCEPTED"}}] + _ISSUE_TO_DISTRIBUTOR_ONLY,
            sources, _sum_agg(), "ITN accountability received"):
        key = (b["key"]["user"], b["key"].get("product") or "")
        received[key] = received.get(key, 0) + (b["qty"]["value"] or 0)

    rsources = [{"user": {"terms": {"field": "Data.facilityName.keyword"}}},
                {"product": {"terms": {"field": "Data.productName.keyword",
                                       "missing_bucket": True}}}]
    returned = {}
    for b in _composite(
            cfg, v1,
            _stock_must(cfg) + [{"term": {"Data.facilityType.keyword": "STAFF"}},
                                {"term": {_ENTRY: "RETURNED"}},
                                {"term": {_STATUS: "ACCEPTED"}}],
            rsources, _sum_agg(), "ITN accountability returns"):
        key = (b["key"]["user"], b["key"].get("product") or "")
        returned[key] = returned.get(key, 0) + (b["qty"]["value"] or 0)

    product_of = {}
    for user, product in list(received) + list(returned):
        product_of.setdefault(user, product)
    default_product = "ITN"
    distributed = {}
    for b in _composite(
            cfg, task,
            _task_must(cfg) + [{"term": {
                "Data.administrationStatus.keyword": "ADMINISTRATION_SUCCESS"}}],
            [{"user": {"terms": {"field": "Data.userName.keyword"}}}],
            _sum_agg("Data.quantity"), "ITN accountability distributed"):
        user = b["key"]["user"]
        key = (user, product_of.get(user, default_product))
        distributed[key] = distributed.get(key, 0) + (b["qty"]["value"] or 0)

    home, loc_of, task_loc = home or {}, loc_of or {}, task_loc or {}

    def where(user):
        hub = home.get(user)
        if hub:
            lga, ward = loc_of.get(hub, ("", ""))
            return lga, ward, hub
        lga, ward, bhub = task_loc.get(user, ("", "", ""))
        return lga, ward, (f"{bhub} (no handover record)" if bhub else "")

    rows = []
    for key in sorted(set(received) | set(returned) | set(distributed)):
        rec, con, ret = (received.get(key, 0), distributed.get(key, 0),
                         returned.get(key, 0))
        rows.append([key[0], key[1], rec, con, ret, rec - con - ret,
                     0, 0, 0, 0, _high_negative_flag(rec, con, ret),
                     *where(key[0])])
    rows.sort(key=lambda r: (-abs(r[5]), r[0]))
    return rows


def _collect_itn(cfg):
    v1 = _stock_index(cfg)
    if not _itn_scanner_mode(cfg, v1):
        return _collect_itn_ledger(cfg, v1, cfg["ES_INDEX_TASK"])
    return _collect_itn_scanner(cfg, v1)


def _collect_itn_scanner(cfg, v1):
    """SCANNER ITN model (Chad): bales, scans, codes, eventType/reason stock."""
    levels = _boundary_levels(cfg, probe_index=v1)

    stock_leaf = {
        "bales_quantity":    {"sum": {"field": f"{_ADDL}.balesQuantity"}},
        "actual_bale_scans": {"sum": {"field": f"{_ADDL}.actualBaleScans"}},
        "manual_bale_scans": {"sum": {"field": f"{_ADDL}.manualBaleScans"}},
        "stock_received": {"filter": {"bool": {"must": [
            {"term": {"Data.eventType.keyword": "RECEIVED"}},
            {"term": {"Data.reason.keyword": "RECEIVED"}}]}},
            "aggs": {"total": {"sum": {"field": "Data.physicalCount"}}}},
        "stock_returned": {"filter": {"bool": {"must": [
            {"term": {"Data.eventType.keyword": "RECEIVED"}},
            {"term": {"Data.reason.keyword": "RETURNED"}}]}},
            "aggs": {"total": {"sum": {"field": "Data.physicalCount"}}}},
        "stock_issued": {"filter": {"bool": {
            "must": [{"term": {"Data.eventType.keyword": "DISPATCHED"}}],
            "must_not": [{"exists": {"field": "Data.reason"}}]}},
            "aggs": {"total": {"sum": {"field": "Data.physicalCount"}}}},
        "stock_wasted": {"filter": {"bool": {"must": [
            {"term": {"Data.eventType.keyword": "DISPATCHED"}},
            {"exists": {"field": "Data.reason"}}]}},
            "aggs": {"total": {"sum": {"field": "Data.physicalCount"}}}},
    }
    stock_res = _search(cfg, v1, {
        "size": 0, "query": {"bool": {"must": _stock_must(cfg)}},
        "aggs": _nested_boundary_aggs(levels, stock_leaf)}, "ITN stock")

    dist_leaf = {
        "distributor": {"filter": {"term": {"Data.role.keyword": "DISTRIBUTOR"}},
                        "aggs": {"total": {"sum": {"field": "Data.quantity"}}}},
        "registrar": {"filter": {"term": {
            "Data.role.keyword": "DISTRIBUTOR_REGISTRAR"}},
            "aggs": {"total": {"sum": {"field": "Data.quantity"}}}},
        "manual_codes":  {"sum": {"field": f"{_ADDL}.manualCodes"}},
        "codes_scanned": {"sum": {"field": f"{_ADDL}.codesScanned"}},
        "dup_codes":   _dup_metric(["manualCodesList", "codesScannedList"]),
        "dup_manual":  _dup_metric(["manualCodesList"]),
        "dup_scanned": _dup_metric(["codesScannedList"]),
    }
    dist_res = _search(cfg, cfg["ES_INDEX_TASK"], {
        "size": 0, "query": {"bool": {"must": _task_must(cfg)}},
        "aggs": _nested_boundary_aggs(levels, dist_leaf)}, "ITN distribution")

    dist_map = {}
    for key, b in _walk_buckets(dist_res, levels):
        manual = b["manual_codes"]["value"] or 0
        scanned = b["codes_scanned"]["value"] or 0
        dist_map[key] = {
            "distributor": b["distributor"]["total"]["value"] or 0,
            "registrar":   b["registrar"]["total"]["value"] or 0,
            "manual_codes": manual, "codes_scanned": scanned,
            "actual_quantity": manual + scanned,
            "dup_codes":   b["dup_codes"]["value"] or 0,
            "dup_manual":  b["dup_manual"]["value"] or 0,
            "dup_scanned": b["dup_scanned"]["value"] or 0,
        }

    rows, seen = [], set()
    for key, b in _walk_buckets(stock_res, levels):
        seen.add(key)
        d = dist_map.get(key, {})
        rows.append(list(key) + [
            b["bales_quantity"]["value"] or 0,
            b["actual_bale_scans"]["value"] or 0,
            b["manual_bale_scans"]["value"] or 0,
            d.get("manual_codes", 0), d.get("codes_scanned", 0),
            d.get("actual_quantity", 0), d.get("dup_codes", 0),
            d.get("dup_manual", 0), d.get("dup_scanned", 0),
            b["stock_received"]["total"]["value"] or 0,
            b["stock_returned"]["total"]["value"] or 0,
            b["stock_issued"]["total"]["value"] or 0,
            b["stock_wasted"]["total"]["value"] or 0,
            d.get("distributor", 0), d.get("registrar", 0),
        ])
    for key, d in dist_map.items():
        if key not in seen:
            rows.append(list(key) + [
                0, 0, 0, d["manual_codes"], d["codes_scanned"],
                d["actual_quantity"], d["dup_codes"], d["dup_manual"],
                d["dup_scanned"], 0, 0, 0, 0,
                d["distributor"], d["registrar"]])
    if not rows:
        return None

    rows.sort(key=lambda r: tuple(r[:len(levels)]))
    headers = ([lvl.capitalize() for lvl in levels] + [
        "Bales Quantity", "Actual Bale Scans", "Manual Bale Scans",
        "Manual Codes", "Codes Scanned", "Actual Quantity Delivered",
        "Duplicate Bednet Codes", "Duplicate Manual Codes",
        "Duplicate Scanned Codes", "Stock Received", "Stock Returned",
        "Stock Issued", "Stock Wasted", "Distributor", "Registrar"])
    n = len(levels)
    totals = {
        "received": sum(r[n + 9] for r in rows),
        "returned": sum(r[n + 10] for r in rows),
        "issued":   sum(r[n + 11] for r in rows),
        "wasted":   sum(r[n + 12] for r in rows),
        "delivered": sum(r[n + 5] for r in rows),
        "dup_codes": sum(r[n + 6] for r in rows),
    }
    return {"variant": "itn", "levels": levels, "headers": headers,
            "rows": rows, "totals": totals, "cdd_rows": []}


# ── workbook (styles inlined — no pipeline.core here) ─────────────────────────

_thin = Side(border_style="thin", color="CCCCCC")
_BORDER = Border(left=_thin, right=_thin, top=_thin, bottom=_thin)
_BANNER_FILL = PatternFill("solid", fgColor="17365D")
_HDR_FILL = PatternFill("solid", fgColor="1A6496")
_TOTAL_FILL = PatternFill("solid", fgColor="EEEEEE")
_FLAG_FILL = PatternFill("solid", fgColor="FFC7CE")   # light red
_FLAG_COLOR = "9C0006"                                # dark red text
_TINT_FILL = PatternFill("solid", fgColor="DCE6F1")   # light blue — anchor col

# Hover notes on computed ledger headers: FORMULA ONLY (user rule 2026-09-24
# — no narrative in cell comments). Keyed by header prefix.
#
# Every term names BOTH ENDS of the movement, so a reader never has to work out
# who sent what to whom. "Total Handovers" is gone — it said neither where the
# stock came from nor where it went.
# A real column now, so the formulas can name it plainly. It counts handover
# RECORDS including RE-ISSUES: stock a CDD returned and was given again is
# counted on every trip, which is why it can exceed what the HF ever received.
_SENT_HF_TO_CDD = "Sent by HF to CDD"

_LEDGER_HEADER_NOTES = {
    "Sent by State to HF": (
        "Every dispatch the State recorded to this HF, whatever its status."),
    "Received by HF from State": (
        "Of Sent by State to HF, the part the HF accepted."),
    "Rejected by HF": (
        "Of Sent by State to HF, the part the HF refused. It stays with the "
        "State."),
    "In Transit from State to HF": (
        "Of Sent by State to HF, dispatched but not yet accepted or refused "
        "by the HF. It is on neither balance."),

    "Sent by HF to CDD": (
        "Every handover the HF recorded to a CDD, whatever its status. Stock "
        "returned by a CDD and given out again is counted on each trip, so "
        "this can exceed Received by HF from State. Stock Given to CDDs "
        "removes the re-issues."),
    "Stock Given to CDDs": (
        f"= {_SENT_HF_TO_CDD} - Rejected by CDD "
        "- (Returned by CDD to HF - Return Rejected by HF)"),
    "Rejected by CDD": (
        f"Of {_SENT_HF_TO_CDD}, the part the CDD refused. It goes back to "
        "the HF."),
    "In Transit from HF to CDD": (
        f"Of {_SENT_HF_TO_CDD}, handed over but not yet accepted or refused "
        "by the CDD. It is on neither balance."),
    "Received by CDD (actual stock": (
        "= Stock Given to CDDs - In Transit from HF to CDD"),
    "Received by CDD (as recorded": (
        "The app's recorded accepted quantity for that day (no netting; "
        "returned stock given again is counted on each trip)."),

    "Returned by CDD to HF": (
        "Every return the CDD recorded to this HF, whatever its status."),
    "Return Received by HF": (
        "Of Returned by CDD to HF, the part the HF accepted back."),
    "Return Rejected by HF": (
        "Of Returned by CDD to HF, the part the HF refused. It stays with "
        "the CDD."),
    "Returned by HF to State": (
        "Every return the HF recorded to the State, whatever its status."),
    "Return Received by State": (
        "Of Returned by HF to State, the part the State accepted back."),
    "Return Rejected by State": (
        "Of Returned by HF to State, the part the State refused. It stays "
        "with the HF."),

    "Stock Left at HF": (
        "= Received by HF from State + (Returned by CDD to HF - Return "
        f"Rejected by HF) - {_SENT_HF_TO_CDD} + Rejected by CDD "
        "- (Returned by HF to State - Return Rejected by State)"),
    "Stock Left with CDDs": (
        "= Received by CDD - Used by CDD - Redose"),
    "Available with CDDs": (
        "= Stock Left with CDDs (yesterday) + Received by CDD (today)"),
}


def _style_cell(cell, fill=None, bold=False, color=None, align="center", size=9):
    cell.border = _BORDER
    if fill:
        cell.fill = fill
    kwargs = {"bold": bold, "size": size, "name": "Calibri"}
    if color:
        kwargs["color"] = color
    cell.font = Font(**kwargs)
    cell.alignment = Alignment(horizontal=align, vertical="center", wrap_text=True)


# The 11th value on each CDD row (the high-negative-difference flag) is
# internal: it drives the red row fill here and the red rows in the Word
# audit table, but is not written as a column.
_CDD_HEADERS = ["CDD (user)", "Product", "Received", "Used", "Given Back",
                "Difference (= received - used - given back)",
                "Unused", "Partial", "Wasted", "Empty"]


def _write_tab(ws, banner, headers, rows, label_cols=3, flag_col=None,
               header_notes=None, tint_headers=()):
    """flag_col: 0-based row index whose non-empty value marks the whole row
    red (used for the CDD accountability flag). header_notes: {header prefix:
    note text} attached as hover comments on matching header cells.
    tint_headers: header prefixes whose whole column gets a light anchor
    tint (reading aid, e.g. 'Available at HF')."""
    from openpyxl.comments import Comment
    from openpyxl.utils import get_column_letter
    ws.append([banner] + [""] * (len(headers) - 1))
    ws.merge_cells(start_row=1, start_column=1, end_row=1, end_column=len(headers))
    _style_cell(ws.cell(row=1, column=1), fill=_BANNER_FILL, bold=True,
                color="FFFFFF", size=11)
    ws.append(headers)
    tint_cols = set()
    for ci in range(1, len(headers) + 1):
        _style_cell(ws.cell(row=2, column=ci), fill=_HDR_FILL, bold=True,
                    color="FFFFFF")
        header = str(headers[ci - 1])
        for prefix, note in (header_notes or {}).items():
            if header.startswith(prefix):
                ws.cell(row=2, column=ci).comment = Comment(note, "stock report")
        if any(header.startswith(p) for p in tint_headers):
            tint_cols.add(ci)
    for ci in range(1, len(headers) + 1):
        if ci <= label_cols:
            width = 26
        else:
            # headers carrying a formula get a wider, wrap-friendly column
            width = 22 if len(str(headers[ci - 1])) > 16 else 13
        ws.column_dimensions[get_column_letter(ci)].width = width
    for row in rows:
        # flag_col may point past the headers: such values colour the row
        # but are not written as a column.
        ws.append(row[:len(headers)])
        flagged = flag_col is not None and len(row) > flag_col and row[flag_col]
        for ci in range(1, len(headers) + 1):
            fill = (_FLAG_FILL if flagged
                    else _TINT_FILL if ci in tint_cols else None)
            _style_cell(ws.cell(row=ws.max_row, column=ci), fill=fill,
                        color=_FLAG_COLOR if flagged else None)


def _render_workbook(cfg, data, path):
    wb = Workbook()
    wb.remove(wb.active)
    period = (f"Cumulative Days 1-{cfg['DAY']}" if cfg.get("cumulative")
              else f"to Day {cfg['DAY']}")
    if data["variant"] == "smc":
        _write_tab(wb.create_sheet("STOCK LEDGER"),
                   f"{cfg['state_name']} — Stock Ledger ({period})  |  "
                   f"How to read: every column counts real doses. When the "
                   f"records are complete, no column exceeds 'Received' — "
                   f"where Used or Given IS higher, or a Stock Left number "
                   f"is negative, some handovers or receipts were not "
                   f"recorded in the app: a recording gap to follow up, not "
                   f"extra stock. CDDs return unused stock each evening; "
                   f"returns are in the Returned columns.",
                   data["headers"], data["rows"],
                   header_notes=_LEDGER_HEADER_NOTES,
                   tint_headers=("Stock Given to CDDs",))
        if data.get("daily_rows"):
            _write_tab(wb.create_sheet("DAILY FLOW"),
                       f"{cfg['state_name']} — Daily Stock Movements "
                       f"({period})  |  Same columns and formulas as the "
                       f"STOCK LEDGER, shown per day: movement columns are "
                       f"that day's movements, and the two Stock Left "
                       f"columns are the balance at the END of that day — "
                       f"a facility's last day matches its STOCK LEDGER "
                       f"row. Dates are the app's record dates.",
                       data["daily_headers"], data["daily_rows"],
                       label_cols=4,
                       header_notes=_LEDGER_HEADER_NOTES,
                       tint_headers=("Stock Given to CDDs",))
        if data["cdd_rows"]:
            _write_tab(wb.create_sheet("CDD ACCOUNTABILITY"),
                       f"{cfg['state_name']} — CDD Stock Accountability "
                       f"({period})  |  Red rows: the CDD used more stock "
                       f"than was recorded as given to them — follow up "
                       f"with the supervisor. Flagged when the gap is at "
                       f"least 20 doses AND at least 5% of their use; "
                       f"smaller gaps are treated as timing noise.",
                       _CDD_HEADERS, data["cdd_rows"], flag_col=10)
    elif data["variant"] == "itn_ledger":
        _write_tab(wb.create_sheet("STOCK LEDGER"),
                   f"{cfg['state_name']} — Bednet Stock Ledger ({period})  |  "
                   f"How to read: every column counts real bednets. When the "
                   f"records are complete, no column exceeds 'Received' — "
                   f"where Distributed or Given IS higher, or a Bednets Left "
                   f"number is negative, some handovers or receipts were not "
                   f"recorded in the app: a recording gap to follow up, not "
                   f"extra stock. Returns are in the Returned columns.",
                   data["headers"], data["rows"], label_cols=4,
                   header_notes=_ITN_LEDGER_HEADER_NOTES,
                   tint_headers=("Bednets Given to Distributors",))
        if data.get("daily_rows"):
            _write_tab(wb.create_sheet("DAILY FLOW"),
                       f"{cfg['state_name']} — Daily Bednet Movements "
                       f"({period})  |  Same columns and formulas as the "
                       f"STOCK LEDGER, shown per day: movement columns are "
                       f"that day's movements, and the two Bednets Left "
                       f"columns are the balance at the END of that day — "
                       f"a hub's last day matches its STOCK LEDGER row. "
                       f"Dates are the app's record dates.",
                       data["daily_headers"], data["daily_rows"],
                       label_cols=5,
                       header_notes=_ITN_LEDGER_HEADER_NOTES,
                       tint_headers=("Bednets Given to Distributors",))
        if data["cdd_rows"]:
            _write_tab(wb.create_sheet("DISTRIBUTOR ACCOUNTABILITY"),
                       f"{cfg['state_name']} — Distributor Stock "
                       f"Accountability ({period})  |  Red rows: the "
                       f"distributor handed out more bednets than were "
                       f"recorded as given to them — follow up with the "
                       f"supervisor. Flagged when the gap is at least 20 "
                       f"bednets AND at least 5% of what they distributed; "
                       f"smaller gaps are treated as timing noise.",
                       _DIST_HEADERS, _dist_display_rows(data["cdd_rows"]),
                       label_cols=5, flag_col=9)
    else:
        _write_tab(wb.create_sheet("STOCK & DISTRIBUTION"),
                   f"{cfg['state_name']} — Stock & Distribution ({period})",
                   data["headers"], data["rows"])
    ws = wb.worksheets[0]
    label_cols = len(data["levels"]) + (
        1 if data["variant"] in ("smc", "itn_ledger") else 0)
    total_row = (["TOTAL"] + [""] * (label_cols - 1)
                 + [sum(r[ci] for r in data["rows"])
                    for ci in range(label_cols, len(data["headers"]))])
    ws.append(total_row)
    for ci in range(1, len(data["headers"]) + 1):
        _style_cell(ws.cell(row=ws.max_row, column=ci), fill=_TOTAL_FILL,
                    bold=True)
    wb.save(path)
    log.info(f"[stock] workbook saved -> {path}")
    return path


def _publish_workbook(cfg, path):
    """Upload the stock workbook to the campaign's Drive folder so the doc
    section can link it. Own upload so report.py/report_itn.py stay untouched.
    Non-fatal; respects no_upload."""
    if cfg.get("no_upload"):
        return ""
    try:
        from dst_data_analysis_report.pipeline import notify
        fid = notify.campaign_folder_id(cfg)
        period = (f"Cumulative Days 1-{cfg['DAY']}" if cfg.get("cumulative")
                  else f"Day {cfg['DAY']}")
        link = notify.upload_file(
            path,
            f"{cfg['state_name']} {period} Stock Data — "
            f"{cfg['DATE_LABEL']} {datetime.now().strftime('%H:%M')}",
            folder_id=fid)
        cfg["stock_drive_link"] = link or ""
        return link or ""
    except Exception as e:                                       # noqa: BLE001
        log.warning(f"[stock] Drive upload failed (non-fatal): {e}")
        return ""


# ── Word section ──────────────────────────────────────────────────────────────
# Written for three readers:
#   the CAMPAIGN MANAGER / programme owner — "is supply healthy?" at a glance;
#   ON-GROUND supervisors — which exact facilities need restocking or a visit;
#   and the AUDIT (last) — per-CDD stock accountability for follow-up.

def _simple_table(doc, cols, data_rows, left_cols=(), bold_rows=(),
                  red_rows=()):
    from docx.enum.text import WD_ALIGN_PARAGRAPH
    from docx.shared import RGBColor
    from dst_data_analysis_report.pipeline.core.word import dat, hdr
    tbl = doc.add_table(rows=1, cols=len(cols))
    tbl.style = "Table Grid"
    for ci, h in enumerate(cols):
        hdr(tbl.cell(0, ci), h)
    for ri, row in enumerate(data_rows, 1):
        tr = tbl.add_row()
        for ci, val in enumerate(row):
            align = (WD_ALIGN_PARAGRAPH.LEFT if ci in left_cols
                     else WD_ALIGN_PARAGRAPH.CENTER)
            dat(tr.cells[ci], val, alt=(ri % 2 == 1), align=align)
            if ri in bold_rows or ri in red_rows:
                for p in tr.cells[ci].paragraphs:
                    for run in p.runs:
                        if ri in bold_rows:
                            run.font.bold = True
                        if ri in red_rows:
                            run.font.color.rgb = RGBColor(0x9C, 0x00, 0x06)


def _significant_diff(row):
    """The audit noise threshold: a CDD accountability difference matters
    when it is at least 20 units AND at least 5% of the larger of got/used."""
    return abs(row[5]) >= 20 and abs(row[5]) >= 0.05 * max(row[2], row[3], 1)


def stock_summary_line(cfg):
    """One-to-two-line deterministic stock summary for the report Conclusion
    and the Slack message (numbers straight from the totals, never through
    the LLM). Empty string when the stage produced nothing, so callers can
    append unconditionally. Internal audiences only."""
    data = cfg.get("stock_data")
    if not data:
        return ""
    t = data["totals"]

    def v(key):
        return t.get(key, 0) or 0

    is_azm = cfg.get("drug_type") == "AZM"
    unit = ("bottles" if is_azm
            else "bednets" if data["variant"] == "itn" else "doses")
    if data["variant"] == "itn_ledger":
        usage = (f", {v('consumed') / v('received') * 100:.0f}% distributed"
                 if v("received") else "")
        line = (f"Stock: {v('received'):,.0f} bednets received at "
                f"distribution hubs{usage}; {v('balance_hf'):,.0f} still at "
                f"hubs and {v('balance_cdd'):,.0f} with distributors.")
        dist = data.get("cdd_rows") or []
        if dist:
            users = {r[0] for r in dist}
            red = {r[0] for r in dist if _dist_flagged(r)}
            line += (f" {len(users) - len(red):,} of {len(users):,} "
                     f"distributors have clean stock records")
            line += (f"; {len(red)} distributed more than recorded as received."
                     if red else ".")
        return line
    if data["variant"] == "itn":
        in_stock = v("received") - v("issued") + v("returned") - v("wasted")
        return (f"Stock: {v('received'):,.0f} {unit} received, "
                f"{v('delivered'):,.0f} delivered to households, "
                f"{in_stock:,.0f} in stock.")
    used_net = v("consumed") + (0 if is_azm else v("redose"))
    usage = (f", {used_net / v('received') * 100:.0f}% used"
             if v("received") else "")
    if v("balance_cdd") >= 0:
        position = (f"{v('balance_cdd'):,.0f} still with CDDs and "
                    f"{v('balance_hf'):,.0f} at facilities")
    else:
        position = (f"{v('balance_hf'):,.0f} at facilities; CDDs used "
                    f"{-v('balance_cdd'):,.0f} more than recorded handovers")
    line = (f"Stock: {v('received'):,.0f} {unit} received at health "
            f"facilities{usage}; {position}.")
    cdd = data.get("cdd_rows") or []
    if cdd:
        users = {r[0] for r in cdd}
        dirty = {r[0] for r in cdd if _dist_flagged(r)}      # red rows only
        clean = len(users) - len(dirty)
        line += (f" {clean:,} of {len(users):,} CDDs have clean stock "
                 f"records")
        line += (f"; {len(dirty)} used more than recorded as received."
                 if dirty else ".")
    return line


def _per_facility(data):
    """Aggregate the ledger rows to one entry per facility (across products)."""
    ix = data.get("ix") or {}
    if ix.get("hf") is None:
        return []
    out = {}
    for r in data["rows"]:
        hf = r[ix["hf"]]
        if not hf:
            continue
        e = out.setdefault(hf, {"lga": "", "consumed": 0, "received": 0,
                                "issued": 0, "bal_hf": 0, "bal_cdd": 0})
        if ix.get("lga") is not None and r[ix["lga"]]:
            e["lga"] = r[ix["lga"]]
        e["consumed"] += r[ix["consumed"]] or 0
        e["received"] += r[ix["received"]] or 0
        e["issued"] += r[ix["issued"]] or 0
        e["bal_hf"] += r[ix["bal_hf"]] or 0
        e["bal_cdd"] += r[ix["bal_cdd"]] or 0
    return [dict(hf=hf, **e) for hf, e in out.items()]


def build_stock_section(doc, cfg, heading_num="6"):
    """Append the stock section to a report doc — BOTH internal and partner
    (per user instruction 2026-09-17; note the audit table names CDD users).
    No-op when the stage produced nothing."""
    data = cfg.get("stock_data")
    if not data:
        return
    from dst_data_analysis_report.pipeline.core.word import (
        GREY_RGB, add_heading, add_para,
        add_hyperlink as _add_hyperlink, two_col_table as _two_col_table)

    t = data["totals"]

    def v(key):
        return t.get(key, 0) or 0

    is_azm = cfg.get("drug_type") == "AZM"
    is_hub = data["variant"] == "itn_ledger"
    unit = ("bottles" if is_azm
            else "bednets" if data["variant"] in ("itn", "itn_ledger")
            else "doses")
    # who holds the stock, in this model's own words
    site, site_col, holders = (("hubs", "Distribution Hub", "distributors")
                               if is_hub else
                               ("facilities", "Health Facility", "CDDs"))

    add_heading(doc, f"{heading_num}.  Stock & Supply Chain Status", 4)
    add_para(doc, "All figures are cumulative for the campaign to date.",
             size=9, color=GREY_RGB)
    sub = 1

    # ── (manager view) supply at a glance: the STOCK-FLOW story ───────────
    # Rows follow the physical journey of the stock, so each level can only
    # shrink as you read down — a gross "handed to CDDs" figure exceeding
    # "received" (from daily give-back-and-reissue cycles) can never appear
    # here. Plain-space indentation only (no unicode symbols); every computed
    # row carries its formula in the label.
    add_heading(doc, f"{heading_num}.{sub}  Supply at a Glance", 5)
    if data["variant"] == "smc":
        net_issued = v("issued") - v("returned") - v("rejected_out")
        used_net = v("consumed") + (0 if is_azm else v("redose"))
        overview = [
            ("Received at health facilities", f"{v('received'):,.0f}"),
            ("    Given to CDDs (after give-backs)", f"{net_issued:,.0f}"),
            (f"        Used for children ({unit})", f"{v('consumed'):,.0f}"),
            ("        Repeat doses (redose)", f"{v('redose'):,.0f}"),
        ]
        if v("in_transit_out"):
            # in-transit sits in its own bucket (goods-in-transit): counted
            # with neither the CDDs nor the facility until confirmed
            overview.append(("        On the way to CDDs (in transit)",
                             f"{v('in_transit_out'):,.0f}"))
        if v("balance_cdd") >= 0:
            overview.append(("        Still with CDDs",
                             f"{v('balance_cdd'):,.0f}"))
        else:
            cov = (net_issued / used_net * 100) if used_net else 0
            overview += [
                ("        Still with CDDs", "—"),
                ("        Given without an app record (min.)",
                 f"{-v('balance_cdd'):,.0f}"),
                ("        Handovers recorded in the app", f"{cov:.1f}%"),
            ]
        overview += [
            ("    Still at health facilities", f"{v('balance_hf'):,.0f}"),
            ("    Returned by health facilities to the state health facility",
             f"{v('returned_upstream'):,.0f}"),
            ("    Damaged", f"{v('damaged'):,.0f}"),
            ("    Lost",    f"{v('lost'):,.0f}"),
        ]
        if v("rejected_in") or v("rejected_out"):
            overview += [
                ("    Rejected by health facilities",
                 f"{v('rejected_in'):,.0f}"),
                ("    Rejected by CDDs", f"{v('rejected_out'):,.0f}"),
            ]
        if v("received"):
            _redose_lbl = "" if is_azm else " + redose"
            overview.append((
                f"Stock usage (= used{_redose_lbl} / received)",
                f"{used_net / v('received') * 100:.1f}%"))
        if v("issued"):
            overview.append((
                "Give-back rate (= returned by CDDs / all handovers)",
                f"{v('returned') / v('issued') * 100:.1f}%"))
        all_cdd = data.get("cdd_rows") or []
        if all_cdd:
            # "clean" = NOT red (user, 2026-10-01, same rule as ITN): only a
            # CDD who used MORE than recorded as given counts against them. A
            # positive gap is stock still in hand, not a recording problem.
            users = {r[0] for r in all_cdd}
            dirty_users = {r[0] for r in all_cdd if _dist_flagged(r)}
            clean = len(users) - len(dirty_users)
            overview.append((
                "CDDs with clean stock records",
                f"{clean:,} of {len(users):,} "
                f"({clean / len(users) * 100:.1f}%)"))
        # Consistency is still verified, just not shown as a table row: if
        # the flow does not add up to "received", say so in the log loudly.
        check_lhs = (net_issued + v("balance_hf") + v("returned_upstream")
                     + v("damaged") + v("lost"))
        diff = check_lhs - v("received")
        if abs(diff) >= 0.5:
            log.error(f"[stock] flow table does NOT reconcile: outflows+"
                      f"balances {check_lhs:,.0f} vs received "
                      f"{v('received'):,.0f} (difference {diff:,.0f})")
    elif is_hub:
        overview = [
            ("Received at distribution hubs", f"{v('received'):,.0f}"),
            ("    Given to distributors (after returns)", f"{v('given'):,.0f}"),
            ("        Distributed to households", f"{v('consumed'):,.0f}"),
        ]
        if v("in_transit_out"):
            overview.append(("        On the way to distributors (in transit)",
                             f"{v('in_transit_out'):,.0f}"))
        if v("balance_cdd") >= 0:
            overview.append(("        Still with distributors",
                             f"{v('balance_cdd'):,.0f}"))
        else:
            overview += [
                ("        Still with distributors", "—"),
                ("        Distributed without an app handover (min.)",
                 f"{-v('balance_cdd'):,.0f}"),
            ]
        overview += [
            ("    Still at distribution hubs", f"{v('balance_hf'):,.0f}"),
            ("    Returned by hubs to LGA facilities",
             f"{v('returned_upstream'):,.0f}"),
        ]
        if v("in_transit_in"):
            overview.append(("On the way from LGA facilities to hubs (in transit)",
                             f"{v('in_transit_in'):,.0f}"))
        if v("rejected_in") or v("rejected_out"):
            overview += [
                ("    Rejected by hubs", f"{v('rejected_in'):,.0f}"),
                ("    Rejected by distributors", f"{v('rejected_out'):,.0f}"),
            ]
        if v("received"):
            overview.append((
                "Stock usage (= distributed / received)",
                f"{v('consumed') / v('received') * 100:.1f}%"))
        if v("issued"):
            overview.append((
                "Return rate (= returned by distributors / all handovers)",
                f"{v('returned') / v('issued') * 100:.1f}%"))
        all_dist = data.get("cdd_rows") or []
        if all_dist:
            # ITN (user, 2026-10-01): "clean" = NOT red. Only distributing
            # MORE than recorded is a problem; a positive gap is bednets still
            # in hand, normal during the campaign, so it does not count as
            # "not clean" (587/751 read as 164 errors when 7 distributors were
            # actually off). No separate holding line (user, 2026-10-01).
            users = {r[0] for r in all_dist}
            red = {r[0] for r in all_dist if _dist_flagged(r)}
            clean = len(users) - len(red)
            overview.append((
                "Distributors with clean stock records",
                f"{clean:,} of {len(users):,} "
                f"({clean / len(users) * 100:.1f}%)"))

        check_lhs = (v("given") + v("balance_hf") + v("returned_upstream"))
        diff = check_lhs - v("received")
        if abs(diff) >= 0.5:
            log.error(f"[stock] ITN flow table does NOT reconcile: outflows+"
                      f"balances {check_lhs:,.0f} vs received "
                      f"{v('received'):,.0f} (difference {diff:,.0f})")
    else:
        overview = [
            ("Bednets received into stock",     f"{v('received'):,.0f}"),
            ("Bednets issued for distribution", f"{v('issued'):,.0f}"),
            ("Bednets returned to stock",       f"{v('returned'):,.0f}"),
            ("Bednets wasted",                  f"{v('wasted'):,.0f}"),
            ("Bednets delivered to households", f"{v('delivered'):,.0f}"),
            ("Duplicate bednet codes detected", f"{v('dup_codes'):,.0f}"),
        ]
    _two_col_table(doc, overview)
    notes = []
    if data["variant"] == "smc":
        if v("returned") > 0:
            notes.append("Given to CDDs counts stock after evening "
                         "give-backs; the every-handover counts are in the "
                         "Excel")
        if v("balance_cdd") < 0:
            notes.append("\"—\": CDDs used more than the recorded handovers "
                         "— a recording gap, not a stock shortage")
        notes.append("Still at facilities = received + give-backs - "
                     "handovers - sent back - damaged - lost")
        notes.append("Still with CDDs = given - used"
                     + ("" if is_azm else " - redose"))
        if is_azm:
            notes.append(f"1 bottle = {AZM_DOSES_PER_BOTTLE} doses")
    elif is_hub:
        if v("returned") > 0:
            notes.append("Given to distributors counts bednets after "
                         "returns; the every-handover counts are in the Excel")
        if v("balance_cdd") < 0:
            notes.append("\"—\": distributors handed out more than the "
                         "recorded handovers — a recording gap, not a stock "
                         "shortage")
        notes.append("Still at hubs = received + returns from distributors - "
                     "handovers + rejected by distributors - returned to LGA")
        notes.append("Still with distributors = given - in transit - "
                     "distributed")
        notes.append("Distributed counts successful distribution records as "
                     "recorded in the app")
    else:
        notes.append("In stock = received - issued + returned - wasted")
        notes.append("Delivered = scanned + manual codes")
    for note in notes:
        add_para(doc, note + ".", size=8, color=GREY_RGB)
    link_p = add_para(doc, "Detail per facility and product: ",
                      size=9, color=GREY_RGB)
    if cfg.get("stock_drive_link"):
        _add_hyperlink(link_p, "Stock Data ↗", cfg["stock_drive_link"])
    else:
        link_p.add_run(cfg.get("stock_xlsx", "") or _stock_xlsx(cfg))
    doc.add_paragraph()
    sub += 1

    facilities = (_per_facility(data)
                  if data["variant"] in ("smc", "itn_ledger") else [])
    day = max(1, int(cfg.get("DAY") or 1))

    # ── (on-ground view) restock list, or leftover list after the campaign ─
    # ONLY facilities that actually record stock in the app belong here: a
    # facility whose consumption comes solely from the task index has a
    # meaningless "balance" of 0 and would wrongly top the restock list —
    # those facilities are the RECORDING problem shown in the next section.
    recording = [f for f in facilities
                 if f["consumed"] > 0 and (f["received"] + f["issued"]) > 0]
    campaign_over = bool(cfg.get("cumulative")) or (
        day >= int(cfg.get("campaign_days") or day))
    if recording and campaign_over:
        # "Restock now" makes no sense once the campaign has ended — what
        # matters then is WHERE the unused stock sits, for retrieval.
        leftovers = sorted(
            (f for f in recording
             if f["bal_hf"] > 0 or f["bal_cdd"] > 0),
            key=lambda f: f["bal_hf"] + max(f["bal_cdd"], 0),
            reverse=True)[:5]
        if leftovers:
            add_heading(doc, f"{heading_num}.{sub}  "
                             f"{site.capitalize()} With Stock Left Over", 5)
            add_para(doc, f"The campaign has ended; this stock should be "
                          f"returned or accounted for. Total left = at "
                          f"{site[:-1]} + with {holders}.",
                     size=8, color=GREY_RGB)
            _simple_table(
                doc, ["#", "LGA / District", site_col,
                      f"At {site[:-1]}", f"With {holders}", "Total left"],
                [[ri, f["lga"], f["hf"], f"{f['bal_hf']:,.0f}",
                  f"{max(f['bal_cdd'], 0):,.0f}",
                  f"{f['bal_hf'] + max(f['bal_cdd'], 0):,.0f}"]
                 for ri, f in enumerate(leftovers, 1)],
                left_cols=(1, 2))
            link_p = add_para(doc, "For more details, all facilities and "
                                   "products: ",
                              size=8, color=GREY_RGB)
            if cfg.get("stock_drive_link"):
                _add_hyperlink(link_p, "Stock Data ↗ (STOCK LEDGER tab)",
                               cfg["stock_drive_link"])
            else:
                link_p.add_run(cfg.get("stock_xlsx", "") or _stock_xlsx(cfg))
            doc.add_paragraph()
            sub += 1
    elif recording and not is_hub:     # no restock list for ITN (user, 2026-10-01)
        for f in recording:
            f["daily"] = f["consumed"] / day
            f["days_left"] = (f["bal_hf"] / f["daily"]
                              if f["bal_hf"] > 0 else 0.0)
        at_risk = sorted(recording, key=lambda f: f["days_left"])[:10]
        add_heading(doc, f"{heading_num}.{sub}  {site.capitalize()} to "
                         f"Restock First", 5)
        add_para(doc, f"{site.capitalize()} that record stock in the app, "
                      f"ranked by days of stock left. Restock anything under "
                      f"1 day.",
                 size=9, color=GREY_RGB)
        add_para(doc, f"Days of stock left = stock in hand / used per day.  "
                      f"Used per day = total used / {day} day(s).",
                 size=8, color=GREY_RGB)
        rows = []
        for ri, f in enumerate(at_risk, 1):
            flag = ("RESTOCK NOW" if f["days_left"] < 1
                    else "LOW" if f["days_left"] < 2 else "OK")
            rows.append([ri, f["lga"], f["hf"], f"{f['bal_hf']:,.0f}",
                         f"{f['daily']:,.0f}", f"{f['days_left']:.1f}", flag])
        _simple_table(doc, ["#", "LGA / District", site_col,
                            "Stock in hand", f"Used per day ({unit})",
                            "Days of stock left", "Action"],
                      rows, left_cols=(1, 2))
        doc.add_paragraph()
        sub += 1

    # (The "Facilities Not Recording Handovers" section was removed on partner
    # feedback 2026-09; the per-CDD audit below covers the same signal.)

    # ── (audit — always LAST) per-CDD stock check ──────────────────────────
    # Noise threshold: a difference matters when it is at least 20 units and
    # at least 5% of the larger of got/used.
    cdd_rows = [r for r in data.get("cdd_rows", []) if _significant_diff(r)]
    if is_hub:
        # ITN audit lists the RED distributors only (see "clean" above)
        cdd_rows = [r for r in cdd_rows if _dist_flagged(r)]
    if cdd_rows and is_hub:
        add_heading(doc, f"{heading_num}.{sub}  Stock Check per Distributor "
                         f"(audit)", 5)
        add_para(doc, "Distributors who handed out more bednets than were "
                      "recorded as given to them, largest gap first, for "
                      "follow-up visits — often an unrecorded handover, or "
                      "distribution recorded under another distributor's "
                      "login.",
                 size=9, color=GREY_RGB)
        add_para(doc, "Difference = Received - Distributed - Returned. Shown "
                      "when the gap is at least 20 bednets and at least 5% of "
                      "what they distributed. Distributors still holding "
                      "bednets are not listed here.",
                 size=8, color=GREY_RGB)
        top = sorted(cdd_rows, key=lambda r: r[5])[:5]
        red_rows = tuple(ri for ri, r in enumerate(top, 1)
                         if len(r) > 10 and r[10])
        _simple_table(doc, ["#", "LGA", "Distribution Hub", "Distributor",
                            "Received", "Distributed", "Returned", "Difference"],
                      [[ri, r[11] if len(r) > 13 else "",
                        r[13] if len(r) > 13 else "", r[0],
                        f"{r[2]:,.0f}", f"{r[3]:,.0f}",
                        f"{r[4]:,.0f}", f"{r[5]:,.0f}"]
                       for ri, r in enumerate(top, 1)],
                      left_cols=(1, 2, 3), red_rows=red_rows)
        link_p = add_para(doc, f"All {len(cdd_rows)} distributors in red: ",
                          size=8, color=GREY_RGB)
        if cfg.get("stock_drive_link"):
            _add_hyperlink(link_p, "Stock Data ↗ (DISTRIBUTOR ACCOUNTABILITY tab)",
                           cfg["stock_drive_link"])
        else:
            link_p.add_run(cfg.get("stock_xlsx", "") or _stock_xlsx(cfg))
    elif cdd_rows:
        add_heading(doc, f"{heading_num}.{sub}  Stock Check per CDD (audit)",
                    5)
        add_para(doc, "Largest differences first, for follow-up visits. "
                      "Positive: stock not yet accounted for. Negative: used "
                      "more than recorded — often an unrecorded handover, or "
                      "stock shared between CDDs under one name.",
                 size=9, color=GREY_RGB)
        add_para(doc, "Difference = Got - Used - Gave back. Rows in red: the "
                      "CDD used more stock than was recorded as given to "
                      "them (high negative difference).",
                 size=8, color=GREY_RGB)
        # pick the 5 biggest variances (collector sorts by |difference|),
        # then DISPLAY them in plain descending order of the difference so
        # the column reads sorted.
        top = sorted(cdd_rows[:5], key=lambda r: r[5], reverse=True)
        red_rows = tuple(ri for ri, r in enumerate(top, 1)
                         if len(r) > 10 and r[10])
        _simple_table(doc, ["#", "Distributor", "Product", "Got",
                            f"Used ({unit})", "Gave back", "Difference"],
                      [[ri, r[0], r[1], f"{r[2]:,.0f}", f"{r[3]:,.0f}",
                        f"{r[4]:,.0f}", f"{r[5]:,.0f}"]
                       for ri, r in enumerate(top, 1)],
                      left_cols=(1, 2), red_rows=red_rows)
        link_p = add_para(doc, f"All {len(cdd_rows)} CDDs with a difference: ",
                          size=8, color=GREY_RGB)
        if cfg.get("stock_drive_link"):
            _add_hyperlink(link_p, "Stock Data ↗ (CDD ACCOUNTABILITY tab)",
                           cfg["stock_drive_link"])
        else:
            link_p.add_run(cfg.get("stock_xlsx", "") or _stock_xlsx(cfg))
    doc.add_paragraph()


# ── stage entry point ─────────────────────────────────────────────────────────

def run(cfg):
    """Collect stock data, write + upload the workbook, stash
    cfg['stock_data'] / cfg['stock_xlsx'] for the report section.

    Returns the workbook path on success, None on the no-op (feature off, or
    zero stock documents matched). Never raises past the caller's guard on
    purpose-built data problems — callers wrap it non-fatally anyway."""
    if not enabled(cfg):
        log.info("[stock] stock report disabled (sheet stock_report / "
                 "DST_STOCK_REPORT / STOCK_REPORT_DEFAULT) — skipped")
        return None
    log.info(f"[stock] {cfg['state_name']} {_variant(cfg).upper()} stock "
             f"report (window to {cfg['LTE'][:10]}) ...")

    data = _collect_itn(cfg) if _variant(cfg) == "itn" else _collect_smc(cfg)
    if not data or not data["rows"]:
        log.error("[stock] zero stock documents matched — no stock section "
                  "this run")
        return None

    path = _stock_xlsx(cfg)
    cfg["stock_data"] = data
    cfg["stock_xlsx"] = path
    _render_workbook(cfg, data, path)
    _publish_workbook(cfg, path)
    return path
