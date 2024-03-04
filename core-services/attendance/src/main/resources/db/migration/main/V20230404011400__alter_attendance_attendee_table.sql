ALTER TABLE eg_wms_attendance_register ADD COLUMN referenceid character varying(256);
ALTER TABLE eg_wms_attendance_register ADD COLUMN servicecode character varying(64);
CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_register_reference_id ON eg_wms_attendance_register (referenceid);
CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_register_service_code ON eg_wms_attendance_register (servicecode);