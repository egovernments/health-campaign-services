ALTER TABLE IF EXISTS address
    ALTER COLUMN locationAccuracy TYPE double precision
    USING locationAccuracy::double precision;

ALTER TABLE IF EXISTS project_address
    ALTER COLUMN locationAccuracy TYPE double precision
    USING locationAccuracy::double precision;

ALTER TABLE IF EXISTS user_location
    ALTER COLUMN locationAccuracy TYPE double precision
    USING locationAccuracy::double precision;

ALTER TABLE IF EXISTS user_action
    ALTER COLUMN locationAccuracy TYPE double precision
    USING locationAccuracy::double precision;
