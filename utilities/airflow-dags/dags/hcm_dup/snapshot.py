"""
hcm_dup/snapshot.py

Steps of hcm_dup_snapshot (hcm_dup_snapshot.py): duplicate analysis of a ClickHouse database raw_<name> that was
loaded once (e.g. CSV exports of one LGA) instead of synced from Postgres. Builds hcm_<name> with the SQL of the
live pipeline (sql/01-30), then campaign cycles and timings (sql/50-53). Every run rebuilds hcm_<name> from scratch.

  prepare      check raw_<name>, create/refresh hcm_<name>, empty its derived tables, dataset + server facts
  boundary     dim_boundary + BI views                            sql/10_dim_boundary.sql
  beneficiary  one row per household member (joins all raw tables) sql/20_affected_households.sql, 21_beneficiary.sql
  cycles       cycle windows from the daily registrations (or the trigger params), every member assigned to one
                                                                   sql/51_cycle_assign.sql
  match        size of the matching problem, then duplicate pairs within each block_level boundary
                                                                   sql/53_match_stats.sql, 30_match.sql
  cycle_pairs  pairs labelled SAME_CYCLE / CROSS_CYCLE_SAME_YEAR / CROSS_YEAR + views   sql/52_cycle_pairs.sql
  report       per-step ClickHouse work from system.query_log -> hcm_<name>.benchmark, extrapolation

Timing: the ClickHouse queries of a step carry log_comment "hcm_snapshot:<run_version>:<step>", so the report can
sum their duration, rows/bytes read and peak memory from system.query_log. Bookkeeping queries (counts) are not
tagged. Wall time is measured on the worker around the tagged queries.
"""
from __future__ import annotations

import logging
import re
import time
from collections import Counter
from datetime import date, datetime, timedelta, timezone

from hcm_dup import config
from hcm_dup.pipeline import EPOCH, Tenant, apply_schema, build_flat_views, connect_ch, load_sql, statements

logger = logging.getLogger("airflow.task")

LOG_PREFIX = "hcm_snapshot"
RAW_TABLES = ("household", "household_member", "individual", "individual_address", "address",
              "boundary_relationship", "message")
# rebuilt by every run (dup_pairs_cycle is dropped and re-created by sql/52)
DERIVED_TABLES = ("dim_boundary", "beneficiary", "dup_pairs", "work_affected_household", "work_match_pairs",
                  "cycles", "beneficiary_cycle")


# =============================================================================
# Config and helpers
# =============================================================================

def snapshot_config(params):
    """Tenant-style config (see config.TENANTS) for the raw_<name> database named in the trigger params."""
    raw_db = str(params["raw_db"]).strip()
    m = re.fullmatch(r"raw_([a-z][a-z0-9_]*)", raw_db)
    if not m:
        raise ValueError(f"raw_db {raw_db!r}: expected raw_<name> (lowercase letters, digits, _)")
    schema = m.group(1)
    if schema in {t["schema"] for t in config.TENANTS}:
        raise ValueError(f"{raw_db} is synced by the live DAG hcm_dup_pipeline_{schema}; this DAG rebuilds "
                         f"hcm_{schema} from scratch and would break that DAG's incremental state")
    match = {**config.MATCH_DEFAULTS, "block_level": params["block_level"], **(params.get("match") or {})}
    unknown = set(match) - set(config.MATCH_DEFAULTS)
    if unknown:
        raise ValueError(f"unknown match settings {sorted(unknown)}; known: {sorted(config.MATCH_DEFAULTS)}")
    detection = {**config.SNAPSHOT_CYCLE_DEFAULTS, **(params.get("cycle_detection") or {})}
    unknown = set(detection) - set(config.SNAPSHOT_CYCLE_DEFAULTS)
    if unknown:
        raise ValueError(f"unknown cycle_detection settings {sorted(unknown)}")
    return {
        "schema": schema,
        "timezone": params["timezone"],
        "boundary_tenant": params["boundary_tenant"],
        "boundary_hierarchy": params["boundary_hierarchy"],
        "message_module": params["message_module"],
        "boundary_locales": list(params["boundary_locales"]),
        "match": match,
        "cycle_detection": detection,
        "cycles": list(params.get("cycles") or []),
        "ch_settings": dict(params.get("ch_settings") or {}),
        "target_members": int(params["target_members"]),
    }


def tagged(run, step):
    """Query settings of a timed step: the user's ClickHouse settings + the log_comment the report looks for."""
    return {**run["cfg"]["ch_settings"], "log_comment": f"{LOG_PREFIX}:{run['run_version']}:{step}"}


