CREATE TABLE IF NOT EXISTS LOCATION_TRACKING (
    id                      CHARACTER VARYING(64),
    clientReferenceId       CHARACTER VARYING(64),
    tenantId                CHARACTER VARYING(1000),
    projectId               CHARACTER VARYING(64),
    addressId               CHARACTER VARYING(1000),
    status                  CHARACTER VARYING(1000),
    additionalDetails       JSONB,
    createdBy               CHARACTER VARYING(64),
    createdTime             BIGINT,
    lastModifiedBy          CHARACTER VARYING(64),
    lastModifiedTime        BIGINT,
    clientCreatedTime       BIGINT,
    clientLastModifiedTime  BIGINT,
    clientCreatedBy         CHARACTER VARYING(64),
    clientLastModifiedBy    CHARACTER VARYING(64),
    rowVersion              BIGINT,
    isDeleted               BOOLEAN,
    CONSTRAINT pk_location_tracking PRIMARY KEY (id),
    CONSTRAINT uq_location_tracking_clientReferenceId UNIQUE (clientReferenceId)
);

CREATE TABLE IF NOT EXISTS LOCATION_POINT (
    id                                  CHARACTER VARYING(64),
    locationTrackingId                  CHARACTER VARYING(64),
    locationTrackingClientReferenceId   CHARACTER VARYING(64),
    latitude                            DOUBLE PRECISION,
    longitude                           DOUBLE PRECISION,
    locationAccuracy                    INTEGER,
    createdBy                           CHARACTER VARYING(64),
    createdTime                         BIGINT,
    lastModifiedBy                      CHARACTER VARYING(64),
    lastModifiedTime                    BIGINT,
    clientCreatedTime                   BIGINT,
    clientLastModifiedTime              BIGINT,
    clientCreatedBy                     CHARACTER VARYING(64),
    clientLastModifiedBy                CHARACTER VARYING(64),
    CONSTRAINT pk_location_points PRIMARY KEY (id),
    CONSTRAINT fk_location_points FOREIGN KEY (locationTrackingId)
        REFERENCES LOCATION_TRACKING(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_location_tracking_projectId ON LOCATION_TRACKING (projectId);
CREATE INDEX IF NOT EXISTS idx_location_tracking_clientReferenceId ON LOCATION_TRACKING (clientReferenceId);
CREATE INDEX IF NOT EXISTS idx_location_tracking_clientCreatedBy ON LOCATION_TRACKING (clientCreatedBy);
CREATE INDEX IF NOT EXISTS idx_location_tracking_status ON LOCATION_TRACKING (status);

CREATE INDEX IF NOT EXISTS idx_location_points_locationTrackingId ON LOCATION_POINTS (locationTrackingId);
CREATE INDEX IF NOT EXISTS idx_location_points_clientCreatedBy ON LOCATION_POINTS (clientCreatedBy);
