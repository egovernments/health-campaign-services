"""
hcm_dup/pipeline.py

Steps of the HCM duplicate pipeline for one Postgres schema, called by the tasks of hcm_dup_pipeline_<schema>
(hcm_dup_pipeline.py). Ported from hcm-custom-reports CLICKHOUSE/pipeline/run.py; the SQL in sql/ is the same.

  prepare         create raw_<schema> / hcm_<schema> if missing; decide full vs incremental; run id
  sync_reference  boundary_relationship + message, full reload every REFERENCE_REFRESH_HOURS, swapped in atomically
  sync_tables     household, household_member, individual, individual_address (watermark), then address
  boundary        rebuild dim_boundary + BI views when the reference tables were reloaded (or it is empty)
  beneficiary     rebuild beneficiary rows of households synced since the last completed run
  match           duplicate pairs for the rebuilt rows against every beneficiary
  complete        checkpoint for the next run (+ after a full run: drop rows no longer in Postgres)

How the sync reads Postgres (no CDC: wal_level is "replica"):
  * first copy: chunks of SYNC_CHUNK_ROWS in key order (primary-key index); progress saved after every chunk in
    hcm_<schema>.etl_sync_state, so a failed task resumes where it stopped
  * afterwards: rows with lastmodifiedtime > watermark - SYNC_OVERLAP_MS, chunked by (lastmodifiedtime, key);
    needs the *_ch_sync_idx indexes on Postgres to avoid table scans
  * every chunk is COPY'd into a bytes buffer, inserted into ClickHouse and released before the next one, so the
    worker holds one chunk at a time; re-copying a chunk is harmless (ReplacingMergeTree keeps one row per key)
  * full reload (every FULL_RELOAD_DAYS or full = true): each table is copied into a staging table and swapped in,
    which also drops rows hard-deleted in Postgres
"""
from __future__ import annotations

import io
import logging
import os
import re
import time
from datetime import datetime, timezone
from pathlib import Path

import clickhouse_connect
import psycopg2
from clickhouse_connect.driver.exceptions import OperationalError as ChOperationalError
from psycopg2 import sql

from hcm_dup import config

logger = logging.getLogger("airflow.task")

SQL_DIR = Path(__file__).resolve().parent / "sql"
RUN_COMPLETE = "run complete"
FULL_COMPLETE = "full reload complete"
EPOCH = "1970-01-01 00:00:00.000"


# =============================================================================
# Connections
# =============================================================================

def env(name, default=None):
    value = os.environ.get(name, default)
    if value is None:
        raise RuntimeError(f"Missing environment variable {name} (see hcm_dup/config.py)")
    return value


def connect_ch():
    return clickhouse_connect.get_client(host=env("HCM_CH_HOST", "clickhouse.clickhouse.svc.cluster.local"),
                                         port=int(env("HCM_CH_PORT", "8123")),
                                         username=env("HCM_CH_USER"), password=env("HCM_CH_PASSWORD"),
                                         send_receive_timeout=3600)


class PgSource:
    """Read-only Postgres connection that reconnects when a chunk fails."""

    def __init__(self):
        self.conn = None

    def connect(self):
        self.close()
        self.conn = psycopg2.connect(host=env("HCM_PG_HOST"), port=int(env("HCM_PG_PORT", "5432")),
                                     dbname=env("HCM_PG_DB"), user=env("HCM_PG_USER"),
                                     password=env("HCM_PG_PASSWORD"), sslmode=env("HCM_PG_SSLMODE", "require"),
                                     application_name="hcm-clickhouse-sync", connect_timeout=30,
                                     keepalives=1, keepalives_idle=30)
        self.conn.set_session(readonly=True, autocommit=True)
        return self.conn

    def cursor(self):
        if self.conn is None or self.conn.closed:
            self.connect()
        return self.conn.cursor()

    def close(self):
        if self.conn is not None and not self.conn.closed:
            try:
                self.conn.close()
            except psycopg2.Error:
                pass
        self.conn = None


