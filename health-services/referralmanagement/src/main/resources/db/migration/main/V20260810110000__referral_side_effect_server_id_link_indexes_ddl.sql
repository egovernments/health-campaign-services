-- Index the server-id link columns on REFERRAL and SIDE_EFFECT that are actually searchable.
--
-- V20260810090000 indexed the clientReferenceId halves. These are the server-id counterparts, and
-- each one below is a documented search field whose value reaches the generated WHERE clause:
--   SIDE_EFFECT.taskId            -- SideEffectSearch.taskId, plus the literal
--                                 -- "SELECT * FROM side_effect ae WHERE ae.taskId IN (:taskIds)"
--                                 -- in SideEffectRepository
--   REFERRAL.projectBeneficiaryId -- ReferralSearch.projectBeneficiaryId; ReferralRepository
--                                 -- rewrites it to "r.projectBeneficiaryId IN (...)"
--   REFERRAL.sideEffectId         -- ReferralSearch.sideEffectId
--
-- SIDE_EFFECT.projectBeneficiaryId is deliberately NOT indexed here: SideEffectSearch exposes only
-- taskId and taskClientReferenceId, and no read predicate on that column exists anywhere in the
-- service. Indexing it would cost write throughput for no reachable query. Add it only if a lookup
-- is actually introduced.
--
-- These predicates are plain IN/AND filters, not an OR group -- so the justification here is simply
-- that a searchable filter column has no index, not any claim about OR handling.
--
-- CONCURRENTLY is deliberate. Both tables grow with campaign activity (one row per recorded adverse
-- event / referral), and a plain CREATE INDEX takes a SHARE lock, which blocks INSERT/UPDATE/DELETE
-- on the table for the whole build (reads are unaffected). Flyway needs no script config for this --
-- its PostgreSQL parser matches ^(CREATE|DROP)( UNIQUE)? INDEX CONCURRENTLY and runs the migration
-- outside a transaction by itself. That auto-detection is also why every statement in this file must
-- stay in the CONCURRENTLY form: mixing in a plain statement, or adding a DO-block post-check, makes
-- Flyway reject the whole migration for combining transactional and non-transactional statements.
-- Verifying pg_index.indisvalid therefore belongs in an ops check, not in this file.
--
-- The DROPs are recovery, not cleanup. A CONCURRENTLY build that is interrupted leaves an INVALID
-- index behind, and CREATE INDEX CONCURRENTLY IF NOT EXISTS would then skip it -- leaving an index
-- that exists, is never used by the planner, and looks present to anyone checking. Dropping first
-- makes a re-run (after the `flyway repair` that a failed non-transactional migration requires)
-- actually rebuild it. On a first run the DROPs are no-ops. Note the trade-off of keeping three
-- indexes in one migration, which matches V20260810090000 and the rest of this repo: a failure on
-- the last one re-runs all three.
--
-- V20260810090000 itself is left non-concurrent on purpose -- it is already pushed, and editing it
-- would change its Flyway checksum and break validation wherever it has already applied.

DROP INDEX CONCURRENTLY IF EXISTS idx_side_effect_taskid;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_side_effect_taskid
    ON SIDE_EFFECT (taskId);

DROP INDEX CONCURRENTLY IF EXISTS idx_referral_projectbeneficiaryid;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_referral_projectbeneficiaryid
    ON REFERRAL (projectBeneficiaryId);

DROP INDEX CONCURRENTLY IF EXISTS idx_referral_sideeffectid;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_referral_sideeffectid
    ON REFERRAL (sideEffectId);
