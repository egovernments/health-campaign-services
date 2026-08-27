# -*- coding: utf-8 -*-
"""Pod entrypoint: run ONE campaign report and exit.

Mirrors utilities/hcm-custom-reports/main.py - the DAG launches this image with
KubernetesPodOperator, one pod per campaign, and everything the run needs arrives
as environment variables. Nothing is read from a Google Sheet or an Airflow
Variable in here; the DAG has already resolved all of that.

Why a pod rather than an in-process import: the pipeline's dependencies
(gspread, pandas, openpyxl, ...) then live in THIS image instead of the shared
Airflow image, which on a hosted Airflow we cannot change. Changing a dependency
becomes a rebuild plus an Airflow Variable edit - no Helm, no cluster access.

Required env:
    DST_POD_CONFIG        the whole deployment configuration as one JSON object,
                          including DST_CAMPAIGN_ROW and DST_RUN_MODE. Applied to
                          os.environ without overwriting anything already set.
    DST_CAMPAIGN_ROW      the sheet row as JSON (normally inside DST_POD_CONFIG)
    DST_RUN_MODE          both | internal | partner | cumulative
    ES_URL                and the rest of the pipeline's configuration
Optional:
    DST_GOOGLE_CREDENTIALS_JSON   service account JSON; written to a 0600 file
                                  and exposed as GOOGLE_CREDENTIALS_PATH
    AIRFLOW_XCOM_RETURN           where to write the marker (default is the
                                  path KubernetesPodOperator reads)

Exit codes: 0 report produced or a routine no-op, 1 failure.
"""
import json
import logging
import os
import stat
import sys
import tempfile

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s | %(message)s",
    stream=sys.stdout,
)
log = logging.getLogger("dst.main")

XCOM_DEFAULT = "/airflow/xcom/return.json"


def _apply_pod_config():
    """Apply DST_POD_CONFIG (one JSON blob from the DAG) to os.environ.

    The DAG sends one variable rather than thirty: fewer templated fields to
    render, and the whole deployment configuration arrives atomically. Values
    already in the environment are NOT overwritten, so a pod-level env var still
    wins for debugging.
    """
    raw = (os.getenv("DST_POD_CONFIG") or "").strip()
    if not raw:
        log.info("DST_POD_CONFIG not set - using the environment as-is")
        return 0
    try:
        cfg = json.loads(raw)
    except ValueError as e:
        log.error(f"DST_POD_CONFIG is not valid JSON, so this run has NO "
                  f"configuration: {e}")
        raise
    if not isinstance(cfg, dict):
        raise ValueError(f"DST_POD_CONFIG must be a JSON object, got "
                         f"{type(cfg).__name__}")
    applied = 0
    for key, value in cfg.items():
        if key not in os.environ and value is not None:
            os.environ[key] = str(value)
            applied += 1
    # Names only - never the values.
    log.info(f"applied {applied} config key(s) from DST_POD_CONFIG: "
             f"{', '.join(sorted(k for k in cfg if not _is_secret(k)))}")
    return applied


def _is_secret(key):
    return any(t in key.upper() for t in
               ("TOKEN", "PASS", "SECRET", "KEY", "CREDENTIAL"))


def _write_credentials():
    """Materialise the service account JSON, if one was passed in.

    Same reasoning as dst_config._write_credentials_file: a private key must not
    be baked into the image, so it arrives as an env var and is written 0600 to
    a per-process directory that dies with the pod.
    """
    raw = (os.getenv("DST_GOOGLE_CREDENTIALS_JSON") or "").strip()
    if not raw:
        return None
    try:
        payload = json.loads(raw)
    except ValueError:
        log.error("DST_GOOGLE_CREDENTIALS_JSON is not valid JSON - ignoring it. "
                  "Google Sheets and Drive will fail.")
        return None
    if not payload.get("private_key"):
        log.error("DST_GOOGLE_CREDENTIALS_JSON has no private_key - ignoring it.")
        return None

    directory = tempfile.mkdtemp(prefix="dst-credentials-")
    path = os.path.join(directory, "credential.json")
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL,
                 stat.S_IRUSR | stat.S_IWUSR)
    try:
        handle = os.fdopen(fd, "w", encoding="utf-8")
    except BaseException:
        os.close(fd)
        raise
    with handle:
        json.dump(payload, handle)
    os.environ["GOOGLE_CREDENTIALS_PATH"] = path
    log.info(f"service account written to {path} "
             f"(client_email={payload.get('client_email', '?')})")
    return path


def _publish_xcom(marker):
    """Hand the marker back to the DAG so finalize_run can record the outcome.

    KubernetesPodOperator with do_xcom_push=True reads this file from a sidecar.
    Failing to write it must never fail the run - the report is already out.
    """
    path = os.getenv("AIRFLOW_XCOM_RETURN", XCOM_DEFAULT)
    try:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(marker, fh, default=str)
        log.info(f"marker written to {path}")
    except Exception as e:                                        # noqa: BLE001
        log.warning(f"could not write the XCom marker to {path} "
                    f"(the run itself is unaffected): {e}")


def main():
    # Config first: DST_CAMPAIGN_ROW itself arrives inside DST_POD_CONFIG.
    try:
        _apply_pod_config()
    except Exception as e:                                        # noqa: BLE001
        log.error(f"could not apply the pod configuration: {e}")
        return 1

    raw_row = (os.getenv("DST_CAMPAIGN_ROW") or "").strip()
    if not raw_row:
        log.error("DST_CAMPAIGN_ROW is not set. This image runs ONE campaign and "
                  "expects the sheet row as JSON - the DAG normally supplies it.")
        return 1
    try:
        row = json.loads(raw_row)
    except ValueError as e:
        log.error(f"DST_CAMPAIGN_ROW is not valid JSON: {e}")
        return 1
    if not isinstance(row, dict):
        log.error(f"DST_CAMPAIGN_ROW must be a JSON object, got "
                  f"{type(row).__name__}")
        return 1

    mode = (os.getenv("DST_RUN_MODE") or "both").strip() or "both"
    _write_credentials()

    # dst_common carries the stage chain, the error classification and the
    # degradation registries. It imports Airflow only behind a guarded try, so it
    # runs unchanged here with no Airflow in the image.
    from dst_common.campaign_runner import execute_campaign

    log.info(f"starting campaign run: state={row.get('state_name')} "
             f"tenant={row.get('tenant')} mode={mode}")
    try:
        marker = execute_campaign(row, mode)
    except Exception as e:                                        # noqa: BLE001
        log.error(f"campaign run FAILED: {type(e).__name__}: {e}", exc_info=True)
        _publish_xcom({"ok": False, "error": f"{type(e).__name__}: {e}"[:900],
                       "tenant": row.get("tenant", ""),
                       "state": row.get("state_name", ""), "mode": mode})
        return 1

    if marker.get("ok") is None:
        log.info(f"no-op: {marker.get('reason')}")
    else:
        degraded = [f"{k}: {v}" for k, v in (marker.get("stages") or {}).items()
                    if str(v).startswith("degraded")]
        if degraded:
            log.warning(f"report produced but DEGRADED - {len(degraded)} "
                        f"issue(s): {' | '.join(degraded)}")
        else:
            log.info("report produced cleanly")
    _publish_xcom(marker)
    return 0


if __name__ == "__main__":
    sys.exit(main())