def with_retries(what, pg, fn):
    for attempt in range(1, config.SYNC_RETRIES + 1):
        try:
            return fn()
        except (psycopg2.OperationalError, psycopg2.InterfaceError, ChOperationalError) as e:
            if attempt == config.SYNC_RETRIES:
                raise
            logger.warning("%s: %s, retrying (%s/%s)", what, type(e).__name__, attempt, config.SYNC_RETRIES - 1)
            time.sleep(5 * attempt)
            pg.connect()


# =============================================================================
# Tenant
# =============================================================================

class Tenant:
    """One Postgres schema and its ClickHouse databases."""

    def __init__(self, cfg):
        self.cfg = cfg
        self.schema = cfg["schema"]
        if not re.fullmatch(r"[a-z][a-z0-9_]*", self.schema):
            raise ValueError(f"schema {self.schema!r}: use lowercase letters, digits and _ only")
        self.raw = f"raw_{self.schema}"
        self.hcm = f"hcm_{self.schema}"
        self.match = cfg["match"]

    def source_schema(self, spec):
        return self.cfg.get(spec.get("source_schema")) or self.schema


def tenant_by_schema(schema):
    for cfg in config.enabled_tenants():
        if cfg["schema"] == schema:
            return Tenant(cfg)
    raise ValueError(f"schema {schema!r} is not an enabled tenant in hcm_dup/config.py")


# =============================================================================
# SQL files and logging
# =============================================================================

def load_sql(t, name):
    """sql/<name> with the database names of this tenant (hcm_raw -> raw_<schema>, hcm -> hcm_<schema>)."""
    text = (SQL_DIR / name).read_text()
    text = re.sub(r"\bhcm_raw\b", t.raw, text)
    return re.sub(r"\bhcm\b(?=[.;])", t.hcm, text)


def statements(text):
    lines = [line for line in text.splitlines() if not line.strip().startswith("--")]
    for statement in "\n".join(lines).split(";\n"):
        statement = statement.strip().rstrip(";")
        if statement:
            yield statement


def run_sql_file(ch, t, name, params):
    """Run every ';'-terminated statement of sql/<name> with server-side parameters."""
    for statement in statements(load_sql(t, name)):
        used = {k: v for k, v in params.items() if "{" + k + ":" in statement}
        ch.command(statement, parameters=used)


def log_step(ch, t, run_version, step, started, rows, note=""):
    utc = lambda ts: datetime.fromtimestamp(ts, timezone.utc)
    ch.insert(f"{t.hcm}.etl_run_log", [[run_version, step, utc(started), utc(time.time()), rows, note]],
              column_names=["run_version", "step", "started_at", "finished_at", "rows", "note"])
    logger.info("%-28s %10s rows %8.1fs  %s", step, rows, time.time() - started, note)


# =============================================================================
# Sync state (hcm_<schema>.etl_sync_state)
# =============================================================================

def get_state(ch, t, table):
    rows = ch.query(f"SELECT phase, wm, last_key, updated_at FROM {t.hcm}.etl_sync_state FINAL "
                    "WHERE table_name = {t:String}", parameters={"t": table}).result_rows
    return rows[0] if rows else None


def set_state(ch, t, table, phase, wm=0, last_key=()):
    ch.insert(f"{t.hcm}.etl_sync_state", [[table, phase, int(wm), [str(k) for k in last_key]]],
              column_names=["table_name", "phase", "wm", "last_key"])


# =============================================================================
# Copying
# =============================================================================

def row_compare(exprs, values, op):
    """(e1, e2, ...) op (v1, v2, ...): row comparison, served by a btree index on the same expressions."""
    return sql.SQL("({}) {} ({})").format(sql.SQL(", ").join(sql.SQL(e) for e in exprs), sql.SQL(op),
                                          sql.SQL(", ").join(sql.Literal(v) for v in values))


