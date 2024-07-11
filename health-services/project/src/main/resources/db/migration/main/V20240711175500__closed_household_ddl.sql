CREATE TABLE IF NOT EXISTS CLOSED_HOUSEHOLD (
	id                                  character varying(64),
    clientReferenceId                   character varying(64),
    tenantId                            character varying(1000),
    projectId                           character varying(64),
    addressId                           character varying(1000),
    status                              character varying(1000),
    additionalDetails                   jsonb,
    createdBy                           character varying(64),
    createdTime                         bigint,
    lastModifiedBy                      character varying(64),
    lastModifiedTime                    bigint,
    clientCreatedTime 				    bigint,
    clientLastModifiedTime 		        bigint,
    clientCreatedBy                     character varying(64),
    clientLastModifiedBy                character varying(64),
    rowVersion                          bigint,
    isDeleted                           boolean,
    CONSTRAINT uk_closed_household_id PRIMARY KEY (id),
    CONSTRAINT uk_closed_household_clientReference_id UNIQUE (clientReferenceId)
);

CREATE INDEX IF NOT EXISTS idx_closed_household_projectId ON CLOSED_HOUSEHOLD (projectId);
CREATE INDEX IF NOT EXISTS idx_closed_household_clientReferenceId ON CLOSED_HOUSEHOLD (clientReferenceId);
CREATE INDEX IF NOT EXISTS idx_closed_household_createdBy ON CLOSED_HOUSEHOLD (createdBy);
CREATE INDEX IF NOT EXISTS idx_closed_household_clientCreatedBy ON CLOSED_HOUSEHOLD (clientCreatedBy);
CREATE INDEX IF NOT EXISTS idx_closed_household_status ON CLOSED_HOUSEHOLD (status);


