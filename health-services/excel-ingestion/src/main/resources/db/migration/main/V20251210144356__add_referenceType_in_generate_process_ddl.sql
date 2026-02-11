-- add a column to store the referenceType
ALTER TABLE eg_ex_in_excel_processing ADD COLUMN referenceType  VARCHAR(100);
ALTER TABLE eg_ex_in_generated_files ADD COLUMN referenceType  VARCHAR(100);