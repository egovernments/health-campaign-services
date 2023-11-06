CREATE TABLE IF NOT EXISTS INDIVIDUAL_ENCRYPTION
(
    individualId character varying(64),
    isEncrypted  boolean,
    PRIMARY KEY (individualId)
)