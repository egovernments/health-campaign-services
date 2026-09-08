CREATE INDEX IF NOT EXISTS idx_individual_iid_tenant_is_deleted ON INDIVIDUAL (individualId, tenantId, isDeleted);
CREATE INDEX IF NOT EXISTS idx_individual_createdtime ON INDIVIDUAL (createdtime);

CREATE INDEX IF NOT EXISTS idx_individual_skills_iid ON INDIVIDUAL_SKILL (individualId);
CREATE INDEX IF NOT EXISTS idx_individual_skills_iid_is_deleted ON INDIVIDUAL_SKILL (individualId, isDeleted);

