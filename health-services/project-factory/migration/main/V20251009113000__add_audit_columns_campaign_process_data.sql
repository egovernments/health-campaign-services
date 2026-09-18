-- Add audit columns to eg_cm_campaign_process_data table
-- These columns will allow NULL values for backward compatibility

ALTER TABLE eg_cm_campaign_process_data 
ADD COLUMN createdby character varying(128),
ADD COLUMN lastmodifiedby character varying(128),
ADD COLUMN createdtime bigint,
ADD COLUMN lastmodifiedtime bigint;