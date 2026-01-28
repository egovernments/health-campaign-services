CREATE INDEX plan_employee_assignment_idx
ON plan_employee_assignment (plan_configuration_id, tenant_id, employee_id, role);

