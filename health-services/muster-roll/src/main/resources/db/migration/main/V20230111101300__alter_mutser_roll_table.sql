ALTER TABLE eg_wms_muster_roll DROP CONSTRAINT uk_eg_wms_muster_roll;
ALTER TABLE eg_wms_muster_roll RENAME COLUMN tenantid TO tenant_id;
ALTER TABLE eg_wms_muster_roll RENAME COLUMN musterrollnumber TO musterroll_number;
ALTER TABLE eg_wms_muster_roll RENAME COLUMN attendanceregisterid TO attendance_register_id;
ALTER TABLE eg_wms_muster_roll RENAME COLUMN startdate TO start_date;
ALTER TABLE eg_wms_muster_roll RENAME COLUMN enddate TO end_date;
ALTER TABLE eg_wms_muster_roll RENAME COLUMN musterrollstatus TO musterroll_status;
ALTER TABLE eg_wms_muster_roll add CONSTRAINT uk_eg_wms_muster_roll UNIQUE (musterroll_number);

ALTER TABLE eg_wms_attendance_summary RENAME COLUMN musterrollnumber TO musterroll_number;

ALTER TABLE eg_wms_attendance_entries RENAME COLUMN musterrollnumber TO musterroll_number;
/*attendance_value - potential values are 0,0.5,1*/
ALTER TABLE eg_wms_attendance_entries RENAME COLUMN attendance TO attendance_value;

DROP INDEX index_eg_wms_muster_roll_tenantId;
DROP INDEX index_eg_wms_muster_roll_musterRollNumber;
DROP INDEX index_eg_wms_muster_roll_attendanceRegisterId;
DROP INDEX index_eg_wms_muster_roll_musterRollStatus;
DROP INDEX index_eg_wms_muster_roll_startDate;
DROP INDEX index_eg_wms_muster_roll_endDate;
CREATE INDEX IF NOT EXISTS index_eg_wms_muster_roll_tenantId ON eg_wms_muster_roll (tenant_id);
CREATE INDEX IF NOT EXISTS index_eg_wms_muster_roll_musterRollNumber ON eg_wms_muster_roll (musterroll_number);
CREATE INDEX IF NOT EXISTS index_eg_wms_muster_roll_attendanceRegisterId ON eg_wms_muster_roll (attendance_register_id);
CREATE INDEX IF NOT EXISTS index_eg_wms_muster_roll_musterRollStatus ON eg_wms_muster_roll (musterroll_status);
CREATE INDEX IF NOT EXISTS index_eg_wms_muster_roll_startDate ON eg_wms_muster_roll (start_date);
CREATE INDEX IF NOT EXISTS index_eg_wms_muster_roll_endDate ON eg_wms_muster_roll (end_date);

DROP INDEX index_eg_wms_attendance_summary_musterRollNumber;
CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_summary_musterRollNumber ON eg_wms_attendance_summary (musterroll_number);

DROP INDEX index_eg_wms_attendance_entries_musterRollNumber;
DROP INDEX index_eg_wms_attendance_entries_attendance;
CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_entries_musterRollNumber ON eg_wms_attendance_entries (musterroll_number);
CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_entries_attendance ON eg_wms_attendance_entries (attendance_value);





