-- Fill existing clientReferenceId column with null uuid values
DO $$
BEGIN
    LOOP
        -- Exit the loop if no more rows need updating
        EXIT WHEN NOT EXISTS (
          SELECT 1 FROM INDIVIDUAL_IDENTIFIER WHERE clientReferenceId IS NULL
        );

        -- Update a batch of rows
        UPDATE INDIVIDUAL_IDENTIFIER
        SET clientReferenceId = gen_random_uuid()::text
        WHERE id IN (
            SELECT id FROM INDIVIDUAL_IDENTIFIER
            WHERE clientReferenceId IS NULL
            LIMIT 1000
        );
    END LOOP;
END $$;
