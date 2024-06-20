ALTER TABLE eg_cm_campaign_details
ADD COLUMN campaignName character varying(128) UNIQUE,
ADD COLUMN projectType character varying(128);
