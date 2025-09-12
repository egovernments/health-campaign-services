-- Create temporary table for parsed sheet data storage
CREATE TABLE eg_ex_in_sheet_data_temp (
    referenceId         VARCHAR(100)    NOT NULL,
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