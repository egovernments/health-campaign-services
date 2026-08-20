-- The de-enrolment date moves out of its own column and into the row's own data.
--
-- Only attendees ever have one, so it does not belong in the schema of a table that holds users,
-- facilities, boundaries and targets as well; it is now an internal key inside data
-- (_denrollmentDate), alongside the other _-prefixed fields the sheet paths already strip.
--
-- isDeleted stays a column on purpose: it is the table's general soft-delete flag, meaningful to any
-- resource type, and this service soft-deletes everywhere.
--
-- The lookup index stays too: the sync still finds its rows by (type, uniqueIdAfterProcess).

-- Carry over anything already recorded, so state collected before this change is not lost.
UPDATE eg_cm_campaign_data
SET data = jsonb_set(COALESCE(data, '{}'::jsonb), '{_denrollmentDate}', to_jsonb(denrollmentDate), true)
WHERE denrollmentDate IS NOT NULL;

ALTER TABLE eg_cm_campaign_data DROP COLUMN IF EXISTS denrollmentDate;
