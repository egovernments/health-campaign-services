CREATE TABLE INDIVIDUAL_ADDRESS
(
    individualId     character varying(64),
    addressId        character varying(64),
    createdBy        character varying(64),
    lastModifiedBy   character varying(64),
    createdTime      bigint,
    lastModifiedTime bigint,
    rowVersion       bigint,
    isDeleted        boolean
);