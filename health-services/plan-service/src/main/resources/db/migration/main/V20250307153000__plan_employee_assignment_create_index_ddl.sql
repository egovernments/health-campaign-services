CREATE INDEX plan_employee_assignment_idx
ON plan_employee_assignment (tenant_id, plan_configuration_id, employee_id, role);

CREATE INDEX pea_plan_configuration_id_idx
ON plan_employee_assignment (plan_configuration_id);

