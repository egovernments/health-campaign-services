ALTER TABLE HOUSEHOLD ADD COLUMN IF NOT EXISTS householdType character varying(64);
UPDATE HOUSEHOLD SET householdType = 'FAMILY' WHERE householdType IS NULL;