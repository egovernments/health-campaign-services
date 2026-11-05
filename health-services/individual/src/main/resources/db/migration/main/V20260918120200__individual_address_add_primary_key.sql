-- Promote individual_address's existing unique key to the PRIMARY KEY.
--
-- Target: PRIMARY KEY (individualId, addressId, type, isDeleted), i.e. exactly the columns of
-- uk_individual_address_mapping (V20230123181900). Keeping isDeleted in the key means:
--   * data that satisfies today's unique constraint also satisfies the PK (only NULLs can block);
--   * a soft-deleted and an active mapping of the same address can still coexist;
--   * the persister's  ON CONFLICT (individualId, addressId, type, isDeleted)  still finds a
--     matching unique index.
--
-- This migration runs automatically on every deploy, once per tenant schema (migrate.sh loops
-- over SCHEMA_NAME), so it must never fail the deploy and must never modify or delete rows:
--   * IDEMPOTENT: converges from any starting state: original (unique key only), already on
--     the target PK, or carrying the earlier 3-column pk_individual_address;
--   * DATA-SAFE: only constraints change; no row is updated or deleted;
--   * SKIPS with a WARNING in the Flyway log, leaving the table as it was, when existing data
--     violates the PK (NULL key columns) or the table lock cannot be taken within lock_timeout.
--     Whatever happens, a 4-column unique key is left in place for the persister's ON CONFLICT.

DO $$
DECLARE
    v_target   text[] := ARRAY['individualid', 'addressid', 'type', 'isdeleted'];
    v_pk_name  text;
    v_pk_cols  text[];
    v_nulls    bigint;
    v_dups     bigint;
BEGIN
    -- Do not queue behind long-running transactions (and block everyone queued behind us).
    PERFORM set_config('lock_timeout', '5s', true);

    SELECT c.conname, array_agg(a.attname::text ORDER BY k.ord)
      INTO v_pk_name, v_pk_cols
      FROM pg_constraint c
      CROSS JOIN LATERAL unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord)
      JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum
     WHERE c.conrelid = 'individual_address'::regclass
       AND c.contype  = 'p'
     GROUP BY c.conname;

    IF v_pk_cols = v_target THEN
        -- Already on the target PK; the old unique key would only be a duplicate index.
        BEGIN
            ALTER TABLE individual_address DROP CONSTRAINT IF EXISTS uk_individual_address_mapping;
        EXCEPTION WHEN lock_not_available THEN
            RAISE WARNING 'individual_address: primary key present; redundant uk_individual_address_mapping kept, table lock not acquired (%)', SQLERRM;
        END;
        RAISE NOTICE 'individual_address: primary key already present, nothing to do';
        RETURN;
    END IF;

    BEGIN
        IF v_pk_name IS NOT NULL THEN
            -- Earlier version of this migration created PK (individualId, addressId, type).
            EXECUTE format('ALTER TABLE individual_address DROP CONSTRAINT %I', v_pk_name);
        END IF;

        ALTER TABLE individual_address
            DROP CONSTRAINT IF EXISTS uk_individual_address_mapping,
            ADD CONSTRAINT pk_individual_address
                PRIMARY KEY (individualId, addressId, type, isDeleted);
        RAISE NOTICE 'individual_address: primary key pk_individual_address added';
    EXCEPTION
        WHEN lock_not_available THEN
            RAISE WARNING 'individual_address: SKIPPED adding primary key, table lock not acquired within lock_timeout (%)', SQLERRM;
        WHEN not_null_violation OR unique_violation THEN
            SELECT count(*) INTO v_nulls FROM individual_address
             WHERE individualId IS NULL OR addressId IS NULL OR type IS NULL OR isDeleted IS NULL;
            SELECT count(*) INTO v_dups
              FROM (SELECT 1 FROM individual_address
                     GROUP BY individualId, addressId, type, isDeleted HAVING count(*) > 1) d;
            RAISE WARNING 'individual_address: SKIPPED adding primary key, existing data violates it (rows with a NULL key column: %, key combinations repeated: %). No data was changed.',
                v_nulls, v_dups;
    END;

    -- On a skip the table is back to its prior state. Make sure the persister's ON CONFLICT
    -- still has its 4-column unique key (only missing on schemas that ran the earlier version).
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conrelid = 'individual_address'::regclass
                      AND contype IN ('p', 'u')
                      AND conname IN ('pk_individual_address', 'uk_individual_address_mapping')
                      AND array_length(conkey, 1) = 4) THEN
        BEGIN
            ALTER TABLE individual_address
                ADD CONSTRAINT uk_individual_address_mapping
                    UNIQUE (individualId, addressId, type, isDeleted);
            RAISE NOTICE 'individual_address: restored uk_individual_address_mapping';
        EXCEPTION WHEN lock_not_available OR unique_violation THEN
            RAISE WARNING 'individual_address: could not restore uk_individual_address_mapping (%)', SQLERRM;
        END;
    END IF;
END $$;
