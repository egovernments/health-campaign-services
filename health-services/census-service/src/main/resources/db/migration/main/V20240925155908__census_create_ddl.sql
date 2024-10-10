-- Table: census
CREATE TABLE census (
  id                                        character varying(64),
  tenant_id                                 character varying(64),
  hierarchy_type                            character varying(64),
  boundary_code                             character varying(64),
  type                                      character varying(64),
  total_population                          bigint,
  effective_from                            bigint,
  effective_to                              bigint,
  source                                    character varying(255),
  status                                    character varying(255),
  assignee                                  character varying(255),
  boundary_ancestral_path                   TEXT,
  additional_details                        JSONB,
  created_by                                character varying(64),
  created_time                              bigint,
  last_modified_by                          character varying(64),
  last_modified_time                        bigint,
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
