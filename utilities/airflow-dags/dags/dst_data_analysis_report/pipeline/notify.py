"""
notify.py — Google Drive upload + Google Sheets write + Slack post

Authentication: single service account (credential.json) for both Drive and Sheets.
No OAuth token required. Grant the service account Editor access to:
  - The campaign config Google Sheet
  - The Drive folder where reports are uploaded
"""
import logging
import os
import time

import requests
from google.oauth2.service_account import Credentials
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

log = logging.getLogger(__name__)

_SCOPES = [
    "https://www.googleapis.com/auth/spreadsheets",
    "https://www.googleapis.com/auth/drive",
]


def _creds():
    from dst_data_analysis_report.pipeline.config import _resolve_creds_path
    return Credentials.from_service_account_file(_resolve_creds_path(), scopes=_SCOPES)


def _drive_creds():
    """Drive credentials — same service account, Shared Drive bypasses quota."""
    return _creds()


# ── Drive folder organisation ────────────────────────────────────────────────
# Reports are filed under the root GOOGLE_DRIVE_FOLDER_ID in a per-campaign tree,
# all derived from existing config (no extra sheet column / env var):
#     <Instance>/<State>/<Campaign>/<Day N | Cumulative (Days 1-N)>
#   Instance = GOOGLE_SHEET_TAB (the per-deployment tab; naming the tab names the folder)
#   State    = state_name column
#   Campaign = campaign_name column (falls back to drug_type)

def _find_or_create_folder(service, name, parent_id):
    """Return the id of sub-folder `name` under `parent_id`, creating it if absent."""
    safe = str(name).strip().replace("/", "-") or "Unnamed"
    esc  = safe.replace("\\", "\\\\").replace("'", "\\'")
    q = (f"name = '{esc}' and mimeType = 'application/vnd.google-apps.folder' "
         f"and '{parent_id}' in parents and trashed = false")
    resp = service.files().list(
        q=q, spaces="drive", fields="files(id,name)",
        supportsAllDrives=True, includeItemsFromAllDrives=True,
    ).execute()
    hits = resp.get("files", [])
    if hits:
        return hits[0]["id"]
    meta = {"name": safe, "mimeType": "application/vnd.google-apps.folder",
            "parents": [parent_id]}
    folder = service.files().create(body=meta, fields="id", supportsAllDrives=True).execute()
    log.info(f"[notify] Drive folder created: {safe}")
    return folder["id"]


def campaign_folder_id(cfg):
    """
    Resolve (creating on demand) the Drive folder this run's files belong in, and
    cache it on cfg. Returns "" if no root folder is configured — callers then fall
    back to the old flat upload. Path: <Instance>/<State>/<Campaign>/<leaf>.
    """
    if cfg.get("_drive_folder_id"):
        return cfg["_drive_folder_id"]
    root = os.getenv("GOOGLE_DRIVE_FOLDER_ID", "").strip()
    if not root:
        return ""
    try:
        service  = build("drive", "v3", credentials=_drive_creds())
        tab      = (os.getenv("GOOGLE_SHEET_TAB", "Sheet1") or "Sheet1").strip()
        instance = tab.title() if tab.islower() else tab
        state    = cfg.get("state_name") or cfg.get("tenant") or "Unknown"
        campaign = cfg.get("campaign_name") or cfg.get("drug_type") or "Campaign"
        leaf = (f"Cumulative (Days 1-{cfg.get('DAY', '')})" if cfg.get("cumulative")
                else f"Day {cfg.get('DAY', '')}")
        fid = root
        for part in (instance, state, campaign, leaf):
            fid = _find_or_create_folder(service, part, fid)
        cfg["_drive_folder_id"] = fid
        log.info(f"[notify] Drive target: {instance}/{state}/{campaign}/{leaf}")
        return fid
    except Exception as e:
        log.warning(f"[notify] could not resolve campaign Drive folder (using root): {e}")
        return ""


# ── Google Drive ───────────────────────────────────────────────────────────────

