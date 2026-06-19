CREATE TABLE IF NOT EXISTS id_transaction_log (
    id                bigserial NOT NULL,
    id_reference      character varying(255),
    user_uuid         character varying(255),
    device_uuid       character varying(255),
    tenantId          character varying(1000),
    status            character varying(255),
    device_info       jsonb,
    additionalFields  jsonb,
    createdBy         character varying(64),
    lastModifiedBy    character varying(64),
    createdTime       bigint,
    lastModifiedTime  bigint,
    rowVersion        bigint,
    CONSTRAINT uk_id_transaction_log_id PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_id_reference ON id_transaction_log (id_reference);
CREATE INDEX IF NOT EXISTS idx_user_uuid ON id_transaction_log (user_uuid);
CREATE INDEX IF NOT EXISTS idx_device_uuid ON id_transaction_log (device_uuid);
CREATE INDEX IF NOT EXISTS idx_device_info_gin ON id_transaction_log USING gin (device_info);
