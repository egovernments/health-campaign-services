CREATE INDEX plan_idx
ON plan (tenant_id, plan_configuration_id, status) INCLUDE (id);