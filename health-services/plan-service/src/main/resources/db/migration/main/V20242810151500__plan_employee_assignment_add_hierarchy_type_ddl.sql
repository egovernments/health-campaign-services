ALTER TABLE plan_employee_assignment ADD hierarchy_type character varying(64);
UPDATE plan_employee_assignment SET hierarchy_type = 'MICROPLAN' WHERE hierarchy_type IS NULL;