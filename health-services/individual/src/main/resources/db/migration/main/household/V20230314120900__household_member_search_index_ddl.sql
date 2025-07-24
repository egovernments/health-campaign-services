CREATE INDEX IF NOT EXISTS idx_household_member_householdId on  HOUSEHOLD_MEMBER (householdId);
CREATE INDEX IF NOT EXISTS idx_household_member_householdClientReferenceId ON HOUSEHOLD_MEMBER (householdClientReferenceId);
CREATE INDEX IF NOT EXISTS idx_household_member_individualId ON HOUSEHOLD_MEMBER (individualId);
CREATE INDEX IF NOT EXISTS idx_household_member_individualClientReferenceId ON HOUSEHOLD_MEMBER (individualClientReferenceId);
CREATE INDEX IF NOT EXISTS idx_household_member_isHeadOfHousehold ON HOUSEHOLD_MEMBER (isHeadOfHousehold);
CREATE INDEX IF NOT EXISTS idx_household_member_tenantId ON HOUSEHOLD_MEMBER (tenantId);