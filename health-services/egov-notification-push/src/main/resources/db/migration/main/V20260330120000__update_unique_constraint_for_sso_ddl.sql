-- SSO policy: one device token per user per facility.
-- Change unique constraint from (devicetoken, facilityid) to (userid, facilityid).

ALTER TABLE eg_push_device_tokens DROP CONSTRAINT uk_eg_push_device_tokens_devicetoken_facilityid;

ALTER TABLE eg_push_device_tokens ADD CONSTRAINT uk_eg_push_device_tokens_userid_facilityid UNIQUE (userid, facilityid);
