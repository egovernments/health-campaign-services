CREATE TABLE IF NOT EXISTS HOUSEHOLD_MEMBER_RELATIONSHIP
(
    id                                   character varying(64),
    clientReferenceId                    character varying(64),
    tenantId                             character varying(1000),
    householdMemberId                    character varying(64),
    householdMemberClientReferenceId     character varying(64),
    relativeId                           character varying(64),
    relativeClientReferenceId            character varying(64),
    relationshipType                     character varying(64),
    createdBy                            character varying(64),
    createdTime                          bigint,
    lastModifiedBy                       character varying(64),
    lastModifiedTime                     bigint,
    clientCreatedBy                      character varying(64),
    clientCreatedTime                    bigint,
    clientLastModifiedBy                 character varying(64),
    clientLastModifiedTime               bigint,
    rowVersion                           bigint,
    isDeleted                            boolean,
    CONSTRAINT uk_household_member_relationship_id PRIMARY KEY (id),
    CONSTRAINT uk_household_member_relationship_client_reference_id UNIQUE (clientReferenceId)
);

CREATE INDEX IF NOT EXISTS idx_hmr_householdMemberId
    ON  HOUSEHOLD_MEMBER_RELATIONSHIP (householdMemberId);
CREATE INDEX IF NOT EXISTS idx_hmr_householdMemberClientReferenceId
    ON HOUSEHOLD_MEMBER_RELATIONSHIP (householdMemberClientReferenceId);
