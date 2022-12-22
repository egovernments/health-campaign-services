CREATE TABLE project
(
    id                character varying(64),
    tenantId          character varying(1000),
    projectTypeId     character varying(64),
    addressId         character varying(64),
    startDate         bigint,
    endDate           bigint,
    isTaskEnabled     boolean,
    parent            character varying(64),
    projectHierarchy  text,
    additionalDetails jsonb,
    createdBy         character varying(64),
    createdTime       bigint,
    lastModifiedBy    character varying(64),
    lastModifiedTime  bigint,
    rowVersion        bigint,
    isDeleted         boolean,
    CONSTRAINT uk_project_id PRIMARY KEY (id)
);