CREATE TABLE eg_wms_attendance_register(
id                           character varying(256),
tenantid                     character varying(64) NOT NULL,
registernumber               character varying(128) NOT NULL,
name                         character varying(128),
startdate                    bigint NOT NULL,
enddate                      bigint NOT NULL,
status                       character varying(64) NOT NULL,
additionaldetails            JSONB,
createdby                    character varying(256)  NOT NULL,
lastmodifiedby               character varying(256),
createdtime                  bigint,
lastmodifiedtime             bigint,
CONSTRAINT uk_eg_wms_attendance_register UNIQUE (registernumber),
CONSTRAINT pk_eg_wms_attendance_register PRIMARY KEY (id)
);

CREATE TABLE eg_wms_attendance_staff(
id                           character varying(256),
individual_id                character varying(64) NOT NULL,
register_id                  character varying(64) NOT NULL,
enrollment_date              bigint NOT NULL,
deenrollment_date            bigint NOT NULL,
additionaldetails            JSONB,
createdby                    character varying(256)  NOT NULL,
lastmodifiedby               character varying(256),
createdtime                  bigint,
lastmodifiedtime             bigint,
CONSTRAINT pk_eg_wms_attendance_staff PRIMARY KEY (id),
CONSTRAINT fk_eg_wms_attendance_staff FOREIGN KEY (register_id) REFERENCES eg_wms_attendance_register (id)
);

CREATE TABLE eg_wms_staff_permissions(
id                           character varying(256),
permission_type              character varying(64) NOT NULL,
staff_id                     character varying(64) NOT NULL,
register_id                  character varying(64) NOT NULL,
additionaldetails            JSONB,
createdby                    character varying(256)  NOT NULL,
lastmodifiedby               character varying(256),
createdtime                  bigint,
lastmodifiedtime             bigint,
CONSTRAINT pk_eg_wms_staff_permissions PRIMARY KEY (id),
CONSTRAINT fk_eg_wms_register_staff_permissions FOREIGN KEY (register_id) REFERENCES eg_wms_attendance_register (id),
CONSTRAINT fk_eg_wms_staff_permissions FOREIGN KEY (staff_id) REFERENCES eg_wms_attendance_staff (id)
);

CREATE TABLE eg_wms_attendance_attendee(
id                           character varying(256),
individual_id                character varying(64) NOT NULL,
register_id                  character varying(64) NOT NULL,
enrollment_date              bigint NOT NULL,
deenrollment_date            bigint NOT NULL,
additionaldetails            JSONB,
createdby                    character varying(256)  NOT NULL,
lastmodifiedby               character varying(256),
createdtime                  bigint,
lastmodifiedtime             bigint,
CONSTRAINT pk_eg_wms_attendance_attendee PRIMARY KEY (id),
CONSTRAINT fk_eg_wms_attendance_attendee FOREIGN KEY (register_id) REFERENCES eg_wms_attendance_register (id)
);

CREATE TABLE eg_wms_attendance_log(
id                           character varying(256),
individual_id                character varying(64) NOT NULL,
register_id                  character varying(64) NOT NULL,
status                       character varying(64),
time                         bigint NOT NULL,
event_type                   character varying(64),
additionaldetails            JSONB,
createdby                    character varying(256)  NOT NULL,
lastmodifiedby               character varying(256),
createdtime                  bigint,
lastmodifiedtime             bigint,
CONSTRAINT pk_eg_wms_attendance_log PRIMARY KEY (id),
CONSTRAINT fk_eg_wms_attendance_log FOREIGN KEY (register_id) REFERENCES eg_wms_attendance_register (id)
);

CREATE TABLE eg_wms_attendance_document(
id                           character varying(256),
filestore_id                 character varying(64) NOT NULL,
document_type                character varying(64),
attendance_log_id            character varying(64) NOT NULL,
additionaldetails            JSONB,
createdby                    character varying(256)  NOT NULL,
lastmodifiedby               character varying(256),
createdtime                  bigint,
lastmodifiedtime             bigint,
CONSTRAINT pk_eg_wms_attendance_document PRIMARY KEY (id),
CONSTRAINT fk_eg_wms_attendance_document FOREIGN KEY (attendance_log_id) REFERENCES eg_wms_attendance_log (id)
);