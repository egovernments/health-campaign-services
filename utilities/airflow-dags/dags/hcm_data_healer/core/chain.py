"""
Relational chain recovery from the egov error tracer.

Finds records missing from ES across six links (gaps A-F, see GAP_LABELS),
confirms each gap against all dates, then recovers the missing payloads from
egov-tracer-error-details. The tracer is searched by the parent reference, not
the missing record's own id. Independent of the DB-vs-ES reconciliation.

Writes a workbook plus one {state}_chain_tracer_<gap>_<ts>.csv per gap for the push phase.
"""

import gc
import json
import os
import re
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime

import pandas as pd

from hcm_data_healer.core.es_client import ESClient, TRACER_INDEX, ID_SCROLL_SIZE
from hcm_data_healer.core.utils import excel
from hcm_data_healer.core.utils.dates import epoch_ms as _epoch_ms, iso as _iso, noop as _noop

# Gaps: A orphan member, B household without members, C member without individual,
# D/F household without a ProjectBeneficiary (via member / direct), E PB without a task.
GAP_LABELS = {
    "A": "Household Member with no matching Household (orphaned member)",
    "B": "Household with no members recorded",
    "C": "Household Member whose Individual record is missing",
    "D": "Household (via Household Member) not registered as a Project Beneficiary",
    "E": "Project Beneficiary with no delivery Task",
    "F": "Household (direct, no member record) not registered as a Project Beneficiary",
}

# Per gap: requestBody key, bulk-create url, and the record field matched against the gap ids.
TRACER_CFG = {
    "A": {"key": "Households",           "url": "/household/v1/bulk/_create",
          "id_field": "clientReferenceId"},
    "B": {"key": "HouseholdMembers",     "url": "/household/member/v1/bulk/_create",
          "id_field": "householdClientReferenceId"},
    "C": {"key": "Individuals",          "url": "/individual/v1/bulk/_create",
          "id_field": "clientReferenceId"},
    "D": {"key": "ProjectBeneficiaries", "url": "/project/beneficiary/v1/bulk/_create",
          "id_field": "beneficiaryClientReferenceId"},
    "E": {"key": "Tasks",                "url": "/project/task/v1/bulk/_create",
          "id_field": "projectBeneficiaryClientReferenceId"},
    # F recovers the same record type as D, so the search config is identical.
    "F": {"key": "ProjectBeneficiaries", "url": "/project/beneficiary/v1/bulk/_create",
          "id_field": "beneficiaryClientReferenceId"},
}

# Parallel slices for the tenant-scoped tracer scan.
TRACER_SCAN_SLICES = 4

# Last-resort per-id search: ids per query, and a per-gap cap so a large gap cannot hang the run.
PHASE4_MAX_IDS = 1000
PHASE4_CHUNK = 50

# All requestBody wrapper keys; every doc is checked for each of them.
BODY_KEYS = ("Households", "HouseholdMembers", "Individuals",
             "ProjectBeneficiaries", "Tasks")

# All-dates existence check per gap: (index suffix, id field, tenant field, isDeleted field or None).
EXIST_CHECK = {
    "A": ("household-index-v1",        "Data.household.clientReferenceId",
          "Data.household.tenantId.keyword",              "Data.household.isDeleted"),
    "B": ("household-member-index-v1", "Data.householdMember.householdClientReferenceId",
          "Data.householdMember.tenantId.keyword",        "Data.householdMember.isDeleted"),
    "C": ("individual-index-v1",       "clientReferenceId",
          "tenantId.keyword",                             "isDeleted"),
    "D": ("project-beneficiary-index-v1", "beneficiaryClientReferenceId",
          "tenantId.keyword",                             "isDeleted"),
    "E": ("project-task-index-v1",     "Data.projectBeneficiaryClientReferenceId",
          "Data.tenantId.keyword",                        None),   # task docs: no isDeleted
    "F": ("project-beneficiary-index-v1", "beneficiaryClientReferenceId",
          "tenantId.keyword",                             "isDeleted"),   # same target as D
}

