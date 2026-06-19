ALTER TABLE eg_cm_campaign_details
ADD COLUMN parentid character varying(128);

ALTER TABLE eg_cm_campaign_details
ADD COLUMN isactive boolean;