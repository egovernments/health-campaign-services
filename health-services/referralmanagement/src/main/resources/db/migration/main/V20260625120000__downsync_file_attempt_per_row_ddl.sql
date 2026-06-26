-- Attempt-per-row model for downsync_locality_file.
--
-- Background: when a FAILED file was retried, the previous code UPDATEd the
-- terminal-state row in place, leaving stale endTime / failureReason from the
-- earlier attempt mixed with the new attempt's startTime. This produced
-- impossible-looking rows (status=IN_PROGRESS with a failureReason already set,
-- endTime before startTime) and silently destroyed the record of the prior
-- failure.
--
-- New model: each retry INSERTs a new row with attemptNumber += 1. Terminal-
-- state rows (SUCCESS / FAILED / SKIPPED / PARTIAL_SUCCESS) are immutable.
-- "Latest state" queries pick the highest attemptNumber per (localityRowId,
-- fileType). The history of every attempt is preserved as separate rows in
-- the same operational table — no separate audit table needed.

ALTER TABLE downsync_locality_file
    ADD COLUMN IF NOT EXISTS attemptNumber INT,
    ADD COLUMN IF NOT EXISTS createdTime   BIGINT;

-- Backfill: existing rows are attempt 1. Use startTime as a stand-in for
-- createdTime where available (it's set when the worker picks up the row);
-- otherwise leave NULL — old rows pre-date this change anyway.
UPDATE downsync_locality_file
SET attemptNumber = 1
WHERE attemptNumber IS NULL;

UPDATE downsync_locality_file
SET createdTime = startTime
WHERE createdTime IS NULL AND startTime IS NOT NULL;

-- Make attemptNumber NOT NULL going forward.
ALTER TABLE downsync_locality_file
    ALTER COLUMN attemptNumber SET NOT NULL,
    ALTER COLUMN attemptNumber SET DEFAULT 1;

-- Index supports "find latest attempt per (localityRowId, fileType)".
CREATE INDEX IF NOT EXISTS idx_dlf_loc_filetype_attempt
    ON downsync_locality_file (localityRowId, fileType, attemptNumber DESC);
