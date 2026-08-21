CREATE INDEX census_idx
ON census (source, tenant_id, status) INCLUDE (id);
