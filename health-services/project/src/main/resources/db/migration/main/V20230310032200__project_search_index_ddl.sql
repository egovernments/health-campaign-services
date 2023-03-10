CREATE INDEX idx_id_tenantId_startDate_endDate_isTaskEnabled_parent_projectTypeId_projectSubType_department_referenceId
    ON project (id, tenantId, startDate, endDate, isTaskEnabled, parent, projectTypeId, projectSubType, department, referenceId);
CREATE INDEX IF NOT EXISTS idx_localityCode ON ADDRESS (localityCode);