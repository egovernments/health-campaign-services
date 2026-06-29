CREATE TABLE IF NOT EXISTS cms_idempotency_registry (
    idempotency_key VARCHAR(256) PRIMARY KEY,
    entity_type     VARCHAR(64)  NOT NULL,
    entity_id       VARCHAR(64)  NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    campaign_id     VARCHAR(64)  NOT NULL,
    created_at      BIGINT       NOT NULL,
    expires_at      BIGINT
);

CREATE INDEX IF NOT EXISTS idx_cms_idempotency_campaign_type
    ON cms_idempotency_registry (campaign_id, entity_type);

CREATE INDEX IF NOT EXISTS idx_cms_idempotency_expires
    ON cms_idempotency_registry (expires_at);

CREATE TABLE IF NOT EXISTS cms_mapping (
    id                 VARCHAR(64) PRIMARY KEY,
    campaign_id        VARCHAR(64) NOT NULL,
    campaign_number    VARCHAR(64) NOT NULL,
    tenant_id          VARCHAR(64) NOT NULL,
    type               VARCHAR(32) NOT NULL,
    parent_resource_id VARCHAR(64),
    project_id         VARCHAR(64),
    boundary_code      VARCHAR(64),
    direction          VARCHAR(32) NOT NULL,
    status             VARCHAR(32) NOT NULL,
    mapping_id         VARCHAR(64),
    retry_count        INT         NOT NULL DEFAULT 0,
    last_error         TEXT,
    generation         INT         NOT NULL DEFAULT 0,
    is_deleted         BOOLEAN     NOT NULL DEFAULT FALSE,
    created_time       BIGINT,
    last_modified_time BIGINT
);

CREATE INDEX IF NOT EXISTS idx_cms_mapping_campaign_type_status
    ON cms_mapping (campaign_id, type, status);

CREATE INDEX IF NOT EXISTS idx_cms_mapping_campaign_number
    ON cms_mapping (campaign_number);

CREATE TABLE IF NOT EXISTS cms_excel_job (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id    VARCHAR(64),
    tenant_id      VARCHAR(64) NOT NULL,
    job_type       VARCHAR(32) NOT NULL,
    status         VARCHAR(32) NOT NULL,
    file_store_id  VARCHAR(128),
    resource_type  VARCHAR(64),
    total_rows     INT,
    processed_rows INT         NOT NULL DEFAULT 0,
    error_count    INT         NOT NULL DEFAULT 0,
    created_at     BIGINT      NOT NULL,
    updated_at     BIGINT      NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_cms_excel_job_campaign_status
    ON cms_excel_job (campaign_id, job_type, status);

CREATE TABLE IF NOT EXISTS cms_excel_row_staging (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id       UUID        NOT NULL REFERENCES cms_excel_job(id),
    campaign_id  VARCHAR(64),
    tenant_id    VARCHAR(64) NOT NULL,
    row_index    INT         NOT NULL,
    row_data     JSONB,
    errors       JSONB,
    created_time BIGINT
);

CREATE INDEX IF NOT EXISTS idx_cms_excel_staging_job
    ON cms_excel_row_staging (job_id);

CREATE INDEX IF NOT EXISTS idx_cms_excel_staging_campaign
    ON cms_excel_row_staging (campaign_id);
