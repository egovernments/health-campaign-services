CREATE INDEX idx_id_clientReferenceId ON HOUSEHOLD (id, clientReferenceId);
CREATE INDEX IF NOT EXISTS idx_localityCode ON ADDRESS (localityCode);
