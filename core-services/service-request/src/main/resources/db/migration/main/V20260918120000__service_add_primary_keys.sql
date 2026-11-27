-- Formalise the identity rules already in force on the service (submission) tables.
--
-- eg_service_definition and eg_service_attribute_definition declare primary keys; these two
-- were created with only UNIQUE(id). A bare UNIQUE permits NULLs, and in PostgreSQL every NULL
-- is distinct, so an unbounded number of NULL-id rows is permissible. Such rows are invisible
-- to the id-based _search path (ServiceRowMapper skips blank ids) and unreachable by _update.
--
-- Ids are always server-generated UUIDs before the Kafka publish
-- (ServiceRequestEnrichmentService), never client-supplied, so no write path changes.
--
-- This script is IDEMPOTENT: each step is guarded so the migration converges on the target
-- state whether the environment is pristine or has had part of this work applied out-of-band.
-- Re-running it is safe.

-- eg_service ----------------------------------------------------------------
ALTER TABLE eg_service
    ALTER COLUMN id SET NOT NULL;

ALTER TABLE eg_service
    DROP CONSTRAINT IF EXISTS uk_eg_service;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = 'eg_service'::regclass
           AND contype  = 'p'
    ) THEN
        ALTER TABLE eg_service
            ADD CONSTRAINT pk_eg_service PRIMARY KEY (id);
    END IF;
END $$;

-- eg_service_attribute_value ------------------------------------------------
ALTER TABLE eg_service_attribute_value
    ALTER COLUMN id SET NOT NULL;

ALTER TABLE eg_service_attribute_value
    DROP CONSTRAINT IF EXISTS uk_eg_attribute_value;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = 'eg_service_attribute_value'::regclass
           AND contype  = 'p'
    ) THEN
        ALTER TABLE eg_service_attribute_value
            ADD CONSTRAINT pk_eg_service_attribute_value PRIMARY KEY (id);
    END IF;
END $$;

-- referenceId joins attribute values back to eg_service.id. Used by
-- ServiceQueryBuilder.getServiceSearchQuery and by the referralmanagement downsync;
-- previously unindexed.
CREATE INDEX IF NOT EXISTS idx_eg_service_attribute_value_referenceid
    ON eg_service_attribute_value (referenceId);
