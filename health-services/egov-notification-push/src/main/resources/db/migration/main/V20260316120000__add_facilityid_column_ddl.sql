-- HRUTHVIK: Add facilityid column to support facility-based push notification routing.
-- Device tokens are now linked to a facilityId so that health-notification-service
-- can send notifications by facility instead of individual user UUIDs.

ALTER TABLE eg_push_device_tokens ADD COLUMN facilityid character varying(256);

CREATE INDEX idx_eg_push_device_tokens_facilityid ON eg_push_device_tokens(facilityid);
