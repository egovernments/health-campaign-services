CREATE INDEX idx_facility_id on FACILITY (id);
CREATE INDEX idx_facility_isPermanent on FACILITY (isPermanent);
CREATE INDEX idx_facility_usage on FACILITY (usage);
CREATE INDEX idx_facility_storageCapacity on FACILITY (storageCapacity);

CREATE INDEX IF NOT EXISTS idx_localityCode ON ADDRESS (localityCode);