CREATE INDEX IF NOT EXISTS idx_id ON HOUSEHOLD_MEMBER (id);
CREATE INDEX IF NOT EXISTS idx_householdId on  HOUSEHOLD_MEMBER (householdId);
CREATE INDEX IF NOT EXISTS idx_householdClientReferenceId ON HOUSEHOLD_MEMBER (householdClientReferenceId);
CREATE INDEX IF NOT EXISTS idx_individualId ON HOUSEHOLD_MEMBER (individualId);
CREATE INDEX IF NOT EXISTS idx_individualClientReferenceId ON HOUSEHOLD_MEMBER (individualClientReferenceId);
CREATE INDEX IF NOT EXISTS idx_isHeadOfHousehold ON HOUSEHOLD_MEMBER (isHeadOfHousehold);
CREATE INDEX IF NOT EXISTS idx_tenantId ON HOUSEHOLD_MEMBER (tenantId);