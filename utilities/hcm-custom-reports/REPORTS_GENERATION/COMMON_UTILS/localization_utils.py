"""
localization_utils.py

Resolve report column headers to localized text via the egov-localization service.
Keys follow HCM_<REPORT_DIR>_<COLUMN> under the fixed module 'hcm-dss'. Any failure
(service unreachable, missing key) falls back to the original English header, so a
report is never blocked by localization.
"""
import os
import re
import requests

# Localization module — env-driven like the rest of the config here
# (ES_HOST/KAFKA_BROKER/LOCALE/TENANT_ID). Defaults to hcm-dss.
MODULE = os.getenv("LOCALIZATION_MODULE", "hcm-dss")

LOCALIZATION_HOST = os.getenv("LOCALIZATION_HOST", "http://egov-localization.egov.svc.cluster.local:8080")
LOCALIZATION_SEARCH_ENDPOINT = os.getenv("LOCALIZATION_SEARCH_ENDPOINT", "/localization/messages/v1/_search")
LOCALE = os.getenv("LOCALE", "en_IN")
TENANT_ID = os.getenv("TENANT_ID", "")

_messages = None  # lazy cache: {code: message}


def _load_messages():
    global _messages
    if _messages is not None:
        return _messages
    _messages = {}
    try:
        url = f"{LOCALIZATION_HOST.rstrip('/')}{LOCALIZATION_SEARCH_ENDPOINT}"
        params = {"locale": LOCALE, "tenantId": TENANT_ID, "module": MODULE}
        resp = requests.post(url, params=params, json={"RequestInfo": {}}, timeout=30)
        if resp.status_code == 200:
            for m in resp.json().get("messages", []):
                if m.get("code"):
                    _messages[m["code"]] = m.get("message")
            print(f"[LOCALIZATION] loaded {len(_messages)} messages (module={MODULE}, locale={LOCALE}, tenant={TENANT_ID})")
        else:
            print(f"[LOCALIZATION] non-200 ({resp.status_code}); falling back to English headers")
    except Exception as e:
        print(f"[LOCALIZATION] fetch failed ({e}); falling back to English headers")
    return _messages


def _code_for(report_dir, column):
    # Must match the key generation: HCM_<REPORT_DIR_UPPER>_<COLUMN alnum-only upper>
    return f"HCM_{report_dir.upper()}_{re.sub(r'[^A-Za-z0-9]', '', column).upper()}"


def localize(code, default=None):
    """Localized message for a code; falls back to default (or the code itself)."""
    msg = _load_messages().get(code)
    return msg if msg else (default if default is not None else code)


def localize_headers(report_dir, columns):
    """Map English column headers to localized headers via HCM_<dir>_<col> keys;
    falls back to the English header when a key is missing."""
    return [localize(_code_for(report_dir, c), c) for c in columns]


def localize_df_columns(report_dir, df):
    """Rename a DataFrame's columns to localized headers (in place) via
    HCM_<dir>_<col> keys. Unmapped columns (e.g. dynamic date/question columns)
    keep their original name via fallback."""
    df.rename(columns={c: localize(_code_for(report_dir, c), c) for c in df.columns}, inplace=True)
    return df
