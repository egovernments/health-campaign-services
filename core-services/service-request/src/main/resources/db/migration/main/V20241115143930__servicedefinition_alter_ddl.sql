-- Migration script to alter the length of 'code' column in both tables to 256

-- Update eg_service_definition table
ALTER TABLE eg_service_definition
ALTER COLUMN code TYPE character varying(256);

-- Update eg_service_attribute_definition table
ALTER TABLE eg_service_attribute_definition
ALTER COLUMN code TYPE character varying(256);
