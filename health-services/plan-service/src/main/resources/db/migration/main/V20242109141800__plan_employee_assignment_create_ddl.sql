-- Table: plan_employee_assignment
CREATE TABLE plan_employee_assignment (
  id                            character varying(64),
  tenant_id                     character varying(64),
  plan_configuration_id         character varying(64),
  employee_id                   character varying(64),
  role                          character varying(64),
  jurisdiction                  JSONB,
  additional_details            JSONB,
  active                        boolean DEFAULT true,
  created_by                    character varying(64),
  created_time                  bigint,
  last_modified_by              character varying(64),
  last_modified_time            bigint,
  CONSTRAINT uk_plan_employee_assignment_id PRIMARY KEY (id),
  FOREIGN KEY (plan_configuration_id) REFERENCES plan_configuration(id)
);
