UPDATE PRODUCT SET additionalDetails=NULL;
ALTER TABLE PRODUCT ALTER COLUMN additionalDetails TYPE jsonb using to_jsonb(additionalDetails)::jsonb;