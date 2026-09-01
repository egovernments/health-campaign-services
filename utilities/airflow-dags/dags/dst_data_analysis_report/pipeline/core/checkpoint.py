"""JSON snapshots of intermediate stage results.

Each pipeline stage saves its collected data before rendering, so a failed or
suspect run can be re-rendered and inspected offline without re-querying ES.
"""
import json
import logging
import os

log = logging.getLogger(__name__)


def checkpoint_path(cfg, stage):
    folder = os.path.join(cfg["out_dir"], "checkpoints")
    os.makedirs(folder, exist_ok=True)
    suffix = "cumulative" if cfg.get("cumulative") else f"day{cfg['DAY']}"
    return os.path.join(folder, f"{stage}_{suffix}.json")


def save_checkpoint(cfg, stage, payload):
    path = checkpoint_path(cfg, stage)
    try:
        with open(path, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, default=str)
        log.info(f"[{stage}] checkpoint saved -> {path}")
    except Exception as e:
        log.warning(f"[{stage}] checkpoint save failed (non-fatal): {e}")
    return path


def load_checkpoint(cfg, stage):
    path = checkpoint_path(cfg, stage)
    with open(path, encoding="utf-8") as f:
        payload = json.load(f)
    log.info(f"[{stage}] checkpoint loaded <- {path}")
    return payload
