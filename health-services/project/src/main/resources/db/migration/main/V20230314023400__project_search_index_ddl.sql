CREATE INDEX idx_project_tenantId ON project (tenantId);
CREATE INDEX idx_project_startDate ON project (startDate);
CREATE INDEX idx_project_endDate ON project (endDate);
CREATE INDEX idx_project_isTaskEnabled ON project (isTaskEnabled);
CREATE INDEX idx_project_parent ON project (parent);
CREATE INDEX idx_project_projectTypeId ON project (projectTypeId);
CREATE INDEX idx_project_projectSubType ON project (projectSubType);
CREATE INDEX idx_project_department ON project (department);
CREATE INDEX idx_project_referenceId ON project (referenceId);

CREATE INDEX IF NOT EXISTS idx_project_boundary ON PROJECT_ADDRESS (boundary);