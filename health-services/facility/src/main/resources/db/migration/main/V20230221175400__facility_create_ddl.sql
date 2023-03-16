CREATE TABLE FACILITY
(
    id                   character varying(64),
    tenantId             character varying(1000),
    isPermanent          boolean,
    name                 character varying(2000),
    usage                character varying(200),
    storageCapacity      bigint,
    addressId            character varying(64),
    additionalDetails    jsonb,
    createdBy            character varying(64),
    createdTime          bigint,
    lastModifiedBy       character varying(64),
    lastModifiedTime     bigint,
    rowVersion           bigint,
    isDeleted            boolean,
    CONSTRAINT pk_facility_id PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS ADDRESS
(
    id                character varying(64),
    tenantId          character varying(1000),
    doorNo            character varying(64),
    latitude          double precision,
    longitude         double precision,
    locationAccuracy  int,
    type              character varying(64),
    addressLine1      character varying(256),
    addressLine2      character varying(256),
    landmark          character varying(256),
    city              character varying(256),
    pincode           character varying(64),
    buildingName      character varying(256),
    street            character varying(256),
    localityCode      character varying(256),
    CONSTRAINT uk_address_id PRIMARY KEY (id)
)