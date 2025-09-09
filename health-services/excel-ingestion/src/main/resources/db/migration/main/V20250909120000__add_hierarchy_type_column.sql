ALTER TABLE eg_ex_in_generated_files ADD COLUMN hierarchyType VARCHAR(100);

CREATE INDEX idx_eg_ex_in_generated_files_hierarchyType ON eg_ex_in_generated_files(hierarchyType);