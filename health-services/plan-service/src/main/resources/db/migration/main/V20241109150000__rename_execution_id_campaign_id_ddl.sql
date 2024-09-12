ALTER TABLE plan_configuration
RENAME COLUMN execution_plan_id TO campaign_id;

ALTER TABLE plan
RENAME COLUMN execution_plan_id TO campaign_id;

ALTER TABLE plan_configuration_operations ADD show_on_estimation_dashboard boolean;
UPDATE plan_configuration_operations SET show_on_estimation_dashboard = true WHERE show_on_estimation_dashboard IS NULL;