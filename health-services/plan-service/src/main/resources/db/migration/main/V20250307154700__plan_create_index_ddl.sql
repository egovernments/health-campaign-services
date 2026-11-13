CREATE INDEX plan_idx
ON plan (plan_configuration_id, tenant_id, status) INCLUDE (id);