-- Enforce "at most one IN_PROGRESS job per tenant" at the database level.
--
-- The controller has two application-level gates today: an in-memory
-- jobRegistry (per pod) and a findInProgressJobByTenant() SELECT-then-INSERT
-- check. Neither is atomic across pods. Two concurrent /generate calls
-- load-balanced to different replicas can both pass the SELECT (which returns
-- 0 rows) before either commits its INSERT, producing two IN_PROGRESS jobs
-- for the same tenant. Those jobs then compete for the same S3 keyspace
-- (s3Key = tenantId/locality/fileType is deterministic) — the exact
-- multipart-race pattern the heartbeat+CAS design of downsync_generation_job
-- was meant to eliminate, just at a coarser granularity.
--
-- A partial unique index closes the race atomically at the DB layer:
--   * At most one row per tenant may have status = 'IN_PROGRESS'.
--   * COMPLETED / FAILED / PARTIAL_FAILURE rows are unconstrained (history is
--     preserved as before).
--   * A racing second INSERT raises SQLSTATE 23505; the controller catches
--     it and returns 409 with the currentJob details, matching the existing
--     conflict UX from the application-level gate.
--
-- Prerequisite: any pre-existing duplicates must be resolved before this
-- migration runs, e.g.:
--   SELECT tenantId, COUNT(*) FROM downsync_generation_job
--    WHERE status = 'IN_PROGRESS' GROUP BY tenantId HAVING COUNT(*) > 1;
-- Cancel the newer duplicates by updating them to 'FAILED' with a marker
-- reason before deploying this migration.

CREATE UNIQUE INDEX IF NOT EXISTS ux_downsync_generation_job_one_inprogress_per_tenant
    ON downsync_generation_job (tenantId)
    WHERE status = 'IN_PROGRESS';
