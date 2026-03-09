CREATE TABLE IF NOT EXISTS scheduled_notification
(
    id                   character varying(64) PRIMARY KEY,
    clientReferenceId    character varying(64),
    tenantId             character varying(64) NOT NULL,

    -- Trigger context
    eventType            character varying(64) NOT NULL,
    entityId             character varying(64) NOT NULL,
    entityType           character varying(64) NOT NULL,

    -- Scheduling
    scheduledAt          date NOT NULL,
    createdAt            date NOT NULL,

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

    -- GenericRepository contract fields
    isDeleted            boolean DEFAULT false,
    rowVersion           integer DEFAULT 1,

    -- Audit fields
    createdBy            character varying(64),
    createdTime          bigint,
    lastModifiedBy       character varying(64),
    lastModifiedTime     bigint
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

-- Index for clientReferenceId (used by GenericRepository cache)
CREATE INDEX IF NOT EXISTS idx_scheduled_notification_client_ref_id
    ON scheduled_notification (clientReferenceId);
