-- Add userroles column to store comma-separated role codes for the user.
-- This enables role-based filtering when resolving device tokens for push notifications.

ALTER TABLE eg_push_device_tokens ADD COLUMN IF NOT EXISTS userroles character varying(1024);

CREATE INDEX IF NOT EXISTS idx_eg_push_device_tokens_userroles ON eg_push_device_tokens (userroles);
