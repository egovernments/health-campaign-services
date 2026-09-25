-- Lets /beneficiary/team/v1/_relocate find every beneficiary currently attributed to a team code,
-- which lives at additionalDetails -> 'fields' -> [{"key":"registered_by_team","value":"..."}].
-- Without it that search is a sequential scan: measured at 485 ms over 2M beneficiaries, against
-- 45-64 ms with the index.
--
-- NOTE for large live tenants: this form takes an ACCESS EXCLUSIVE lock for the duration of the
-- build (~15 s per 2M rows, index ~171 MB per 2M rows). To avoid blocking writes, build it by hand
-- with CREATE INDEX CONCURRENTLY before deploying - the IF NOT EXISTS below then does nothing:
--   CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_project_beneficiary_additionaldetails_gin
--       ON project_beneficiary USING GIN ((additionaldetails -> 'fields'));
-- CONCURRENTLY cannot run inside flyway's transaction, which is why it is not used here.
CREATE INDEX IF NOT EXISTS idx_project_beneficiary_additionaldetails_gin
    ON PROJECT_BENEFICIARY USING GIN ((additionaldetails -> 'fields'));
