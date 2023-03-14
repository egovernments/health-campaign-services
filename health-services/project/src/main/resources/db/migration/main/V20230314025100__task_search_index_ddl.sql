CREATE INDEX idx_id ON PROJECT_TASK (id);
CREATE INDEX idx_projectId ON PROJECT_TASK (projectId);
CREATE INDEX idx_projectBeneficiaryId ON PROJECT_TASK (projectBeneficiaryId);
CREATE INDEX idx_clientReferenceId ON PROJECT_TASK (clientReferenceId);
CREATE INDEX idx_projectBeneficiaryClientReferenceId ON PROJECT_TASK (projectBeneficiaryClientReferenceId);
CREATE INDEX idx_plannedStartDate ON PROJECT_TASK (plannedStartDate);
CREATE INDEX idx_plannedEndDate ON PROJECT_TASK (plannedEndDate);
CREATE INDEX idx_actualStartDate ON PROJECT_TASK (actualStartDate);
CREATE INDEX idx_actualEndDate ON PROJECT_TASK (actualEndDate);
CREATE INDEX idx_createdBy ON PROJECT_TASK (createdBy);
CREATE INDEX idx_status ON PROJECT_TASK (status);

CREATE INDEX IF NOT EXISTS idx_localityCode ON ADDRESS (localityCode);
