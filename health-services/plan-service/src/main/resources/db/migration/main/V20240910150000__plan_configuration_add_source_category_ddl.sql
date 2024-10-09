ALTER TABLE plan_configuration_assumptions
ADD COLUMN source varchar(64) NOT NULL,
ADD COLUMN category varchar(64) NOT NULL;

UPDATE plan_configuration_assumptions SET source = 'MDMS' WHERE source IS NULL;
UPDATE plan_configuration_assumptions SET category = 'GENERAL_INFORMATION' WHERE category IS NULL;

ALTER TABLE plan_configuration_operations
ADD COLUMN source varchar(64) NOT NULL,
ADD COLUMN category varchar(64) NOT NULL;

UPDATE plan_configuration_operations SET source = 'MDMS' WHERE source IS NULL;
UPDATE plan_configuration_operations SET category = 'GENERAL_INFORMATION' WHERE category IS NULL;
