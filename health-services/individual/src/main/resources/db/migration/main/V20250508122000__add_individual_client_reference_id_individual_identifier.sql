-- Rename the existing column clientReferenceId
-- 1. Since it's being used as individual client reference id
ALTER TABLE INDIVIDUAL_IDENTIFIER
    RENAME COLUMN clientReferenceId TO individualClientReferenceId;

-- 2. Drop the unique constraint (if any)
ALTER TABLE INDIVIDUAL_IDENTIFIER
    DROP CONSTRAINT IF EXISTS individual_identifier_clientreferenceid_key;

-- 3. Add a new clientReferenceId column
ALTER TABLE INDIVIDUAL_IDENTIFIER
    ADD COLUMN IF NOT EXISTS clientReferenceId character varying(64)
    DEFAULT gen_random_uuid()::text UNIQUE;
