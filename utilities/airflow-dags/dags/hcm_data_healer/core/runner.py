"""
Headless entry point for analyze and push, used by the Airflow DAG and the CLI.
Run from the dags/ folder:

    python -m hcm_data_healer.core.runner analyze --tenant so --start 2026-07-01 --end 2026-07-05
    python -m hcm_data_healer.core.runner push    --tenant so --start 2026-07-01 --end 2026-07-05 \
        --recon-dir <recon run dir> --chain-dir <tracer run dir> --yes

Both return JSON-safe dicts (XCom-ready). Push writes to the system and must
only run on a reviewed analysis (--yes on the CLI).
"""

import argparse
import json
import logging
import os
import sys
import threading
import time
from datetime import datetime

from hcm_data_healer.core.config import DEFAULT_USER_UUID, PROJECT_ROOT, build_campaign_context
from hcm_data_healer.core.utils import processor

log = logging.getLogger("hcm_data_healer")

# Local runs only; under Airflow the DAG passes a temp dir (code mount is read-only).
DEFAULT_OUTPUT = os.path.join(PROJECT_ROOT, "output")


class _ProgressLog:
    """Thread-safe progress callback that logs at most once per stage every `every` seconds."""

    def __init__(self, prefix, every=30):
        self.prefix = prefix
        self.every = every
        self._last = {}
        self._lock = threading.Lock()

    def __call__(self, stage, cur, tot):
        now = time.time()
        with self._lock:
            if now - self._last.get(stage, 0) < self.every:
                return
            self._last[stage] = now
        log.info(f"{self.prefix} {stage}: {cur:,} / {tot:,}")


def _records(df):
    """DataFrame -> list of plain dicts (numpy ints are not JSON serialisable)."""
    if df is None or df.empty:
        return []
    return json.loads(df.to_json(orient="records"))


def _num(value):
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0


def _dirs(output_root):
    dirs = {k: os.path.join(output_root, k) for k in ("recon", "tracer", "push")}
    for d in dirs.values():
        os.makedirs(d, exist_ok=True)
    return dirs


def make_context(tenant, start_date, end_date, flag_processed=False):
    """build_campaign_context with the default push identity."""
    return build_campaign_context(
        tenant=tenant,
        user_uuid=DEFAULT_USER_UUID,
        start_date=start_date, end_date=end_date,
        flag_processed=flag_processed)


def analyze(cfg, output_root=DEFAULT_OUTPUT, run_chain=True, ts=None, log_fn=None):
    """Reconciliation (DB vs ES) plus error-tracer chain recovery.

    Raises if every entity failed, rather than report zero gaps. A chain failure
    does not raise; it is recorded in `stages`.
    """
    dirs = _dirs(output_root)
    ts = ts or datetime.now().strftime("%Y%m%d_%H%M%S")
    stages = {}

    log_fn = log_fn or log.info
    check = processor.run_check(cfg, dirs["recon"], log=log_fn,
                                progress=_ProgressLog("[RECON]"), ts=ts)
    errored = [r["Entity"] for r in _records(check["summary"]) if r.get("Error")]
    n_entities = len(check["summary"])
    if n_entities and len(errored) == n_entities:
        raise RuntimeError(f"reconciliation failed for every entity ({', '.join(errored)}) "
                           f"- check DB/ES connectivity; see the log for each error")
    stages["reconciliation"] = (f"degraded: entity error(s): {', '.join(errored)}"
                                if errored else "ok")

    chain = None
    if run_chain:
        try:
            chain = processor.run_chain(cfg, dirs["tracer"], log=log_fn,
                                        progress=_ProgressLog("[CHAIN]"), ts=ts)
            stages["chain"] = "ok"
        except Exception as exc:
            log.exception("[CHAIN] failed - reconciliation results are still valid")
            stages["chain"] = f"failed: {type(exc).__name__}: {exc}"[:500]
    else:
        stages["chain"] = "skipped"

    recon_rows = _records(check["summary"])
    chain_rows = _records(chain["summary"]) if chain else []
    totals = {
        "ghosts": sum(_num(r.get("Ghost (ES not in DB)")) for r in recon_rows),
        "index_failed": sum(_num(r.get("Index-failed (DB not in ES)")) for r in recon_rows),
        "chain_missing": sum(_num(r.get("Missing (ES)")) for r in chain_rows),
        "chain_recoverable": sum(_num(r.get("In Tracer (new)")) for r in chain_rows),
        "chain_unrecoverable": sum(_num(r.get("Not in Tracer")) for r in chain_rows),
    }
    log_fn(f"[RUN] totals: {totals}")
    return {
        "ok": True,
        "tenant": cfg["tenant"],
        "start_date": cfg["start_date"],
        "end_date": cfg["end_date"],
        "ts": ts,
        "stages": stages,
        "recon_dir": check["run_dir"],
        "recon_summary_path": check["summary_path"],
        "chain_dir": chain["run_dir"] if chain else None,
        "chain_excel_path": chain["excel_path"] if chain else None,
        "reconciliation": recon_rows,
        "chain": chain_rows,
        "totals": totals,
    }