def run_sql(ch, t, name, params, settings):
    """Run every statement of sql/<name> (database names of this tenant) with server-side parameters."""
    for statement in statements(load_sql(t, name)):
        used = {k: v for k, v in params.items() if "{" + k + ":" in statement}
        ch.command(statement, parameters=used, settings=settings)


def count(ch, table, where=""):
    return int(ch.command(f"SELECT count() FROM {table} {where}"))


def utc(ts):
    return datetime.fromtimestamp(ts, timezone.utc)


def record_step(ch, t, run, step, started, rows, note=""):
    finished = time.time()
    ch.insert(f"{t.hcm}.benchmark",
              [[run["run_version"], t.raw, step, utc(started), utc(finished), finished - started, int(rows), note]],
              column_names=["run_version", "raw_db", "step", "started_at", "finished_at", "wall_seconds",
                            "rows_out", "note"])
    logger.info("%-14s %12s rows %9.1fs  %s", step, f"{int(rows):,}", finished - started, note)


def record_metrics(ch, t, run, items):
    """items: [(metric, value, note)]"""
    ch.insert(f"{t.hcm}.benchmark_metrics",
              [[run["run_version"], t.raw, m, float(v or 0), n] for m, v, n in items],
              column_names=["run_version", "raw_db", "metric", "value", "note"])
    for m, v, n in items:
        logger.info("  %-34s %16s  %s", m, f"{float(v or 0):,.0f}", n)


def tenant(run):
    return Tenant(run["cfg"])


# =============================================================================
# Cycles
# =============================================================================

def detect_cycles(daily, min_day_records, merge_gap_days, min_records, peak_share, pad_days):
    """Cycle windows from [(date, registrations)] (see config.SNAPSHOT_CYCLE_DEFAULTS)."""
    counts = {d: int(c) for d, c in daily}
    bursts = []
    for d in sorted(d for d, c in counts.items() if c >= min_day_records):
        if bursts and (d - bursts[-1][-1]).days <= merge_gap_days:
            bursts[-1].append(d)
        else:
            bursts.append([d])
    peaks = []
    for burst in bursts:
        total = sum(counts[d] for d in burst)
        if total < min_records:
            continue
        top = max(counts[d] for d in burst)
        peak_days = [d for d in burst if counts[d] >= peak_share * top]
        peaks.append({"peak_start": peak_days[0], "peak_end": peak_days[-1], "peak_day_records": top,
                      "burst_records": total})
    return windows(peaks, pad_days, "DETECTED")


def windows(cycles, pad_days, source):
    """Number the cycles per year and set their windows: start = peak start - pad_days, end = day before the next
    cycle's start in the same year, 31 Dec for the year's last cycle."""
    cycles = sorted(cycles, key=lambda c: c["peak_start"])
    per_year = Counter()
    out = []
    for i, c in enumerate(cycles):
        year = c["peak_start"].year
        per_year[year] += 1
        nxt = cycles[i + 1] if i + 1 < len(cycles) else None
        start = c.get("start_date") or c["peak_start"] - timedelta(days=pad_days)
        if c.get("end_date"):
            end = c["end_date"]
        elif nxt and nxt["peak_start"].year == year:
            end = (nxt.get("start_date") or nxt["peak_start"] - timedelta(days=pad_days)) - timedelta(days=1)
        else:
            end = date(year, 12, 31)
        out.append({"cycle_id": c.get("cycle_id") or f"{year}-C{per_year[year]}", "year": year,
                    "cycle_no": per_year[year], "start_date": start, "end_date": end,
                    "peak_start": c.get("peak_start") if source == "DETECTED" else None,
                    "peak_end": c.get("peak_end"), "peak_day_records": c.get("peak_day_records"),
                    "burst_records": c.get("burst_records"), "source": source})
    for a, b in zip(out, out[1:]):
        if b["start_date"] <= a["end_date"]:
            raise ValueError(f"cycle windows overlap: {a['cycle_id']} ends {a['end_date']}, "
                             f"{b['cycle_id']} starts {b['start_date']}")
    return out


def manual_cycles(entries):
    """Cycles from the trigger params: [{"start": "2025-06-23", "end": "2025-07-20", "cycle_id": "2025-C1"}, ...]
    (end and cycle_id optional: end defaults to the day before the next start / 31 Dec)."""
    parsed = []
    for e in entries:
        start = date.fromisoformat(str(e["start"]))
        parsed.append({"peak_start": start, "start_date": start,
                       "end_date": date.fromisoformat(str(e["end"])) if e.get("end") else None,
                       "cycle_id": e.get("cycle_id")})
    return windows(parsed, 0, "MANUAL")


