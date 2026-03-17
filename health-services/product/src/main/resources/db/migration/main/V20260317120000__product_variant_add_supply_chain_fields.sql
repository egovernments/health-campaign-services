ALTER TABLE PRODUCT_VARIANT
    ADD COLUMN IF NOT EXISTS gtin          character varying(14),
    ADD COLUMN IF NOT EXISTS batchNumber   character varying(20),
    ADD COLUMN IF NOT EXISTS serialNumber  character varying(20),
    ADD COLUMN IF NOT EXISTS expiryDate    bigint,
    ADD COLUMN IF NOT EXISTS baseUnit      character varying(100),
    ADD COLUMN IF NOT EXISTS netContent    bigint;