TRACER_COLS = ["Gap", "Day", "Timestamp", "Error_Doc_UUID", "Error_Code", "Error_Message",
               "Username", "API_URL", "id_field", "id_value", "clientReferenceId",
               "tenantId", "isDeleted", "rowVersion", "payload_json"]

GAP_COLS = {
    "A": ["clientReferenceId", "householdClientReferenceId", "individualClientReferenceId", "isHeadOfHousehold"],
    "B": ["Household_clientReferenceId", "locality"],
    "C": ["clientReferenceId", "householdClientReferenceId", "individualClientReferenceId", "isHeadOfHousehold"],
    "D": ["HouseholdMember_householdClientReferenceId"],
    "E": ["PB_clientReferenceId", "beneficiaryClientReferenceId"],
    "F": ["Household_clientReferenceId", "locality"],
}


def _is_processed(val):
    """True if Data.isProcessed is true, "true" or 1."""
    return val is True or str(val).strip().lower() in ("true", "1")


# Some tracer docs (e.g. UPLOAD_ERROR_FROM_APP) store requestBody as a Java
# toString literal with unquoted keys and values. These regexes quote them so
# json.loads can parse it. A value containing a comma would be mis-split.
_LOOSE_KEY_RE = re.compile(r'([{,]\s*)([A-Za-z_][A-Za-z0-9_]*)\s*:')
_LOOSE_VAL_RE = re.compile(r':\s*([^{}\[\],"]*?)\s*(?=[,}\]])')
_LOOSE_NUM_RE = re.compile(r'-?\d+(\.\d+)?$')


def _loose_quote_val(m):
    val = m.group(1)
    if val in ("null", "true", "false") or _LOOSE_NUM_RE.match(val):
        return ": " + val
    return ': "' + val.replace("\\", "\\\\").replace('"', '\\"') + '"'


def _loose_json(s):
    s = _LOOSE_KEY_RE.sub(r'\1"\2":', s)
    s = _LOOSE_VAL_RE.sub(_loose_quote_val, s)
    try:
        return json.loads(s)
    except Exception:
        return None


def _to_df(rows, cols):
    if not rows:
        return pd.DataFrame(columns=cols)
    df = pd.DataFrame(rows)
    for c in cols:
        if c not in df.columns:
            df[c] = ""
    return df[cols]


def _existing_ids(es, index_url, field, tenant, tenant_field, deleted_field,
                  candidate_ids, progress=_noop, gap=""):
    """Return the candidate ids that exist in the target index on any date."""
    ids = [str(i) for i in candidate_ids if i]
    found = set()
    if not ids:
        return found
    kw = f"{field}.keyword"
    BATCH = 2000
    for i in range(0, len(ids), BATCH):
        chunk = ids[i:i + BATCH]
        must = [{"terms": {kw: chunk}}, {"term": {tenant_field: tenant}}]
        if deleted_field:
            must.append({"term": {deleted_field: False}})
        body = {
            "size": 0,
            "query": {"bool": {"must": must}},
            # One bucket per id that exists; the query limits keys to this chunk.
            "aggs": {"present": {"terms": {"field": kw, "size": len(chunk)}}},
        }
        data = es._post(index_url, body)
        for b in data.get("aggregations", {}).get("present", {}).get("buckets", []):
            found.add(b["key"])
        progress(f"Gap {gap}: confirming against all dates", min(i + BATCH, len(ids)), len(ids))
    return found


