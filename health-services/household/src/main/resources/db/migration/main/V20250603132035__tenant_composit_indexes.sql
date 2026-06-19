CREATE INDEX IF NOT EXISTS idx_householdMemberRelationship_selfId_tenantId
    ON  HOUSEHOLD_MEMBER_RELATIONSHIP (selfId, tenantId);
CREATE INDEX IF NOT EXISTS idx_householdMemberRelationship_selfClientReferenceId_tenantId
    ON HOUSEHOLD_MEMBER_RELATIONSHIP (selfClientReferenceId, tenantId);

CREATE INDEX IF NOT EXISTS idx_householdMember_clientReferenceId_tenantId
    ON HOUSEHOLD_MEMBER (clientReferenceId, tenantId);
CREATE INDEX IF NOT EXISTS idx_householdMember_id_tenantId
    ON HOUSEHOLD_MEMBER (id, tenantId);

CREATE INDEX IF NOT EXISTS idx_household_clientReferenceId_tenantId
    ON HOUSEHOLD (clientReferenceId, tenantId);
CREATE INDEX IF NOT EXISTS idx_household_id_tenantId
    ON HOUSEHOLD (id, tenantId);

DROP INDEX IF EXISTS idx_householdMemberRelationship_selfId;
DROP INDEX IF EXISTS idx_householdMemberRelationship_selfClientReferenceId;
DROP INDEX IF EXISTS idx_household_id;
DROP INDEX IF EXISTS idx_household_clientReferenceId;
DROP INDEX IF EXISTS idx_household_member_id;
DROP INDEX IF EXISTS idx_household_member_clientReferenceId;
