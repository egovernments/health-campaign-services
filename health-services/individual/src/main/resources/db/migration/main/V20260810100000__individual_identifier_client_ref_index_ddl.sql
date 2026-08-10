-- Restore an index on INDIVIDUAL_IDENTIFIER.individualClientReferenceId.
--
-- V20250508122000 renamed clientReferenceId -> individualClientReferenceId, then dropped the
-- unique constraint that had been backing it, and added a NEW clientReferenceId column carrying
-- its own UNIQUE. Net effect: the link column lost the index it had inherited from that
-- constraint, and nothing replaced it.
--
-- It is on a hot path. IndividualRepository.enrichIndividuals runs per individual on all three
-- read paths (findById, find, findByRadius) and, whenever the row carries both a server id and a
-- clientReferenceId, issues:
--   SELECT * FROM individual_identifier ii
--    WHERE (ii.individualId = :individualId OR ii.individualClientReferenceId = :clientReferenceId)
-- (with only individualId in the WHERE when clientReferenceId is absent). individualId is indexed;
-- this is the only unindexed half of that OR. The planner decides per query, but with no index
-- available on this side it has no seek option for it at all.
--
-- Presents as search/downsync slowdown at scale, never as an error — which is why it went unnoticed.
--
-- CONCURRENTLY is deliberate: individual_identifier grows with every registered individual, and a
-- plain CREATE INDEX takes a SHARE lock, which blocks INSERT/UPDATE/DELETE on the table for the
-- whole build (reads are unaffected). Flyway needs no script config for this — its PostgreSQL parser matches
-- ^(CREATE|DROP)( UNIQUE)? INDEX CONCURRENTLY and runs the migration outside a transaction by
-- itself. That auto-detection is also why every statement in this file must stay in the
-- CONCURRENTLY form: mixing in a plain statement makes Flyway reject the whole migration for
-- combining transactional and non-transactional statements.
--
-- The DROP is recovery, not cleanup. A CONCURRENTLY build that is interrupted leaves an INVALID
-- index behind, and CREATE INDEX CONCURRENTLY IF NOT EXISTS would then skip it — leaving an index
-- that exists, is never used by the planner, and looks present to anyone checking. Dropping first
-- makes a re-run (after the `flyway repair` that a failed non-transactional migration requires)
-- actually rebuild it. On a first run the DROP is a no-op.

DROP INDEX CONCURRENTLY IF EXISTS idx_individual_identifier_individualclientreferenceid;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_individual_identifier_individualclientreferenceid
    ON INDIVIDUAL_IDENTIFIER (individualClientReferenceId);