def _upload_to_drive(file_path, title, folder_id=None):
    """Upload file to Drive (converts docx→Google Doc, xlsx→Google Sheet). Returns shareable URL."""
    service   = build("drive", "v3", credentials=_drive_creds())
    folder_id = folder_id or os.getenv("GOOGLE_DRIVE_FOLDER_ID", "")

    ext = os.path.splitext(file_path)[1].lower()
    if ext == ".xlsx":
        upload_mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        target_mime = "application/vnd.google-apps.spreadsheet"
        url_tmpl    = "https://docs.google.com/spreadsheets/d/{}/edit"
    else:
        upload_mime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        target_mime = "application/vnd.google-apps.document"
        url_tmpl    = "https://docs.google.com/document/d/{}/edit"

    metadata = {"name": title, "mimeType": target_mime}
    if folder_id:
        metadata["parents"] = [folder_id]

    file = service.files().create(
        body=metadata,
        media_body=MediaFileUpload(file_path, mimetype=upload_mime, resumable=True),
        fields="id,name",
        supportsAllDrives=True,
    ).execute()

    file_id = file["id"]

    # Make readable by anyone with the link
    service.permissions().create(
        fileId=file_id,
        body={"type": "anyone", "role": "reader"},
        supportsAllDrives=True,
    ).execute()

    url = url_tmpl.format(file_id)
    log.info(f"[notify] Drive upload done: {file['name']} -> {url}")
    return url


# ── Slack ──────────────────────────────────────────────────────────────────────

# Channels this run failed to post to. The Slack post IS the deliverable, so a
# lost post used to be the quietest possible failure: report on Drive, Run Log
# SUCCESS, empty Error column, nobody told. campaign_runner clears this before a
# run and reads it after, exactly as it does FAILED_UPLOADS.
FAILED_POSTS = []

_POST_ATTEMPTS = 3

# Slack errors that a retry cannot fix - the message will never be accepted, so
# retrying only delays the alert.
_PERMANENT_SLACK = {"channel_not_found", "not_in_channel", "invalid_auth",
                    "account_inactive", "token_revoked", "is_archived",
                    "msg_too_long", "no_text"}


class PermanentSlackError(RuntimeError):
    """Slack will never accept this message - retrying only delays the alert."""


def _slack_post(channel, text, token):
    """Post to Slack, retrying transient failures.

    Rate limiting is expected here, not exceptional: at 17:00 every campaign in
    the fleet posts within the same minute, so a 429 is normal load.

    Raises on final failure. The Slack post IS the deliverable, so the caller
    must not treat a lost post as a successful run - see FAILED_POSTS.
    """
    last = None
    for attempt in range(1, _POST_ATTEMPTS + 1):
        try:
            r = requests.post(
                "https://slack.com/api/chat.postMessage",
                headers={"Authorization": f"Bearer {token}"},
                json={"channel": channel, "text": text},
                timeout=30,
            )
            if r.status_code == 429:
                raise RuntimeError("rate limited by Slack (429), Retry-After="
                                   f"{r.headers.get('Retry-After', '?')}s")
            r.raise_for_status()
            resp = r.json()
            if resp.get("ok"):
                return resp
            err = resp.get("error")
            if err in _PERMANENT_SLACK:
                raise PermanentSlackError(
                    f"Slack rejected the post to {channel}: {err}. A retry "
                    f"cannot help - fix the channel id, the bot's membership, "
                    f"or SLACK_TOKEN.")
            raise RuntimeError(f"Slack postMessage failed: {err}")
        except PermanentSlackError as e:
            log.error(f"[notify] {e} The report was NOT delivered to {channel}.")
            FAILED_POSTS.append(f"{channel} (rejected by Slack)")
            raise
        except Exception as e:                                    # noqa: BLE001
            last = e
            if attempt < _POST_ATTEMPTS:
                log.warning(f"[notify] Slack post to {channel} failed (attempt "
                            f"{attempt}/{_POST_ATTEMPTS}), retrying: {e}")
                time.sleep(2 * attempt)
    log.error(f"[notify] Slack post to {channel} FAILED after {_POST_ATTEMPTS} "
              f"attempts - the report was NOT delivered to this channel: {last}",
              exc_info=True)
    FAILED_POSTS.append(str(channel))
    raise RuntimeError(f"Slack post to {channel} failed after "
                       f"{_POST_ATTEMPTS} attempts: {last}")


# ── shared helper (called by report.py for raw Excel uploads) ──────────────────

# Google's resumable-upload endpoint returns a bare 500 "Internal Error"
# unpredictably, and in practice it hits the FIRST upload of a run. Because the
# performance Excel is always uploaded first, it was the file that never arrived:
# every run on 2026-08-21 logged
#   upload_file failed (non-fatal): <HttpError 500 ... uploadType=resumable>
# for performance_dayN.xlsx while the CDD workbook, the Word report and the chart
# all uploaded fine seconds later. The run reported SUCCESS with the pipeline's
# primary artifact missing from Drive.
_UPLOAD_ATTEMPTS = 3

