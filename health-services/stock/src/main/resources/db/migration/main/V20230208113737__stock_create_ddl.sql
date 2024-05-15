CREATE TABLE STOCK
(
    id                   character varying(64),
    clientReferenceId    character varying(64),
    tenantId             character varying(1000),
    facilityId           character varying(64),
    productVariantId     character varying(64),
    quantity             bigint,
    referenceId          character varying(200),
    referenceIdType      character varying(100),
    transactionType      character varying(100),
    transactionReason    character varying(100),
    transactingPartyId   character varying(64),
    transactingPartyType character varying(100),
    additionalDetails    jsonb,
    createdBy            character varying(64),
    createdTime          bigint,
    lastModifiedBy       character varying(64),
    lastModifiedTime     bigint,
    rowVersion           bigint,
    isDeleted            boolean,
    CONSTRAINT uk_stock_id PRIMARY KEY (id),
    CONSTRAINT uk_stock_clientReferenceId UNIQUE (clientReferenceId)
);

CREATE TABLE STOCK_RECONCILIATION_LOG
(
    id                       character varying(64),
    clientReferenceId        character varying(64),
    tenantId                 character varying(1000),
    facilityId               character varying(64),
    dateOfReconciliation     bigint,
    calculatedCount          int,
    physicalRecordedCount    int,
    commentsOnReconciliation character varying(1000),
    createdBy                character varying(64),
    createdTime              bigint,
    lastModifiedBy           character varying(64),
    lastModifiedTime         bigint,
    additionalFields         jsonb,
    rowVersion               bigint,
    isDeleted                boolean,
    CONSTRAINT uk_stock_reconciliation_id PRIMARY KEY (id),
    CONSTRAINT uk_stock_reconciliation_clientReferenceId UNIQUE (clientReferenceId)
);