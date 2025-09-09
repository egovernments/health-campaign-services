CREATE TABLE eg_ex_in_generated_files
(
    id                VARCHAR(100) PRIMARY KEY,
    referenceId       VARCHAR(100) NOT NULL,
    tenantId          VARCHAR(100) NOT NULL,
    type              VARCHAR(50) NOT NULL,
    fileStoreId       VARCHAR(200),
    status            VARCHAR(20) NOT NULL,
    errorDetails      TEXT,
    additionalDetails JSONB,
    createdBy         VARCHAR(100),
    lastModifiedBy    VARCHAR(100),
    createdTime       BIGINT,
    lastModifiedTime  BIGINT
);

CREATE INDEX idx_eg_ex_in_generated_files_referenceId ON eg_ex_in_generated_files(referenceId);
CREATE INDEX idx_eg_ex_in_generated_files_status ON eg_ex_in_generated_files(status);
CREATE INDEX idx_eg_ex_in_generated_files_type ON eg_ex_in_generated_files(type);