def table_ref(t, spec):
    return sql.SQL("{}.{}").format(sql.Identifier(t.source_schema(spec)), sql.Identifier(spec["table"]))


def base_conditions(t, spec):
    if "where" in spec:
        return [sql.SQL(spec["where"]).format(message_module=sql.Literal(t.cfg["message_module"]),
                                              tenant=sql.Literal(t.cfg["boundary_tenant"]))]
    return []


def copy_rows(pg, ch, t, spec, conditions, target=None):
    """COPY the rows matching `conditions` into a buffer, insert it into ClickHouse, release it.
    Callers bound the rows (chunk / id batch), so one call holds at most one chunk in memory."""
    where = sql.SQL(" AND ").join(conditions) if conditions else sql.SQL("TRUE")
    query = sql.SQL("COPY (SELECT {} FROM {} WHERE {}) TO STDOUT WITH (FORMAT csv, NULL '\\N')").format(
        sql.SQL(spec["select"]), table_ref(t, spec), where)

    def attempt():
        buf = io.BytesIO()
        try:
            with pg.cursor() as cur:
                cur.copy_expert(query.as_string(cur), buf)
            if buf.tell() == 0:
                return 0
            summary = ch.raw_insert(target or f"{t.raw}.{spec['table']}", column_names=spec["columns"],
                                    insert_block=buf.getvalue(), fmt="CSV")
            return summary.written_rows
        finally:
            buf.close()

    return with_retries(f"copy {spec['table']}", pg, attempt)


def chunk_upper_bound(pg, t, spec, order_exprs, conditions):
    """Order values of the SYNC_CHUNK_ROWS-th row after `conditions` (None = fewer rows left = last chunk)."""
    where = sql.SQL(" AND ").join(conditions) if conditions else sql.SQL("TRUE")
    exprs = sql.SQL(", ").join(sql.SQL(e) for e in order_exprs)
    query = sql.SQL("SELECT {} FROM {} WHERE {} ORDER BY {} OFFSET {} LIMIT 1").format(
        exprs, table_ref(t, spec), where, exprs, sql.Literal(config.SYNC_CHUNK_ROWS - 1))

    def attempt():
        with pg.cursor() as cur:
            cur.execute(query)
            return cur.fetchone()

    return with_retries(f"bound {spec['table']}", pg, attempt)


def copy_in_chunks(pg, ch, t, spec, order_exprs, start_conditions, lower=None, on_chunk=None, target=None):
    """Copy all rows after `lower` (in order_exprs order) in chunks; on_chunk(upper) after each chunk."""
    total = 0
    while True:
        conds = base_conditions(t, spec) + list(start_conditions)
        if lower:
            conds.append(row_compare(order_exprs, lower, ">"))
        upper = chunk_upper_bound(pg, t, spec, order_exprs, conds)
        if upper is not None:
            conds.append(row_compare(order_exprs, upper, "<="))
        total += copy_rows(pg, ch, t, spec, conds, target)
        if upper is None:
            return total
        lower = upper
        if on_chunk:
            on_chunk(upper)
        logger.info("    %s: %s rows so far", spec["table"], total)


def pg_now_ms(pg):
    with pg.cursor() as cur:
        cur.execute("SELECT (extract(epoch FROM clock_timestamp()) * 1000)::bigint")
        return cur.fetchone()[0]


def sync_initial(pg, ch, t, spec, state):
    """First copy of a table in key order; resumes from last_key after an interruption."""
    table = spec["table"]
    if state and state[0] == "initial":
        started_ms, lower = state[1], list(state[2]) or None
        logger.info("    %s: resuming first copy after key %s", table, lower)
    else:
        # rows changed after this moment are picked up by the first incremental run
        started_ms, lower = pg_now_ms(pg), None
        set_state(ch, t, table, "initial", started_ms, [])
    rows = copy_in_chunks(pg, ch, t, spec, spec["key"], [], lower,
                          on_chunk=lambda upper: set_state(ch, t, table, "initial", started_ms, upper))
    set_state(ch, t, table, "incremental", started_ms)
    return rows, "first copy"


