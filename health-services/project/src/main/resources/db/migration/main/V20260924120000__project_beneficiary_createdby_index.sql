-- Mode B of /beneficiary/team/v1/_update resolves a team's registrations by their creator.
-- createdBy holds the egov-user uuid and was unindexed, so the lookup would otherwise
-- sequentially scan the largest table in the schema.
--
-- id is the third column so that the keyset cursor (id > :afterId) is satisfied by the index
-- itself rather than as a heap filter, and so the ORDER BY id needs no sort. Without it, every
-- page of a prolific user re-scans all of that user's index entries.
CREATE INDEX IF NOT EXISTS idx_project_beneficiary_tenantid_createdby_id
    ON PROJECT_BENEFICIARY (tenantId, createdBy, id);
