-- Adds a btree index on INDIVIDUAL.mobileNumber.
-- Mobile-number searches (validation, create pre-checks, HRMS user validation) previously did a
-- full sequential scan of the individual table because this column was never indexed
-- (V20230314122200 indexed givenName/familyName/gender/etc. but not mobileNumber).
-- The column stores deterministic ciphertext (keyId|ciphertext), so exact-match IN lookups
-- use this index. Verified live: Index Scan, ~0.17 ms vs a full ~490k-row seq scan.
CREATE INDEX IF NOT EXISTS idx_individual_mobilenumber ON INDIVIDUAL (mobileNumber);
