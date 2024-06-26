CREATE INDEX IF NOT EXISTS idx_individual_address_individual_type_lastmodified ON INDIVIDUAL_ADDRESS (individualId, type, lastModifiedTime);
CREATE INDEX IF NOT EXISTS idx_individual_address_covering ON INDIVIDUAL_ADDRESS (individualId, type, lastModifiedTime, addressId, isDeleted);
