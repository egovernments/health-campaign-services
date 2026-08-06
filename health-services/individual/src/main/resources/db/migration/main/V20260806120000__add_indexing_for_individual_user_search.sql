CREATE INDEX IF NOT EXISTS idx_individual_useruuid_created ON INDIVIDUAL (userUuid, createdtime DESC);
CREATE INDEX IF NOT EXISTS idx_individual_username_tenant_type_deleted ON INDIVIDUAL (username, tenantId, type, isDeleted);
CREATE INDEX IF NOT EXISTS idx_individual_mobilenumber_created ON INDIVIDUAL (mobileNumber, createdtime DESC);
