-- Backs SheetDataTempRepository search: filter (tenantId, referenceId, fileStoreId, sheetName)
-- + ORDER BY sheetName, rowNumber. Also future-proofs keyset pagination.
CREATE INDEX IF NOT EXISTS idx_eg_ex_in_sheet_data_temp_search
    ON eg_ex_in_sheet_data_temp (tenantId, referenceId, fileStoreId, sheetName, rowNumber);
