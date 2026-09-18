
ALTER TABLE individual_address
    ALTER COLUMN individualId SET NOT NULL,
    ALTER COLUMN addressId    SET NOT NULL,
    ALTER COLUMN type         SET NOT NULL;

ALTER TABLE individual_address
    DROP CONSTRAINT uk_individual_address_mapping,
    ADD CONSTRAINT pk_individual_address PRIMARY KEY (individualId, addressId, type);
