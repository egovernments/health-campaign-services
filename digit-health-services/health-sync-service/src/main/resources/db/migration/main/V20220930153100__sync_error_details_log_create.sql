CREATE TABLE sync_error_details_log
(
    id                 character varying(64),
    syncLogId          character varying(64),
    tenantId           character varying(64),
    recordId           character varying(64),
    recordIdType       character varying(64),
    errorCodes         text,
    errorMessages      text,
    createdBy          character varying(64),
    lastModifiedBy     character varying(64),
    createdTime        bigint,
    lastModifiedTime   bigint,
    CONSTRAINT uk_sync_error_details_log_id PRIMARY KEY (id)
);