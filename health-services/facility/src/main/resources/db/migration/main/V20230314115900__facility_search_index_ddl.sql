CREATE INDEX idx_id on FACILITY (id);
CREATE INDEX idx_isPermanent on FACILITY (isPermanent);
CREATE INDEX idx_usage on FACILITY (usage);
CREATE INDEX idx_storageCapacity on FACILITY (storageCapacity);

CREATE INDEX IF NOT EXISTS idx_localityCode ON ADDRESS (localityCode);