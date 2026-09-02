ALTER TABLE eg_hcm_worker_registry
    ADD COLUMN IF NOT EXISTS beneficiaryCode character varying(256);