def push(cfg, recon_dir, chain_dir=None, output_root=DEFAULT_OUTPUT, opts=None,
         keep_records=False, record_sink=None, should_stop=None, log_fn=None):
    """Push a reviewed analysis back to the system (see processor.run_push).

    keep_records=True adds one dict per record acted on; off by default because
    it can be too large for XCom. "aborted" reports an early stop."""
    if not recon_dir or not os.path.isdir(recon_dir):
        raise ValueError(f"recon_dir {recon_dir!r} is not a directory - run analyze first")
    if chain_dir and not os.path.isdir(chain_dir):
        raise ValueError(f"chain_dir {chain_dir!r} is not a directory")
    dirs = _dirs(output_root)
    result = processor.run_push(cfg, recon_dir, chain_dir, dirs["push"], opts=opts,
                                log=log_fn or log.info, progress=_ProgressLog("[PUSH]"),
                                record_sink=record_sink, should_stop=should_stop)
    return {
        "ok": True,
        "tenant": cfg["tenant"],
        "ts": result["ts"],
        "push_dir": result["run_dir"],
        "excel_path": result["excel_path"],
        "failed_path": result["failed_path"],
        "pushed": result["pushed"],
        "updated": result["updated"],
        "skipped": result["skipped"],
        "failed": result["failed"],
        "no_audit": result.get("no_audit", 0),
        "aborted": result.get("aborted", False),
        "abort_reason": result.get("abort_reason", ""),
        "flag_ok": result.get("flag_ok"),
        "flag_note": result.get("flag_note", ""),
        "summary": _records(result["summary"]),
        **({"records": _records(result["results"])} if keep_records else {}),
    }


# CLI
def _parser():
    p = argparse.ArgumentParser(prog="python -m hcm_data_healer.core.runner",
                                description="HCM Data Healer, headless.")
    sub = p.add_subparsers(dest="command", required=True)

    def common(sp):
        sp.add_argument("--tenant", required=True)
        sp.add_argument("--start", required=True, help="YYYY-MM-DD")
        sp.add_argument("--end", required=True, help="YYYY-MM-DD")
        sp.add_argument("--output", default=DEFAULT_OUTPUT, help="output root folder")

    a = sub.add_parser("analyze", help="reconciliation + error-tracer recovery (read-only)")
    common(a)
    a.add_argument("--no-chain", action="store_true", help="skip the error-tracer recovery")

    w = sub.add_parser("push", help="WRITE a reviewed analysis back to the system")
    common(w)
    w.add_argument("--recon-dir", required=True)
    w.add_argument("--chain-dir")
    w.add_argument("--no-create", action="store_true", help="skip CREATE of ghosts")
    w.add_argument("--no-update", action="store_true", help="skip UPDATE of index-failed")
    w.add_argument("--no-chain-push", action="store_true", help="skip recovered chain payloads")
    w.add_argument("--no-flag", action="store_true",
                   help="do not mark recovered tracer docs isProcessed=true")
    w.add_argument("--yes", action="store_true",
                   help="required: confirms the analysis was reviewed")
    return p


def main(argv=None):
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    args = _parser().parse_args(argv)

    if args.command == "analyze":
        cfg = make_context(args.tenant, args.start, args.end)
        result = analyze(cfg, args.output, run_chain=not args.no_chain)
    else:
        if not args.yes:
            print("push writes to the system. Review the analysis, then re-run with --yes.",
                  file=sys.stderr)
            return 2
        cfg = make_context(args.tenant, args.start, args.end,
                           flag_processed=not args.no_flag)
        opts = {"do_create_ghosts": not args.no_create,
                "do_update_missing": not args.no_update,
                "do_push_chain": not args.no_chain_push}
        result = push(cfg, args.recon_dir, args.chain_dir, args.output, opts=opts)

    print(json.dumps(result, indent=2, default=str))
    return 0


if __name__ == "__main__":
    sys.exit(main())
