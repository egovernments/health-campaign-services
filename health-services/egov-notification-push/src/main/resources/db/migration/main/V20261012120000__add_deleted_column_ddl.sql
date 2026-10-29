ALTER TABLE eg_push_device_tokens ADD COLUMN IF NOT EXISTS deleted boolean NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_eg_push_device_tokens_userid_live ON eg_push_device_tokens (userid) WHERE NOT deleted;

CREATE INDEX IF NOT EXISTS idx_eg_push_device_tokens_facilityid_live ON eg_push_device_tokens (facilityid) WHERE NOT deleted;
