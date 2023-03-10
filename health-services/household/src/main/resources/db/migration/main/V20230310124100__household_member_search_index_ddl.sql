CREATE INDEX IF NOT EXISTS
    idx_id_householdId_householdClientReferenceId_individualId_individualClientReferenceId_isHeadOfHousehold_tenantId
    ON
    HOUSEHOLD_MEMBER (
    id,
    householdId,
    householdClientReferenceId,
    individualId,
    individualClientReferenceId,
    isHeadOfHousehold,
    tenantId);