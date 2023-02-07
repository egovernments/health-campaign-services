CREATE TABLE PRODUCT
(
    id                character varying(64),
    tenantId          character varying(1000),
    type              character varying(200),
    name              character varying(1000),
    manufacturer      character varying(1000),
    additionalDetails text,
    createdBy         character varying(64),
    lastModifiedBy    character varying(64),
    createdTime       bigint,
    lastModifiedTime  bigint,
    rowVersion        bigint,
    isDeleted         boolean,
    CONSTRAINT uk_product_id PRIMARY KEY (id)
);