CREATE TABLE HOUSEHOLD
(
    id                character varying(64),
    tenantId          character varying(1000),
    clientReferenceId character varying(1000),
    numberOfMembers   integer,
    addressId         character varying(1000),
    additionalDetails jsonb,
    createdBy         character varying(64),
    lastModifiedBy    character varying(64),
    createdTime       bigint,
    lastModifiedTime  bigint,
    rowVersion        bigint,
    isDeleted         boolean,
    CONSTRAINT uk_household_id PRIMARY KEY (id),
    CONSTRAINT uk_household_client_reference_id UNIQUE (clientReferenceId)
);