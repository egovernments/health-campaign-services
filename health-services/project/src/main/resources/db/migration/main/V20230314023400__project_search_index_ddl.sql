CREATE INDEX idx_id ON project (id);
CREATE INDEX idx_tenantId ON project (tenantId);
CREATE INDEX idx_startDate ON project (startDate);
CREATE INDEX idx_endDate ON project (endDate);
CREATE INDEX idx_isTaskEnabled ON project (isTaskEnabled);
CREATE INDEX idx_parent ON project (parent);
CREATE INDEX idx_projectTypeId ON project (projectTypeId);
CREATE INDEX idx_projectSubType ON project (projectSubType);
CREATE INDEX idx_department ON project (department);
CREATE INDEX idx_referenceId ON project (referenceId);

CREATE INDEX IF NOT EXISTS idx_localityCode ON ADDRESS (localityCode);