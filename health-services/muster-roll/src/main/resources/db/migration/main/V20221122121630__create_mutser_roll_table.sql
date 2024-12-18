CREATE TABLE eg_wms_muster_roll(
id                           character varying(256),
tenantid                     character varying(64) NOT NULL,
musterrollnumber             character varying(128) NOT NULL,
attendanceregisterid         character varying(256) NOT NULL,
startdate                    bigint NOT NULL,
enddate                      bigint NOT NULL,
musterrollstatus             character varying(64) NOT NULL,
status                       character varying(64) NOT NULL,
additionaldetails            JSONB,
createdby                    character varying(256)  NOT NULL,
lastmodifiedby               character varying(256),
createdtime                  bigint,
lastmodifiedtime             bigint,
CONSTRAINT uk_eg_wms_muster_roll UNIQUE (musterrollnumber),
CONSTRAINT pk_eg_wms_muster_roll PRIMARY KEY (id)
);

CREATE TABLE eg_wms_attendance_summary(
id                           character varying(256),
individual_id                character varying(256) NOT NULL,
muster_roll_id               character varying(256) NOT NULL,
musterrollnumber             character varying(128) NOT NULL,
total_attendance             NUMERIC,
additionaldetails            JSONB,
createdby                    character varying(256)  NOT NULL,
lastmodifiedby               character varying(256),
createdtime                  bigint,
lastmodifiedtime             bigint,
CONSTRAINT pk_eg_wms_attendance_summary PRIMARY KEY (id),
CONSTRAINT fk_eg_wms_attendance_summary FOREIGN KEY (muster_roll_id) REFERENCES eg_wms_muster_roll (id)
);

CREATE TABLE eg_wms_attendance_entries(
id                           character varying(256),
attendance_summary_id        character varying(256) NOT NULL,
individual_id                character varying(256) NOT NULL,
musterrollnumber             character varying(128) NOT NULL,
date_of_attendance           bigint,
attendance                   NUMERIC,
additionaldetails            JSONB,
createdby                    character varying(256)  NOT NULL,
lastmodifiedby               character varying(256),
createdtime                  bigint,
lastmodifiedtime             bigint,
CONSTRAINT pk_eg_wms_attendance_entries PRIMARY KEY (id),
CONSTRAINT fk_eg_wms_attendance_entries FOREIGN KEY (attendance_summary_id) REFERENCES eg_wms_attendance_summary (id)
);