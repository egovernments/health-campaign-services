CREATE TABLE IF NOT EXISTS cms_saga_instance (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id         VARCHAR(64) NOT NULL,
    tenant_id           VARCHAR(64) NOT NULL,
    saga_type           VARCHAR(64) NOT NULL,
    status              VARCHAR(32) NOT NULL,
    current_step        VARCHAR(64),
    last_completed_step VARCHAR(64),
    retry_count         INT         NOT NULL DEFAULT 0,
    payload             JSONB,
    error_details       JSONB,
    started_at          BIGINT      NOT NULL,
    updated_at          BIGINT      NOT NULL,
    completed_at        BIGINT,
    version             INT         NOT NULL DEFAULT 0,
    CONSTRAINT uc_saga_campaign_type UNIQUE (campaign_id, saga_type)
);

CREATE INDEX IF NOT EXISTS idx_cms_saga_campaign
    ON cms_saga_instance (campaign_id);

CREATE INDEX IF NOT EXISTS idx_cms_saga_status_type
    ON cms_saga_instance (status, saga_type);

CREATE TABLE IF NOT EXISTS cms_saga_event (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    saga_id     UUID        NOT NULL REFERENCES cms_saga_instance(id),
    campaign_id VARCHAR(64) NOT NULL,
    step_name   VARCHAR(64) NOT NULL,
    event_type  VARCHAR(32) NOT NULL,
    payload     JSONB,
    occurred_at BIGINT      NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_cms_saga_event_saga
    ON cms_saga_event (saga_id);

CREATE INDEX IF NOT EXISTS idx_cms_saga_event_campaign
    ON cms_saga_event (campaign_id);

CREATE TABLE IF NOT EXISTS cms_saga_dead_letter (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    saga_id     UUID,
    campaign_id VARCHAR(64),
    tenant_id   VARCHAR(64),
    step_name   VARCHAR(64),
    error_type  VARCHAR(128),
    error_msg   TEXT,
    payload     JSONB,
    occurred_at BIGINT      NOT NULL,
    resolved    BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_cms_dlq_campaign
    ON cms_saga_dead_letter (campaign_id, resolved);