def sync_incremental(pg, ch, t, spec, state):
    """Rows changed since the watermark, in (lastmodifiedtime, key) order."""
    table, wm = spec["table"], state[1]
    order = [config.WM_EXPR] + spec["key"]
    start = [sql.SQL("{} > {}").format(sql.SQL(config.WM_EXPR), sql.Literal(wm - config.SYNC_OVERLAP_MS))]
    high = [wm]

    def advance(upper):
        high[0] = max(high[0], int(upper[0]))
        set_state(ch, t, table, "incremental", high[0])

    rows = copy_in_chunks(pg, ch, t, spec, order, start, on_chunk=advance)
    newest = ch.command(f"SELECT max(lastmodifiedtime) FROM {t.raw}.{table}")
    set_state(ch, t, table, "incremental", max(high[0], int(newest or 0)))
    return rows, f"changed since {wm - config.SYNC_OVERLAP_MS}"


def sync_address(pg, ch, t, spec, state, sync_started):
    """Addresses referenced by rows copied in this run, plus referenced addresses still missing."""
    if state is None or state[0] == "initial":
        return sync_initial(pg, ch, t, spec, state)
    ids = ch.query(f"""
        SELECT DISTINCT addressid FROM (
            SELECT addressid FROM {t.raw}.household WHERE _synced_at >= {{since:DateTime64(3)}}
            UNION ALL
            SELECT addressid FROM {t.raw}.individual_address WHERE _synced_at >= {{since:DateTime64(3)}}
            UNION ALL   -- referenced but never copied (e.g. committed after its household was read)
            SELECT addressid FROM {t.raw}.household WHERE addressid NOT IN (SELECT id FROM {t.raw}.address)
            UNION ALL
            SELECT addressid FROM {t.raw}.individual_address WHERE addressid NOT IN (SELECT id FROM {t.raw}.address)
        ) WHERE addressid != ''""", parameters={"since": sync_started}).result_columns
    ids = ids[0] if ids else []
    rows = 0
    for i in range(0, len(ids), config.ADDRESS_ID_BATCH):
        batch = list(ids[i:i + config.ADDRESS_ID_BATCH])
        rows += copy_rows(pg, ch, t, spec, [sql.SQL("id = ANY({})").format(sql.Literal(batch))])
    set_state(ch, t, spec["table"], "incremental", 0)
    return rows, f"{len(ids)} ids referenced, {len(ids) - rows} not in Postgres"


def swap_in(ch, t, table, load, allow_empty=True):
    """Load a table into a staging copy with load(staging) and swap it in (readers never see it empty).
    With allow_empty=False an empty load is not swapped in and returns 0, so the live table is kept."""
    live, staging = f"{t.raw}.{table}", f"{t.raw}.{table}__staging"
    ch.command(f"CREATE TABLE IF NOT EXISTS {staging} AS {live}")
    ch.command(f"TRUNCATE TABLE {staging}")
    rows = load(staging)
    if rows or allow_empty:
        ch.command(f"EXCHANGE TABLES {live} AND {staging}")
    ch.command(f"DROP TABLE {staging}")
    return rows


def sync_full(pg, ch, t, spec):
    """Re-copy a whole table in key order into staging and swap it in; drops rows deleted in Postgres."""
    started_ms = pg_now_ms(pg)
    rows = swap_in(ch, t, spec["table"],
                   lambda staging: copy_in_chunks(pg, ch, t, spec, spec["key"], [], target=staging))
    set_state(ch, t, spec["table"], "incremental", started_ms if spec["mode"] == "incremental" else 0)
    return rows, "full reload"


