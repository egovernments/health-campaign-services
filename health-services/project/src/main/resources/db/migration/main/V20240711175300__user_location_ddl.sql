CREATE TABLE IF NOT EXISTS USER_LOCATION (
    id                                  CHARACTER VARYING(64),
    clientReferenceId                   CHARACTER VARYING(64),
    tenantId                            CHARACTER VARYING(1000),
    projectId                           character varying(64),
    latitude                            DOUBLE PRECISION,
    longitude                           DOUBLE PRECISION,
    locationAccuracy                    INTEGER,
    boundaryCode                        CHARACTER VARYING(256),
    action                              CHARACTER VARYING(256),
    createdBy                           CHARACTER VARYING(64),
    createdTime                         BIGINT,
    lastModifiedBy                      CHARACTER VARYING(64),
    lastModifiedTime                    BIGINT,
    clientCreatedTime                   BIGINT,
    clientLastModifiedTime              BIGINT,
    clientCreatedBy                     CHARACTER VARYING(64),
    clientLastModifiedBy                CHARACTER VARYING(64),
    additionalDetails                   jsonb,
    CONSTRAINT pk_user_location PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_user_location_clientCreatedBy ON USER_LOCATION (clientCreatedBy);
