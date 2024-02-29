ALTER TABLE eg_wms_attendance_register ALTER COLUMN registernumber TYPE character varying(256);
ALTER TABLE eg_wms_attendance_register ALTER COLUMN name TYPE character varying(256);

ALTER TABLE eg_wms_attendance_attendee ALTER COLUMN individual_id TYPE character varying(256);
ALTER TABLE eg_wms_attendance_attendee ALTER COLUMN register_id TYPE character varying(256);
ALTER TABLE eg_wms_attendance_attendee ADD COLUMN attendee_type character varying(256);
ALTER TABLE eg_wms_attendance_attendee ADD COLUMN permission_type character varying(256);
ALTER TABLE eg_wms_attendance_attendee ADD COLUMN tenantid character varying(64);

ALTER TABLE eg_wms_attendance_log ALTER COLUMN individual_id TYPE character varying(256);
ALTER TABLE eg_wms_attendance_log ALTER COLUMN register_id TYPE character varying(256);
ALTER TABLE eg_wms_attendance_log ADD COLUMN tenantid character varying(64);

ALTER TABLE eg_wms_attendance_document ALTER COLUMN filestore_id TYPE character varying(256);
ALTER TABLE eg_wms_attendance_document ALTER COLUMN attendance_log_id TYPE character varying(256);
ALTER TABLE eg_wms_attendance_document ALTER COLUMN document_type TYPE character varying(256);
ALTER TABLE eg_wms_attendance_document ADD COLUMN tenantid character varying(64);


CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_log_tenantId ON eg_wms_attendance_log (tenantId);
CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_log_register_id ON eg_wms_attendance_log (register_id);
CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_log_time ON eg_wms_attendance_log (time);
CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_log_individual_id ON eg_wms_attendance_log (individual_id);
CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_log_status ON eg_wms_attendance_log (status);
CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_log_createdtime ON eg_wms_attendance_log (createdtime);

CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_register_tenantId ON eg_wms_attendance_register (tenantId);
CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_register_id ON eg_wms_attendance_register (id);
CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_register_registernumber ON eg_wms_attendance_register (registernumber);
CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_register_name ON eg_wms_attendance_register (name);
CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_register_startdate ON eg_wms_attendance_register (startdate);
CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_register_enddate ON eg_wms_attendance_register (enddate);
CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_register_status ON eg_wms_attendance_register (status);
CREATE INDEX IF NOT EXISTS index_eg_wms_attendance_register_createdtime ON eg_wms_attendance_register (createdtime);

DROP TABLE IF EXISTS eg_wms_attendance_staff, eg_wms_staff_permissions CASCADE;