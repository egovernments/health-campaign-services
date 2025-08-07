CREATE TABLE IF NOT EXISTS abha_individual_transaction
(
    id                character varying(64),
    individualId      character varying(64),
    transactionId     character varying(255),
    tenantId          character varying(1000),
    additionalDetails jsonb,
    createdBy         character varying(64),
    lastModifiedBy    character varying(64),
    createdTime       bigint,
    lastModifiedTime  bigint,
    rowVersion        bigint,
    isDeleted         boolean,
    CONSTRAINT pk_abha_individual_transaction_id PRIMARY KEY (id),
    CONSTRAINT uk_abha_individual_transaction_individual_id UNIQUE (individualId)
);
