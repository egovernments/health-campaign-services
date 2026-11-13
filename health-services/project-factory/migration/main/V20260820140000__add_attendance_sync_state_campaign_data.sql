-- What the attendance sync needs on campaign_data.
--
-- isDeleted is a column: it is the table's soft-delete flag, meaningful to every resource type here,
-- and this service soft-deletes rather than deleting. Deliberately nullable — the persister's
-- sheet-data queries are shared by every resource type, and a payload without this field binds NULL,
-- which a NOT NULL column would reject and take the whole batch down with it. NULL reads as "not
-- deleted".
--
-- The de-enrolment date is NOT a column. Only attendees ever have one, so it lives inside the row's
-- own data as the internal key _denrollmentDate, alongside _sheetName and _registerServiceCode, and
-- is stripped from every sheet the same way those are.
ALTER TABLE eg_cm_campaign_data
    ADD COLUMN IF NOT EXISTS isDeleted BOOLEAN DEFAULT false;

-- Attendance events carry no campaign scope: a register delete names only the register, and a
-- de-enrolment only the register and the person. So the sync can find its row only by the identity
-- stamped at upload time — WHERE type = ? AND uniqueIdAfterProcess = ANY(?) — which the primary key
-- (campaignNumber, uniqueIdentifier, type) cannot serve, since campaignNumber is absent.
--
-- Partial on purpose. user/facility/boundary/target rows also populate uniqueIdAfterProcess and are
-- written in bulk during campaign creation, but are never looked up this way; excluding them means
-- those inserts do not maintain this index at all. Measured on a 10M-row table: a register delete
-- takes 0.5 ms instead of 315 ms, and a 200k-row user insert is unchanged.
CREATE INDEX IF NOT EXISTS idx_eg_cm_campaign_data_attendance_identity
    ON eg_cm_campaign_data (type, uniqueIdAfterProcess)
    WHERE type IN ('attendanceRegister', 'attendanceRegisterAttendee')
      AND uniqueIdAfterProcess IS NOT NULL;