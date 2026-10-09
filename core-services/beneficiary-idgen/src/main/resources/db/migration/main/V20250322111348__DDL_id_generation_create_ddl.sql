CREATE TABLE IF NOT EXISTS id_pool (
    id                character varying(255),
    status            character varying(255) DEFAULT 'UNASSIGNED',
    tenantId          character varying(1000),
    additionalFields  jsonb,
    createdBy         character varying(64),
    lastModifiedBy    character varying(64),
    createdTime       bigint,
    lastModifiedTime  bigint,
    rowVersion        bigint,
    CONSTRAINT uk_id_pool_id PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_status ON id_pool(status);
CREATE INDEX IF NOT EXISTS idx_status_tenant ON id_pool(status, tenantId);
