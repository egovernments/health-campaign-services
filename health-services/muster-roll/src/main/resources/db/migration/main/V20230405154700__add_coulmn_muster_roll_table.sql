ALTER TABLE eg_wms_muster_roll ADD COLUMN reference_id character varying(256);
ALTER TABLE eg_wms_muster_roll ADD COLUMN service_code character varying(64);
CREATE INDEX IF NOT EXISTS index_eg_wms_muster_roll_reference_id ON eg_wms_muster_roll (reference_id);
CREATE INDEX IF NOT EXISTS index_eg_wms_muster_roll_service_code ON eg_wms_muster_roll (service_code);





