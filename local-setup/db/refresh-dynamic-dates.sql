-- Run on every `docker compose up` by the `db-refresh-dates` one-shot service.
--
-- Purpose: shift any pre-seeded date-anchored rows (campaigns, plans, etc.) to be
-- "current" so testers always see active windows.
--
-- Each UPDATE is wrapped in a DO block with EXCEPTION handling so a missing table
-- (e.g. when the service hasn't been migrated yet) is a no-op, not a hard error.
--
-- Add a new DO block whenever a date-bearing seed table is introduced.

\set ON_ERROR_STOP off

-- ── Campaign details ────────────────────────────────────────────────────────
DO $$ BEGIN
  UPDATE eg_cm_campaign_details
     SET start_date = (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint,
         end_date   = (EXTRACT(EPOCH FROM NOW() + INTERVAL '90 days') * 1000)::bigint
   WHERE status IN ('drafted','created','started');
  RAISE NOTICE 'campaigns: % rows shifted', (SELECT COUNT(*) FROM eg_cm_campaign_details
                                              WHERE status IN ('drafted','created','started'));
EXCEPTION
  WHEN undefined_table THEN RAISE NOTICE 'eg_cm_campaign_details not present — skipping';
END $$;

-- ── Plan configurations (add columns as needed when the schema lands) ───────
-- DO $$ BEGIN
--   UPDATE plan_configuration
--      SET execution_plan_start_date = ...,
--          execution_plan_end_date   = ...;
-- EXCEPTION WHEN undefined_table THEN RAISE NOTICE 'plan_configuration not present — skipping';
-- END $$;

-- ── Add further date-shift blocks here ──────────────────────────────────────
