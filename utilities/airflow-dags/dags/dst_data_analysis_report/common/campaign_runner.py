"""Executes the whole campaign pipeline for one sheet row, inside one task.

Everything runs in a single process/pod on purpose: intermediate files
(Excels, chart, docx, checkpoints) stay on local disk, so no shared storage
is needed between Airflow tasks. Per-stage visibility comes from the stage-
tagged pipeline logs and from the checkpoints, which are pushed to the
campaign's Drive temp/ folder even when the run fails.

Error classification (retry only what a retry can fix):
  - KeyError/ValueError/TypeError/AttributeError -> AirflowFailException:
    malformed data or config fails identically on retry, so fail immediately.
  - Anything else (network, ES, Drive, Slack) -> normal raise, Airflow retries.
  - cdd_sync failures never kill the run — the report proceeds without sync
    data, and the marker records the degradation.
"""
import json
import logging
import os
from datetime import timedelta

log = logging.getLogger(__name__)

try:
    from airflow.sdk.exceptions import AirflowFailException
except ImportError:
    try:
        from airflow.exceptions import AirflowFailException
    except ImportError:  # unit tests without Airflow installed
        class AirflowFailException(Exception):
            pass

# FileNotFoundError is included because a missing target book (drive.py's
# download_target_book) is a configuration error: three identical attempts
# three minutes apart cannot conjure the file, they just delay the alert.
# IndexError belongs here for the same reason as the rest: a malformed target
# book or a short tuple unpack fails identically on every attempt.
DATA_ERRORS = (KeyError, ValueError, TypeError, AttributeError,
               FileNotFoundError, IndexError)

def _is_permanent_http(exc):
    """True for HTTP statuses that a retry cannot possibly fix.

    A 404 index_not_found means the tenant prefix or index name is wrong; 401/403
    mean the credentials are wrong. Retrying twice at three minutes just delays
    the alert by six minutes — observed live 2026-08-21, when a tenant with no ES
    indices burned three attempts on
    "404 Client Error: Not Found for url: .../ba-project-task-index-v1/_search".
    Everything else (5xx, timeouts, connection resets) stays retryable.
    """
    response = getattr(exc, "response", None)
    status = getattr(response, "status_code", None)
    return status in (400, 401, 403, 404)


# Checked BEFORE DATA_ERRORS. A JSON decode failure is a ValueError subclass,
# so an ES/Drive 502 served as an HTML error page — the textbook transient —
# was being classified as permanent and failing without a retry.
_TRANSIENT_FIRST = [json.JSONDecodeError]
try:
    from requests.exceptions import JSONDecodeError as _RequestsJSONDecodeError
    _TRANSIENT_FIRST.append(_RequestsJSONDecodeError)
except ImportError:
    pass
TRANSIENT_ERRORS = tuple(_TRANSIENT_FIRST)
ITN_DRUG_TYPES = {"ITN", "LLIN"}
CUMULATIVE_MODE = "cumulative"


def apply_cumulative(cfg):
    """Turn a daily cfg into a whole-campaign one (ported from run.py's
    run_cumulative, which was the only caller of the pipeline's ~89
    cfg["cumulative"] branches).

    Covers campaign_start through the mop-up end date inclusive, and measures
    coverage against the FULL campaign target: analyze._load_targets uses
    divisor 1 when cumulative is set, instead of dividing by campaign_days.
    """
    import os
    from dst_data_analysis_report.pipeline.config import _date_label

    start = cfg["campaign_start"]
    end = cfg.get("mopup_end_date") or cfg["campaign_end"]
    if end < start:
        raise ValueError(f"mop-up end {end} is before campaign_start {start}")

    total_days = (end - start).days + 1
    cfg.update({
        "cumulative":         True,
        "DAY":                total_days,
        # labels read "Days 1-N"; the target maths is decoupled via the flag
        "campaign_days":      total_days,
        "GTE":                f"{start.isoformat()}T00:00:00.000Z",
        "LTE":                f"{end.isoformat()}T23:59:59.999Z",
        "CAMPAIGN_DATES":     [(start + timedelta(days=i)).isoformat()
                               for i in range(total_days)],
        "END_LABEL":          _date_label(end),
        # without this the cumulative Word/Slack output carries TODAY's date
        # label instead of the campaign's end date
        "DATE_LABEL":         _date_label(end),
        # the mop-up date is past campaign_end by design, so the daily
        # in-window guard must not apply to this run
        "in_campaign_window": True,
    })

    out_dir = cfg["out_dir"]
    slug = str(cfg["state_name"]).replace(" ", "_")
    cfg["perf_xlsx"] = os.path.join(out_dir, "performance_cumulative.xlsx")
    cfg["sync_xlsx"] = os.path.join(out_dir, "cdd_sync_cumulative.xlsx")
    cfg["docx_path"] = os.path.join(
        out_dir, f"{slug}_Cumulative_Campaign_Report.docx")
    cfg["partner_docx_path"] = os.path.join(
        out_dir, f"{slug}_Cumulative_PartnerReport.docx")
    return cfg


