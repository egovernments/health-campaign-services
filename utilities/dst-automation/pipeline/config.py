"""
config.py — Google Sheet reader + config builder
Reads one row per active campaign and returns a fully resolved config dict.
"""
import os
import logging
import tempfile
from datetime import date, timedelta, datetime

import gspread
from google.oauth2.service_account import Credentials
from dotenv import load_dotenv

log = logging.getLogger(__name__)

_REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def _dotenv_is_local_only():
    """Whether to read the repo-root .env at all.

    The .env is LOCAL convenience — `python run.py` on a laptop or a JupyterHub
    box. A managed deployment (Airflow) owns its own environment, supplied by the
    process env and Airflow Variables, and the repo .env must not be able to
    inject anything into it.

    override=False is NOT sufficient protection. It defers to variables that
    already EXIST, but a deployment also states intent by leaving a variable
    ABSENT: ES_INDEX_PREFIX unset means tenant-prefixed indices, set-but-empty
    means un-prefixed (see build()). The repo — and its .env — is mounted into
    the Airflow containers, so loading it supplied the `ES_INDEX_PREFIX=` line
    meant for Togo and silently switched every tenant to un-prefixed indices.
    On 2026-08-21 a run read project-task-index-v1 instead of
    so-project-task-index-v1, matched 0 of 84 seeded docs, and published a
    complete report describing nothing — task SUCCESS, silently wrong extract.
    It even defeated the documented fix: a `{"ES_INDEX_PREFIX": null}` override
    in dst_groups removes the key, but group_environment runs BEFORE this module
    is imported, so the load put it straight back.

    This only began biting once the dotenv PATH was corrected; while it pointed
    at the non-existent pipeline/.env the load was a silent no-op.

    DST_LOAD_DOTENV forces the decision; otherwise Airflow's own environment is
    the signal.
    """
    flag = os.getenv("DST_LOAD_DOTENV", "").strip().lower()
    if flag in ("0", "false", "no"):
        return False
    if flag in ("1", "true", "yes"):
        return True
    return not any(key in os.environ for key in
                   ("AIRFLOW_HOME", "AIRFLOW_CTX_DAG_ID",
                    "AIRFLOW__CORE__EXECUTOR"))


if _dotenv_is_local_only():
    load_dotenv(dotenv_path=os.path.join(_REPO_ROOT, ".env"), override=False)
else:
    log.info("[config] managed deployment — repo .env NOT loaded; configuration "
             "comes from the process environment and Airflow Variables only")

_SCOPES = [
    "https://www.googleapis.com/auth/spreadsheets",
    "https://www.googleapis.com/auth/drive",
]

_MONTH_MAP = {
    1: "January", 2: "February", 3: "March", 4: "April",
    5: "May",     6: "June",     7: "July",  8: "August",
    9: "September", 10: "October", 11: "November", 12: "December",
}


def _resolve_creds_path():
    """Return credential.json path — falls back to file in project root if env path is wrong OS."""
    configured = os.getenv("GOOGLE_CREDENTIALS_PATH", "")
    if configured and os.path.exists(configured):
        return configured
    # Fall back: credential.json at the project root, one level above pipeline/)
    fallback = os.path.join(os.path.dirname(__file__), "..", "credential.json")
    fallback = os.path.abspath(fallback)
    if os.path.exists(fallback):
        log.info(f"[config] GOOGLE_CREDENTIALS_PATH not found; using fallback: {fallback}")
        return fallback
    raise FileNotFoundError(
        f"credential.json not found. Tried:\n  {configured}\n  {fallback}\n"
        f"Set GOOGLE_CREDENTIALS_PATH in .env to the correct path."
    )


def _gs_client():
    creds = Credentials.from_service_account_file(_resolve_creds_path(), scopes=_SCOPES)
    return gspread.Client(auth=creds)


def _parse_date(val):
    if not val:
        return None
    s = str(val).strip()
    for fmt in ("%Y-%m-%d", "%d/%m/%Y", "%Y-%m-%d %H:%M:%S", "%Y-%m-%d %H-%M-%S"):
        try:
            return datetime.strptime(s, fmt).date()
        except ValueError:
            continue
    return None


def _bool(val):
    return str(val).strip().upper() in ("TRUE", "YES", "1", "Y")


