CREATE TABLE PROJECT_TASK
(
    id                                  character varying(64),
    clientReferenceId                   character varying(64),
    tenantId                            character varying(1000),
    projectId                           character varying(64),
    projectBeneficiaryId                character varying(64),
    projectBeneficiaryClientReferenceId character varying(64),
    plannedStartDate                    bigint,
    plannedEndDate                      bigint,
    actualStartDate                     bigint,
    actualEndDate                       bigint,
    addressId                           character varying(1000),
    status                              character varying(1000),
    additionalDetails                   jsonb,
    createdBy                           character varying(64),
    createdTime                         bigint,
    lastModifiedBy                      character varying(64),
    lastModifiedTime                    bigint,
    rowVersion                          bigint,
    isDeleted                           boolean,
    CONSTRAINT uk_project_task_id PRIMARY KEY (id),
    CONSTRAINT uk_task_clientReference_id UNIQUE (clientReferenceId)
);