def select_pipeline_modules(drug_type):
    """ITN/LLIN campaigns use the household-grain module set; everything else
    (SPAQ, AZM) uses the per-child originals."""
    if drug_type in ITN_DRUG_TYPES:
        from dst_data_analysis_report.pipeline import analyze_itn, cdd_sync_itn, report_itn
        return analyze_itn, cdd_sync_itn, report_itn
    from dst_data_analysis_report.pipeline import analyze, cdd_sync, report
    return analyze, cdd_sync, report


def _run_stage(stage_name, fn, marker):
    """Run one stage with the error classification the DAG relies on.

    marker may be None for stages that run BEFORE it can be built - config
    parsing needs cfg["tenant"] to exist, and cfg is what config parsing
    produces. The classification is the point; the bookkeeping is optional.
    """
    def _mark(outcome):
        if marker is not None:
            marker["stages"][stage_name] = outcome

    try:
        result = fn()
        _mark("ok")
        return result
    except TRANSIENT_ERRORS as e:
        _mark(f"failed: {e}")
        raise
    except DATA_ERRORS as e:
        _mark(f"failed: {e}")
        raise AirflowFailException(
            f"{stage_name} failed on malformed data/config — retry would fail "
            f"identically: {type(e).__name__}: {e}") from e
    except Exception as e:
        _mark(f"failed: {e}")
        # Ordering matters: DATA_ERRORS above must win first. Only what is left
        # over gets the HTTP-status test, so a 404/401/403 fails fast while every
        # other network fault stays retryable.
        if _is_permanent_http(e):
            raise AirflowFailException(
                f"{stage_name} failed with an HTTP status a retry cannot fix "
                f"(wrong index name, tenant prefix, or credentials): {e}") from e
        raise




def _degrade(marker, stage, reason):
    """Record a degradation reason without discarding an earlier one.

    Both the failed-narrative and the failed-upload paths assigned
    marker["stages"]["report"] directly, so whichever ran second erased the
    other. Naming only one of two real problems is worse than naming none: the
    REPORT INCOMPLETE alert told the reader to check Drive links and never
    mentioned that the body sent to partners was a placeholder.
    """
    existing = marker["stages"].get(stage, "")
    if str(existing).startswith("degraded"):
        marker["stages"][stage] = f"{existing}  ALSO: {reason}"
    else:
        marker["stages"][stage] = f"degraded: {reason}"


def _name_registry_modules(analyze_mod):
    """Every module whose NAME_BATCH_FAILURES this run can write to.

    Always includes dst_data_analysis_report.pipeline.analyze, because the ITN path calls ITS name helpers.
    """
    from dst_data_analysis_report.pipeline import analyze as base_analyze
    mods = [base_analyze]
    if (analyze_mod is not base_analyze
            and hasattr(analyze_mod, "NAME_BATCH_FAILURES")):
        mods.append(analyze_mod)
    return mods

