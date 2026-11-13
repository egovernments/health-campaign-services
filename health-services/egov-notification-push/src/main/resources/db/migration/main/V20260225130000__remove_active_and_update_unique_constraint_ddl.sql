-- Remove duplicate devicetoken rows keeping only the most recently modified
DELETE FROM eg_push_device_tokens a
  USING eg_push_device_tokens b
  WHERE a.devicetoken = b.devicetoken
    AND a.lastmodifiedtime < b.lastmodifiedtime;

-- Drop old composite unique constraint and active index
ALTER TABLE eg_push_device_tokens DROP CONSTRAINT uk_eg_push_device_tokens;
DROP INDEX IF EXISTS idx_eg_push_device_tokens_active;

-- Drop active column
ALTER TABLE eg_push_device_tokens DROP COLUMN active;

-- Add unique constraint on devicetoken alone
ALTER TABLE eg_push_device_tokens ADD CONSTRAINT uk_eg_push_device_tokens_devicetoken UNIQUE (devicetoken);
