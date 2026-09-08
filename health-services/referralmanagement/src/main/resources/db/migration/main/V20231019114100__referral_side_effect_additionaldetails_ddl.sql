ALTER TABLE side_effect ADD COLUMN IF NOT EXISTS additionalDetails jsonb;
ALTER TABLE referral ADD COLUMN IF NOT EXISTS additionalDetails jsonb;