def sync_reference(pg, ch, t, spec, state, force):
    """Reload a small table in full every REFERENCE_REFRESH_HOURS (or when forced, or while the copy is empty).
    None = skipped."""
    if state and not force and int(ch.command(f"SELECT count() FROM {t.raw}.{spec['table']}")) > 0:
        age_h = (datetime.now(timezone.utc) - state[3].replace(tzinfo=timezone.utc)).total_seconds() / 3600
        if age_h < config.REFERENCE_REFRESH_HOURS:
            return None, f"skipped, reloaded {age_h:.1f} h ago"
    rows = swap_in(ch, t, spec["table"],
                   lambda staging: copy_rows(pg, ch, t, spec, base_conditions(t, spec), target=staging),
                   allow_empty=False)
    if rows == 0:
        # wrong schema / module, or not loaded yet: keep what we have and try again next run
        source = f"{t.source_schema(spec)}.{spec['table']}"
        logger.warning("%s returned no rows: kept the current copy, will retry next run "
                       "(check boundary_schema / message_schema / boundary_tenant / message_module in hcm_dup/config.py)",
                       source)
        return None, f"EMPTY in {source}, kept current copy"
    set_state(ch, t, spec["table"], "reference", 0)
    return rows, "reloaded"


# =============================================================================
# Steps (one per DAG task)
# =============================================================================

def schema_exists(ch, t):
    return int(ch.command("SELECT count() FROM system.tables WHERE database = {d:String} AND name = 'etl_run_log'",
                          parameters={"d": t.hcm})) > 0


def apply_schema(ch, t):
    """Create databases, functions, tables and views of this tenant (all IF NOT EXISTS / OR REPLACE).
    The default scenarios are inserted only into an empty dup_scenarios, so edited scenarios are kept."""
    has_scenarios = schema_exists(ch, t) and int(ch.command(f"SELECT count() FROM {t.hcm}.dup_scenarios")) > 0
    for statement in statements(load_sql(t, "01_schema.sql")):
        if has_scenarios and statement.startswith(f"INSERT INTO {t.hcm}.dup_scenarios"):
            continue
        ch.command(statement)


def last_completed_checkpoint(ch, t):
    """ClickHouse time at the end of the last run that finished all steps (epoch if none yet)."""
    last = ch.command(f"SELECT argMax(note, finished_at) FROM {t.hcm}.etl_run_log WHERE step = '{RUN_COMPLETE}'")
    return str(last) if last else EPOCH


def full_reload_due(ch, t):
    """True when the last full reload (or, if none, the first completed run) is FULL_RELOAD_DAYS old."""
    if config.FULL_RELOAD_DAYS <= 0:
        return False
    days = ch.command(f"""
        SELECT dateDiff('hour', if(countIf(step = '{FULL_COMPLETE}') > 0,
                                   maxIf(finished_at, step = '{FULL_COMPLETE}'),
                                   minIf(finished_at, step = '{RUN_COMPLETE}')), now64(3)) / 24
        FROM {t.hcm}.etl_run_log WHERE step IN ('{FULL_COMPLETE}', '{RUN_COMPLETE}')""")
    return days is not None and float(days) >= config.FULL_RELOAD_DAYS


def step_prepare(schema, full=False, refresh_reference=False):
    """Returns the run context passed to the other tasks."""
    t = tenant_by_schema(schema)
    ch = connect_ch()
    if not schema_exists(ch, t):
        logger.info("creating databases %s and %s", t.raw, t.hcm)
        apply_schema(ch, t)
    first_run = last_completed_checkpoint(ch, t) == EPOCH
    if not full and not first_run and full_reload_due(ch, t):
        logger.info("full reload due (every %s days)", config.FULL_RELOAD_DAYS)
        full = True
    run = {
        "run_version": int(time.time() * 1000),
        "full": bool(full),
        "refresh_reference": bool(refresh_reference or full),
        # rows synced after the last *completed* run are rebuilt, so a run that failed half-way is caught up
        "since": EPOCH if full else last_completed_checkpoint(ch, t),
        "sync_started": str(ch.command("SELECT now64(3)")),
    }
    logger.info("run %s", run)
    return run


