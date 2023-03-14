CREATE INDEX idx_id ON HOUSEHOLD (id);
CREATE INDEX idx_clientReferenceId ON HOUSEHOLD (clientReferenceId);

CREATE INDEX IF NOT EXISTS idx_localityCode ON ADDRESS (localityCode);
