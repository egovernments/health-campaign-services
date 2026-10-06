"""
Phase functions called by core.runner.

  run_check : DB vs ES reconciliation; writes gap and detail CSVs plus a summary.
  run_chain : chain recovery from the error tracer (core.chain).
  run_push  : creates ghosts, re-indexes index-failed records and creates chain
              recoveries, after an existence check; writes a result workbook.
"""

import gc
import glob
import json
import os
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime

import pandas as pd

from hcm_data_healer.core import chain as chain_mod
from hcm_data_healer.core.api_client import APIClient, ExistenceCheckFailed, StopRun
from hcm_data_healer.core.db_client import DBClient
from hcm_data_healer.core.entities import get_entities
from hcm_data_healer.core.es_client import ESClient
from hcm_data_healer.core.utils import excel
from hcm_data_healer.core.utils.dates import epoch_ms as _epoch_ms, iso as _iso, noop as _noop
from hcm_data_healer.core.utils.ids import unpack

# Parents are created before the records that reference them.
CREATE_ORDER = ["Household", "Individual", "HouseholdMember", "ProjectBeneficiary",
                "ProjectTask", "HFReferral"]

# Chain gap -> entity, and the create order (F is a ProjectBeneficiary, pushed before tasks).
CHAIN_GAP_ENTITY = {"A": "Household", "B": "HouseholdMember", "C": "Individual",
                    "D": "ProjectBeneficiary", "E": "ProjectTask",
                    "F": "ProjectBeneficiary"}
CHAIN_PUSH_ORDER = ["A", "C", "B", "D", "F", "E"]

# Outcome text per status for the push result workbook.
OUTCOME_TEXT = {
    "PUSHED":           "Created successfully",
    "UPDATED":          "Re-indexed successfully (update sent)",
    "SKIPPED_EXISTS":   "Skipped - already exists in the system",
    "SKIPPED":          "Skipped - duplicate (API reported it already exists)",
    "NO_PAYLOAD":       "Failed - no source payload to build the record",
    "NOT_FOUND_IN_API": "Failed - not found via search API (cannot update)",
    "NO_CLIENT_AUDIT":  "Skipped - source has no clientAuditDetails",
    "FAILED":           "Failed - see Response for the error",
}


def _case_desc(case):
    m = {"CREATE": "Reconciliation: CREATE ghost (ES not in DB)",
         "UPDATE": "Reconciliation: UPDATE index-failed (DB not in ES)"}
    if case in m:
        return m[case]
    if str(case).startswith("CHAIN_"):
        return f"Error-tracer chain gap {str(case).split('_')[-1]}: create recovered record"
    return case


def _source_of(case):
    return "Error-tracer chain" if str(case).startswith("CHAIN") else "Reconciliation"


def _run_dir(base, state, ts, label=""):
    # e.g. ko_reconciliation_<ts>, ko_push_<ts>
    name = f"{state}_{label}_{ts}" if label else f"{state}_{ts}"
    d = os.path.join(base, name)
    os.makedirs(d, exist_ok=True)
    return d


