CREATE INDEX IF NOT EXISTS index_eg_wms_muster_roll_tenantId ON eg_wms_muster_roll (tenantId);
CREATE INDEX IF NOT EXISTS index_eg_wms_muster_roll_id ON eg_wms_muster_roll (id);
CREATE INDEX IF NOT EXISTS index_eg_wms_muster_roll_musterRollNumber ON eg_wms_muster_roll (musterRollNumber);
CREATE INDEX IF NOT EXISTS index_eg_wms_muster_roll_attendanceRegisterId ON eg_wms_muster_roll (attendanceRegisterId);
CREATE INDEX IF NOT EXISTS index_eg_wms_muster_roll_status ON eg_wms_muster_roll (status);
CREATE INDEX IF NOT EXISTS index_eg_wms_muster_roll_musterRollStatus ON eg_wms_muster_roll (musterRollStatus);
CREATE INDEX IF NOT EXISTS index_eg_wms_muster_roll_startDate ON eg_wms_muster_roll (startDate);
CREATE INDEX IF NOT EXISTS index_eg_wms_muster_roll_endDate ON eg_wms_muster_roll (endDate);
CREATE INDEX IF NOT EXISTS index_eg_wms_muster_roll_createdtime ON eg_wms_muster_roll (createdtime);


CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_summary_id ON eg_wms_attendance_summary (id);
CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_summary_musterRollNumber ON eg_wms_attendance_summary (musterRollNumber);
CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_summary_individual_id ON eg_wms_attendance_summary (individual_id);
CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_summary_total_attendance ON eg_wms_attendance_summary (total_attendance);
CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_summary_createdtime ON eg_wms_attendance_summary (createdtime);

CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_entries_id ON eg_wms_attendance_entries (id);
CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_entries_musterRollNumber ON eg_wms_attendance_entries (musterRollNumber);
CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_entries_individual_id ON eg_wms_attendance_entries (individual_id);
CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_entries_attendance_summary_id ON eg_wms_attendance_entries (attendance_summary_id);
CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_entries_attendance ON eg_wms_attendance_entries (attendance);
CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_entries_date_of_attendance ON eg_wms_attendance_entries (date_of_attendance);
CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_entries_createdtime ON eg_wms_attendance_entries (createdtime);
