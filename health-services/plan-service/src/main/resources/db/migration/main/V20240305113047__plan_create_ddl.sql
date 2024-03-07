CREATE TABLE plan (
  id varchar(64),
  tenant_id varchar(64),
  locality varchar(64),
  execution_plan_id varchar(64),
  plan_configuration_id varchar(64),
  additional_details JSONB,
  created_by varchar(64),
  created_time bigint,
  last_modified_by varchar(64),
  last_modified_time bigint,
  CONSTRAINT uk_plan_id PRIMARY KEY (id)
);

CREATE TABLE plan_activity (
  id varchar(64) NOT NULL,
  code varchar(128) NOT NULL,
  description varchar(2048),
  planned_start_date bigint,
  planned_end_date bigint,
  dependencies character varying(2048),
  plan_id varchar(64) NOT NULL,
  created_by varchar(64),
  created_time bigint,
  last_modified_by varchar(64),
  last_modified_time bigint,
  CONSTRAINT uk_plan_activity_id PRIMARY KEY (id),
  FOREIGN KEY (plan_id) REFERENCES plan(id)
);

CREATE TABLE plan_activity_condition (
  id varchar(64),
  entity varchar(64),
  entity_property varchar(64),
  expression varchar(2048),
  activity_id varchar(64),
  is_active boolean DEFAULT true,
  created_by varchar(64),
  created_time bigint,
  last_modified_by varchar(64),
  last_modified_time bigint,
  CONSTRAINT uk_plan_activity_condition_id PRIMARY KEY (id),
  FOREIGN KEY (activity_id) REFERENCES plan_activity(id)
);

CREATE TABLE plan_resource (
  id varchar(64),
  resource_type varchar(256),
  estimated_number numeric(12,2),
  plan_id varchar(64),
  activity_code varchar(128),
  created_by varchar(64),
  created_time bigint,
  last_modified_by varchar(64),
  last_modified_time bigint,
  CONSTRAINT uk_plan_resource_id PRIMARY KEY (id),
  FOREIGN KEY (plan_id) REFERENCES plan(id)
);

CREATE TABLE plan_target (
  id varchar(64),
  metric varchar(128),
  metric_value numeric(12,2),
  metric_comparator varchar(64),
  metric_unit varchar(128),
  plan_id varchar(64),
  activity_code varchar(128),
  created_by varchar(64),
  created_time bigint,
  last_modified_by varchar(64),
  last_modified_time bigint,
  CONSTRAINT uk_plan_target_id PRIMARY KEY (id),
  FOREIGN KEY (plan_id) REFERENCES plan(id)
);