def execute_campaign(row, mode="both"):
    """Run analyze -> cdd_sync -> report -> notify for one campaign row.

    Returns a marker dict: {"ok": True, ...} for a real report,
    {"ok": None, "reason": ...} for a routine no-op (inactive / out of window).
    Raises on genuine failure. Checkpoints are uploaded to Drive in all cases.
    """
    from dst_data_analysis_report.pipeline import config, notify

    # Cleared BEFORE build, because build is what populates it. Clearing later
    # (alongside the other registries) silently discarded every correction.
    config.CONFIG_CORRECTIONS.clear()
    # Inside _run_stage so a bad sheet cell is classified as a DATA error and
    # fails IMMEDIATELY. config._validate_row's docstring already promised this
    # ("the task fails immediately with no retries"), but build() was called
    # outside the wrapper, so a ValueError propagated as an ordinary failure and
    # got retries=2 with a 3-minute delay - the sheet-fix alert arrived six
    # minutes and three identical, ES-free failures later.
    cfg = _run_stage("config", lambda: config.build(row), None)
    state = cfg["state_name"]
    is_cumulative = mode == CUMULATIVE_MODE

    if not cfg["active"]:
        return {"ok": None, "reason": "row inactive"}
    if is_cumulative:
        # Classified for the same reason as config.build: apply_cumulative's
        # "mop-up end before campaign_start" check raises ValueError, which is a
        # sheet error - retrying it twice only delays the alert.
        _run_stage("cumulative_window", lambda: apply_cumulative(cfg), None)
    elif not cfg["in_campaign_window"]:
        return {"ok": None,
                "reason": f"outside campaign window "
                          f"({cfg['campaign_start']} to {cfg['campaign_end']})"}

    analyze_mod, cdd_sync_mod, report_mod = select_pipeline_modules(cfg["drug_type"])

    # Reset before the run: these lists are how the pipeline reports partial
    # failures it deliberately survives — artifacts that never reached Drive
    # (notify.FAILED_UPLOADS) and name-resolution batches that failed
    # (analyze*.NAME_BATCH_FAILURES). Both would otherwise pass silently.
    notify.FAILED_UPLOADS.clear()
    notify.FAILED_POSTS.clear()
    from dst_data_analysis_report.pipeline import analyze as _base_analyze
    _base_analyze.RUN_DEGRADATIONS.clear()
    try:
        report_mod.TRAJECTORY_FAILURES.clear()
    except AttributeError:
        pass          # report_itn has no ES trajectory back-fill
    # The registry lives in dst_data_analysis_report.pipeline.analyze, and analyze_itn IMPORTS the two name
    # helpers from there (analyze_itn.py:90) rather than owning copies — so an ITN
    # run records its failures into dst_data_analysis_report.pipeline.analyze's list, not analyze_itn's.
    # Reading only analyze_mod would make this check dead for every ITN campaign.
    for _mod in _name_registry_modules(analyze_mod):
        _mod.NAME_BATCH_FAILURES.clear()
    marker = {"ok": True, "tenant": cfg["tenant"], "state": state,
              "mode": mode, "day": cfg["DAY"], "stages": {},
              "drive_link": "", "drive_folder_url": ""}

    if is_cumulative:
        log.info(f"[runner] {state} CUMULATIVE Days 1-{cfg['DAY']} "
                 f"({cfg['campaign_start']} to {cfg.get('mopup_end_date') or cfg['campaign_end']})")
    else:
        log.info(f"[runner] {state} Day {cfg['DAY']}/{cfg['campaign_days']} mode={mode}")
    try:
        _run_stage("analyze", lambda: analyze_mod.run(cfg), marker)

        # A failed name batch is not a crash — the report still builds — but the
        # missing-name DQ columns then overstate a data problem that is really a
        # partial fetch. Say so, rather than reporting a clean run.
        lost = [f for _mod in _name_registry_modules(analyze_mod)
                for f in _mod.NAME_BATCH_FAILURES]
        if lost:
            _degrade(marker, "analyze",
                     f"{len(lost)} name-resolution batch(es) FAILED, so the "
                     f"missing-name data-quality columns overstate the real gap "
                     f"({'; '.join(lost[:2])})")
            log.error(f"[runner] {len(lost)} name batch(es) failed — DQ columns "
                      f"are not trustworthy for this run")

        # Conditions the analyze stage survives but that make its numbers
        # meaningless: zero task documents matched, or all targets are zero.
        # Both used to publish a complete, green, plausible report.
        for reason in _base_analyze.RUN_DEGRADATIONS:
            _degrade(marker, "analyze", reason)
        for reason in config.CONFIG_CORRECTIONS:
            _degrade(marker, "config", reason)

        try:
            produced = cdd_sync_mod.run(cfg)
            # run() returns the workbook path on success and None on the no-op,
            # so the return alone is the signal. Do NOT fall back to "the file
            # exists" — a stale workbook from an earlier run would mask a
            # cdd_sync that did nothing this time.
            if produced is None:
                # cdd_sync returns None without raising when campaign_number /
                # project_type_id is blank or zero CDDs match. Marking that "ok"
                # made the audit row assert a healthy run while the report said
                # "CDDs synced: 0 of 0" as though it were measured.
                _degrade(marker, "cdd_sync", (
                    "CDD sync numbers are MISSING from the report — no "
                    "CDD or sync records matched this campaign. Check "
                    "campaign_number / project_type_id in the sheet, that field "
                    "staff are registered for this campaign, and that CDD_ROLE "
                    "matches this tenant's role name"))
                log.error("[runner] cdd_sync produced no workbook — sync numbers "
                          "will be absent from the report")
            else:
                marker["stages"]["cdd_sync"] = "ok"
        except Exception as e:
            _degrade(marker, "cdd_sync",
                     f"CDD sync numbers are MISSING from the report — the sync "
                     f"step errored: {type(e).__name__}: {e}")
            log.error(f"[runner] cdd_sync failed (non-fatal — report continues "
                      f"without sync data): {e}", exc_info=True)

        docx_path, partner_docx_path, slack_text = _run_stage(
            "report", lambda: report_mod.run(cfg), marker)

        # The LLM is non-fatal by design, but "non-fatal" was also SILENT: the
        # placeholder string is posted to partners as the report body while the
        # run reports success. Surface it so the outcome is recorded and alerted.
        if "[Narrative not generated" in str(slack_text):
            _degrade(marker, "report", (
                "the report body has NO written summary — it carries the "
                "placeholder '[Narrative not generated]' because Groq failed after "
                "3 attempts. Check GROQ_API_KEY and GROQ_MODEL (a decommissioned "
                "model returns 404) before sharing this with partners"))
            log.error("[runner] narrative generation failed — the report body "
                      "carries a placeholder")

        if notify.FAILED_UPLOADS:
            lost = ", ".join(sorted(set(notify.FAILED_UPLOADS)))
            _degrade(marker, "report",
                     f"these files are NOT on Google Drive after 3 upload "
                     f"attempts, so links to them will not work: {lost}")
            log.error(f"[runner] these artifacts never reached Drive: {lost}")

        missed_days = list(getattr(report_mod, "TRAJECTORY_FAILURES", []))
        if missed_days:
            _degrade(marker, "report",
                     f"{len(missed_days)} campaign day(s) could not be read from "
                     f"Elasticsearch and were counted as ZERO, so the cumulative "
                     f"total in the report and the Slack post is UNDERSTATED "
                     f"({'; '.join(missed_days[:3])})")

        drive_link = _run_stage(
            "notify",
            lambda: notify.run(cfg, docx_path, slack_text,
                               partner_docx_path=partner_docx_path,
                               mode="both" if is_cumulative else mode),
            marker)
        marker["drive_link"] = drive_link or ""

        # The Slack post IS the deliverable. A lost post was previously the
        # quietest failure in the system: report on Drive, run SUCCESS, empty
        # Error column, and nobody received anything.
        if notify.FAILED_POSTS:
            undelivered = ", ".join(sorted(set(notify.FAILED_POSTS)))
            _degrade(marker, "notify",
                     f"the report was NOT DELIVERED to: {undelivered}. The "
                     f"document is on Drive but no one was notified — send the "
                     f"link manually, then fix the channel id or the bot's "
                     f"membership of that channel")
            log.error(f"[runner] report not delivered to: {undelivered}")
    finally:
        # The campaign folder holds every artifact this run published, so it is
        # what the audit row points at. cfg caches the id, so this is free.
        try:
            fid = notify.campaign_folder_id(cfg)
            if fid:
                marker["drive_folder_url"] = f"https://drive.google.com/drive/folders/{fid}"
        except Exception as e:
            log.warning(f"[runner] could not resolve Drive folder url: {e}")
        # Even a failed run leaves its checkpoints on Drive for offline debugging
        # (the pod's local disk disappears with the pod).
        try:
            notify.upload_checkpoints(cfg)
        except Exception as e:
            log.warning(f"[runner] checkpoint upload failed (non-fatal): {e}")

    log.info(f"[runner] {state} complete: {marker['stages']}")
    return marker
