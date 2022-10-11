CREATE TABLE sync_log
(
    id                 character varying(64),
    referenceId        character varying(64),
    referenceIdType    character varying(20),
    tenantId           character varying(64),
    fileStoreId        character varying(64),
    checksum           character varying(32),
    status             character varying(64),
    comment            text,
    totalCount         integer,
    successCount       integer,
    errorCount         integer,
    createdBy          character varying(64),
    lastModifiedBy     character varying(64),
    createdTime        bigint,
    lastModifiedTime   bigint,
    CONSTRAINT uk_sync_log_id PRIMARY KEY (id)
);