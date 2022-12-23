CREATE TABLE PROJECT_STAFF
(
    id                character varying(64),
    tenantId          character varying(1000),
    projectId 		  character varying(64),
    staffId 		  character varying(64),
    startDate         bigint,
    endDate           bigint,
    additionalDetails jsonb,
    createdBy         character varying(64),
    lastModifiedBy    character varying(64),
    createdTime       bigint,
    lastModifiedTime  bigint,
    rowVersion        bigint,
    isDeleted         boolean,
    CONSTRAINT uk_project_staff_id PRIMARY KEY (id)
);