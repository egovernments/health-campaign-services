TRUNCATE TABLE PROJECT;
ALTER TABLE PROJECT ADD COLUMN projectNumber character varying(128) NOT NULL;
ALTER TABLE PROJECT ADD COLUMN projectSubType character varying(128) NOT NULL;
ALTER TABLE PROJECT ADD COLUMN projectType character varying(64);
ALTER TABLE PROJECT ADD COLUMN name character varying(128);
ALTER TABLE PROJECT ADD COLUMN department character varying(64) NOT NULL;
ALTER TABLE PROJECT ADD COLUMN description character varying(256) NOT NULL;
ALTER TABLE PROJECT ADD COLUMN referenceId character varying(100) NOT NULL;