def _pad_cycle(val):
    # Sheets returns a numeric cell as 2 (or 2.0); ES cycleIndex is "02"
    s = str(val).strip()
    if s.endswith(".0"):
        s = s[:-2]
    return s.zfill(2) if s.isdigit() else s


# ── in-code feature defaults ───────────────────────────────────────────────────
# ITN duplicate-distribution matrix (analyze_itn._classify_duplicates). SMC/AZM
# rows never read it.
#
# Resolution order, most specific first:
#   1. a dup_matrix cell on the sheet row   (TRUE/FALSE, per campaign)
#   2. the DST_DUP_MATRIX environment key   (per deployment)
#   3. DUP_MATRIX_FALLBACK below            (per checkout)
#
# Step 2 exists because step 3 alone was unreachable on the deployment that
# needs it. The KB records DUP_MATRIX=TRUE as a standing Chad ITN divergence,
# but the hosted Airflow has no env vars, no file mounts and a read-only
# git-sync checkout - Admin -> Variables is the only writable surface. So the
# feature could only be switched on by editing this file, which that deployment
# cannot do. As an env key it is now settable from the dst_config Variable's
# "env" block, like every other deployment setting.
#
# Unset means unchanged: absent DST_DUP_MATRIX and an empty sheet cell resolve
# to the same FALSE this constant has always held.
DUP_MATRIX_FALLBACK = "FALSE"


def _dup_matrix_default():
    return (os.getenv("DST_DUP_MATRIX", "").strip() or DUP_MATRIX_FALLBACK)



def _date_label(d):
    return f"{d.day} {_MONTH_MAP[d.month]} {d.year}"


def _safe_int(val, default):
    try:
        return int(float(val))
    except (ValueError, TypeError):
        return default


def _sanitize_tab(name):
    """openpyxl sheet name: strip invalid chars []:*?/\\ and cap at 31."""
    safe = str(name).translate(str.maketrans("", "", "[]:*?/\\")).strip()
    return safe[:31] or "SECONDARY"


def _parse_secondary_products(row):
    """
    Parse the secondary product(s) counted alongside the primary drug.

    Read from a single sheet column — `secondary_products` if present, else the
    existing `secondary_product` column. The value drives one of two formats:

    1. LIST format (contains '|' or ';') — one or more entries, each pipe-delimited
       as  productName|label|ageMin|ageMax  (label/bands optional), ';'-separated:
           Red VAS|Red VAS; Blue VAS|Blue VAS
           VITAMIN_A_RED|Red VAS|12|59; VITAMIN_A_BLUE|Blue VAS|6|11
       Omit the age band to count the product across ALL ages (the productName
       already encodes the band; age-filtering would undercount vs dashboard).

    2. LEGACY single value (no delimiter) — a bare product name, counted age 3-59,
       tab "ORS-ZINC" (keeps Kebbi/Sokoto ORS-Zinc working unchanged).

    Returns a list of {name, label, age_min, age_max, tab} dicts (possibly empty).
    """
    raw = (str(row.get("secondary_products", "")).strip()
           or str(row.get("secondary_product", "")).strip())
    if not raw:
        return []

    # Legacy single product (no list delimiters) — bare name, age 3-59.
    if "|" not in raw and ";" not in raw:
        return [{"name": raw, "label": raw,
                 "age_min": 3, "age_max": 59, "tab": "ORS-ZINC"}]

    # List format.
    specs = []
    for chunk in raw.split(";"):
        chunk = chunk.strip()
        if not chunk:
            continue
        parts = [p.strip() for p in chunk.split("|")]
        name  = parts[0] if parts else ""
        if not name:
            continue
        label = parts[1] if len(parts) > 1 and parts[1] else name
        amin  = _safe_int(parts[2], None) if len(parts) > 2 and parts[2] != "" else None
        amax  = _safe_int(parts[3], None) if len(parts) > 3 and parts[3] != "" else None
        specs.append({"name": name, "label": label,
                      "age_min": amin, "age_max": amax,
                      "tab": _sanitize_tab(label)})
    return specs


