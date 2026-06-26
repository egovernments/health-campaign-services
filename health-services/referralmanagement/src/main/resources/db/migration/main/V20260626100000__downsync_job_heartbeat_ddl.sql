-- Heartbeat column for downsync_generation_job.
--
-- The owning pod writes lastHeartbeat every N seconds while it's processing
-- the job. A claim is only allowed when the heartbeat is stale (older than 3×
-- the interval) or NULL. This defeats the staggered-pod claim race that the
-- rowVersion optimistic lock alone could not — a later-starting pod sees a
-- fresh heartbeat and skips, instead of winning the claim on the bumped
-- rowVersion.
--
-- See JobHeartbeatScheduler for the bump cadence; see CLAIM_RESUME_JOB and
-- SWEEP_STALE_FILES_FOR_JOB in DownsyncGenerationJobRepository for the claim
-- + sweep flow that uses this column.

ALTER TABLE downsync_generation_job
    ADD COLUMN IF NOT EXISTS lastHeartbeat BIGINT;

-- Existing IN_PROGRESS rows have no recorded heartbeat — anything older than
-- the staleness threshold (or NULL) is claimable, so leaving them NULL means
-- the next pod startup will treat them as abandoned and reclaim them. That's
-- the desired behavior for any in-flight job at deploy time.
