CREATE INDEX idx_id_isPermanent_usage_storageCapacity ON FACILITY (id, isPermanent, usage, storageCapacity);
CREATE INDEX IF NOT EXISTS idx_localityCode ON ADDRESS (localityCode);