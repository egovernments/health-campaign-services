CREATE TABLE project_target
(
    id               character varying(64),
    projectId        character varying(64) NOT NULL,
    beneficiaryType  character varying(64),
    totalNo          bigint,
    targetNo         bigint,
    isDeleted        boolean,
    createdBy        character varying(64) NOT NULL,
    lastModifiedBy   character varying(64),
    createdTime      bigint,
    lastModifiedTime bigint,
    CONSTRAINT uk_project_target_id PRIMARY KEY (id)
);