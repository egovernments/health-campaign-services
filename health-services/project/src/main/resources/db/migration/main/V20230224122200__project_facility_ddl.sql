CREATE TABLE PROJECT_FACILITY
(
    id                character varying(64),
    tenantId          character varying(1000),
    projectId 		  character varying(64),
    facilityId 		  character varying(64),
    additionalDetails jsonb,
    createdBy         character varying(64),
    lastModifiedBy    character varying(64),
    createdTime       bigint,
    lastModifiedTime  bigint,
    rowVersion        bigint,
    isDeleted         boolean,
    CONSTRAINT uk_project_facility_id PRIMARY KEY (id)
);