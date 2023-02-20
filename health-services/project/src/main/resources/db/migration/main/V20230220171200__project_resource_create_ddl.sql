CREATE TABLE project_resource
(
    id                character varying(64),
    tenantId          character varying(1000),
    projectId         character varying(64),
    productVariantId  character varying(64),
    isBaseUnitVariant boolean,
    startDate         bigint,
    endDate           bigint,
    additionalDetails jsonb,
    createdBy         character varying(64),
    createdTime       bigint,
    lastModifiedBy    character varying(64),
    lastModifiedTime  bigint,
    rowVersion        bigint,
    isDeleted         boolean,
    CONSTRAINT uk_project_resource_id PRIMARY KEY (id)
);