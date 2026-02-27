CREATE TABLE IF NOT EXISTS scheduled_notification
(
    id                   character varying(64) PRIMARY KEY,
    tenantId             character varying(64) NOT NULL,

    -- Trigger context
    eventType            character varying(64) NOT NULL,
    entityId             character varying(64) NOT NULL,
    entityType           character varying(64) NOT NULL,

    -- Scheduling
    scheduledAt          bigint NOT NULL,
    createdAt            bigint NOT NULL,

    -- Notification details
    templateCode         character varying(128) NOT NULL,
    recipientType        character varying(64) NOT NULL,
    recipientId          character varying(64) NOT NULL,
    mobileNumber         character varying(20),

    -- Message context (JSON with placeholder data)
    contextData          jsonb NOT NULL,

    -- Status tracking
    status               character varying(32) NOT NULL,
    attempts             integer DEFAULT 0,
    lastAttemptAt        bigint,
    errorMessage         text,

    -- Audit fields
    createdBy            character varying(64),
    lastModifiedBy       character varying(64),
    lastModifiedTime     bigint,

    CONSTRAINT uk_scheduled_notification_id PRIMARY KEY (id)
);

-- Index for scheduled time and status queries (main query for picking pending notifications)
CREATE INDEX IF NOT EXISTS idx_scheduled_notification_scheduled_at_status
    ON scheduled_notification (scheduledAt, status);

-- Index for tenant-based queries with status filter
CREATE INDEX IF NOT EXISTS idx_scheduled_notification_tenant_status
    ON scheduled_notification (tenantId, status);

-- Index for entity-based queries (find notifications by triggering entity)
CREATE INDEX IF NOT EXISTS idx_scheduled_notification_entity
    ON scheduled_notification (entityType, entityId);

-- Index for tenant queries
CREATE INDEX IF NOT EXISTS idx_scheduled_notification_tenant
    ON scheduled_notification (tenantId);
