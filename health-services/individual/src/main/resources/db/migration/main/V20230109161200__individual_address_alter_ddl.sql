ALTER TABLE address ADD COLUMN clientReferenceId character varying(64) UNIQUE;

ALTER TABLE individual_address ADD COLUMN addressClientReferenceId character varying(64);