# Titles of artifacts that did NOT reach Drive this run. campaign_runner clears
# this before a run and reads it after, so a silently missing deliverable becomes
# a "degraded" outcome that alerts, instead of a green run with nothing to open.
FAILED_UPLOADS = []


def upload_file(path, title, folder_id=None):
    """Upload any file to Drive. Returns the shareable link, or "" on failure.

    Retries: Drive 5xx on resumable upload is transient and usually succeeds on
    the next attempt. Still non-fatal after all attempts — a report that reached
    Slack is better than no report — but the caller can now tell the difference,
    and report.py marks the run degraded when a PRIMARY artifact is lost.
    """
    last = None
    for attempt in range(1, _UPLOAD_ATTEMPTS + 1):
        try:
            return _upload_to_drive(path, title, folder_id=folder_id)
        except Exception as e:                                    # noqa: BLE001
            last = e
            status = getattr(getattr(e, "resp", None), "status", None)
            transient = status is None or int(status) >= 500 or int(status) == 429
            if not transient:
                log.error(f"[notify] upload of {title!r} rejected by Drive "
                          f"(status {status}, a retry cannot help): {e}", exc_info=True)
                return ""
            if attempt < _UPLOAD_ATTEMPTS:
                log.warning(f"[notify] upload of {title!r} failed (attempt "
                            f"{attempt}/{_UPLOAD_ATTEMPTS}), retrying: {e}")
                time.sleep(2 * attempt)
    log.error(f"[notify] upload of {title!r} FAILED after {_UPLOAD_ATTEMPTS} "
              f"attempts — this artifact is NOT on Drive: {last}")
    FAILED_UPLOADS.append(str(title))
    return ""


# ── campaign temp files (checkpoints) ──────────────────────────────────────────

def temp_folder_id(cfg):
    """Resolve (creating on demand) <Instance>/<State>/<Campaign>/<Day N>/temp."""
    fid = campaign_folder_id(cfg)
    if not fid:
        return ""
    from dst_data_analysis_report.pipeline.core import drive
    return drive.find_or_create_folder("temp", fid)


def upload_chart(cfg):
    """Publish the run's progress chart to the campaign's Drive folder.

    The PNG is embedded in the Word report but was otherwise the one artifact
    that never left the machine; with Drive as the output store and a
    disposable scratch dir, it would simply be lost. Raw upload (no Google
    conversion), non-fatal.
    """
    from dst_data_analysis_report.pipeline.core import drive
    suffix = ("cumulative" if cfg.get("cumulative") else f"day{cfg.get('DAY','')}")
    # SPAQ/AZM and ITN name the file differently
    candidates = [f"progress_chart_{suffix}.png", f"itn_progress_chart_{suffix}.png"]
    try:
        folder = campaign_folder_id(cfg)
        if not folder:
            return ""
        for name in candidates:
            path = os.path.join(cfg["out_dir"], name)
            if os.path.exists(path):
                drive.upload_raw(path, name, folder)
                log.info(f"[notify] chart published to Drive: {name}")
                return name
    except Exception as e:
        log.warning(f"[notify] chart upload failed (non-fatal): {e}")
    return ""


def upload_checkpoints(cfg, stages=("analyze", "cdd_sync")):
    """Push this run's checkpoint JSONs to the campaign's Drive temp folder.

    Raw upload (no conversion, no public link — checkpoints carry beneficiary
    names), overwriting any previous copy of the same run. Non-fatal throughout.
    """
    from dst_data_analysis_report.pipeline.core import drive
    from dst_data_analysis_report.pipeline.core.checkpoint import checkpoint_path
    uploaded = {}
    try:
        folder = temp_folder_id(cfg)
        if not folder:
            log.info("[notify] no Drive root configured — skipping checkpoint upload")
            return uploaded
        for stage in stages:
            path = checkpoint_path(cfg, stage)
            if os.path.exists(path):
                uploaded[stage] = drive.upload_raw(path, os.path.basename(path), folder)
    except Exception as e:
        log.warning(f"[notify] checkpoint upload failed (non-fatal): {e}")
    return uploaded


def download_checkpoint(cfg, stage):
    """Fetch a stage checkpoint from the campaign's Drive temp folder into the
    local checkpoints directory, enabling rerun_from_checkpoint on any machine.
    Returns the local path, or None when the checkpoint is not on Drive."""
    from dst_data_analysis_report.pipeline.core import drive
    from dst_data_analysis_report.pipeline.core.checkpoint import checkpoint_path
    folder = temp_folder_id(cfg)
    if not folder:
        return None
    path = checkpoint_path(cfg, stage)
    return drive.download_raw(os.path.basename(path), folder, path)


