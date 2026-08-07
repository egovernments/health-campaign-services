ALTER TABLE IF EXISTS address
    ALTER COLUMN locationAccuracy TYPE double precision
    USING locationAccuracy::double precision;
