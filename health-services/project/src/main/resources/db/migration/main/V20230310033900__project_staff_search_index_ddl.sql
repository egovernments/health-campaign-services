CREATE INDEX idx_id_tenantId_staffId_projectId_startDate_endDate
    ON PROJECT_STAFF (id, tenantId, staffId, projectId, startDate, endDate);