def get_active_rows():
    """Return all rows from the DST config Google Sheet.

    The worksheet tab is configurable per deployment via GOOGLE_SHEET_TAB
    (defaults to "Sheet1"). This lets each environment/cluster read its own
    tab from the same shared sheet — e.g. GOOGLE_SHEET_TAB=taraba on the
    Taraba Jupyter, GOOGLE_SHEET_TAB=togo on the Togo one.
    """
    sheet_id = os.getenv("GOOGLE_SHEET_ID")
    if not sheet_id:
        raise ValueError("GOOGLE_SHEET_ID not set in .env")
    tab    = os.getenv("GOOGLE_SHEET_TAB", "Sheet1").strip() or "Sheet1"
    client = _gs_client()
    try:
        sheet = client.open_by_key(sheet_id).worksheet(tab)
    except gspread.WorksheetNotFound:
        raise ValueError(
            f"Worksheet tab '{tab}' not found in sheet {sheet_id}. "
            f"Check GOOGLE_SHEET_TAB in .env, or create the tab with the "
            f"standard column headers."
        )
    rows = sheet.get_all_records(numericise_ignore=["all"])
    log.info(f"Google Sheet tab '{tab}': {len(rows)} rows loaded")
    return rows


# Values a human types into the sheet. Anything outside these sets changes which
# ES query runs or which pipeline is selected, so guessing produces a
# plausible-looking report about the wrong thing.
VALID_DRUG_TYPES = ("SPAQ", "AZM", "ITN", "LLIN")
_TRUE_WORDS = ("TRUE", "YES", "1", "Y", "ON")
_FALSE_WORDS = ("FALSE", "NO", "0", "N", "OFF")


# Configuration problems that were CORRECTED rather than rejected. campaign_runner
# turns each one into a degraded outcome so a human fixes the sheet, while the
# report still goes out with the right numbers.
CONFIG_CORRECTIONS = []


def _validate_row(row, campaign_start, campaign_end, campaign_days):
    """Reject contradictory config LOUDLY, naming the field and the value.

    Every check here was a silent failure before: the run either produced a
    report about nothing, or a report whose numbers were quietly wrong, or it
    died mid-pipeline with an exception that named no field. Because these raise
    ValueError, campaign_runner classifies them as data errors — the task fails
    IMMEDIATELY with no retries, and the message travels into the Slack alert, so
    the alert alone is enough to fix the sheet without opening a log.

    Skipped entirely for an inactive row: nobody is waiting on its report, and
    failing a row somebody has deliberately parked is just noise.
    """
    state = row.get("state_name") or row.get("tenant") or "?"
    if not _bool(row.get("active", "TRUE")):
        return

    def bad(message):
        raise ValueError(f"[{state}] {message}")

    if campaign_end < campaign_start:
        bad(f"campaign_end ({campaign_end.isoformat()}) is BEFORE campaign_start "
            f"({campaign_start.isoformat()}). The dates are probably swapped. "
            f"Nothing would ever be in window, so no report would ever run and "
            f"nothing would warn you.")

    raw_days = str(row.get("campaign_days", "") or "").strip()
    if raw_days:
        try:
            numeric_days = int(float(raw_days))
        except (TypeError, ValueError):
            numeric_days = None          # non-numeric already fell back to 4
        if numeric_days is not None and numeric_days <= 0:
            bad(f"campaign_days is {raw_days!r}. It is the DIVISOR for every "
                f"coverage percentage: 0 raises ZeroDivisionError mid-run (after "
                f"two pointless retries) and a negative value inverts every "
                f"figure. Set it to the number of days in the campaign.")

    raw_mopup = str(row.get("mopup_end_date", "") or "").strip()
    if raw_mopup:
        parsed_mopup = _parse_date(raw_mopup)
        if not parsed_mopup:
            bad(f"mopup_end_date is {raw_mopup!r}, which is not a date. Use "
                f"YYYY-MM-DD, or leave it blank if this campaign has no mop-up.")
        if parsed_mopup < campaign_start:
            bad(f"mopup_end_date ({raw_mopup}) is before campaign_start "
                f"({campaign_start.isoformat()}). The cumulative report covers "
                f"campaign_start through mop-up, so this range is empty.")

    raw_cycle = str(row.get("cycle_index", "") or "").strip()
    if raw_cycle and not raw_cycle.replace(".0", "").isdigit():
        bad(f"cycle_index is {raw_cycle!r}, which is not a number. ES matches "
            f"cycleIndex EXACTLY (as a zero-padded string like '02'), so this "
            f"matches no document and the report comes out silently EMPTY.")

    raw_drug = str(row.get("drug_type", "") or "").strip().upper()
    if raw_drug and raw_drug not in VALID_DRUG_TYPES:
        bad(f"drug_type is {raw_drug!r}. Valid values are "
            f"{', '.join(VALID_DRUG_TYPES)}. It selects the pipeline, so a typo "
            f"silently runs the SPAQ per-child pipeline over an ITN campaign and "
            f"produces a wrong-shaped report.")

    raw_admin = str(row.get("is_admin_console", "") or "").strip().upper()
    if raw_admin and raw_admin not in _TRUE_WORDS + _FALSE_WORDS:
        bad(f"is_admin_console is {raw_admin!r}. Use TRUE or FALSE. It chooses "
            f"whether ES is filtered by campaignNumber or by projectTypeId, so an "
            f"unrecognised value silently selects the wrong query and returns "
            f"nothing.")



