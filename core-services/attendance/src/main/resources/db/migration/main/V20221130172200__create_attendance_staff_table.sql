CREATE TABLE eg_wms_attendance_staff(
id                           character varying(256),
individual_id                character varying(256) NOT NULL,
register_id                  character varying(256) NOT NULL,
tenantid                     character varying(64),
enrollment_date              bigint NOT NULL,
deenrollment_date            bigint,
additionaldetails            JSONB,
createdby                    character varying(256)  NOT NULL,
lastmodifiedby               character varying(256),
createdtime                  bigint,
lastmodifiedtime             bigint,
CONSTRAINT pk_eg_wms_attendance_staff PRIMARY KEY (id),
CONSTRAINT fk_eg_wms_attendance_staff FOREIGN KEY (register_id) REFERENCES eg_wms_attendance_register (id)
);

ALTER TABLE eg_wms_attendance_attendee ALTER COLUMN deenrollment_date DROP NOT NULL;