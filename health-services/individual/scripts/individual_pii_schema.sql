-- PII encryption at rest (individual service).
-- Run ONLY by pii_migrate.py (its 'schema' step, inside 'migrate'), in one transaction. It is not a Flyway migration:
-- every environment runs it once, before the new individual image starts.
-- Cipher text is stored in the EXISTING columns; no column is added for cipher text.
--  * Text PII columns become TEXT: cipher text is longer than the plain value.
--  * dateOfBirth (date) becomes TEXT. Existing values are kept as ISO text (YYYY-MM-DD), which is also
--    the text the service encrypts, so old rows stay readable before and after the backfill.
--  * The *Hash columns hold the HMAC-SHA256 used for exact-match search (no PII value), only for the
--    fields callers search by: mobile number, given name, username and identifier ID.
--  * ADDRESS latitude/longitude stay numbers (not encrypted).
--
-- household_address_mv (created by referralmanagement) uses ADDRESS columns, and Postgres refuses to change
-- the type of a column a view uses. The view and its indexes are saved, dropped, and recreated from the saved
-- text after the type changes. If the view does not exist, nothing is done for it.

CREATE TEMP TABLE pii_saved_mv (kind TEXT, ord INT, ddl TEXT) ON COMMIT DROP;

DO $$
DECLARE
    mv regclass := to_regclass('household_address_mv');
BEGIN
    IF mv IS NOT NULL THEN
        INSERT INTO pii_saved_mv
            SELECT 'view', 0, 'CREATE MATERIALIZED VIEW household_address_mv AS ' || pg_get_viewdef(mv, true);
        INSERT INTO pii_saved_mv
            SELECT 'index', row_number() OVER (ORDER BY indexname), indexdef
            FROM pg_indexes
            WHERE schemaname = current_schema() AND tablename = 'household_address_mv';
        EXECUTE 'DROP MATERIALIZED VIEW household_address_mv';
    END IF;
END $$;

ALTER TABLE individual ALTER COLUMN givenName TYPE TEXT;
ALTER TABLE individual ALTER COLUMN familyName TYPE TEXT;
ALTER TABLE individual ALTER COLUMN otherNames TYPE TEXT;
ALTER TABLE individual ALTER COLUMN fatherName TYPE TEXT;
ALTER TABLE individual ALTER COLUMN husbandName TYPE TEXT;
ALTER TABLE individual ALTER COLUMN gender TYPE TEXT;
ALTER TABLE individual ALTER COLUMN bloodGroup TYPE TEXT;
ALTER TABLE individual ALTER COLUMN altContactNumber TYPE TEXT;
ALTER TABLE individual ALTER COLUMN email TYPE TEXT;
ALTER TABLE individual ALTER COLUMN username TYPE TEXT;
-- date -> text rewrites the table (exclusive lock while it runs). to_char keeps the ISO form whatever DateStyle is.
-- Only while the column is still a date, so the script can run again.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND table_name = 'individual'
                 AND column_name = 'dateofbirth' AND data_type = 'date') THEN
        EXECUTE 'ALTER TABLE individual ALTER COLUMN dateOfBirth TYPE TEXT USING to_char(dateOfBirth, ''YYYY-MM-DD'')';
    END IF;
END $$;

ALTER TABLE individual ADD COLUMN IF NOT EXISTS mobileNumberHash VARCHAR(64);
ALTER TABLE individual ADD COLUMN IF NOT EXISTS givenNameHash VARCHAR(64);
ALTER TABLE individual ADD COLUMN IF NOT EXISTS usernameHash VARCHAR(64);

ALTER TABLE individual_identifier ADD COLUMN IF NOT EXISTS identifierIdHash VARCHAR(64);

-- ADDRESS is shared with household, facility and project. Only the individual's rows hold cipher text;
-- the other services keep writing plain values into the same (wider) columns.
ALTER TABLE address ALTER COLUMN doorNo TYPE TEXT;
ALTER TABLE address ALTER COLUMN addressLine1 TYPE TEXT;
ALTER TABLE address ALTER COLUMN addressLine2 TYPE TEXT;
ALTER TABLE address ALTER COLUMN landmark TYPE TEXT;
ALTER TABLE address ALTER COLUMN city TYPE TEXT;
ALTER TABLE address ALTER COLUMN pincode TYPE TEXT;
ALTER TABLE address ALTER COLUMN buildingName TYPE TEXT;
ALTER TABLE address ALTER COLUMN street TYPE TEXT;

-- Recreate the view and its indexes exactly as they were (from the saved definitions).
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT ddl FROM pii_saved_mv ORDER BY CASE kind WHEN 'view' THEN 0 ELSE 1 END, ord LOOP
        EXECUTE r.ddl;
    END LOOP;
END $$;

-- Plain (not CONCURRENTLY): pii_migrate.py runs this file in one transaction.
CREATE INDEX IF NOT EXISTS idx_individual_mobilenumberhash ON individual (mobileNumberHash);
CREATE INDEX IF NOT EXISTS idx_individual_givennamehash ON individual (givenNameHash);
CREATE INDEX IF NOT EXISTS idx_individual_usernamehash ON individual (usernameHash);
CREATE INDEX IF NOT EXISTS idx_individual_identifier_identifieridhash ON individual_identifier (identifierIdHash);
