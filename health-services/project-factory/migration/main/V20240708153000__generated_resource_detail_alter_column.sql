DO $$
DECLARE
    table_name1 TEXT := 'eg_cm_generated_resource_details';
    column_name1 TEXT := 'campaignId';
    column_name2 TEXT := 'campaignid';
BEGIN
    -- Check if "campaignId" column exists and drop it if it does
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = table_name1
          AND column_name = column_name1
    ) THEN
        EXECUTE format('ALTER TABLE %I DROP COLUMN %I;', table_name1, column_name1);
    END IF;

    -- Check if "campaignid" column exists (case-insensitive) and create it if it doesn't
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = table_name1
          AND lower(column_name) = lower(column_name2)
    ) THEN
        EXECUTE format('ALTER TABLE %I ADD COLUMN %I character varying(128);', table_name1, column_name2);
    END IF;
END $$;
