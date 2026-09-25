-- Which individual is on which team, and the history of how that changed. Written only by
-- egov-persister, off save-individual-team-code-mapping-topic (ends the individual's other live
-- memberships, then records the new one) and update-individual-team-code-mapping-topic (removal,
-- which only ends them).
--
-- There is no unique constraint on (tenantId, teamCode, individualId) on purpose. Assigning a code
-- an individual previously held inserts a fresh ASSIGNED row beside the old UNASSIGNED one, so a
-- pair can legitimately repeat and the full join/leave history survives. At most one row per
-- individual is ASSIGNED at a time, maintained by the persister's sweep.
CREATE TABLE IF NOT EXISTS INDIVIDUAL_TEAM_CODE_MAPPING
(
    id                character varying(64)   NOT NULL,
    tenantId          character varying(1000) NOT NULL,
    teamCode          character varying(64)   NOT NULL,
    individualId      character varying(64)   NOT NULL,
    userUuid          character varying(64),
    status            character varying(64)   NOT NULL,
    assignedBy        character varying(64),
    assignedTime      bigint,
    unassignedBy      character varying(64),
    unassignedTime    bigint,
    createdBy         character varying(64),
    lastModifiedBy    character varying(64),
    createdTime       bigint,
    lastModifiedTime  bigint,
    rowVersion        bigint,
    isDeleted         boolean DEFAULT false,
    CONSTRAINT pk_individual_team_code_mapping PRIMARY KEY (id)
);

-- serves the derived status/assignedCount on /team/v1/_search, and the member list
CREATE INDEX IF NOT EXISTS idx_individual_team_code_mapping_code
    ON INDIVIDUAL_TEAM_CODE_MAPPING (tenantId, teamCode, status);

-- serves the persister sweep, which ends an individual's other live memberships
CREATE INDEX IF NOT EXISTS idx_individual_team_code_mapping_individual
    ON INDIVIDUAL_TEAM_CODE_MAPPING (tenantId, individualId, status);
