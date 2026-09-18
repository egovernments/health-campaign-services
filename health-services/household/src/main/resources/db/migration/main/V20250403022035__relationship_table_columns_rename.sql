DROP INDEX IF EXISTS idx_hmr_householdMemberId;
DROP INDEX IF EXISTS idx_hmr_householdMemberClientReferenceId;


ALTER TABLE HOUSEHOLD_MEMBER_RELATIONSHIP RENAME
    COLUMN householdMemberId TO selfId;
ALTER TABLE HOUSEHOLD_MEMBER_RELATIONSHIP RENAME
    COLUMN householdMemberClientReferenceId TO selfClientReferenceId;


CREATE INDEX IF NOT EXISTS idx_hmr_selfId
    ON  HOUSEHOLD_MEMBER_RELATIONSHIP (selfId);
CREATE INDEX IF NOT EXISTS idx_hmr_selfClientReferenceId
    ON HOUSEHOLD_MEMBER_RELATIONSHIP (selfClientReferenceId);
