-- Project search joins PROJECT_ADDRESS and reads PROJECT_TARGET / PROJECT_DOCUMENT by projectId; without these each search scans the whole table.
CREATE INDEX IF NOT EXISTS idx_project_address_projectId ON PROJECT_ADDRESS (projectId);
CREATE INDEX IF NOT EXISTS idx_project_target_projectId ON PROJECT_TARGET (projectId);
CREATE INDEX IF NOT EXISTS idx_project_document_projectId ON PROJECT_DOCUMENT (projectId);