def step_sync_reference(schema, run):
    """Returns True when boundary_relationship / message were reloaded."""
    t = tenant_by_schema(schema)
    ch, pg = connect_ch(), PgSource()
    reloaded = False
    try:
        for spec in (s for s in config.SYNC_TABLES if s["mode"] == "reference"):
            started = time.time()
            rows, note = sync_reference(pg, ch, t, spec, get_state(ch, t, spec["table"]), run["refresh_reference"])
            reloaded |= rows is not None
            log_step(ch, t, run["run_version"], f"sync {spec['table']}", started, rows or 0, note)
    finally:
        pg.close()
    return reloaded


def step_sync_tables(schema, run):
    t = tenant_by_schema(schema)
    ch, pg = connect_ch(), PgSource()
    try:
        for spec in (s for s in config.SYNC_TABLES if s["mode"] != "reference"):
            started = time.time()
            state = get_state(ch, t, spec["table"])
            if run["full"] and state is not None and state[0] != "initial":
                rows, note = sync_full(pg, ch, t, spec)
            elif spec["mode"] == "address":
                rows, note = sync_address(pg, ch, t, spec, state, run["sync_started"])
            elif state is None or state[0] == "initial":
                rows, note = sync_initial(pg, ch, t, spec, state)
            else:
                rows, note = sync_incremental(pg, ch, t, spec, state)
            log_step(ch, t, run["run_version"], f"sync {spec['table']}", started, rows or 0, note)
    finally:
        pg.close()


def boundary_fingerprint(ch, t):
    return ch.command("SELECT groupBitXor(cityHash64(code, name, boundarytype, arrayStringConcat(path_codes, '|'))) "
                      f"FROM {t.hcm}.dim_boundary")


def level_columns(ch, t, codes, names, wrap="{}"):
    """One code + name column per boundary level (state_code, state_name, lga_code, ...), from the levels present
    in dim_boundary, read from the given boundary maps."""
    levels = ch.query(f"SELECT boundarytype FROM {t.hcm}.dim_boundary GROUP BY boundarytype "
                      "ORDER BY min(depth)").result_columns
    columns = []
    for level in (levels[0] if levels else []):
        slug = re.sub(r"[^a-z0-9]+", "_", level.lower()).strip("_")
        quoted = level.replace("\\", "\\\\").replace("'", "\\'")
        columns.append(f"{wrap.format(f'{codes}[{chr(39)}{quoted}{chr(39)}]')} AS {slug}_code, "
                       f"{wrap.format(f'{names}[{chr(39)}{quoted}{chr(39)}]')} AS {slug}_name")
    return columns


def leading(columns):
    """Columns as a select-list prefix ("c1,\n    c2,\n    "); empty when there are no boundary levels yet."""
    return "".join(f"{c},\n    " for c in columns)