# =============================================================================
# Steps
# =============================================================================

def step_prepare(params):
    """Returns the run context passed to the other tasks."""
    cfg = snapshot_config(params)
    t = Tenant(cfg)
    ch = connect_ch()
    run = {"run_version": int(time.time() * 1000), "cfg": cfg}

    present = dict(ch.query("SELECT name, total_rows FROM system.tables WHERE database = {d:String}",
                            parameters={"d": t.raw}).result_rows)
    missing = [name for name in RAW_TABLES if name not in present]
    if missing:
        raise ValueError(f"{t.raw} lacks tables {missing} (load them first, e.g. BAUCHI_ANALYSIS/load_bauchi_lga.sh)")
    raw_rows = {name: count(ch, f"{t.raw}.{name}", "FINAL") for name in RAW_TABLES}
    empty = [name for name, n in raw_rows.items() if n == 0]
    if empty:
        raise ValueError(f"{t.raw}: tables {empty} are empty")

    logger.info("building %s from %s (run %s)", t.hcm, t.raw, run["run_version"])
    apply_schema(ch, t)
    run_sql(ch, t, "50_snapshot_schema.sql", {}, {})
    for table in DERIVED_TABLES:
        ch.command(f"TRUNCATE TABLE {t.hcm}.{table}")
    ch.command(f"DROP TABLE IF EXISTS {t.hcm}.dup_pairs_cycle")
    for table in ("work_match_input", "work_match_candidates"):      # created by sql/30_match.sql
        ch.command(f"TRUNCATE TABLE IF EXISTS {t.hcm}.{table}")

    items = [(f"raw rows: {name}", n, "") for name, n in raw_rows.items()]
    try:
        version, threads = ch.query("SELECT version(), toString(getSetting('max_threads'))").first_row
        items.append(("server: max_threads", float(re.sub(r"\D", "", threads) or 0), f"ClickHouse {version}"))
        for metric, value in ch.query(
                "SELECT metric, value FROM system.asynchronous_metrics WHERE metric IN "
                "('NumberOfCPUCores', 'NumberOfPhysicalCores', 'CGroupMaxCPU', 'OSMemoryTotal', 'CGroupMemoryTotal')"
        ).result_rows:
            items.append((f"server: {metric}", value, ""))
    except Exception as e:      # facts for the report only; a restricted user may not see them
        logger.warning("server facts unavailable: %s", e)
    items.append(("settings: ch_settings", 0, str(cfg["ch_settings"])))
    items.append(("settings: match", 0, str(cfg["match"])))
    record_metrics(ch, t, run, items)
    return run


def step_boundary(run):
    t, cfg = tenant(run), run["cfg"]
    ch = connect_ch()
    started = time.time()
    run_sql(ch, t, "10_dim_boundary.sql", {
        "tenant": cfg["boundary_tenant"], "hierarchy": cfg["boundary_hierarchy"],
        "message_module": cfg["message_module"], "locales": cfg["boundary_locales"],
    }, tagged(run, "boundary"))
    rows = count(ch, f"{t.hcm}.dim_boundary")
    record_step(ch, t, run, "boundary", started, rows)

    types = ch.query(f"SELECT DISTINCT boundarytype FROM {t.hcm}.dim_boundary").result_columns
    types = types[0] if types else []
    if cfg["match"]["block_level"] not in types:
        raise ValueError(f"block_level {cfg['match']['block_level']!r} is not a boundary type of hierarchy "
                         f"{cfg['boundary_hierarchy']!r} for tenant {cfg['boundary_tenant']!r} (found: {types})")
    build_flat_views(ch, t)


def step_beneficiary(run):
    t, v = tenant(run), run["run_version"]
    ch = connect_ch()
    started = time.time()
    run_sql(ch, t, "20_affected_households.sql", {"run_version": v, "since": EPOCH}, tagged(run, "households"))
    households = count(ch, f"{t.hcm}.work_affected_household", f"WHERE run_version = {int(v)}")
    record_step(ch, t, run, "households", started, households)

    started = time.time()
    run_sql(ch, t, "21_beneficiary.sql", {"run_version": v, "tz": run["cfg"]["timezone"]}, tagged(run, "beneficiary"))
    rows = count(ch, f"{t.hcm}.beneficiary", "FINAL")
    record_step(ch, t, run, "beneficiary", started, rows, f"{households:,} households")
    record_metrics(ch, t, run, [("beneficiary rows", rows, "household members (children + heads)"),
                                ("households", households, "")])


