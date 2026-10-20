ALTER TABLE downsync_generation_locality ADD COLUMN IF NOT EXISTS category VARCHAR(10);

ALTER TABLE downsync_locality_file ADD COLUMN IF NOT EXISTS filesize BIGINT;
