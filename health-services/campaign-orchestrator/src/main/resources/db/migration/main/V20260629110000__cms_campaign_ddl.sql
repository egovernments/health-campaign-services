CREATE TABLE IF NOT EXISTS cms_campaign (
    id                  VARCHAR(64)  PRIMARY KEY,
    campaign_number     VARCHAR(64)  NOT NULL UNIQUE,
    tenant_id           VARCHAR(64)  NOT NULL,
    project_type        VARCHAR(64)  NOT NULL,
    campaign_name       VARCHAR(256) NOT NULL,
    hierarchy_type      VARCHAR(64)  NOT NULL,
    start_date          BIGINT       NOT NULL,
    end_date            BIGINT       NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    action              VARCHAR(32)  NOT NULL,
    boundaries          JSONB,
    resources           JSONB,
    delivery_rules      JSONB,
    additional_details  JSONB,
    boundary_code       VARCHAR(64),
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by          VARCHAR(64),
    last_modified_by    VARCHAR(64),
    created_time        BIGINT,
    last_modified_time  BIGINT,
    row_version         INTEGER      NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_cms_campaign_tenant_status
    ON cms_campaign (tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_cms_campaign_number
    ON cms_campaign (campaign_number);

CREATE INDEX IF NOT EXISTS idx_cms_campaign_tenant_deleted
    ON cms_campaign (tenant_id, is_deleted);

CREATE TABLE IF NOT EXISTS cms_campaign_resource (
    id                 VARCHAR(64)  PRIMARY KEY,
    campaign_id        VARCHAR(64)  NOT NULL REFERENCES cms_campaign(id),
    tenant_id          VARCHAR(64)  NOT NULL,
    type               VARCHAR(64)  NOT NULL,
    file_store_id      VARCHAR(128),
    filename           VARCHAR(256),
    status             VARCHAR(32)  NOT NULL,
    is_deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_time       BIGINT,
    last_modified_time BIGINT
);

CREATE INDEX IF NOT EXISTS idx_cms_campaign_resource_campaign_type
    ON cms_campaign_resource (campaign_id, type, status);

CREATE TABLE IF NOT EXISTS cms_campaign_row_data (
    id                      VARCHAR(64) PRIMARY KEY,
    campaign_id             VARCHAR(64) NOT NULL,
    campaign_number         VARCHAR(64) NOT NULL,
    tenant_id               VARCHAR(64) NOT NULL,
    type                    VARCHAR(32) NOT NULL,
    row_index               INT         NOT NULL,
    boundary_code           VARCHAR(64),
    status                  VARCHAR(32) NOT NULL,
    unique_id_after_process VARCHAR(64),
    data                    JSONB,
    is_deleted              BOOLEAN     NOT NULL DEFAULT FALSE,
    created_time            BIGINT,
    last_modified_time      BIGINT
);

CREATE INDEX IF NOT EXISTS idx_cms_row_data_campaign_type_status
    ON cms_campaign_row_data (campaign_id, type, status);

CREATE INDEX IF NOT EXISTS idx_cms_row_data_campaign_number
    ON cms_campaign_row_data (campaign_number, type);

CREATE TABLE IF NOT EXISTS cms_campaign_row_error (
    id            VARCHAR(64) PRIMARY KEY,
    campaign_id   VARCHAR(64) NOT NULL,
    tenant_id     VARCHAR(64) NOT NULL,
    row_index     INT         NOT NULL,
    type          VARCHAR(32),
    column_name   VARCHAR(128),
    error_code    VARCHAR(64),
    error_message TEXT,
    created_time  BIGINT
);

CREATE INDEX IF NOT EXISTS idx_cms_row_error_campaign
    ON cms_campaign_row_error (campaign_id);

CREATE TABLE IF NOT EXISTS cms_process (
    id                 VARCHAR(64) PRIMARY KEY,
    campaign_id        VARCHAR(64) NOT NULL,
    tenant_id          VARCHAR(64) NOT NULL,
    process_name       VARCHAR(64) NOT NULL,
    type               VARCHAR(32),
    status             VARCHAR(32) NOT NULL,
    is_deleted         BOOLEAN     NOT NULL DEFAULT FALSE,
    created_time       BIGINT,
    last_modified_time BIGINT
);

CREATE INDEX IF NOT EXISTS idx_cms_process_campaign_status
    ON cms_process (campaign_id, status);
