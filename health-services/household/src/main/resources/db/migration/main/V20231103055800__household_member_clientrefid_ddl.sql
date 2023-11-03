ALTER TABLE HOUSEHOLD_MEMBER ADD COLUMN IF NOT EXISTS clientreferenceid character varying(256);

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE OR REPLACE FUNCTION gen_random_uuid()
RETURNS UUID AS $$
BEGIN
  RETURN uuid_generate_v4();
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION fill_empty_uuids()
RETURNS VOID AS $$
DECLARE
    record_row RECORD;
BEGIN
    FOR record_row IN SELECT * FROM HOUSEHOLD_MEMBER WHERE clientreferenceid IS NULL OR clientreferenceid = '' LOOP
        record_row.clientreferenceid := gen_random_uuid();
        UPDATE HOUSEHOLD_MEMBER
        SET clientreferenceid = record_row.clientreferenceid
        WHERE id = record_row.id;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

SELECT fill_empty_uuids();
ALTER TABLE HOUSEHOLD_MEMBER ALTER COLUMN clientreferenceid SET NOT NULL;
