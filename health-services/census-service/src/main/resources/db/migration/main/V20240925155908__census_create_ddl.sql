-- Table: census
CREATE TABLE census (
  id                                        character varying(64) NOT NULL,
  tenant_id                                 character varying(64) NOT NULL,
  hierarchy_type                            character varying(64) NOT NULL,
  boundary_code                             character varying(64) NOT NULL,
  type                                      character varying(64) NOT NULL,
  total_population                          bigint NOT NULL,
  effective_from                            bigint NOT NULL,
  effective_to                              bigint,
  source                                    character varying(255) NOT NULL,
  status                                    character varying(255),
  assignee                                  character varying(255),
  facility_assigned                         boolean,
  boundary_ancestral_path                   TEXT NOT NULL,
  additional_details                        JSONB,
  created_by                                character varying(64) NOT NULL,
  created_time                              bigint NOT NULL,
  last_modified_by                          character varying(64) NOT NULL,
  last_modified_time                        bigint NOT NULL,
  CONSTRAINT uk_census_id PRIMARY KEY (id)
);

-- Table: population_by_demographics
CREATE TABLE population_by_demographics (
  id                                        character varying(64),
  census_id                                 character varying(64),
  demographic_variable                      character varying(64),
  population_distribution                   JSONB,
  created_by                                character varying(64),
  created_time                              bigint,
  last_modified_by                          character varying(64),
  last_modified_time                        bigint,
  CONSTRAINT uk_population_by_demographics_id PRIMARY KEY (id),
  FOREIGN KEY (census_id) REFERENCES census(id)
);