def recover_chain(cfg, output_root, log=None, progress=None, ts=None):
    """Run chain recovery for one tenant and date window.

    Returns {run_dir, ts, summary, excel_path}. `ts` is shared with the reconciliation run.
    """
    log = log or _noop
    progress = progress or _noop
    # Callbacks are called from worker threads; serialize them.
    _cb_lock = threading.Lock()
    _raw_log, _raw_progress = log, progress

    def log(msg):
        with _cb_lock:
            _raw_log(msg)

    def progress(stage, cur, tot):
        with _cb_lock:
            _raw_progress(stage, cur, tot)

    tenant = cfg["tenant"]
    start, end = cfg["start_date"], cfg["end_date"]

    gte_ms, lte_ms = _epoch_ms(start), _epoch_ms(end, end_of_day=True)
    gte_iso, lte_iso = _iso(start), _iso(end, end_of_day=True)

    es = ESClient(cfg["es"]["base_url"], cfg["es"]["username"], cfg["es"]["password"],
                  verify_ssl=cfg["es"].get("verify_ssl", False),
                  batch_size=cfg["es"].get("batch_size", 5000))

    ts = ts or datetime.now().strftime("%Y%m%d_%H%M%S")
    run_dir = os.path.join(output_root, f"{tenant}_error_tracer_recovery_{ts}")
    os.makedirs(run_dir, exist_ok=True)
    prefix = cfg.get("es_index_prefix", f"{tenant}-")
    idx = lambda suffix: f"{es.base}/{prefix}{suffix}/_search"

    log(f"[CHAIN] Relational chain recovery - tenant '{tenant}', window {start} to {end}")
    log(f"[CHAIN] Output: {run_dir}")

    # Load the five indices concurrently; each callback writes only its own structures.

    # Household: crid -> locality
    hh_ids = {}

    def cb_hh(hits):
        for h in hits:
            hh = h.get("_source", {}).get("Data", {}).get("household", {})
            crid = hh.get("clientReferenceId", "")
            if crid:
                hh_ids[crid] = (hh.get("address") or {}).get("localityCode", {}).get("code", "")
        progress("Loading Households (ES)", len(hh_ids), len(hh_ids))

    q_hh = {
        "size": ID_SCROLL_SIZE,
        "query": {"bool": {"must": [
            {"term":  {"Data.household.tenantId.keyword": tenant}},
            {"term":  {"Data.household.isDeleted": False}},
            {"range": {"Data.household.auditDetails.createdTime": {"gte": gte_ms, "lte": lte_ms}}},
        ]}},
        "_source": ["Data.household.clientReferenceId",
                    "Data.household.address.localityCode.code"],
    }

    # Household Member: tuples + relational sets
    mbr_data = []       # (crid, hh_crid, ind_crid, is_head)
    mbr_hh_set = set()  # householdClientReferenceId values
    mbr_ind_map = {}    # individualClientReferenceId -> memberClientReferenceId
    head_ind_ids = set()  # individual ids of household heads

    def cb_mbr(hits):
        for h in hits:
            m = h.get("_source", {}).get("Data", {}).get("householdMember", {})
            crid = m.get("clientReferenceId", "")
            if not crid:
                continue
            hh_crid = m.get("householdClientReferenceId", "") or ""
            ind_crid = m.get("individualClientReferenceId", "") or ""
            is_head = m.get("isHeadOfHousehold", False)
            mbr_data.append((crid, hh_crid, ind_crid, is_head))
            if hh_crid:
                mbr_hh_set.add(hh_crid)
            if ind_crid:
                mbr_ind_map[ind_crid] = crid
                if is_head:
                    head_ind_ids.add(ind_crid)
        progress("Loading Household Members (ES)", len(mbr_data), len(mbr_data))

    q_mbr = {
        "size": ID_SCROLL_SIZE,
        "query": {"bool": {"must": [
            {"term":  {"Data.householdMember.tenantId.keyword": tenant}},
            {"term":  {"Data.householdMember.isDeleted": False}},
            {"range": {"Data.householdMember.auditDetails.createdTime": {"gte": gte_ms, "lte": lte_ms}}},
        ]}},
        "_source": ["Data.householdMember.clientReferenceId",
                    "Data.householdMember.householdClientReferenceId",
                    "Data.householdMember.individualClientReferenceId",
                    "Data.householdMember.isHeadOfHousehold"],
    }

    # Individual: id set
    ind_ids = set()

    def cb_ind(hits):
        for h in hits:
            crid = h.get("_source", {}).get("clientReferenceId", "")
            if crid:
                ind_ids.add(crid)
        progress("Loading Individuals (ES)", len(ind_ids), len(ind_ids))

    q_ind = {
        "size": ID_SCROLL_SIZE,
        "query": {"bool": {"must": [
            {"term":  {"tenantId.keyword": tenant}},
            {"term":  {"isDeleted": False}},
            {"range": {"auditDetails.createdTime": {"gte": gte_ms, "lte": lte_ms}}},  # @timestamp changes on re-index
        ]}},
        "_source": ["clientReferenceId"],
    }

    # Project Beneficiary: crid -> beneficiaryClientReferenceId
    pb_data = {}
    pb_ben_ids = set()

    def cb_pb(hits):
        for h in hits:
            pb = h.get("_source", {})
            crid = pb.get("clientReferenceId", "")
            bcrid = pb.get("beneficiaryClientReferenceId", "")
            if crid:
                pb_data[crid] = bcrid
                if bcrid:
                    pb_ben_ids.add(bcrid)
        progress("Loading Project Beneficiaries (ES)", len(pb_data), len(pb_data))

    q_pb = {
        "size": ID_SCROLL_SIZE,
        "query": {"bool": {"must": [
            {"term":  {"tenantId.keyword": tenant}},
            {"term":  {"isDeleted": False}},
            {"range": {"auditDetails.createdTime": {"gte": gte_ms, "lte": lte_ms}}},  # @timestamp changes on re-index
        ]}},
        "_source": ["clientReferenceId", "beneficiaryClientReferenceId"],
    }

    # Project Task: set of projectBeneficiaryClientReferenceId
    task_pb_ids = set()

    def cb_task(hits):
        for h in hits:
            t = h.get("_source", {}).get("Data", {})
            pb_crid = t.get("projectBeneficiaryClientReferenceId", "")
            if pb_crid:
                task_pb_ids.add(pb_crid)
        progress("Loading Project Tasks (ES)", len(task_pb_ids), len(task_pb_ids))

    q_task = {
        "size": ID_SCROLL_SIZE,
        "query": {"bool": {"must": [
            {"term":  {"Data.tenantId.keyword": tenant}},
            {"range": {"Data.taskDates": {"gte": start, "lte": end}}},
        ]}},
        "_source": ["Data.projectBeneficiaryClientReferenceId"],
    }

    t0 = time.time()
    loads = [
        ("household-index-v1",           q_hh,   cb_hh),
        ("household-member-index-v1",    q_mbr,  cb_mbr),
        ("individual-index-v1",          q_ind,  cb_ind),
        ("project-beneficiary-index-v1", q_pb,   cb_pb),
        ("project-task-index-v1",        q_task, cb_task),
    ]
    with ThreadPoolExecutor(max_workers=len(loads)) as ex:
        for f in [ex.submit(es._scroll, idx(sfx), q, cb) for sfx, q, cb in loads]:
            f.result()      # re-raise worker failures
    log(f"[CHAIN] Loaded Households (ES): {len(hh_ids):,}")
    log(f"[CHAIN] Loaded Household Members (ES): {len(mbr_data):,} ({len(head_ind_ids):,} are household heads)")
    log(f"[CHAIN] Loaded Individuals (ES): {len(ind_ids):,}")
    log(f"[CHAIN] Loaded Project Beneficiaries (ES): {len(pb_data):,}")
    log(f"[CHAIN] Loaded Project Tasks (ES): {len(task_pb_ids):,} distinct beneficiary references")
    log(f"[CHAIN] All 5 indices loaded concurrently in {time.time() - t0:.0f}s")

    # Pass 1: windowed set difference gives suspects; pass 2 confirms them on all dates.
    gap_ids = {
        "A": mbr_hh_set - set(hh_ids.keys()),
        "B": set(hh_ids.keys()) - mbr_hh_set,
        "C": set(mbr_ind_map.keys()) - ind_ids,
        "D": mbr_hh_set - pb_ben_ids,
        "E": set(pb_data.keys()) - task_pb_ids,
        # Exclude households already covered by D so no PB is pushed twice.
        "F": set(hh_ids.keys()) - pb_ben_ids - mbr_hh_set,
    }
    # D and F assume household beneficiaries; skip them when PBs mostly point at individuals.
    to_ind = len(pb_ben_ids & (ind_ids | set(mbr_ind_map.keys())))
    to_hh = len(pb_ben_ids & (set(hh_ids.keys()) | mbr_hh_set))
    ben_type = "INDIVIDUAL" if to_ind > to_hh else "HOUSEHOLD"
    log(f"[CHAIN] Beneficiary model inferred: {ben_type} "
        f"({to_hh:,} PBs point at households, {to_ind:,} at individuals)")
    if ben_type == "INDIVIDUAL":
        log("[CHAIN] Gaps D and F skipped: beneficiaries are individuals, so a household "
            "without its own ProjectBeneficiary is normal")
        gap_ids["D"], gap_ids["F"] = set(), set()
    for g in "ABCDEF":
        log(f"[CHAIN] Gap {g} - {GAP_LABELS[g]}: {len(gap_ids[g]):,} candidate(s) in the date window")

    # Pass 2: drop suspects whose counterpart exists on any date (gaps run concurrently).
    def _confirm(g):
        suffix, field, tfield, dfield = EXIST_CHECK[g]
        return g, _existing_ids(es, idx(suffix), field, tenant, tfield, dfield,
                                gap_ids[g], progress=progress, gap=g)

    todo = [g for g in "ABCDEF" if gap_ids[g]]
    if todo:
        t0 = time.time()
        with ThreadPoolExecutor(max_workers=len(todo)) as ex:
            for g, present in ex.map(_confirm, todo):
                n0 = len(gap_ids[g])
                gap_ids[g] = gap_ids[g] - present
                dropped = n0 - len(gap_ids[g])
                log(f"[CHAIN] Gap {g} - after all-dates existence check: {len(gap_ids[g]):,} truly missing; "
                    f"{dropped:,} dismissed (the linked record does exist, just outside the date window)")
        log(f"[CHAIN] All-dates existence checks done in {time.time() - t0:.0f}s")

    # Gap E: household heads are never dosed, so a head PB without a task is expected.
    # Identified by isHeadOfHousehold, not date of birth.
    if gap_ids["E"]:
        heads = {pb for pb in gap_ids["E"] if pb_data.get(pb, "") in head_ind_ids}
        gap_ids["E"] = gap_ids["E"] - heads
        log(f"[CHAIN] Gap E - dosing-target filter: {len(gap_ids['E']):,} eligible member(s) with no delivery task "
            f"(the real coverage gap); {len(heads):,} excluded as household heads "
            f"(the registered household respondent, never a dosing target)")

    gap_a_rows, gap_c_rows = [], []
    for crid, hh_crid, ind_crid, is_head in mbr_data:
        row = {"clientReferenceId": crid, "householdClientReferenceId": hh_crid,
               "individualClientReferenceId": ind_crid, "isHeadOfHousehold": is_head}
        if hh_crid in gap_ids["A"]:
            gap_a_rows.append(row)
        if ind_crid and ind_crid in gap_ids["C"]:
            gap_c_rows.append(row)
    gap_rows = {
        "A": gap_a_rows,
        "B": [{"Household_clientReferenceId": hid, "locality": hh_ids[hid]} for hid in gap_ids["B"]],
        "C": gap_c_rows,
        "D": [{"HouseholdMember_householdClientReferenceId": hid} for hid in gap_ids["D"]],
        "E": [{"PB_clientReferenceId": pid, "beneficiaryClientReferenceId": pb_data[pid]}
              for pid in gap_ids["E"]],
        "F": [{"Household_clientReferenceId": hid, "locality": hh_ids[hid]}
              for hid in gap_ids["F"]],
    }
    del mbr_data
    gc.collect()

    # tracer_rows: recovered, eligible for push. processed_rows: already flagged
    # isProcessed, reported but not re-pushed.
    tracer_rows = {g: [] for g in "ABCDEF"}
    processed_rows = {g: [] for g in "ABCDEF"}
    targets = {g: gap_ids[g] for g in "ABCDEF" if gap_ids[g]}
    for g in "ABCDEF":
        if g not in targets:
            log(f"[CHAIN] Gap {g} - nothing missing, skipping error-tracer search")
    if targets:
        total_missing = sum(len(t) for t in targets.values())
        log(f"[CHAIN] Searching error-tracer for {total_missing:,} missing record(s) "
            f"across gap(s) {', '.join(sorted(targets))} - single combined scan")
        found_all, already_all = _tracer_scan_all(es, targets, gte_iso, tenant,
                                                  progress, log=log)
        for g in sorted(targets):
            found, already = found_all[g], already_all[g]
            tracer_rows[g] = list(found.values())
            processed_rows[g] = list(already.values())
            cov = len(found) / len(targets[g]) * 100
            note = (f"; {len(already):,} already recovered in a previous run (skipped)"
                    if already else "")
            log(f"[CHAIN] Gap {g} - recovered {len(found):,} of {len(targets[g]):,} "
                f"({cov:.1f}%) from the error-tracer{note}")

    # Push input: new recoveries only, so processed ids are never re-created.
    for g in "ABCDEF":
        _to_df(tracer_rows[g], TRACER_COLS).to_csv(
            os.path.join(run_dir, f"{tenant}_chain_tracer_{g}_{ts}.csv"), index=False)

    summary_rows = []
    for g in "ABCDEF":
        t_target = len(gap_ids[g])
        t_found = len(tracer_rows[g])
        t_proc = len(processed_rows[g])
        t_absent = max(t_target - t_found - t_proc, 0)
        ever = t_found + t_proc      # seen in the tracer, new or processed
        summary_rows.append({
            "Gap": g, "Relation": GAP_LABELS[g],
            "Missing (ES)": t_target, "In Tracer (new)": t_found,
            "Already Processed": t_proc, "Not in Tracer": t_absent,
            "Coverage %": f"{ever / t_target * 100:.1f}%" if t_target else "N/A",
        })
    df_sum = pd.DataFrame(summary_rows)

    # Sheets per gap: GAP (missing), TRACER (recovered), PROCESSED (already flagged), UNREC (not in tracer).
    ordered = [("SUMMARY", df_sum)]
    fill_map = {"SUMMARY": excel.SUM_FILL}
    for g in "ABCDEF":
        recovered = {str(r["id_value"]) for r in tracer_rows[g]}
        processed = {str(r["id_value"]) for r in processed_rows[g]}
        unrec_ids = [i for i in gap_ids[g]
                     if str(i) not in recovered and str(i) not in processed]
        df_unrec = pd.DataFrame(
            {"Gap": g, "Relation": GAP_LABELS[g], "id_value": unrec_ids,
             "Status": "NOT_IN_TRACER"}
            if unrec_ids else
            {c: [] for c in ("Gap", "Relation", "id_value", "Status")})
        ordered.append((f"GAP_{g}", _to_df(gap_rows[g], GAP_COLS[g])))
        ordered.append((f"TRACER_{g}", _to_df(tracer_rows[g], TRACER_COLS)))
        ordered.append((f"PROCESSED_{g}", _to_df(processed_rows[g], TRACER_COLS)))
        ordered.append((f"UNREC_{g}", df_unrec))
        df_unrec.to_csv(os.path.join(run_dir, f"{tenant}_chain_unrec_{g}_{ts}.csv"), index=False)
        # Processed yet still missing means an earlier push did not persist; keep for audit.
        _to_df(processed_rows[g], TRACER_COLS).to_csv(
            os.path.join(run_dir, f"{tenant}_chain_processed_{g}_{ts}.csv"), index=False)
        fill_map[f"GAP_{g}"] = excel.RED_FILL
        fill_map[f"TRACER_{g}"] = excel.GRN_FILL
        fill_map[f"PROCESSED_{g}"] = excel.SUM_FILL
        fill_map[f"UNREC_{g}"] = excel.YEL_FILL

    out_xlsx = os.path.join(run_dir, f"{tenant}_chain_recovery_{ts}.xlsx")
    excel.write_sheets(out_xlsx, ordered, fill_map=fill_map)
    log(f"[CHAIN] Workbook written: {out_xlsx}")

    return {"run_dir": run_dir, "ts": ts, "summary": df_sum, "excel_path": out_xlsx}


