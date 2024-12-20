-- Table: plan_additional_field
CREATE TABLE plan_additional_field (
  id                            character varying(64) NOT NULL,
  plan_id                       character varying(64) NOT NULL,
  "key"                         character varying(64) NOT NULL,
  "value"                       numeric(12,2) NOT NULL,
  show_on_ui                    boolean DEFAULT true NOT NULL,
  editable                      boolean DEFAULT true NOT NULL,
  "order"                       bigint NOT NULL,
  CONSTRAINT uk_additional_field_id PRIMARY KEY (id),
  FOREIGN KEY (plan_id) REFERENCES plan(id)
);
