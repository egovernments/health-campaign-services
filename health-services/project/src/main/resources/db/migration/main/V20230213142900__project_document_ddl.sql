CREATE TABLE project_document
(
    id                character varying(64),
    projectId         character varying(64) NOT NULL,
    documentType      character varying(256),
    filestoreId       character varying(256) NOT NULL,
    documentUid       character varying(64),
    additionalDetails jsonb,
    status            character varying(64),
    createdBy         character varying(64)  NOT NULL,
    lastModifiedBy    character varying(64),
    createdTime       bigint,
    lastModifiedTime  bigint,
    CONSTRAINT uk_project_document_id PRIMARY KEY (id)
);