def _tracer_scan_all(es, targets, gte_iso, tenant, progress, log=_noop):
    """Search the tracer once for all gaps; `targets` is {gap: set(missing ids)}.

    Three passes, each on ids still unmatched: bulk-create url+key, tenant-scoped
    sliced scan, then a capped per-id search. Newest doc wins. Returns
    (found, already), each {gap: {id: row}}; `already` holds isProcessed matches.
    """
    targets = {g: set(t) for g, t in targets.items() if t}
    gap_cfgs = {g: TRACER_CFG[g] for g in targets}
    # found: eligible for push. already: isProcessed=true, never re-pushed.
    found = {g: {} for g in targets}
    already = {g: {} for g in targets}
    total_target = sum(len(t) for t in targets.values())
    scanned = {"n": 0}
    lock = threading.Lock()   # cb runs concurrently in the sliced scroll
    base_source = ["Data.uuid", "Data.@timestamp", "Data.apiDetails.url",
                   "Data.apiDetails.requestBody", "Data.errors", "Data.isProcessed"]

    def n_found():
        return sum(len(f) for f in found.values())

    def remaining(g):
        return targets[g] - set(found[g].keys())

    def all_done():
        return all(not remaining(g) for g in targets)

    def cb(hits):
        # Parse outside the lock; merge under it.
        matches = []   # (gap, rid, ts, processed, row)
        for raw in hits:
            d = (raw.get("_source") or {}).get("Data") or {}
            ts = d.get("@timestamp", "")
            doc_uuid = d.get("uuid") or raw.get("_id")
            errors = d.get("errors") or []
            err0 = errors[0] if (errors and isinstance(errors[0], dict)) else {}   # errors may hold a null
            err_code = err0.get("errorCode", "")
            err_msg = err0.get("errorMessage", "")
            raw_body = (d.get("apiDetails") or {}).get("requestBody")
            api_url = (d.get("apiDetails") or {}).get("url", "")
            # requestBody may be a dict, JSON, an unquoted Java literal, or unparseable.
            if isinstance(raw_body, (dict, list)):
                body = raw_body
            elif isinstance(raw_body, str) and raw_body.strip():
                try:
                    body = json.loads(raw_body)
                except Exception:
                    body = _loose_json(raw_body)
                    if body is None:
                        continue
            else:
                continue
            # A bare list is the record list itself; a dict is checked for every wrapper key.
            if isinstance(body, list):
                record_lists = [body]
                username = ""
            elif isinstance(body, dict):
                username = ((body.get("RequestInfo") or {}).get("userInfo") or {}).get("userName", "")  # userInfo may be null
                record_lists = [body.get(k) for k in BODY_KEYS if isinstance(body.get(k), list)]
            else:
                continue
            processed = _is_processed(d.get("isProcessed"))
            for records in record_lists:
                for rec in records:
                    if not isinstance(rec, dict) or rec.get("tenantId", "") != tenant:
                        continue
                    for g, cfg in gap_cfgs.items():
                        rid = rec.get(cfg["id_field"], "")
                        if not rid or rid not in targets[g]:
                            continue
                        matches.append((g, rid, ts, processed, {
                            "Gap": g, "Day": ts[:10], "Timestamp": ts,
                            "Error_Doc_UUID": doc_uuid, "Error_Code": err_code,
                            "Error_Message": err_msg, "Username": username, "API_URL": api_url,
                            "id_field": cfg["id_field"], "id_value": rid,
                            "clientReferenceId": rec.get("clientReferenceId", ""),
                            "tenantId": rec.get("tenantId", ""),
                            "isDeleted": rec.get("isDeleted", False),
                            "rowVersion": rec.get("rowVersion", ""),
                            "payload_json": json.dumps(rec),
                        }))
        with lock:
            scanned["n"] += len(hits)
            for g, rid, ts, processed, row in matches:
                f, a = found[g], already[g]
                if processed:
                    # an unprocessed match always wins over a processed one
                    if rid not in f and (rid not in a or ts > a[rid]["Timestamp"]):
                        a[rid] = row
                else:
                    if rid not in f or ts > f[rid]["Timestamp"]:
                        f[rid] = row
                    a.pop(rid, None)
            # Scanned count is rounded to 100k to limit progress updates.
            progress(f"Searching error-tracer ({scanned['n'] // 100000 * 100000:,} docs scanned)",
                     n_found(), total_target)
            return all_done()   # True stops the scroll early

    url = f"{es.base}/{TRACER_INDEX}/_search"

    # Pass 1: bulk-create url + body key for every gap; cb filters by record tenantId.
    pairs = sorted({(c["key"], c["url"]) for c in gap_cfgs.values()})
    t0 = time.time()
    es._scroll(url, {
        "size": es.batch,
        "query": {"bool": {
            "must": [{"range": {"Data.@timestamp": {"gte": gte_iso, "lte": "now"}}}],
            "should": [{"bool": {"must": [
                {"match_phrase": {"Data.apiDetails.requestBody": key}},
                {"term":         {"Data.apiDetails.url.keyword": bulk_path}},
            ]}} for key, bulk_path in pairs],
            "minimum_should_match": 1,
        }},
        "sort": ["_doc"],
        "_source": base_source,
    }, cb)
    log(f"[CHAIN] Tracer scan 1/3 (bulk-create urls): {n_found():,}/{total_target:,} matched, "
        f"{scanned['n']:,} docs scanned ({time.time() - t0:.0f}s)")

    # Pass 2: tenant-scoped sliced scan with no url/key filter, for payloads sent elsewhere.
    if not all_done():
        t0 = time.time()
        n_before, s_before = n_found(), scanned["n"]
        es._sliced_scroll(url, {
            "size": es.batch,
            "query": {"bool": {"must": [
                {"match_phrase": {"Data.apiDetails.requestBody": f'"tenantId":"{tenant}"'}},
                {"range": {"Data.@timestamp": {"gte": gte_iso, "lte": "now"}}},
            ]}},
            "sort": ["_doc"],
            "_source": base_source,
        }, cb, slices=TRACER_SCAN_SLICES)
        log(f"[CHAIN] Tracer scan 2/3 (deep tenant scan, {TRACER_SCAN_SLICES} parallel slices): "
            f"+{n_found() - n_before:,} matched, {scanned['n'] - s_before:,} docs scanned "
            f"({time.time() - t0:.0f}s)")

    # Pass 3: search the literal id with no tenant/date filter, batched and capped per gap.
    residual = []
    for g in sorted(targets):
        rem = remaining(g)
        if not rem:
            continue
        if len(rem) > PHASE4_MAX_IDS:
            log(f"[CHAIN] Gap {g} - skipping last-resort per-id tracer search: "
                f"{len(rem):,} id(s) remain (cap {PHASE4_MAX_IDS:,}); "
                f"they stay in Not-in-Tracer")
        else:
            residual.extend(rem)
    if residual:
        t0 = time.time()
        n_before = n_found()
        for i in range(0, len(residual), PHASE4_CHUNK):
            if all_done():
                break
            chunk = residual[i:i + PHASE4_CHUNK]
            es._scroll(url, {
                "size": es.batch,
                "query": {"bool": {
                    "should": [{"match_phrase": {"Data.apiDetails.requestBody": rid}}
                               for rid in chunk],
                    "minimum_should_match": 1,
                }},
                "sort": ["_doc"],
                "_source": base_source,
            }, cb)
            progress("Last-resort id tracer search",
                     min(i + PHASE4_CHUNK, len(residual)), len(residual))
        if n_found() > n_before:
            log(f"[CHAIN] Tracer scan 3/3 (last-resort id search) recovered "
                f"{n_found() - n_before:,} more record(s) ({time.time() - t0:.0f}s)")

    # An id recovered as new is never also reported as processed.
    for g in targets:
        already[g] = {k: v for k, v in already[g].items() if k not in found[g]}
    return found, already
