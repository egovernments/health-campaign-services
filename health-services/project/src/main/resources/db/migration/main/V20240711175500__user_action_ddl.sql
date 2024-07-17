CREATE TABLE IF NOT EXISTS USER_ACTION (
id                          character varying(64),
clientReferenceId           character varying(64),
tenantId                    character varying(1000),
projectId                   character varying(64),
latitude                    double precision,
longitude                   double precision,
locationAccuracy            INTEGER,
boundaryCode                CHARACTER VARYING(256),
action                      CHARACTER VARYING(256),
beneficiaryTag              CHARACTER VARYING(64),
resourceTag                 CHARACTER VARYING(64),
status                      character varying(1000),
additionalDetails           jsonb,
createdBy                   character varying(64),
createdTime                 bigint,
lastModifiedBy              character varying(64),
lastModifiedTime            bigint,
clientCreatedTime           bigint,
clientLastModifiedTime 	    bigint,
clientCreatedBy             character varying(64),
clientLastModifiedBy        character varying(64),
rowVersion                  bigint,
    CONSTRAINT pk_user_action_id PRIMARY KEY (id),
    CONSTRAINT uk_user_action_clientReference_id UNIQUE (clientReferenceId)
);

CREATE INDEX IF NOT EXISTS idx_user_action_projectId_clientCreatedBy ON USER_ACTION (projectId, clientCreatedBy);
