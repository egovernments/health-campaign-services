ALTER TABLE eg_cm_campaign_mapping_data ADD COLUMN IF NOT EXISTS retryCount integer NOT NULL DEFAULT 0;
ALTER TABLE eg_cm_campaign_mapping_data ADD COLUMN IF NOT EXISTS lastError TEXT;
