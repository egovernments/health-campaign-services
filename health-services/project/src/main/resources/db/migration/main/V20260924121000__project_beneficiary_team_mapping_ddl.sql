-- Append only history of which team a beneficiary was attributed to. One row per change:
-- rows are never updated, so the sequence of rows for a beneficiary IS the history.
-- relocationReason is null for a plain assignment and carries the operator's reason for a
-- relocation. Written exclusively by egov-persister off save-project-beneficiary-team-mapping-topic.
CREATE TABLE IF NOT EXISTS PROJECT_BENEFICIARY_TEAM_MAPPING
(
    id                                  character varying(64)   NOT NULL,
    tenantId                            character varying(1000) NOT NULL,
    projectBeneficiaryId                character varying(64),
    projectBeneficiaryClientReferenceId character varying(64),
    projectId                           character varying(64),
    teamCode                            character varying(64),
    previousTeamCode                    character varying(64),
    relocationReason                    character varying(1000),
    createdBy                           character varying(64),
    lastModifiedBy                      character varying(64),
    createdTime                         bigint,
    lastModifiedTime                    bigint,
    rowVersion                          bigint,
    isDeleted                           boolean DEFAULT false,
    CONSTRAINT pk_project_beneficiary_team_mapping PRIMARY KEY (id)
);

-- createdTime DESC so "the latest mapping for this beneficiary" is an index-only lookup
CREATE INDEX IF NOT EXISTS idx_pb_team_mapping_beneficiary
    ON PROJECT_BENEFICIARY_TEAM_MAPPING (tenantId, projectBeneficiaryId, createdTime DESC);

CREATE INDEX IF NOT EXISTS idx_pb_team_mapping_teamcode
    ON PROJECT_BENEFICIARY_TEAM_MAPPING (tenantId, teamCode, createdTime DESC);
