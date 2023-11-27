CREATE TABLE IF NOT EXISTS INDIVIDUAL_MIGRATION_FOR_ENCRYPTION
(
    individualId character varying(64),
    isMigrated  boolean,
    migratedTime bigint,
    PRIMARY KEY (individualId)
)