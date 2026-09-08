-- Add NOT NULL and DEFAULT constraints to id_pool
ALTER TABLE id_pool
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN tenantId SET NOT NULL,
    ALTER COLUMN rowVersion SET NOT NULL;

-- Add NOT NULL and DEFAULT constraints to id_transaction_log
ALTER TABLE id_transaction_log
    ALTER COLUMN tenantId SET NOT NULL,
    ALTER COLUMN rowVersion SET NOT NULL;
