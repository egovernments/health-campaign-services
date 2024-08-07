DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'PROJECT_TASK' AND column_name = 'taskStatus'
    ) THEN
        ALTER TABLE PROJECT_TASK RENAME COLUMN status TO taskStatus;
        ALTER TABLE PROJECT_TASK ADD COLUMN status varchar(1000);
    END IF;
END $$;
