
ALTER TABLE eg_service
    ALTER COLUMN id SET NOT NULL;
ALTER TABLE eg_service
    DROP CONSTRAINT uk_eg_service,
    ADD CONSTRAINT pk_eg_service PRIMARY KEY (id);

ALTER TABLE eg_service_attribute_value
    ALTER COLUMN id SET NOT NULL;
ALTER TABLE eg_service_attribute_value
    DROP CONSTRAINT uk_eg_attribute_value,
    ADD CONSTRAINT pk_eg_service_attribute_value PRIMARY KEY (id);

-- referenceId joins attribute values back to eg_service.id. Used by
-- ServiceQueryBuilder.getServiceSearchQuery and by the referralmanagement downsync;
-- previously unindexed.
CREATE INDEX IF NOT EXISTS idx_eg_service_attribute_value_referenceid
    ON eg_service_attribute_value (referenceId);
