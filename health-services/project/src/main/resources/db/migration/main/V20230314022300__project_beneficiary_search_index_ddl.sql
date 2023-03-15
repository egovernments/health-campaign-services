CREATE INDEX IF NOT EXISTS idx_project_beneficiary_tenantId ON PROJECT_BENEFICIARY (tenantId);
CREATE INDEX IF NOT EXISTS idx_project_beneficiary_projectId ON PROJECT_BENEFICIARY (projectId);
CREATE INDEX IF NOT EXISTS idx_project_beneficiary_beneficiaryId ON PROJECT_BENEFICIARY (beneficiaryId);
CREATE INDEX IF NOT EXISTS idx_project_beneficiary_clientReferenceId ON PROJECT_BENEFICIARY (clientReferenceId);
CREATE INDEX IF NOT EXISTS idx_project_beneficiary_dateOfRegistration ON PROJECT_BENEFICIARY (dateOfRegistration);
