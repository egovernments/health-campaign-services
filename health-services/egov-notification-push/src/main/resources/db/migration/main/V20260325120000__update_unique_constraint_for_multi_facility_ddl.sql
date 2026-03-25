-- Allow a single device token to be associated with multiple facility IDs.
-- Change unique constraint from (devicetoken) to (devicetoken, facilityid).

ALTER TABLE eg_push_device_tokens DROP CONSTRAINT uk_eg_push_device_tokens_devicetoken;

ALTER TABLE eg_push_device_tokens ADD CONSTRAINT uk_eg_push_device_tokens_devicetoken_facilityid UNIQUE (devicetoken, facilityid);
