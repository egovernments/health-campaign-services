-- Add PRIMARY KEY (id) to household_member.
--
-- household_member.id has never carried a uniqueness constraint, so some schemas may hold
-- NULL or duplicate ids. This migration runs automatically on every deploy, once per tenant
-- schema (migrate.sh loops over SCHEMA_NAME), so it must never fail the deploy and must never
-- modify or delete existing rows. It therefore:
--
--   * is IDEMPOTENT: if the table already has a primary key it does nothing;
--   * is DATA-SAFE: it only adds a constraint; no row is updated or deleted;
--   * SKIPS (with a WARNING in the Flyway log) instead of failing when the schema holds NULL
--     or duplicate ids, or when the table lock cannot be taken within lock_timeout. A skipped
--     schema keeps working exactly as before; it needs a follow-up migration once its data
--     has been cleaned.
--
-- Note: ADD PRIMARY KEY holds an ACCESS EXCLUSIVE lock on the table while the index is built,
-- so reads/writes of household_member in that schema wait for the build to finish.

DO $$
DECLARE
    v_null_ids bigint;
    v_dup_ids  bigint;
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint
                WHERE conrelid = 'household_member'::regclass AND contype = 'p') THEN
        RAISE NOTICE 'household_member: primary key already present, nothing to do';
        RETURN;
    END IF;

    -- Do not queue behind long-running transactions (and block everyone queued behind us).
    PERFORM set_config('lock_timeout', '5s', true);

    BEGIN
        ALTER TABLE household_member
            ADD CONSTRAINT pk_household_member PRIMARY KEY (id);
        RAISE NOTICE 'household_member: primary key pk_household_member added';
    EXCEPTION
        WHEN lock_not_available THEN
            RAISE WARNING 'household_member: SKIPPED adding primary key, table lock not acquired within lock_timeout (%)', SQLERRM;
        WHEN not_null_violation OR unique_violation THEN
            SELECT count(*) INTO v_null_ids FROM household_member WHERE id IS NULL;
            SELECT count(*) INTO v_dup_ids
              FROM (SELECT id FROM household_member
                     WHERE id IS NOT NULL GROUP BY id HAVING count(*) > 1) d;
            RAISE WARNING 'household_member: SKIPPED adding primary key, existing data violates it (rows with NULL id: %, ids repeated: %). No data was changed.',
                v_null_ids, v_dup_ids;
    END;
END $$;
