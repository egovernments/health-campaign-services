-- =============================================================================
-- Tables of the snapshot DAG (hcm_dup_snapshot.py), created in hcm_<name> next to those of 01_schema.sql.
-- cycles / beneficiary_cycle / dup_pairs_cycle are rebuilt by every run, benchmark* keep every run.
-- =============================================================================

-- Campaign cycle windows (detected from daily registrations, or given in the trigger params).
CREATE TABLE IF NOT EXISTS hcm.cycles
(
    cycle_id            String,                     -- e.g. 2025-C1: year of the peak, n-th cycle of that year
    year                UInt16,
    cycle_no            UInt8,
    start_date          Date,                       -- peak start - pad days
    end_date            Date,                       -- day before the next window, 31 Dec for a year's last cycle
    peak_start          Nullable(Date),             -- busy days found in the data (NULL for manual cycles)
    peak_end            Nullable(Date),
    peak_day_records    Nullable(UInt32),
    burst_records       Nullable(UInt64),
    source              LowCardinality(String),     -- DETECTED / MANUAL
    run_version         UInt64
)
ENGINE = MergeTree
ORDER BY start_date;

-- The cycle of every beneficiary row: client registration date, or the server date when the client date
-- falls outside every window (wrong device clock), else UNASSIGNED.
CREATE TABLE IF NOT EXISTS hcm.beneficiary_cycle
(
    member_crid         String,
    is_head             UInt8,
    is_deleted          UInt8,
    block_code          String,                     -- boundary of the block level (falls back to the locality)
    block_name          String,
    registered_date     Date,                       -- client date, campaign timezone
    server_date         Date,
    cycle_id            LowCardinality(String),     -- UNASSIGNED when neither date is in a window
    cycle_year          UInt16,
    cycle_no            UInt8,
    cycle_ordinal       UInt16,                     -- 1.. in time order over all years, 0 = UNASSIGNED
    date_source         LowCardinality(String),     -- CLIENT / SERVER / NONE
    run_version         UInt64
)
ENGINE = MergeTree
ORDER BY member_crid;

-- One row per step and run: wall clock (Airflow worker) and ClickHouse work (system.query_log).
CREATE TABLE IF NOT EXISTS hcm.benchmark
(
    run_version         UInt64,
    raw_db              String,
    step                LowCardinality(String),
    started_at          DateTime64(3),
    finished_at         DateTime64(3),
    wall_seconds        Float64,
    rows_out            Int64,
    queries             UInt32,
    ch_seconds          Float64,                    -- sum of query durations
    read_rows           UInt64,
    read_bytes          UInt64,
    written_rows        UInt64,
    peak_memory_bytes   UInt64,                     -- largest single query
    note                String,
    updated_at          DateTime64(3) DEFAULT now64(3)
)
ENGINE = ReplacingMergeTree(updated_at)
ORDER BY (run_version, step);

-- Dataset sizes, server resources and extrapolations of a run.
CREATE TABLE IF NOT EXISTS hcm.benchmark_metrics
(
    run_version         UInt64,
    raw_db              String,
    metric              String,
    value               Float64,
    note                String,
    created_at          DateTime64(3) DEFAULT now64(3)
)
ENGINE = MergeTree
ORDER BY (run_version, metric);

-- Cycles with their size and how many records needed the server date.
CREATE OR REPLACE VIEW hcm.v_cycle_overview AS
SELECT
    x.cycle_id AS cycle_id, c.start_date AS start_date, c.end_date AS end_date,
    c.peak_start AS peak_start, c.peak_end AS peak_end,
    x.members AS members, x.children AS children,
    x.assigned_by_server_date AS assigned_by_server_date, x.blocks AS blocks
FROM
(
    SELECT cycle_id, min(cycle_ordinal) AS ord,
           countIf(is_deleted = 0) AS members,
           countIf(is_deleted = 0 AND is_head = 0) AS children,
           countIf(is_deleted = 0 AND date_source = 'SERVER') AS assigned_by_server_date,
           uniqExact(block_code) AS blocks
    FROM hcm.beneficiary_cycle
    GROUP BY cycle_id
) AS x
LEFT JOIN hcm.cycles AS c ON c.cycle_id = x.cycle_id
ORDER BY x.ord;
