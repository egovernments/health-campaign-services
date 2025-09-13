-- Create table for generation file tracking
CREATE TABLE eg_ex_in_generated_files
(
    id                VARCHAR(100) PRIMARY KEY,
    referenceId       VARCHAR(100) NOT NULL,
    tenantId          VARCHAR(100) NOT NULL,
    type              VARCHAR(50) NOT NULL,
    hierarchyType     VARCHAR(100),
    fileStoreId       VARCHAR(200),
    status            VARCHAR(20) NOT NULL,
    errorDetails      TEXT,
    additionalDetails JSONB,
    locale            VARCHAR(64),
    createdBy         VARCHAR(100),
    lastModifiedBy    VARCHAR(100),
    createdTime       BIGINT,
    lastModifiedTime  BIGINT
);

CREATE INDEX idx_eg_ex_in_generated_files_referenceId ON eg_ex_in_generated_files(referenceId);
CREATE INDEX idx_eg_ex_in_generated_files_id ON eg_ex_in_generated_files(id);

-- Create table for processing file tracking
CREATE TABLE eg_ex_in_excel_processing
(
    id                    VARCHAR(100) PRIMARY KEY,
    referenceId           VARCHAR(100) NOT NULL,
    tenantId              VARCHAR(100) NOT NULL,
    type                  VARCHAR(50) NOT NULL,
    hierarchyType         VARCHAR(100) NOT NULL,
    fileStoreId           VARCHAR(200) NOT NULL,
    processedFileStoreId  VARCHAR(200),
    status                VARCHAR(20) NOT NULL,
    additionalDetails     JSONB,
    createdBy             VARCHAR(100),
    lastModifiedBy        VARCHAR(100),
    createdTime           BIGINT,
    lastModifiedTime      BIGINT
);

CREATE INDEX idx_eg_ex_in_excel_processing_referenceId ON eg_ex_in_excel_processing(referenceId);
CREATE INDEX idx_eg_ex_in_excel_processing_id ON eg_ex_in_excel_processing(id);

-- Create temporary table for parsed sheet data storage
CREATE TABLE eg_ex_in_sheet_data_temp (
    referenceId         VARCHAR(100)    NOT NULL,
    tenantId            VARCHAR(100)    NOT NULL,
    fileStoreId         VARCHAR(100)    NOT NULL,
    sheetName           VARCHAR(100)    NOT NULL,
    rowNumber           INTEGER         NOT NULL,
    rowJson             JSONB           NOT NULL,
    createdBy           VARCHAR(100)    NOT NULL,
    createdTime         BIGINT          NOT NULL,
    deleteTime          BIGINT          NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000 + 86400000),
    
    PRIMARY KEY (referenceId, fileStoreId, sheetName, rowNumber)
);

-- Create indexes for efficient querying
CREATE INDEX idx_eg_ex_in_sheet_data_temp_filestore ON eg_ex_in_sheet_data_temp(fileStoreId);
CREATE INDEX idx_eg_ex_in_sheet_data_temp_sheet ON eg_ex_in_sheet_data_temp(sheetName);