# ── public entry point ─────────────────────────────────────────────────────────

def run(cfg, docx_path, slack_text, partner_docx_path=None, mode="both"):
    """
    mode:
      "both"     — post internal (main channel) and partner report (default)
      "internal" — post only the internal report to the main channel
      "partner"  — post only the partner report to the partner channel
    """
    token   = os.getenv("SLACK_TOKEN")
    channel = cfg.get("slack_channel", "")

    do_internal = mode in ("both", "internal")
    do_partner  = mode in ("both", "partner")

    # Per-campaign Drive folder (auto-created); "" falls back to the flat root folder
    fid = campaign_folder_id(cfg)

    # Upload main report to Drive (only when posting internally)
    drive_link = None
    if do_internal and docx_path and os.path.exists(docx_path):
        try:
            from datetime import datetime as _dt
            title      = f"{cfg['state_name']} Day {cfg['DAY']} Report — {cfg['DATE_LABEL']} {_dt.now().strftime('%H:%M')}"
            # upload_file (not _upload_to_drive): retries, and records a loss in
            # FAILED_UPLOADS so the run is marked degraded and alerts. The Word
            # report is THE deliverable — losing it silently is the worst case.
            drive_link = upload_file(docx_path, title, folder_id=fid)
            if not drive_link:
                log.error("[notify] the Word REPORT is not on Drive — the Slack "
                          "post will go out without a working link")
        except Exception as e:
            log.warning(f"[notify] Drive upload failed (non-fatal): {e}")

    # Post to Slack. A missing token skips only the POSTS — the Drive uploads
    # at the end of this function must still run, or a token misconfiguration
    # would silently cost us the chart and the checkpoints as well.
    if not token:
        log.warning("[notify] SLACK_TOKEN not set — skipping Slack posts, "
                    "Drive publication still runs")

    # Main channel — full report (only if configured; a failure must not block the partner post)
    if token and do_internal and channel:
        try:
            message = slack_text
            if drive_link:
                message = f"{slack_text}\n\nFull report: {drive_link}"
            _slack_post(channel, message, token)
            log.info(f"[notify] Slack post done -> {channel}")
        except Exception as e:
            log.error(f"[notify] Slack failed (non-fatal): {e}", exc_info=True)
    elif token and do_internal:
        log.error("[notify] slack_channel is not set on the sheet row, so the "
                  "internal report was NOT posted anywhere. The partner post "
                  "still runs.")
        if mode == "internal":
            FAILED_POSTS.append("internal (slack_channel not set)")

    # Partner channel — report without DQ sections (if configured)
    partner_channel = cfg.get("slack_channel_partners", "")
    if token and do_partner and partner_channel and partner_docx_path and os.path.exists(partner_docx_path):
        # Upload and post in separate try blocks so Slack post fires even if Drive fails
        partner_link = ""
        try:
            from datetime import datetime as _dt2
            partner_title = (f"{cfg['state_name']} Day {cfg['DAY']} Report — "
                             f"{cfg['DATE_LABEL']} {_dt2.now().strftime('%H:%M')}")
            partner_link = upload_file(partner_docx_path, partner_title,
                                       folder_id=fid)
            if not partner_link:
                log.error("[notify] the PARTNER report is not on Drive — the "
                          "partner post will go out without a working link")
        except Exception as e:
            log.warning(f"[notify] Partner Drive upload failed (non-fatal): {e}")

        try:
            partner_msg = slack_text
            if partner_link:
                partner_msg = f"{slack_text}\n\nFull report: {partner_link}"
            _slack_post(partner_channel, partner_msg, token)
            log.info(f"[notify] Partner Slack post done -> {partner_channel}")
        except Exception as e:
            log.warning(f"[notify] Partner channel post failed (non-fatal): {e}")

    elif token and do_partner:
        # Previously this branch did not exist: a partner slot whose report file
        # was never written, or whose channel cell is blank, silently did nothing
        # and the run was recorded SUCCESS.
        if not partner_channel:
            reason = ("slack_channel_partners is not set on the sheet row")
        elif not partner_docx_path:
            reason = ("the report stage produced no partner document "
                      "(partner_report_times may be set without a partner "
                      "report being generated)")
        else:
            reason = f"the partner document is missing from disk: {partner_docx_path}"
        log.error(f"[notify] the PARTNER report was NOT posted - {reason}")
        if mode == "partner":
            # This slot exists only to serve partners, so nothing was delivered.
            FAILED_POSTS.append(f"partner ({reason})")

    upload_chart(cfg)
    upload_checkpoints(cfg)

    return drive_link
