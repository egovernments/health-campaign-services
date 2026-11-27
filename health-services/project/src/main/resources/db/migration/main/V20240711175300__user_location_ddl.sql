CREATE TABLE IF NOT EXISTS USER_LOCATION (
    id                                  CHARACTER VARYING(64),
    clientReferenceId                   CHARACTER VARYING(64),
    tenantId                            CHARACTER VARYING(1000) NOT NULL,
    projectId                           CHARACTER VARYING(64) NOT NULL,
    latitude                            DOUBLE PRECISION NOT NULL,
    longitude                           DOUBLE PRECISION NOT NULL,
    locationAccuracy                    INTEGER NOT NULL,
    boundaryCode                        CHARACTER VARYING(256) NOT NULL,
    action                              CHARACTER VARYING(256),
    createdBy                           CHARACTER VARYING(64) NOT NULL,
    createdTime                         BIGINT NOT NULL,
    lastModifiedBy                      CHARACTER VARYING(64) NOT NULL,
    lastModifiedTime                    BIGINT  NOT NULL,
    clientCreatedTime                   BIGINT,
    clientLastModifiedTime              BIGINT,
    clientCreatedBy                     CHARACTER VARYING(64),
    clientLastModifiedBy                CHARACTER VARYING(64),
    additionalDetails                   jsonb,
    CONSTRAINT pk_user_location PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_user_location_clientCreatedBy ON USER_LOCATION (clientCreatedBy);