# Phase 1: reconciliation check
def run_check(cfg, output_root, log=None, progress=None, ts=None):
    log = log or _noop
    progress = progress or _noop
    state = cfg["tenant"]
    start, end = cfg["start_date"], cfg["end_date"]
    gte_ms, lte_ms = _epoch_ms(start), _epoch_ms(end, end_of_day=True)
    gte_iso, lte_iso = _iso(start), _iso(end, end_of_day=True)

    entities = get_entities(cfg["tenant"], cfg["task_filter"],
                            cfg.get("service_template"), cfg.get("service_overrides"),
                            index_prefix=cfg.get("es_index_prefix"))
    db = DBClient(cfg["db"])
    es = ESClient(cfg["es"]["base_url"], cfg["es"]["username"], cfg["es"]["password"],
                  verify_ssl=cfg["es"].get("verify_ssl", False),
                  batch_size=cfg["es"].get("batch_size", 5000))

    # Shared with the chain run so both outputs carry one timestamp.
    ts = ts or datetime.now().strftime("%Y%m%d_%H%M%S")
    run_dir = _run_dir(output_root, state, ts, "reconciliation")
    log(f"[RECON] Elasticsearch vs Database reconciliation - tenant '{state}', window {start} to {end}")
    log(f"[RECON] Output: {run_dir}")

    summary_rows = []
    gaps = {}
    for entity in entities:
        name = entity["name"]
        folder = os.path.join(run_dir, entity["folder"])
        os.makedirs(folder, exist_ok=True)
        try:
            # Load DB and ES ids in parallel; ids are packed keys to save memory.
            log(f"\n[RECON] {name}: loading ids from Database and Elasticsearch ...")
            with ThreadPoolExecutor(max_workers=2) as pool:
                f_db = pool.submit(db.load_ids, entity, gte_ms, lte_ms,
                                   lambda t, u, n=name: progress(f"{n} DB", t, u))
                f_es = pool.submit(es.load_ids, entity, gte_iso, lte_iso, gte_ms, lte_ms,
                                   lambda t, u, n=name: progress(f"{n} ES", t, u))
                db_ids = f_db.result()
                es_ids = f_es.result()
            log(f"[RECON] {name}: Database {len(db_ids):,} | Elasticsearch {len(es_ids):,}")

            es_not_in_db = es_ids - db_ids   # ghosts (packed keys)
            db_not_in_es = db_ids - es_ids   # index-failed (packed keys)
            n_db, n_es, n_matched = len(db_ids), len(es_ids), len(db_ids & es_ids)
            del db_ids, es_ids
            gc.collect()

            gaps[name] = {"es_not_in_db": len(es_not_in_db), "db_not_in_es": len(db_not_in_es)}
            log(f"[RECON] {name}: {n_matched:,} matched | {len(es_not_in_db):,} ghost "
                f"(in ES, missing from DB -> CREATE) | {len(db_not_in_es):,} index-failed "
                f"(in DB, missing from ES -> re-index)")

            ghost_ids = [unpack(k) for k in es_not_in_db]
            idxfail_ids = [unpack(k) for k in db_not_in_es]
            del es_not_in_db, db_not_in_es
            gc.collect()

            pd.DataFrame({"clientReferenceId": sorted(ghost_ids)}).to_csv(
                os.path.join(folder, f"{state}_ES_not_in_DB_{ts}.csv"), index=False)
            pd.DataFrame({"clientReferenceId": sorted(idxfail_ids)}).to_csv(
                os.path.join(folder, f"{state}_DB_not_in_ES_{ts}.csv"), index=False)

            if ghost_ids:
                log(f"[RECON] {name}: exporting ES source rows for {len(ghost_ids):,} ghost(s) ...")
                df_es = es.fetch_details(entity, ghost_ids)
                df_es.to_csv(os.path.join(folder, f"{state}_ES_details_not_in_DB_{ts}.csv"), index=False)
                del df_es
                gc.collect()
            if idxfail_ids:
                log(f"[RECON] {name}: exporting DB rows for {len(idxfail_ids):,} index-failed record(s) ...")
                # Streamed to CSV to avoid holding all rows in memory.
                db.fetch_details_to_csv(
                    entity, idxfail_ids,
                    os.path.join(folder, f"{state}_DB_details_not_in_ES_{ts}.csv"))

            summary_rows.append({
                "Entity": name, "DB ids": n_db, "ES ids": n_es,
                "Matched": n_matched, "Ghost (ES not in DB)": len(ghost_ids),
                "Index-failed (DB not in ES)": len(idxfail_ids), "Error": "",
            })
            del ghost_ids, idxfail_ids
            gc.collect()
        except Exception as exc:
            log(f"[RECON] {name}: ERROR - {exc} (skipping this entity)")
            gaps[name] = {"es_not_in_db": 0, "db_not_in_es": 0}
            summary_rows.append({
                "Entity": name, "DB ids": "ERR", "ES ids": "ERR", "Matched": "",
                "Ghost (ES not in DB)": "", "Index-failed (DB not in ES)": "",
                "Error": str(exc)[:300],
            })

    df_sum = pd.DataFrame(summary_rows)
    sum_path = os.path.join(run_dir, f"{state}_reconciliation_summary_{ts}.xlsx")
    excel.write_sheets(sum_path, {"SUMMARY": df_sum}, default_fill=excel.SUM_FILL)
    log(f"\n[RECON] Summary workbook: {sum_path}")

    return {"run_dir": run_dir, "ts": ts, "summary": df_sum,
            "summary_path": sum_path, "gaps": gaps}


