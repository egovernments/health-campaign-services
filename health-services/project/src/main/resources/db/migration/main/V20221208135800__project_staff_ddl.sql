CREATE TABLE PROJECT_STAFF
(
    id                character varying(64) NOT NULL PRIMARY KEY,
    tenantId          character varying(1000) NOT NULL,
    projectId 		  character varying(64) NOT NULL,
    staffId 		  character varying(255) NOT NULL,
    startDate         bigint,
    endDate           bigint,
    additionalDetails text,
    createdBy         character varying(64),
    lastModifiedBy    character varying(64),
    createdTime       bigint,
    lastModifiedTime  bigint,
    rowVersion        bigint,
    isDeleted         boolean
 );