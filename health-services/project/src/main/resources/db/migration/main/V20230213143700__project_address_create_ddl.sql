CREATE TABLE project_address
(
    id               character varying(64),
    tenantId         character varying(64) NOT NULL,
    projectId        character varying(64) NOT NULL,
    door_no          character varying(64),
    latitude         bigint,
    longitude        bigint,
    locationAccuracy bigint,
    type             character varying(64),
    addressLine1     character varying(256),
    addressLine2     character varying(256),
    landmark         character varying(256),
    city             character varying(256),
    pinCode          character varying(64),
    buildingName     character varying(256),
    street           character varying(256),
    locality         character varying(128),
    CONSTRAINT uk_project_address_id PRIMARY KEY (id)
);