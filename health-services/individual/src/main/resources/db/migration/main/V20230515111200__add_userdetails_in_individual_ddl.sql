ALTER TABLE INDIVIDUAL ADD COLUMN username character varying(64);
ALTER TABLE INDIVIDUAL ADD COLUMN password character varying(200);
ALTER TABLE INDIVIDUAL ADD COLUMN type character varying(64);
ALTER TABLE INDIVIDUAL ADD COLUMN roles jsonb;