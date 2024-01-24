ALTER TABLE eg_wms_attendance_log ADD COLUMN clientreferenceid TYPE character varying(256);
ALTER TABLE eg_wms_attendance_log ADD COLUMN clientcreatedby TYPE character varying(256);
ALTER TABLE eg_wms_attendance_log ADD COLUMN clientlastmodifiedby TYPE character varying(256);
ALTER TABLE eg_wms_attendance_log ADD COLUMN clientcreatedtime TYPE bigint;
ALTER TABLE eg_wms_attendance_log ADD COLUMN clientlastmodifiedtime TYPE bigint;