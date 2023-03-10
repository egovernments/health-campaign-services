CREATE INDEX
    idx_id_projectId_projectBeneficiaryId_clientReferenceId_projectBeneficiaryClientReferenceId
    _plannedStartDate_plannedEndDate_actualStartDate_actualEndDate_createdBy_status
    ON PROJECT_TASK (
    id,
    projectId,
    projectBeneficiaryId,
    clientReferenceId,
    projectBeneficiaryClientReferenceId,
    plannedStartDate,
    plannedEndDate,
    actualStartDate,
    actualEndDate,
    createdBy,
    status);
CREATE INDEX IF NOT EXISTS idx_localityCode ON ADDRESS (localityCode);