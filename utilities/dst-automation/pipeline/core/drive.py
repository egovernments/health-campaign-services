"""Google Drive helpers for machine artifacts (checkpoints, staged temp files).

Unlike the report uploads in notify.py, these transfers never convert files
(bytes round-trip exactly), never grant anyone-with-link access (checkpoints
carry beneficiary names), and overwrite by files.update because the service
account cannot delete. All calls target a Shared Drive (supportsAllDrives).
"""
import io
import logging
import mimetypes
import os

from google.oauth2.service_account import Credentials
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload, MediaIoBaseDownload

log = logging.getLogger(__name__)

_SCOPES = ["https://www.googleapis.com/auth/drive"]


def _service():
    from pipeline.config import _resolve_creds_path
    creds = Credentials.from_service_account_file(_resolve_creds_path(), scopes=_SCOPES)
    return build("drive", "v3", credentials=creds)


def _escape(name):
    return str(name).replace("\\", "\\\\").replace("'", "\\'")


def find_file(name, folder_id, service=None):
    """Return the file id of `name` inside `folder_id`, or None."""
    service = service or _service()
    q = (f"name = '{_escape(name)}' and '{folder_id}' in parents "
         f"and trashed = false")
    resp = service.files().list(
        q=q, spaces="drive", fields="files(id,name)",
        supportsAllDrives=True, includeItemsFromAllDrives=True,
    ).execute()
    hits = resp.get("files", [])
    return hits[0]["id"] if hits else None


def find_or_create_folder(name, parent_id, service=None):
    """Return the id of sub-folder `name` under `parent_id`, creating it if absent."""
    service = service or _service()
    q = (f"name = '{_escape(name)}' and mimeType = 'application/vnd.google-apps.folder' "
         f"and '{parent_id}' in parents and trashed = false")
    resp = service.files().list(
        q=q, spaces="drive", fields="files(id,name)",
        supportsAllDrives=True, includeItemsFromAllDrives=True,
    ).execute()
    hits = resp.get("files", [])
    if hits:
        return hits[0]["id"]
    meta = {"name": str(name), "mimeType": "application/vnd.google-apps.folder",
            "parents": [parent_id]}
    folder = service.files().create(body=meta, fields="id", supportsAllDrives=True).execute()
    log.info(f"[drive] folder created: {name}")
    return folder["id"]


def upload_raw(path, name, folder_id):
    """Upload `path` as `name` into `folder_id` without conversion.

    Overwrites an existing file of the same name in place. Returns the file id.
    """
    service = _service()
    mime = mimetypes.guess_type(path)[0] or "application/octet-stream"
    media = MediaFileUpload(path, mimetype=mime, resumable=True)

    existing = find_file(name, folder_id, service)
    if existing:
        service.files().update(
            fileId=existing, media_body=media, supportsAllDrives=True,
        ).execute()
        log.info(f"[drive] updated: {name}")
        return existing

    file = service.files().create(
        body={"name": name, "parents": [folder_id]},
        media_body=media, fields="id", supportsAllDrives=True,
    ).execute()
    log.info(f"[drive] uploaded: {name}")
    return file["id"]


def download_raw(name, folder_id, dest_path):
    """Download `name` from `folder_id` to `dest_path`. Returns dest_path, or None if absent."""
    service = _service()
    file_id = find_file(name, folder_id, service)
    if not file_id:
        log.info(f"[drive] not found: {name}")
        return None
    os.makedirs(os.path.dirname(dest_path) or ".", exist_ok=True)
    request = service.files().get_media(fileId=file_id, supportsAllDrives=True)
    buf = io.BytesIO()
    downloader = MediaIoBaseDownload(buf, request)
    done = False
    while not done:
        _, done = downloader.next_chunk()
    with open(dest_path, "wb") as f:
        f.write(buf.getvalue())
    log.info(f"[drive] downloaded: {name} -> {dest_path}")
    return dest_path


# ── Target books ─────────────────────────────────────────────────────────────
# Campaign target CSVs live in ONE Drive folder (DST_TARGET_FOLDER_ID) instead of
# on the runner's filesystem: a pod has no persistent disk, and the old relative
# `target/xx_target.csv` paths resolved only on the JupyterHub boxes — off them
# _load_targets fell through to "all targets = 0" and still published a report.
#
# WHICH file is named by the campaign config sheet: the `target_csv` column is
# read as a FILE NAME inside that folder, not as a path. Existing rows already
# work unchanged because only the basename is used —
# `target/ba_target.csv` -> looks up `ba_target.csv`.
#
# If the column is blank, fall back to a naming convention, most specific first,
# so one tenant can still hold books for two concurrent campaigns:
#     <tenant>_<campaign_number>_target.csv
#     <tenant>_<cycle_index>_target.csv
#     <tenant>_target.csv
# Every candidate is tried with and without the .csv suffix (Drive drops it when
# a CSV is uploaded as a Google Sheet).

