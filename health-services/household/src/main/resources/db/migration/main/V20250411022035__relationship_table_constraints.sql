ALTER TABLE household_member_relationship
    ALTER COLUMN selfId SET NOT NULL,
    ALTER COLUMN relationshipType SET NOT NULL,
    ALTER COLUMN tenantId SET NOT NULL;

ALTER TABLE household_member_relationship
    ADD CONSTRAINT hmr_relative_id_not_null
        CHECK (relativeId IS NOT NULL OR relativeClientReferenceId IS NOT NULL);

