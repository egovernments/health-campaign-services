CREATE INDEX idx_project_task_projectId ON PROJECT_TASK (projectId);
CREATE INDEX idx_project_task_projectBeneficiaryId ON PROJECT_TASK (projectBeneficiaryId);
CREATE INDEX idx_project_task_clientReferenceId ON PROJECT_TASK (clientReferenceId);
CREATE INDEX idx_project_task_projectBeneficiaryClientReferenceId ON PROJECT_TASK (projectBeneficiaryClientReferenceId);
CREATE INDEX idx_project_task_plannedStartDate ON PROJECT_TASK (plannedStartDate);
CREATE INDEX idx_project_task_plannedEndDate ON PROJECT_TASK (plannedEndDate);
CREATE INDEX idx_project_task_actualStartDate ON PROJECT_TASK (actualStartDate);
CREATE INDEX idx_project_task_actualEndDate ON PROJECT_TASK (actualEndDate);
CREATE INDEX idx_project_task_createdBy ON PROJECT_TASK (createdBy);
CREATE INDEX idx_project_task_status ON PROJECT_TASK (status);

CREATE INDEX IF NOT EXISTS idx_localityCode ON ADDRESS (localityCode);
