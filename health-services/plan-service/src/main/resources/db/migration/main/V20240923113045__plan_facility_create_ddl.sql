-- Table: plan_facility
CREATE TABLE plan_facility_linkage (
  id varchar(64),
  tenant_id varchar(64),
  plan_configuration_id varchar(64),
  facility_id varchar(64),
  residing_boundary varchar(64),
  service_boundaries varchar(2048),
  additional_details JSONB,
  active boolean,
  created_by varchar(64),
  created_time bigint,
  last_modified_by varchar(64),
  last_modified_time bigint,
  CONSTRAINT uk_plan_facility_linkage_id PRIMARY KEY (id),
  FOREIGN KEY (plan_configuration_id) REFERENCES plan_configuration(id),
  FOREIGN KEY (facility_id) REFERENCES facility(id)
);