-- Table: plan_configuration
CREATE TABLE plan_configuration (
  id                            character varying(64),
  tenant_id                     character varying(64),
  name                          character varying(128),
  execution_plan_id             character varying(64),
  created_by                    character varying(64),
  created_time                  bigint,
  last_modified_by              character varying(64),
  last_modified_time            bigint,
  CONSTRAINT uk_plan_configuration_id PRIMARY KEY (id)
);


-- Table: plan_configuration_files
CREATE TABLE plan_configuration_files (
  id                            character varying(64),
  plan_configuration_id         character varying(64),
  filestore_id                  character varying(128),
  input_file_type               character varying(64),
  created_by                    character varying(64),
  created_time                  bigint,
  last_modified_by              character varying(64),
  last_modified_time            bigint,
  CONSTRAINT uk_plan_configuration_files_id PRIMARY KEY (id),
  FOREIGN KEY (plan_configuration_id) REFERENCES plan_configuration(id)
);


-- Table: plan_configuration_assumptions
CREATE TABLE plan_configuration_assumptions (
  id                            character varying(64),
  key                           character varying(256),
  value                         numeric(12,2),
  plan_configuration_id         character varying(64),
  created_by                    character varying(64),
  created_time                  bigint,
  last_modified_by              character varying(64),
  last_modified_time            bigint,
  CONSTRAINT uk_plan_configuration_assumptions_id PRIMARY KEY (id),
  FOREIGN KEY (plan_configuration_id) REFERENCES plan_configuration(id)
);


-- Table: plan_configuration_operations
CREATE TABLE plan_configuration_operations (
  id                            character varying(64),
  input                         character varying(256),
  operator                      character varying(64),
  assumption_value              character varying(256),
  output                        character varying(64),
  plan_configuration_id         character varying(64),
  created_by                    character varying(64),
  created_time                  bigint,
  last_modified_by              character varying(64),
  last_modified_time            bigint,
  CONSTRAINT uk_plan_configuration_operations_id PRIMARY KEY (id),
  FOREIGN KEY (plan_configuration_id) REFERENCES plan_configuration(id)
);


-- Table: plan_configuration_mapping
CREATE TABLE plan_configuration_mapping (
  id                            character varying(64),
  mapped_from                   character varying(256),
  mapped_to                     character varying(256),
  plan_configuration_id         character varying(64),
  created_by                    character varying(64),
  created_time                  bigint,
  last_modified_by              character varying(64),
  last_modified_time            bigint,
  CONSTRAINT uk_plan_configuration_mapping_id PRIMARY KEY (id)
);