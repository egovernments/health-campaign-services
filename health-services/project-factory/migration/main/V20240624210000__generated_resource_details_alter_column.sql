DO $$
DECLARE
    table_name1 TEXT := 'eg_cm_generated_resource_details';
    column_name1 TEXT := 'campaignId';
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = table_name1
          AND column_name = column_name1
    ) THEN
        EXECUTE format('ALTER TABLE %I ADD COLUMN %I character varying(128);', table_name1, column_name1);
    END IF;
END $$;