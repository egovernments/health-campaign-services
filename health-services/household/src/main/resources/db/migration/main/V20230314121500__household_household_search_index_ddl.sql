CREATE INDEX idx_household_id ON HOUSEHOLD (id);
CREATE INDEX idx_household_clientReferenceId ON HOUSEHOLD (clientReferenceId);

CREATE INDEX IF NOT EXISTS idx_localityCode ON ADDRESS (localityCode);