def build_flat_views(ch, t):
    """Views for BI tools (Superset), with plain boundary columns of the LATER record (where the duplicate
    happened) instead of boundary maps. Regenerated on every boundary rebuild, as the levels come from the data."""
    h = t.hcm
    pair_levels = level_columns(ch, t, "boundary_codes_b", "boundary_names_b")
    select = ",\n    ".join(pair_levels + ["*"])
    ch.command(f"CREATE OR REPLACE VIEW {h}.v_dup_pairs_flat AS SELECT\n    {select}\nFROM {h}.dup_pairs FINAL")
    ch.command(f"CREATE OR REPLACE VIEW {h}.v_scenario_pairs_flat AS SELECT\n    {select}\nFROM {h}.v_scenario_pairs")

    # one row per scenario x pair x child: COUNT DISTINCT member_id = children involved (not pairs)
    levels = leading(pair_levels)
    ch.command(f"""CREATE OR REPLACE VIEW {h}.v_scenario_members_flat AS SELECT
    {levels}scenario_id, scenario_description, sort_order,
    member_id, if(member_id = id_a, 'EARLIER', 'LATER') AS side,
    id_a, id_b, household_crid_a, household_crid_b,
    registered_date_b, day_relation, days_apart, distance_m,
    -- numbered so charts list the bands in distance order
    transform(distance_band,
              ['0-100m', '100-200m', '200-500m', '500m-1km', '1-2km', '2-5km', '5-10km', '>10km', 'MISSING_COORDS'],
              ['1: 0-100 m', '2: 100-200 m', '3: 200-500 m', '4: 500 m-1 km', '5: 1-2 km', '6: 2-5 km',
               '7: 5-10 km', '8: > 10 km', '9: missing coords'], distance_band) AS distance_band,
    if(p_gender_match = 1, 'SAME', 'DIFFERENT') AS head_gender,
    c_name_sim, p_name_sim, c_name_method, p_name_method, c_dob_diff_days, p_dob_diff_days,
    same_household, same_user, created_by_b, match_signature, match_strength
FROM {h}.v_scenario_pairs
ARRAY JOIN [id_a, id_b] AS member_id""")

    # one row per scenario x pair of households (different households) with how many children they share
    hh_levels = leading(level_columns(ch, t, "boundary_codes_b", "boundary_names_b", wrap="any({})"))
    ch.command(f"""CREATE OR REPLACE VIEW {h}.v_household_dups_flat AS SELECT
    {hh_levels}scenario_id, any(scenario_description) AS scenario_description,
    household_crid_a, household_crid_b,
    max(registered_date_b) AS registered_date_b,
    count() AS duplicated_children,
    min(distance_m) AS distance_m
FROM {h}.v_scenario_pairs
WHERE same_household = 0
GROUP BY scenario_id, household_crid_a, household_crid_b""")

    # child registrations and duplicated registrations per locality x day x scenario; both are additive,
    # so SUM(duplicate_records) / SUM(child_registrations) is the duplicate rate at any boundary level
    reg_levels = leading(level_columns(ch, t, "reg.codes", "reg.names"))
    ch.command(f"""CREATE OR REPLACE VIEW {h}.v_daily_duplicate_rate AS
WITH
    reg AS (
        SELECT boundary_path[-1] AS loc, registered_date AS day, any(boundary_codes) AS codes,
               any(boundary_names) AS names, count() AS child_registrations
        FROM {h}.beneficiary FINAL
        WHERE is_head = 0 AND is_deleted = 0
        GROUP BY loc, day
    ),
    dup AS (
        SELECT scenario_id, boundary_path_b[-1] AS loc, registered_date_b AS day, uniqExact(id_b) AS duplicate_records
        FROM {h}.v_scenario_pairs
        GROUP BY scenario_id, loc, day
    ),
    sc AS (
        SELECT scenario_id, description FROM {h}.dup_scenarios FINAL WHERE enabled = 1
    )
SELECT
    {reg_levels}reg.day AS registered_date_b,
    sc.scenario_id AS scenario_id,
    sc.description AS scenario_description,
    reg.child_registrations AS child_registrations,
    dup.duplicate_records AS duplicate_records
FROM reg
CROSS JOIN sc
LEFT JOIN dup ON dup.scenario_id = sc.scenario_id AND dup.loc = reg.loc AND dup.day = reg.day""")