def step_cycles(run):
    t, cfg = tenant(run), run["cfg"]
    ch = connect_ch()
    settings = tagged(run, "cycles")
    started = time.time()
    daily = ch.query(f"SELECT registered_date, count() FROM {t.hcm}.beneficiary FINAL WHERE is_deleted = 0 "
                     "GROUP BY registered_date ORDER BY registered_date", settings=settings).result_rows
    cycles = manual_cycles(cfg["cycles"]) if cfg["cycles"] else detect_cycles(daily, **cfg["cycle_detection"])
    if not cycles:
        raise ValueError(f"no cycles found with {cfg['cycle_detection']}; lower min_day_records / min_records "
                         "or pass the cycles in the trigger params")
    columns = ["cycle_id", "year", "cycle_no", "start_date", "end_date", "peak_start", "peak_end",
               "peak_day_records", "burst_records", "source", "run_version"]
    ch.insert(f"{t.hcm}.cycles", [[c[k] for k in columns[:-1]] + [run["run_version"]] for c in cycles],
              column_names=columns, settings=settings)
    run_sql(ch, t, "51_cycle_assign.sql", {"run_version": run["run_version"], "tz": cfg["timezone"],
                                           "block_level": cfg["match"]["block_level"]}, settings)
    rows = count(ch, f"{t.hcm}.beneficiary_cycle")
    record_step(ch, t, run, "cycles", started, rows, f"{len(cycles)} cycles ({cycles[0]['source']})")

    logger.info("%-9s %-11s %-11s %-11s %-11s %10s %10s %9s", "cycle", "start", "end", "peak start", "peak end",
                "members", "children", "by server")
    for r in ch.query(f"SELECT cycle_id, start_date, end_date, peak_start, peak_end, members, children, "
                      f"assigned_by_server_date FROM {t.hcm}.v_cycle_overview").result_rows:
        logger.info("%-9s %-11s %-11s %-11s %-11s %10s %10s %9s", *[str(x) for x in r[:5]], *[f"{x:,}" for x in r[5:]])
    return [{k: str(v) if isinstance(v, date) else v for k, v in c.items()} for c in cycles]


def step_match(run):
    t, m = tenant(run), tenant(run).match
    ch = connect_ch()
    started = time.time()
    stats_sql = next(statements(load_sql(t, "53_match_stats.sql")))
    children, blocks, max_block, join_rows = ch.query(stats_sql, parameters={
        "block_level": m["block_level"], "child_gender_exact": m["child_gender_exact"],
        "child_dob_max_days": m["child_dob_max_days"]}, settings=tagged(run, "match_stats")).first_row
    record_step(ch, t, run, "match_stats", started, join_rows or 0, "not part of matching; sizes for the report")
    record_metrics(ch, t, run, [
        ("matchable children", children, "non-head, has DOB, name, head name and a block"),
        ("blocks", blocks, f"{m['block_level']} boundaries with matchable children"),
        ("max block children", max_block, "largest block"),
        ("avg block children", (children or 0) / max(blocks or 1, 1), ""),
        ("join rows", join_rows, "blocking join output before name/DOB filters"),
    ])

    started = time.time()
    run_sql(ch, t, "30_match.sql", {"run_version": run["run_version"], **m}, tagged(run, "match"))
    rows = count(ch, f"{t.hcm}.dup_pairs", "FINAL")
    record_step(ch, t, run, "match", started, rows, f"block_level {m['block_level']}")
    candidates = count(ch, f"{t.hcm}.work_match_candidates", f"WHERE run_version = {int(run['run_version'])}")
    record_metrics(ch, t, run, [
        ("match candidates", candidates, "pairs left for the name similarity after the cheap filters"),
        ("dup pairs", rows, "base conditions"),
    ])


def step_cycle_pairs(run):
    t = tenant(run)
    ch = connect_ch()
    started = time.time()
    run_sql(ch, t, "52_cycle_pairs.sql", {}, tagged(run, "cycle_pairs"))
    rows = count(ch, f"{t.hcm}.dup_pairs_cycle")
    record_step(ch, t, run, "cycle_pairs", started, rows)

    logger.info("pairs per cycle relation (all base-condition pairs / different individuals):")
    for relation, pairs, other in ch.query(
            f"SELECT cycle_relation, count(), countIf(same_individual = 0) FROM {t.hcm}.dup_pairs_cycle "
            "GROUP BY cycle_relation ORDER BY cycle_relation").result_rows:
        logger.info("  %-24s %12s %12s", relation, f"{pairs:,}", f"{other:,}")


