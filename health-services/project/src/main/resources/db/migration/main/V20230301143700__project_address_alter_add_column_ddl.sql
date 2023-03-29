ALTER TABLE project_address RENAME COLUMN locality TO boundary;
ALTER TABLE project_address ADD COLUMN boundary_type character varying(64);