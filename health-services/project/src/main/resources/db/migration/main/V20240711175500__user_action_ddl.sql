CREATE TABLE IF NOT EXISTS USER_ACTION (
id                          character varying(64),
clientReferenceId           character varying(64),
tenantId                    character varying(1000) not null,
projectId                   character varying(64) not null,
latitude                    double precision not null,
longitude                   double precision not null,
locationAccuracy            INTEGER not null,
boundaryCode                CHARACTER VARYING(256) not null,
action                      CHARACTER VARYING(256) not null,
beneficiaryTag              CHARACTER VARYING(64),
resourceTag                 CHARACTER VARYING(64),
status                      character varying(1000),
additionalDetails           jsonb,
createdBy                   character varying(64) not null,
createdTime                 bigint not null,
lastModifiedBy              character varying(64) not null,
lastModifiedTime            bigint not null,
clientCreatedTime           bigint,
clientLastModifiedTime 	    bigint,
clientCreatedBy             character varying(64),
clientLastModifiedBy        character varying(64),
rowVersion                  bigint,
    CONSTRAINT pk_user_action_id PRIMARY KEY (id),
    CONSTRAINT uk_user_action_clientReference_id UNIQUE (clientReferenceId)
);

CREATE INDEX IF NOT EXISTS idx_user_action_projectId_clientCreatedBy ON USER_ACTION (projectId, clientCreatedBy);
