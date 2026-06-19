DROP INDEX IF EXISTS idx_hmr_selfId;
DROP INDEX IF EXISTS idx_hmr_selfClientReferenceId;

CREATE INDEX IF NOT EXISTS idx_householdMemberRelationship_selfId
    ON  HOUSEHOLD_MEMBER_RELATIONSHIP (selfId);
CREATE INDEX IF NOT EXISTS idx_householdMemberRelationship_selfClientReferenceId
    ON HOUSEHOLD_MEMBER_RELATIONSHIP (selfClientReferenceId);