def step_boundary(schema, run, reference_reloaded):
    """Rebuild dim_boundary when the reference tables were reloaded, or when it is still empty."""
    t = tenant_by_schema(schema)
    ch = connect_ch()
    if not reference_reloaded and int(ch.command(f"SELECT count() FROM {t.hcm}.dim_boundary")) > 0:
        logger.info("boundaries unchanged since last rebuild, nothing to do")
        return
    started = time.time()
    before = boundary_fingerprint(ch, t)
    run_sql_file(ch, t, "10_dim_boundary.sql", {
        "tenant": t.cfg["boundary_tenant"], "hierarchy": t.cfg["boundary_hierarchy"],
        "message_module": t.cfg["message_module"], "locales": t.cfg["boundary_locales"],
    })
    rows = int(ch.command(f"SELECT count() FROM {t.hcm}.dim_boundary"))
    build_flat_views(ch, t)
    types = ch.query(f"SELECT DISTINCT boundarytype FROM {t.hcm}.dim_boundary").result_columns
    types = types[0] if types else []
    if t.match["block_level"] not in types:
        logger.warning("block_level %r is not a boundary type here (%s): matching falls back to same locality",
                       t.match["block_level"], ", ".join(types) or "no boundaries")
    changed = before != boundary_fingerprint(ch, t) and int(ch.command(f"SELECT count() FROM {t.hcm}.beneficiary")) > 0
    note = "CHANGED: trigger with full = true to refresh boundaries on existing records" if changed else ""
    log_step(ch, t, run["run_version"], "boundary", started, rows, note)


def step_beneficiary(schema, run):
    t = tenant_by_schema(schema)
    ch = connect_ch()
    v = run["run_version"]
    # a retried task starts from a clean slate for this run
    ch.command(f"DELETE FROM {t.hcm}.work_affected_household WHERE run_version = {{v:UInt64}}", parameters={"v": v})

    started = time.time()
    run_sql_file(ch, t, "20_affected_households.sql", {"run_version": v, "since": run["since"]})
    households = int(ch.command(f"SELECT count() FROM {t.hcm}.work_affected_household WHERE run_version = {{v:UInt64}}",
                                parameters={"v": v}))
    log_step(ch, t, v, "affected households", started, households, f"since {run['since']}")

    started = time.time()
    run_sql_file(ch, t, "21_beneficiary.sql", {"run_version": v, "tz": t.cfg["timezone"]})
    rows = int(ch.command(f"SELECT count() FROM {t.hcm}.beneficiary WHERE _version = {{v:UInt64}}",
                          parameters={"v": v}))
    log_step(ch, t, v, "beneficiary rebuilt", started, rows)


def step_match(schema, run):
    t = tenant_by_schema(schema)
    ch = connect_ch()
    v = run["run_version"]
    ch.command(f"DELETE FROM {t.hcm}.work_match_pairs WHERE run_version = {{v:UInt64}}", parameters={"v": v})
    started = time.time()
    run_sql_file(ch, t, "30_match.sql", {"run_version": v, **t.match})
    rows = int(ch.command(f"SELECT count() FROM {t.hcm}.dup_pairs WHERE _version = {{v:UInt64}}",
                          parameters={"v": v}))
    log_step(ch, t, v, "match", started, rows, "new/updated pairs")


def step_complete(schema, run):
    """Checkpoint for the next run. After a full run, rows not rebuilt by it belong to records that no longer
    exist in Postgres, so they are removed from beneficiary and dup_pairs."""
    t = tenant_by_schema(schema)
    ch = connect_ch()
    v = run["run_version"]
    if run["full"]:
        started = time.time()
        for table in ("beneficiary", "dup_pairs"):
            ch.command(f"DELETE FROM {t.hcm}.{table} WHERE _version < {{v:UInt64}}", parameters={"v": v})
        log_step(ch, t, v, FULL_COMPLETE, started, 0, "rows of records deleted in Postgres removed")
    # checkpoint on ClickHouse's clock: every row synced by this run has an older _synced_at
    checkpoint = str(ch.command("SELECT now64(3)"))
    log_step(ch, t, v, RUN_COMPLETE, time.time(), 0, checkpoint)
