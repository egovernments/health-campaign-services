ALTER TABLE eg_wms_attendance_attendee DROP COLUMN attendee_type;
ALTER TABLE eg_wms_attendance_attendee DROP COLUMN permission_type;
ALTER TABLE eg_wms_attendance_document ADD COLUMN status character varying(64);
ALTER TABLE eg_wms_attendance_register ALTER COLUMN enddate DROP NOT NULL;