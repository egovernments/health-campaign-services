ALTER TABLE eg_wms_attendance_log ADD COLUMN IF NOT EXISTS clientreferenceid character varying(256);
ALTER TABLE eg_wms_attendance_log ADD COLUMN IF NOT EXISTS clientcreatedby character varying(256);
ALTER TABLE eg_wms_attendance_log ADD COLUMN IF NOT EXISTS clientlastmodifiedby character varying(256);
ALTER TABLE eg_wms_attendance_log ADD COLUMN IF NOT EXISTS clientcreatedtime bigint;
ALTER TABLE eg_wms_attendance_log ADD COLUMN IF NOT EXISTS clientlastmodifiedtime bigint;