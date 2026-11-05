-- Use CONCURRENTLY to avoid blocking writes on eg_cm_campaign_data during index build.
-- This statement must run outside a transaction block.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_eg_cm_campaign_data_campaign_type_status
ON eg_cm_campaign_data (campaignnumber, type, status);
