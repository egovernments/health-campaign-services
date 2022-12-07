CREATE TABLE PRODUCT_VARIANT
(
    id                character varying(64),
    tenantId          character varying(1000),
    productId         character varying(64),
    sku               character varying(1000),
    variation         character varying(1000),
    additionalDetails jsonb,
    createdBy         character varying(64),
    lastModifiedBy    character varying(64),
    createdTime       bigint,
    lastModifiedTime  bigint,
    rowVersion        bigint,
    isDeleted         boolean,
    CONSTRAINT uk_product_variant_id PRIMARY KEY (id)
);