def build(row):
    """
    Build a fully resolved config dict from a Google Sheet row.
    Auto-computes DAY, GTE, LTE, DATE_LABEL, CAMPAIGN_DATES, ES index names.
    """
    tenant       = str(row.get("tenant", "")).strip().lower()
    campaign_start = _parse_date(row.get("campaign_start", ""))
    campaign_end   = _parse_date(row.get("campaign_end", ""))

    extract_date = date.today()
    # Local test override — set TEST_EXTRACT_DATE=YYYY-MM-DD in .env
    _test_date = os.getenv("TEST_EXTRACT_DATE", "").strip()
    if _test_date:
        try:
            extract_date = date.fromisoformat(_test_date)
            log.info(f"[config] TEST_EXTRACT_DATE override active: {extract_date}")
        except ValueError:
            log.warning(f"[config] Invalid TEST_EXTRACT_DATE '{_test_date}' — using today")

    if not campaign_start or not campaign_end:
        raise ValueError(
            f"[{row.get('state_name')}] campaign_start / campaign_end missing or "
            f"unparseable: campaign_start={row.get('campaign_start')!r}, "
            f"campaign_end={row.get('campaign_end')!r}. Use YYYY-MM-DD or DD/MM/YYYY."
        )

    if not os.getenv("ES_URL"):
        raise ValueError("ES_URL not set in .env — cannot connect to Elasticsearch")

    today = extract_date
    in_window = campaign_start <= extract_date <= campaign_end

    try:
        campaign_days_cfg = int(float(row.get("campaign_days", 4) or 4))
    except (ValueError, TypeError):
        campaign_days_cfg = 4

    # campaign_days is the DIVISOR for the daily target and the clamp on DAY, so
    # a value disagreeing with the campaign dates is silently destructive: on a
    # 5-day campaign left at 4, the daily target is inflated, Day 5 is labelled
    # "Day 4" and overwrites performance_day4.xlsx, and CAMPAIGN_DATES loses a
    # day so every CDD loses a sync day. analyze_itn already recomputes the real
    # length from the dates to route around exactly this.
    #
    # Deliberately REPORTED, not corrected. Which field is authoritative is a
    # product decision, not a code one: campaign_end may legitimately extend past
    # the last treatment day, in which case deriving campaign_days from the span
    # would inflate the target the opposite way. And rejecting would stop every
    # report for the campaign until someone edits the sheet. The actual defect
    # here was SILENCE - a blank cell became 4 with no signal at all.
    if campaign_start and campaign_end:
        span = (campaign_end - campaign_start).days + 1
        if span > 0 and campaign_days_cfg != span:
            CONFIG_CORRECTIONS.append(
                f"campaign_days on the sheet is {campaign_days_cfg}, but "
                f"campaign_start {campaign_start} to campaign_end "
                f"{campaign_end} is {span} day(s). The sheet value was used, "
                f"so if it is wrong then the daily target (total/"
                f"{campaign_days_cfg}) and the day number in this report are "
                f"wrong. Fix the campaign_days cell, or campaign_end.")
            log.error(f"[config] campaign_days={campaign_days_cfg} disagrees "
                      f"with the campaign dates ({span} days). Keeping the sheet "
                      f"value — but if the cell is wrong, the daily target and "
                      f"the day number in this report are wrong too")

    _validate_row(row, campaign_start, campaign_end, campaign_days_cfg)
    day = (today - campaign_start).days + 1
    day = max(1, min(day, campaign_days_cfg))

    campaign_dates = [
        (campaign_start + timedelta(days=i)).isoformat() for i in range(day)
    ]

    start_label = _date_label(campaign_start)
    end_label   = _date_label(campaign_end)
    date_label  = _date_label(today)

    gte = f"{today.isoformat()}T00:00:00.000Z"
    lte = f"{today.isoformat()}T23:59:59.999Z"

    # Local SCRATCH only — Google Drive is the output store. Every durable
    # artifact (reports, Excels, chart, checkpoints) is published to the
    # campaign's Drive folder, so this directory is disposable and the code
    # picks it itself: no env var, no sheet column to get wrong.
    # It must never sit inside the package — under Kubernetes the code is
    # typically a read-only git-sync mount and makedirs() would raise
    # "Read-only file system" before any work starts.
    # The legacy out_dir column is still honoured for the JupyterHub boxes,
    # which read their reports off local disk; it is deprecated.
    # Per CAMPAIGN, not per tenant. Two campaigns on one tenant run
    # concurrently (Bauchi SMC + ITN, Chad C1 + C2) and Airflow allows 16
    # parallel runs, so a tenant-only directory made both write
    # performance_dayN.xlsx, cdd_sync_dayN.xlsx and the same checkpoints - one
    # run could overwrite the Excel another was about to read back, publishing
    # the wrong campaign's numbers with no error. Verified live 2026-08-20.
    # A sheet-supplied out_dir is left exactly as given: the JupyterHub boxes
    # run one campaign per tenant and expect their known paths.
    from pipeline.schedule_utils import campaign_key
    out_dir = (str(row.get("out_dir", "")).strip()
               or os.path.join(tempfile.gettempdir(), "dst", tenant,
                               campaign_key(row)))
    os.makedirs(out_dir, exist_ok=True)
    os.makedirs(os.path.join(out_dir, "logs"), exist_ok=True)

    # ES index prefix. Nigeria central instances are tenant-prefixed (e.g.
    # "ba-project-task-index-v1"). Togo's dedicated cluster uses UN-prefixed
    # indices ("project-task-index-v1") with tenantId carried inside each doc.
    # Control per deployment via ES_INDEX_PREFIX in .env:
    #   unset      -> "{tenant}-"  (default, Nigeria central)
    #   ES_INDEX_PREFIX=   (empty) -> no prefix (Togo dedicated cluster)
    #   ES_INDEX_PREFIX=xx-        -> custom prefix
    _prefix_env = os.getenv("ES_INDEX_PREFIX")
    idx_prefix  = f"{tenant}-" if _prefix_env is None else _prefix_env

    return {
        # identity
        "active":          _bool(row.get("active", "TRUE")),
        "in_campaign_window": in_window,
        "campaign_name":   str(row.get("campaign_name", "")).strip(),
        "state_name":      str(row.get("state_name", "")).strip(),
        "tenant":          tenant,
        "drug_type":       str(row.get("drug_type", "SPAQ")).strip().upper(),

        # dates
        "campaign_start":  campaign_start,
        "campaign_end":    campaign_end,
        # Optional. When set, the whole-campaign cumulative report fires at
        # 23:59 UTC on this date and is posted to BOTH channels. Blank = never.
        "mopup_end_date":  _parse_date(row.get("mopup_end_date", "")),
        "extract_date":    today,   # always today — no sheet override
        "campaign_days":   campaign_days_cfg,
        "DAY":             day,
        "GTE":             gte,
        "LTE":             lte,
        "DATE_LABEL":      date_label,
        "START_LABEL":     start_label,
        "END_LABEL":       end_label,
        "CAMPAIGN_DATES":  campaign_dates,

        # ES credentials from .env
        "es_url":  os.getenv("ES_URL"),
        "es_auth": (os.getenv("ES_USER"), os.getenv("ES_PASS")) if os.getenv("ES_USER") else None,
        "ES_INDEX_TASK":      f"{idx_prefix}project-task-index-v1",
        "ES_INDEX_STAFF":     f"{idx_prefix}project-staff-index-v1",
        "ES_INDEX_SYNC":      f"{idx_prefix}user-sync-index-v1",
        "ES_INDEX_IND":       f"{idx_prefix}individual-index-v1",
        "ES_INDEX_PB":        f"{idx_prefix}project-beneficiary-index-v1",
        "ES_INDEX_HH_MEMBER": f"{idx_prefix}household-member-index-v1",

        # Campaign identifier — drives ES filter in analyze.py and cdd_sync.py
        # is_admin_console=TRUE  → filter by campaignNumber (Nigeria, Chad admin)
        # is_admin_console=FALSE → filter by projectTypeId (Togo) OR projectType+cycleIndex (AZM Nigeria/Congo)
        "is_admin_console":  _bool(row.get("is_admin_console", "TRUE")),
        "campaign_number":   str(row.get("campaign_number", "")).strip(),
        "project_type_id":   str(row.get("project_type_id", "")).strip(),
        "project_type":      str(row.get("project_type", "")).strip(),
        # ES stores cycleIndex as zero-padded text ("01"/"02") — a bare sheet
        # number (2) would term-match nothing, so pad digit-only values
        "cycle_index":       _pad_cycle(row.get("cycle_index", "")),

        # ES date range field: "taskDates" (default) or "@timestamp"
        "task_date_field":   str(row.get("task_date_field", "taskDates")).strip() or "taskDates",

        # Whether analyze.py adds doseIndex=1 to treatment query
        # FALSE for all Nigeria SMC states (extraction scripts confirm doseIndex not used)
        # FALSE for AZM. TRUE only if your task docs require it.
        "dose_index_filter": _bool(row.get("dose_index_filter", "FALSE")),

        # Whether analyze.py adds campaign filter (campaignNumber/projectTypeId/projectType)
        # to the task index query. FALSE for all Nigeria SMC states — date range alone
        # isolates the campaign. TRUE only for AZM/non-admin where multiple project types
        # share the same tenant and date range.
        "task_campaign_filter": _bool(row.get("task_campaign_filter", "FALSE")),

        # ITN only: duplicate-distribution matrix (same/different user x same/different
        # day per household). Off keeps every existing number, query, Word section and
        # Slack post unchanged (the performance Excel only gains six empty trailing
        # columns). Default resolves via _dup_matrix_default above — no sheet column
        # required); a non-empty dup_matrix sheet cell overrides it per row, and
        # DST_DUP_MATRIX overrides the checkout default per deployment.
        "dup_matrix": _bool(str(row.get("dup_matrix", "")).strip()
                            or _dup_matrix_default()),

        # secondary product(s) counted alongside the primary drug — empty = disabled.
        # Legacy single string (age 3-59) OR a spec list (see _parse_secondary_products).
        "secondary_product":  str(row.get("secondary_product", "")).strip(),
        "secondary_products": _parse_secondary_products(row),

        # targets / counts
        # target_file is the current name (a file name inside DST_TARGET_FOLDER_ID);
        # target_csv is the legacy column and still wins nothing but compatibility,
        # so prod sheets keep working until they are renamed. Same dual-read
        # pattern as secondary_products/secondary_product above.
        "target_csv":      str(row.get("target_file", "") or
                               row.get("target_csv", "")).strip(),
        # hfs_total / flws_total were read here and consumed nowhere; the
        # reported HF count is computed live from the facility rows.
        # lgas_total stays: report.py uses it as an optional override of the
        # count of LGAs that actually reported.
        "lgas_total":      int(float(row.get("lgas_total", 0) or 0)),

        # output
        "out_dir":         out_dir,
        "slack_channel":          str(row.get("slack_channel", "")).strip(),
        "slack_channel_partners": str(row.get("slack_channel_partners", "")).strip(),

        # scheduler — comma-separated 24h times e.g. "11:00,14:00,17:00,20:00"
        "report_times": [
            t.strip() for t in str(row.get("report_times", "")).split(",")
            if t.strip()
        ],

        # partner report schedule — separate times for the partner-channel post.
        # If empty, the partner report goes out together with the internal report
        # at report_times (backward-compatible). If set, report_times becomes
        # internal-only and these times drive the partner-only post.
        "partner_report_times": [
            t.strip() for t in str(row.get("partner_report_times", "")).split(",")
            if t.strip()
        ],

        # derived filenames
        "perf_xlsx":  os.path.join(out_dir, f"performance_day{day}.xlsx"),
        "sync_xlsx":  os.path.join(out_dir, f"cdd_sync_day{day}.xlsx"),
        "docx_path":  os.path.join(out_dir,
                                   f"{str(row.get('state_name','')).strip().replace(' ','_')}"
                                   f"_Day{day}_Report_{datetime.now().strftime('%H%M')}.docx"),
        "partner_docx_path": os.path.join(out_dir,
                                          f"{str(row.get('state_name','')).strip().replace(' ','_')}"
                                          f"_Day{day}_PartnerReport_{datetime.now().strftime('%H%M')}.docx"),
    }
