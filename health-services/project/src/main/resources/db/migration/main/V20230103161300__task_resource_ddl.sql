CREATE TABLE TASK_RESOURCE
(
    id                    character varying(64),
    tenantId              character varying(1000),
    productVariantId      character varying(64),
    taskId                character varying(64),
    quantity              bigint,
    isDelivered           boolean,
    reasonIfNotDelivered  character varying(1000),
    createdBy             character varying(64),
    createdTime           bigint,
    lastModifiedBy        character varying(64),
    lastModifiedTime      bigint,
    isDeleted             boolean,
    CONSTRAINT uk_task_resource_id PRIMARY KEY (id)
);