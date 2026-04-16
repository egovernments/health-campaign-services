-- Allow a single device token to be associated with multiple facility IDs.
-- Normalize NULL facilityid values to an impossible sentinel for uniqueness so
-- global tokens still upsert correctly without allowing duplicate NULL rows.

ALTER TABLE eg_push_device_tokens DROP CONSTRAINT uk_eg_push_device_tokens_devicetoken;

CREATE UNIQUE INDEX uk_eg_push_device_tokens_devicetoken_facilityid
  ON eg_push_device_tokens (devicetoken, COALESCE(facilityid, chr(31)));
