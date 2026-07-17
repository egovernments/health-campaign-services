"""
localization_utils.py

Resolve report column headers to localized text via the egov-localization service.
Each report holds an explicit {column -> localization code} map and we look the code
up verbatim (same mechanism as excel-ingestion), so nothing is derived at runtime.
Any failure (service unreachable, missing/unmapped key) falls back to the raw column,
so a report is never blocked by localization.
"""
import os
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


def localize(code, default=None):
    """Localized message for a code; falls back to default when the code is unmapped
    or the service has no message for it."""
    if code:
        msg = _load_messages().get(code)
        if msg:
            return msg
    return default if default is not None else (code or "")


def localize_headers(columns, code_map):
    """Map ordered column headers to localized text. code_map: {column -> code}.
    Falls back to the raw column when unmapped or the message is missing."""
    return [localize(code_map.get(c), c) for c in columns]


def localize_df_columns(df, code_map):
    """Rename a DataFrame's columns (in place) via code_map: {column -> code}.
    Unmapped columns (e.g. dynamic date/question columns) keep their raw name."""
    df.rename(columns={c: localize(code_map[c], c) for c in df.columns if c in code_map}, inplace=True)
    return df
