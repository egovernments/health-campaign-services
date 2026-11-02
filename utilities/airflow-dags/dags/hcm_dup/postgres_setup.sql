-- =============================================================================
-- Postgres setup for the HCM duplicate pipeline (hcm_dup_pipeline_<schema>). Run by a DBA, once per schema.
-- No restart, no server parameter / WAL change. Not executed by Airflow.
--
--   psql "host=<server> dbname=<db> user=<admin> sslmode=require" \
--        -v schema=ba -v boundary_schema=public -v message_schema=public -v sync_password='<password>' -f postgres_setup.sql
--
--   boundary_schema / message_schema: where boundary_relationship / message live (default: schema); must match
--   boundary_schema / message_schema of the tenant in hcm_dup/config.py.
--
-- * Must run in psql (not a GUI that wraps statements in a transaction): CREATE INDEX CONCURRENTLY cannot run
--   inside a transaction. Each statement here autocommits.
-- * CONCURRENTLY does not block the application's inserts/updates; it takes longer on big tables. If a build
--   fails it leaves an INVALID index (see the check at the end): DROP INDEX CONCURRENTLY <schema>.<name>; and rerun.
-- * The index expression must stay identical to WM_EXPR in hcm_dup/config.py, or Postgres will not use it.
-- * address, boundary_relationship and message need no index (read by primary key / small).
-- * For a second schema rerun with its -v schema=...; the role is created only the first time.
-- =============================================================================
\set ON_ERROR_STOP on
SET statement_timeout = 0;
\if :{?boundary_schema}
\else
\set boundary_schema :schema
\endif
\if :{?message_schema}
\else
\set message_schema :schema
\endif

-- 1. Read-only login for the sync (its user/password go into the sops secrets file: pg-sync-username/-password)
SELECT NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hcm_ch_sync') AS create_role \gset
\if :create_role
CREATE ROLE hcm_ch_sync LOGIN PASSWORD :'sync_password';
\endif
ALTER ROLE hcm_ch_sync SET default_transaction_read_only = on;
ALTER ROLE hcm_ch_sync SET statement_timeout = '10min';
SELECT format('GRANT CONNECT ON DATABASE %I TO hcm_ch_sync', current_database()) \gexec

GRANT USAGE ON SCHEMA :"schema" TO hcm_ch_sync;
GRANT SELECT ON :"schema".household, :"schema".household_member, :"schema".individual,
                :"schema".individual_address, :"schema".address
             TO hcm_ch_sync;
GRANT USAGE ON SCHEMA :"boundary_schema" TO hcm_ch_sync;
GRANT SELECT ON :"boundary_schema".boundary_relationship TO hcm_ch_sync;
GRANT USAGE ON SCHEMA :"message_schema" TO hcm_ch_sync;
GRANT SELECT ON :"message_schema".message TO hcm_ch_sync;

-- 2. Indexes for the incremental sync: rows changed since the last run, in (lastmodifiedtime, key) order
CREATE INDEX CONCURRENTLY IF NOT EXISTS household_ch_sync_idx
    ON :"schema".household ((coalesce(lastmodifiedtime, createdtime, 0)), id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS household_member_ch_sync_idx
    ON :"schema".household_member ((coalesce(lastmodifiedtime, createdtime, 0)), clientreferenceid);

CREATE INDEX CONCURRENTLY IF NOT EXISTS individual_ch_sync_idx
    ON :"schema".individual ((coalesce(lastmodifiedtime, createdtime, 0)), id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS individual_address_ch_sync_idx
    ON :"schema".individual_address ((coalesce(lastmodifiedtime, createdtime, 0)), individualid, addressid, type, isdeleted);

-- 3. Check: all four must be valid (t)
SELECT c.relname AS index, i.indisvalid AS valid, pg_size_pretty(pg_relation_size(c.oid)) AS size
FROM pg_index i
JOIN pg_class c ON c.oid = i.indexrelid
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = :'schema' AND c.relname LIKE '%\_ch\_sync\_idx'
ORDER BY 1;

-- 4. Sizes of the synced tables (to size the ClickHouse disk)
SELECT c.relname AS table, pg_size_pretty(pg_total_relation_size(c.oid)) AS total_size, c.reltuples::bigint AS approx_rows
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = :'schema' AND c.relkind = 'r'
  AND c.relname IN ('household', 'household_member', 'individual', 'individual_address', 'address')
ORDER BY pg_total_relation_size(c.oid) DESC;
