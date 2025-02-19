ALTER TABLE plan_configuration_files ADD active boolean;
UPDATE plan_configuration_files SET active = true WHERE active IS NULL;

ALTER TABLE plan_configuration_assumptions ADD active boolean;
UPDATE plan_configuration_assumptions SET active = true WHERE active IS NULL;

ALTER TABLE plan_configuration_operations ADD active boolean;
UPDATE plan_configuration_operations SET active = true WHERE active IS NULL;

ALTER TABLE plan_configuration_mapping ADD active boolean;
UPDATE plan_configuration_mapping SET active = true WHERE active IS NULL;