# Phase 2: error-tracer chain recovery
def _read_ids(folder, pattern, col="clientReferenceId"):
    files = sorted(glob.glob(os.path.join(folder, pattern)))
    if not files:
        return []
    df = pd.read_csv(files[-1])
    if col not in df.columns:
        return []
    return df[col].dropna().astype(str).unique().tolist()


def run_chain(cfg, output_root, log=None, progress=None, ts=None):
    """Run chain recovery (core.chain); independent of run_check."""
    return chain_mod.recover_chain(cfg, output_root, log=log, progress=progress, ts=ts)


# Phase 3: push
def _latest(folder, pattern):
    files = sorted(glob.glob(os.path.join(folder, pattern)))
    return files[-1] if files else None


def run_push(cfg, recon_run_dir, chain_run_dir, output_root, opts=None,
             log=None, progress=None, record_sink=None, should_stop=None):
    """Create ghosts, re-index index-failed records and create chain recoveries.

    opts toggles do_create_ghosts, do_update_missing and do_push_chain. record_sink
    receives result batches during the run; should_stop returns a stop reason.
    Before any create, an id found by the search API or in the DB table is skipped;
    if the check fails, the ids are marked FAILED and nothing is created.
    """
    log = log or _noop
    progress = progress or _noop
    opts = opts or {}
    do_create = opts.get("do_create_ghosts", True)
    do_update = opts.get("do_update_missing", True)
    do_push_chain = opts.get("do_push_chain", True) and bool(chain_run_dir)

    state = cfg["tenant"]
    entities = {e["name"]: e for e in get_entities(
        cfg["tenant"], cfg["task_filter"],
        cfg.get("service_template"), cfg.get("service_overrides"),
        index_prefix=cfg.get("es_index_prefix"))}
    api = APIClient(cfg["tenant"], cfg["auth_token"], cfg["user_uuid"],
                    options=cfg.get("push_options", {}), verify_ssl=cfg.get("hcm_verify_ssl", False))

    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    run_dir = _run_dir(output_root, state, ts, "push")
    log(f"[PUSH] Reconciliation input: {recon_run_dir}")
    log(f"[PUSH] Chain input: {chain_run_dir or '(none)'}")
    log(f"[PUSH] Output: {run_dir}")

    all_results = []
    pending = []                 # rows not yet sent to record_sink
    sink_every = 500
    db = DBClient(cfg["db"])

    def record(entity_name, case, crid, status, resp, payload=None, http=None, error=""):
        """Record one result row with its payload and the HCM response."""
        if payload is not None and not isinstance(payload, str):
            payload = json.dumps(payload, default=str)
        row = {"Entity": entity_name, "Case": case, "clientReferenceId": crid,
               "Status": status, "Response": str(resp)[:4000],
               "Payload": payload or "", "HttpStatus": http or "", "Error": str(error or "")[:2000]}
        all_results.append(row)
        if record_sink:
            pending.append(row)
            if len(pending) >= sink_every:
                flush()

    def flush():
        if record_sink and pending:
            batch = pending[:]
            pending.clear()
            record_sink(batch)

    def safe_existing(entity, ids, case):
        """Return ids that already exist (search API or DB table).

        Fails closed: on any check error, every id is recorded FAILED and None is returned.
        """
        try:
            return set(api.check_exists(entity, ids)) | db.existing_ids(entity, ids)
        except ExistenceCheckFailed as exc:
            reason = f"existence check failed, not created: {exc}"
        except StopRun:
            raise
        except Exception as exc:                                  # DB lookup error
            reason = f"DB existence check failed, not created: {type(exc).__name__}: {exc}"
        log(f"[PUSH] {entity['name']} {case}: {reason}")
        for rid in ids:
            record(entity["name"], case, rid, "FAILED", reason)
        return None

    def stop_reason():
        return (should_stop() if should_stop else "") or ""

    ordered_names = [n for n in CREATE_ORDER if n in entities]
    aborted = False   # set on auth failure or emergency stop
    abort_reason = ""

    # Reconciliation: create ghosts, rebuilt from ES details.
    if do_create:
        for name in ordered_names:
            entity = entities[name]
            folder = os.path.join(recon_run_dir, entity["folder"])
            ids = _read_ids(folder, f"{state}_ES_not_in_DB_*.csv")
            if not ids:
                continue
            es_details = _latest(folder, f"{state}_ES_details_not_in_DB_*.csv")
            df_es = pd.read_csv(es_details) if es_details and os.path.exists(es_details) else pd.DataFrame()
            if not df_es.empty:
                df_es.columns = [c.strip() for c in df_es.columns]

            why = stop_reason()
            if why:
                aborted, abort_reason = True, why
                break
            log(f"\n[PUSH] {name}: creating {len(ids):,} ghost record(s) ...")
            try:
                existing = safe_existing(entity, ids, "CREATE")
            except StopRun as exc:
                log(f"[STOP] {exc} - aborting run")
                aborted, abort_reason = True, str(exc); break
            if existing is None:
                continue
            log(f"[PUSH] {name}: {len(existing):,} already exist (will skip)")

            id_col = entity["push_id_col_es"]
            row_by_id = {}
            if not df_es.empty and id_col in df_es.columns:
                for _, r in df_es.iterrows():
                    row_by_id[str(r.get(id_col))] = r

            n = 0
            for rid in ids:
                n += 1
                progress(f"{name} create", n, len(ids))
                try:
                    payload = entity["build"](row_by_id[rid], cfg["tenant"]) if rid in row_by_id else None
                except Exception as exc:                          # noqa: BLE001
                    record(name, "CREATE", rid, "FAILED", f"payload rebuild failed: {exc}",
                           error=f"payload rebuild failed: {exc}")
                    continue
                if rid in existing:
                    record(name, "CREATE", rid, "SKIPPED_EXISTS", "", payload=payload)
                    continue
                try:
                    if payload is None:
                        record(name, "CREATE", rid, "NO_PAYLOAD", "not in ES details")
                        continue
                    if not payload.get("clientAuditDetails"):
                        record(name, "CREATE", rid, "NO_CLIENT_AUDIT", "", payload=payload)
                        continue
                    status, http, err = api.create(entity, payload)
                    record(name, "CREATE", rid, status, f"[es-rebuild] HTTP {http} {err}",
                           payload=payload, http=http, error=err)
                except StopRun as exc:
                    log(f"[STOP] {exc} - aborting run")
                    aborted, abort_reason = True, str(exc); break
                except Exception as exc:
                    record(name, "CREATE", rid, "FAILED", str(exc), payload=payload, error=str(exc))
            if aborted:
                break

    # Reconciliation: re-index records missing from ES via update.
    if do_update and not aborted:
        for name in ordered_names:
            entity = entities[name]
            folder = os.path.join(recon_run_dir, entity["folder"])
            ids = _read_ids(folder, f"{state}_DB_not_in_ES_*.csv")
            if not ids:
                continue
            why = stop_reason()
            if why:
                aborted, abort_reason = True, why
                break
            log(f"\n[PUSH] {name}: re-indexing {len(ids):,} index-failed record(s) ...")
            n = 0
            for rid in ids:
                n += 1
                progress(f"{name} update", n, len(ids))
                try:
                    full = api.search_one(entity, rid)
                    if full is None:
                        record(name, "UPDATE", rid, "NOT_FOUND_IN_API", ""); continue
                    # Re-posted unchanged; a null clientAuditDetails is only noted.
                    note = "" if full.get("clientAuditDetails") else " | clientAuditDetails NULL"
                    status, http, err = api.update(entity, full)
                    record(name, "UPDATE", rid, status, f"HTTP {http} {err}{note}",
                           payload=full, http=http, error=err)
                except StopRun as exc:
                    log(f"[STOP] {exc} - aborting run")
                    aborted, abort_reason = True, str(exc); break
                except Exception as exc:
                    record(name, "UPDATE", rid, "FAILED", str(exc), error=str(exc))
            if aborted:
                break

    # Chain: create recovered tracer payloads after the existence check.
    doc_of_crid = {}        # recovered crid -> its source tracer Error_Doc_UUID
    status_of_crid = {}     # recovered crid -> final push status
    if do_push_chain and not aborted:
        for gap in CHAIN_PUSH_ORDER:
            ename = CHAIN_GAP_ENTITY[gap]
            entity = entities.get(ename)
            if entity is None:
                continue
            tfile = _latest(chain_run_dir, f"{state}_chain_tracer_{gap}_*.csv")
            if not tfile or not os.path.exists(tfile):
                continue
            tdf = pd.read_csv(tfile)
            if tdf.empty or "clientReferenceId" not in tdf.columns or "payload_json" not in tdf.columns:
                continue
            # Dedupe by clientReferenceId and remember each source tracer doc.
            has_uuid = "Error_Doc_UUID" in tdf.columns
            payload_by_id = {}
            for _, r in tdf.iterrows():
                crid = str(r.get("clientReferenceId", "")).strip()
                if not crid or crid.lower() == "nan":
                    continue
                # Only _create bodies are re-posted; update/delete bodies would roll back data.
                api_url = str(r.get("API_URL", "") or "").lower()
                deleted = str(r.get("isDeleted", "")).strip().lower() in ("true", "1")
                if (api_url and api_url != "nan" and "_create" not in api_url) or deleted:
                    record(ename, f"CHAIN_{gap}", crid, "SKIPPED_NOT_CREATE",
                           f"tracer body from {api_url or '?'} isDeleted={deleted}",
                           payload=r.get("payload_json"))
                    continue
                payload_by_id[crid] = r["payload_json"]
                if has_uuid:
                    du = str(r.get("Error_Doc_UUID", "")).strip()
                    if du and du.lower() != "nan":
                        doc_of_crid[crid] = du
            ids = list(payload_by_id.keys())
            if not ids:
                continue
            case = f"CHAIN_{gap}"
            why = stop_reason()
            if why:
                aborted, abort_reason = True, why
                break
            log(f"\n[PUSH] Gap {gap} ({ename}): creating {len(ids):,} recovered record(s) ...")
            try:
                existing = safe_existing(entity, ids, case)
            except StopRun as exc:
                log(f"[STOP] {exc} - aborting run")
                aborted, abort_reason = True, str(exc); break
            if existing is None:
                continue
            log(f"[PUSH] Gap {gap}: {len(existing):,} already exist (will skip)")

            n = 0
            for rid in ids:
                n += 1
                progress(f"chain {gap} create", n, len(ids))
                if rid in existing:
                    record(ename, case, rid, "SKIPPED_EXISTS", "", payload=payload_by_id[rid])
                    status_of_crid[rid] = "SKIPPED_EXISTS"
                    continue
                try:
                    payload = json.loads(payload_by_id[rid])
                    if not payload.get("clientAuditDetails"):
                        record(ename, case, rid, "NO_CLIENT_AUDIT", "", payload=payload)
                        status_of_crid[rid] = "NO_CLIENT_AUDIT"
                        continue
                    status, http, err = api.create(entity, payload)
                    record(ename, case, rid, status, f"[chain] HTTP {http} {err}",
                           payload=payload, http=http, error=err)
                    status_of_crid[rid] = status
                except StopRun as exc:
                    log(f"[STOP] {exc} - aborting run")
                    aborted, abort_reason = True, str(exc); break
                except Exception as exc:
                    record(ename, case, rid, "FAILED", str(exc), payload=payload_by_id[rid], error=str(exc))
                    status_of_crid[rid] = "FAILED"
            if aborted:
                break

    # Optional: flag tracer docs as processed only when every id they carried pushed OK.
    # flag_ok: True flagged, False failed, None not attempted.
    flush()                    # audit receives every outcome before flagging
    if aborted:
        log(f"[PUSH] STOPPED before finishing: {abort_reason}")

    flag_note, flag_ok = "", None
    if cfg.get("flag_processed") and doc_of_crid:
        OK = {"PUSHED", "UPDATED", "SKIPPED_EXISTS"}
        doc_crids = {}
        for crid, du in doc_of_crid.items():
            doc_crids.setdefault(du, set()).add(crid)
        docs_to_flag = {du for du, crids in doc_crids.items()
                        if all(status_of_crid.get(c) in OK for c in crids)}
        if not docs_to_flag:
            flag_note = ("Mark-processed: nothing to flag (no error-tracer records fully "
                         "pushed this run).")
            log(f"[PUSH] {flag_note}")
        else:
            try:
                es = ESClient(cfg["es"]["base_url"], cfg["es"]["username"],
                              cfg["es"]["password"], verify_ssl=cfg["es"].get("verify_ssl", False))
                n_flag = es.flag_tracer_processed(
                    sorted(docs_to_flag),
                    progress=lambda c, t: progress("flag processed", c, t))
                flag_ok = True
                flag_note = (f"Mark-processed: set isProcessed=true on {n_flag:,} tracer "
                             f"doc(s) ({len(docs_to_flag):,} fully-recovered).")
                log(f"[PUSH] {flag_note}")
            except Exception as exc:
                # Non-fatal: the next run's existence check prevents re-creates.
                flag_ok = False
                flag_note = (f"Mark-processed FAILED (records were still pushed OK): {exc} "
                             "Use an ES user with update rights on the tracer index to enable it.")
                log(f"[PUSH] {flag_note}")

    # Result workbook: one sheet per outcome.
    cols = ["Source", "Entity", "Operation", "clientReferenceId",
            "Status", "Outcome", "Response", "Case"]
    if not all_results:
        df = pd.DataFrame(columns=cols)
    else:
        df = pd.DataFrame(all_results)
        df["Source"] = df["Case"].map(_source_of)
        df["Operation"] = df["Case"].map(_case_desc)
        df["Outcome"] = df["Status"].map(OUTCOME_TEXT).fillna(df["Status"])
        df = df[[c for c in cols if c in df.columns]]

    summary_rows = []
    if not df.empty:
        for (name, case), sub in df.groupby(["Entity", "Case"], sort=False):
            summary_rows.append({
                "Source": _source_of(case), "Entity": name, "Operation": _case_desc(case),
                "Created": int((sub["Status"] == "PUSHED").sum()),
                "Re-indexed": int((sub["Status"] == "UPDATED").sum()),
                "Duplicate/Exists": int(sub["Status"].str.contains("SKIP", na=False).sum()),
                "Not found": int((sub["Status"] == "NOT_FOUND_IN_API").sum()),
                "No payload": int((sub["Status"] == "NO_PAYLOAD").sum()),
                "No client audit": int((sub["Status"] == "NO_CLIENT_AUDIT").sum()),
                "Failed": int((sub["Status"] == "FAILED").sum()),
                "Total": len(sub),
            })

    is_created = df["Status"] == "PUSHED"
    is_reindexed = df["Status"] == "UPDATED"
    is_duplicate = df["Status"].str.contains("SKIP", na=False)
    is_failed = df["Status"].isin(["FAILED", "NOT_FOUND_IN_API", "NO_PAYLOAD"])
    failed_df = df[is_failed].copy()

    sheets = {
        "SUMMARY": pd.DataFrame(summary_rows),
        "CREATED": df[is_created],
        "RE_INDEXED": df[is_reindexed],
        "DUPLICATE_EXISTS": df[is_duplicate],
        "FAILED": failed_df,
        "ALL_RESULTS": df,
    }
    fill_map = {"SUMMARY": excel.SUM_FILL, "CREATED": excel.GRN_FILL,
                "RE_INDEXED": excel.GRN_FILL, "DUPLICATE_EXISTS": excel.YEL_FILL,
                "FAILED": excel.RED_FILL, "ALL_RESULTS": excel.SUM_FILL}
    out_xlsx = os.path.join(run_dir, f"{state}_push_result_{ts}.xlsx")
    excel.write_sheets(out_xlsx, sheets, fill_map=fill_map)

    failed_path = os.path.join(run_dir, f"{state}_push_FAILED_{ts}.csv")
    failed_df.to_csv(failed_path, index=False)
    log(f"\n[PUSH] Result workbook: {out_xlsx}")
    log(f"[PUSH] Failures CSV: {failed_path} ({len(failed_df):,} row(s))")

    pushed = int(is_created.sum())
    updated = int(is_reindexed.sum())
    skipped = int(is_duplicate.sum())
    failed = int(len(failed_df))
    no_audit = int((df["Status"] == "NO_CLIENT_AUDIT").sum()) if not df.empty else 0
    log(f"[PUSH] Done - created {pushed:,}, re-indexed {updated:,}, "
        f"skipped/duplicate {skipped:,}, failed {failed:,}, "
        f"no clientAuditDetails {no_audit:,}")
    return {"run_dir": run_dir, "ts": ts, "excel_path": out_xlsx,
            "failed_path": failed_path, "failed_df": failed_df,
            "results": df, "summary": pd.DataFrame(summary_rows),
            "flag_note": flag_note, "flag_ok": flag_ok,
            "pushed": pushed, "updated": updated, "skipped": skipped, "failed": failed,
            "no_audit": no_audit, "aborted": aborted, "abort_reason": abort_reason}
