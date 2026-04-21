CREATE UNIQUE INDEX IF NOT EXISTS uq_scheduled_notification_dedupe
    ON scheduled_notification (tenantId, entityType, entityId, eventType, templateCode, recipientId, scheduledAt)
    WHERE isDeleted = false;
