CREATE INDEX census_idx
ON census (tenant_id, source, status) INCLUDE (id);
