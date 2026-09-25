-- Registry of minted team codes. Written only by egov-persister, off
-- save-individual-team-code-topic - nothing in the service writes this table directly.
--
-- Deliberately carries no status or assignedCount column. Both would be state derived from
-- INDIVIDUAL_TEAM_CODE_MAPPING, which is written asynchronously, and a counter kept in step with a
-- queue-behind table drifts. /team/v1/_search derives them at read time instead: a code reads as
-- ASSIGNED while anyone is on it, UNASSIGNED when nobody is, INACTIVE once isDeleted is set.
CREATE TABLE IF NOT EXISTS INDIVIDUAL_TEAM_CODE
(
    id                character varying(64)   NOT NULL,
    teamCode          character varying(64)   NOT NULL,
    tenantId          character varying(1000) NOT NULL,
    additionalDetails jsonb,
    createdBy         character varying(64),
    lastModifiedBy    character varying(64),
    createdTime       bigint,
    lastModifiedTime  bigint,
    rowVersion        bigint,
    isDeleted         boolean DEFAULT false,
    CONSTRAINT pk_individual_team_code PRIMARY KEY (id),
    CONSTRAINT uk_individual_team_code_tenant_code UNIQUE (tenantId, teamCode)
);