TARGET_FOLDER_ENV = "DST_TARGET_FOLDER_ID"
_GOOGLE_SHEET_MIME = "application/vnd.google-apps.spreadsheet"


def target_book_candidates(cfg):
    """Candidate Drive file names for this campaign's target book, best first.

    The `target_csv` column names the file; only its basename is used, so the
    legacy `target/ba_target.csv` values resolve to `ba_target.csv` with no
    sheet edit. Convention names are appended as a fallback for blank cells.
    """
    names, seen = [], set()

    def _add(value):
        value = str(value or "").strip()
        if not value:
            return
        stem = value[:-4] if value.lower().endswith(".csv") else value
        for candidate in (f"{stem}.csv", stem):
            if candidate not in seen:
                seen.add(candidate)
                names.append(candidate)

    configured = str(cfg.get("target_csv", "")).strip()
    if configured and not configured.lower().startswith("http"):
        _add(os.path.basename(configured.replace("\\", "/")))

    tenant = str(cfg.get("tenant", "")).strip().lower()
    for key in ("campaign_number", "cycle_index"):
        value = str(cfg.get(key, "")).strip()
        if value:
            _add(f"{tenant}_{value}_target")
    _add(f"{tenant}_target")
    return names


def _find_with_mime(name, folder_id, service):
    q = (f"name = '{_escape(name)}' and '{folder_id}' in parents "
         f"and trashed = false")
    res = service.files().list(q=q, fields="files(id,name,mimeType)", pageSize=1,
                               supportsAllDrives=True,
                               includeItemsFromAllDrives=True).execute()
    files = res.get("files", [])
    return (files[0]["id"], files[0]["mimeType"]) if files else (None, None)


def download_target_book(cfg, dest_dir):
    """Fetch this campaign's target book from Drive to dest_dir. Returns the
    local path. Raises FileNotFoundError if the folder is configured but holds
    no matching file — a report built on zero targets must never be published."""
    folder_id = os.getenv(TARGET_FOLDER_ENV, "").strip()
    if not folder_id:
        return None

    service = _service()
    tried = target_book_candidates(cfg)
    for name in tried:
        file_id, mime = _find_with_mime(name, folder_id, service)
        if not file_id:
            continue
        os.makedirs(dest_dir, exist_ok=True)
        # keep the Drive name locally so a wrong-book mix-up is obvious in logs
        local_name = name if name.lower().endswith(".csv") else f"{name}.csv"
        dest = os.path.join(dest_dir, local_name)
        if mime == _GOOGLE_SHEET_MIME:
            # uploaded as a Google Sheet — get_media does not work on native types
            request = service.files().export_media(fileId=file_id, mimeType="text/csv")
        else:
            request = service.files().get_media(fileId=file_id, supportsAllDrives=True)
        buf = io.BytesIO()
        downloader = MediaIoBaseDownload(buf, request)
        done = False
        while not done:
            _, done = downloader.next_chunk()
        with open(dest, "wb") as f:
            f.write(buf.getvalue())
        log.info(f"[drive] target book '{name}' -> {dest}")
        return dest

    raise FileNotFoundError(
        f"No target book in Drive folder {folder_id} for tenant "
        f"{cfg.get('tenant')!r}. Tried: {', '.join(tried)}")


def resolve_target_book(cfg):
    """Local path to the campaign's target book.

    Drive (DST_TARGET_FOLDER_ID) wins when configured; otherwise falls back to
    the row's target_csv, which may be a local path or a Google Sheets URL, so
    existing local and JupyterHub runs keep working unchanged.
    """
    configured = str(cfg.get("target_csv", "")).strip()
    if os.getenv(TARGET_FOLDER_ENV, "").strip():
        # A Google Sheets URL is read directly by analyze.py — leave it alone.
        if configured.startswith("https://docs.google.com/spreadsheets/"):
            return configured
        dest_dir = os.path.join(cfg.get("out_dir", "."), "targets")
        return download_target_book(cfg, dest_dir)
    return configured
