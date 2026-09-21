-- Create index to allow search query perform better
CREATE INDEX IF NOT EXISTS idx_id_pool_status_tenant_id_id
    ON id_pool (status, tenantId, id);