def step_report(run):
    """Per-step ClickHouse work from system.query_log into hcm_<name>.benchmark; extrapolation to target_members."""
    t, v, cfg = tenant(run), run["run_version"], run["cfg"]
    ch = connect_ch()
    try:
        ch.command("SYSTEM FLUSH LOGS")
    except Exception as e:      # needs the SYSTEM FLUSH LOGS grant; otherwise wait for the periodic flush
        logger.warning("SYSTEM FLUSH LOGS failed (%s), waiting for the query_log flush", e)
        time.sleep(15)

    work = {}
    try:
        for row in ch.query(
                "SELECT splitByChar(':', log_comment)[3] AS step, count(), sum(query_duration_ms) / 1000, "
                "sum(read_rows), sum(read_bytes), sum(written_rows), greatest(max(memory_usage), 0) "
                "FROM system.query_log "
                "WHERE event_date >= yesterday() AND type = 'QueryFinish' AND startsWith(log_comment, {p:String}) "
                "GROUP BY step", parameters={"p": f"{LOG_PREFIX}:{v}:"}).result_rows:
            work[row[0]] = row[1:]
    except Exception as e:
        logger.warning("system.query_log unavailable (%s): only wall times are reported", e)

    steps = ch.query(f"SELECT step, started_at, finished_at, wall_seconds, rows_out, note FROM {t.hcm}.benchmark "
                     "FINAL WHERE run_version = {v:UInt64} ORDER BY started_at", parameters={"v": v}).result_rows
    rows = [[v, t.raw, s, start, end, wall, out, *work.get(s, (0, 0., 0, 0, 0, 0)), note]
            for s, start, end, wall, out, note in steps]
    ch.insert(f"{t.hcm}.benchmark", rows,
              column_names=["run_version", "raw_db", "step", "started_at", "finished_at", "wall_seconds", "rows_out",
                            "queries", "ch_seconds", "read_rows", "read_bytes", "written_rows", "peak_memory_bytes",
                            "note"])

    gb = 1024 ** 3
    logger.info("%-13s %9s %9s %7s %14s %9s %10s %14s", "step", "wall s", "CH s", "queries", "rows read",
                "GB read", "peak GB", "rows out")
    for r in rows:
        logger.info("%-13s %9.1f %9.1f %7d %14s %9.2f %10.2f %14s", r[2], r[5], r[8], r[7], f"{r[9]:,}",
                    r[10] / gb, r[12] / gb, f"{r[6]:,}")

    # Extrapolation. Building rows (beneficiary, cycles, cycle_pairs) grows with the number of records. Matching
    # grows with join rows: linearly if a bigger dataset has more blocks of the same size (more health facilities),
    # quadratically if the same blocks get denser. The truth is in between, closer to linear for more geography.
    metric = dict(ch.query(f"SELECT metric, argMax(value, created_at) FROM {t.hcm}.benchmark_metrics "
                           "WHERE run_version = {v:UInt64} GROUP BY metric", parameters={"v": v}).result_rows)
    wall = {r[2]: r[5] for r in rows}
    members = metric.get("beneficiary rows") or 0
    if members:
        f = cfg["target_members"] / members
        build = sum(wall.get(s, 0) for s in ("boundary", "households", "beneficiary", "cycles", "cycle_pairs"))
        match = wall.get("match", 0)
        estimates = [
            ("seconds per 1M members: build", build / members * 1e6, "boundary + beneficiary + cycles + cycle_pairs"),
            ("seconds per 1B join rows: match", match / max(metric.get("join rows") or 1, 1) * 1e9, ""),
            ("estimate seconds: build", build * f, f"x{f:.1f} records"),
            ("estimate seconds: match (more blocks)", match * f, "same block sizes, more health facilities"),
            ("estimate seconds: match (denser blocks)", match * f * f, "same health facilities, bigger blocks"),
            ("estimate peak memory bytes: beneficiary", (work.get("beneficiary") or (0,) * 6)[5] * f,
             "if it grows with records (not with grace_hash, which caps join memory)"),
            ("estimate peak memory bytes: match", (work.get("match") or (0,) * 6)[5] * f, "more blocks of the same size"),
        ]
        logger.info("extrapolation from %s to %s members:", f"{members:,.0f}", f"{cfg['target_members']:,}")
        record_metrics(ch, t, run, estimates)

    logger.info("data quality (%s.v_data_quality):", t.hcm)
    for check, n in ch.query(f"SELECT * FROM {t.hcm}.v_data_quality").result_rows:
        logger.info("  %12s  %s", f"{n:,}", check)
    return {r[2]: {"wall_seconds": round(r[5], 1), "ch_seconds": round(r[8], 1), "rows_out": r[6]} for r in rows}
