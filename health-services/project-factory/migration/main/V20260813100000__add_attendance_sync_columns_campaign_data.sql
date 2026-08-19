-- Columns behind the attendance sync: the console keeps its own copy of what it created, and until
-- now nothing told it when a register was deleted or a person de-enrolled in the attendance service.

-- Set when a register is deleted in the attendance service, so it stops appearing in downloads.
-- Deliberately nullable: the persister's sheet-data queries are shared by every resource type, and
-- payloads without this field bind NULL, which a NOT NULL column would reject — aborting the whole
-- batch. NULL is read as "not deleted".
ALTER TABLE eg_cm_campaign_data
    ADD COLUMN IF NOT EXISTS isDeleted BOOLEAN DEFAULT false;

-- De-enrolment carries an effective date, so a future-dated removal is still active today. Stored
-- rather than applied on arrival, because the attendance event fires once and never again when the
-- date comes round — downloads compare it against the current date instead.
ALTER TABLE eg_cm_campaign_data
    ADD COLUMN IF NOT EXISTS denrollmentDate BIGINT;

-- Both attendance events identify their row by (type, uniqueIdAfterProcess) with no campaign scope,
-- which the primary key (campaignNumber, uniqueIdentifier, type) cannot serve.
--
-- Partial on purpose. user/facility/boundary rows also populate uniqueIdAfterProcess and are written
-- in bulk during campaign creation, but are never looked up this way; excluding them keeps those
-- inserts maintaining a single index, and shrinks the build to the attendance slice so it does not
-- block writes while it runs.
--
-- Renamed from idx_eg_cm_campaign_data_unique_id_after_process, the full-table version of this index:
-- CREATE INDEX IF NOT EXISTS matches on name alone, so the old name would have kept the full index.
-- Only dev ever ran that version; drop it there by hand.
CREATE INDEX IF NOT EXISTS idx_eg_cm_campaign_data_attendance_identity
    ON eg_cm_campaign_data (type, uniqueIdAfterProcess)
    WHERE type IN ('attendanceRegister', 'attendanceRegisterAttendee')
      AND uniqueIdAfterProcess IS NOT NULL;
