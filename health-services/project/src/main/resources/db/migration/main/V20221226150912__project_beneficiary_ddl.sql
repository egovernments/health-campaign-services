CREATE TABLE IF NOT EXISTS PROJECT_BENEFICIARY
(
    id                                  character varying(64),
    tenantId                            character varying(1000),
    projectId                           character varying(64),
    beneficiaryId                       character varying(64),
    clientReferenceId                   character varying(64),
    beneficiaryClientReferenceId        character varying(64),
    createdBy                           character varying(64),
    lastModifiedBy                      character varying(64),
    dateOfRegistration                  bigint,
    additionalDetails                   jsonb,
    createdTime                         bigint,
    lastModifiedTime                    bigint,
    rowVersion                          bigint,
    isDeleted                           boolean,
    CONSTRAINT uk_project_beneficiary_id PRIMARY KEY (id),
    CONSTRAINT uk_project_beneficiary_client_reference_id UNIQUE (clientReferenceId)
);