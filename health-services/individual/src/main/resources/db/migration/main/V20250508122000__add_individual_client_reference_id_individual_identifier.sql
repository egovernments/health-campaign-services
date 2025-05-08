-- Rename the existing column clientReferenceId
-- Since it's being used as individual client reference id
ALTER TABLE INDIVIDUAL_IDENTIFIER
    RENAME COLUMN clientReferenceId TO individualClientReferenceId;

-- Add a new clientReferenceId column
ALTER TABLE INDIVIDUAL_IDENTIFIER
    ADD COLUMN IF NOT EXISTS clientReferenceId character varying(64);
