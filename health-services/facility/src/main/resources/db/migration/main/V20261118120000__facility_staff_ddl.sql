CREATE TABLE IF NOT EXISTS FACILITY_STAFF
(
    id                character varying(64),
    tenantId          character varying(1000),
    facilityId        character varying(64) NOT NULL,
    staffId           character varying(64) NOT NULL,
    additionalDetails jsonb,
    createdBy         character varying(64),
    lastModifiedBy    character varying(64),
    createdTime       bigint,
    lastModifiedTime  bigint,
    rowVersion        bigint,
    isDeleted         boolean DEFAULT false,
    CONSTRAINT pk_facility_staff_id PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_facility_staff_active ON FACILITY_STAFF (tenantId, facilityId, staffId) WHERE isDeleted = false;
CREATE INDEX IF NOT EXISTS idx_facility_staff_staffId ON FACILITY_STAFF (staffId);
CREATE INDEX IF NOT EXISTS idx_facility_staff_facilityId ON FACILITY_STAFF (facilityId);
