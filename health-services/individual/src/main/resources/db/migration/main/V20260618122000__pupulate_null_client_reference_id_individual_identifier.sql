-- 4. Fill existing clientReferenceId column with null uuid values
UPDATE INDIVIDUAL_IDENTIFIER
SET clientReferenceId = gen_random_uuid()::text
WHERE clientReferenceId IS NULL;
