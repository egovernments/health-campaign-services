ALTER TABLE hf_referral ADD COLUMN IF NOT EXISTS localitycode character varying(100);

CREATE INDEX IF NOT EXISTS idx_hf_referral_localitycode ON hf_referral(localitycode);
