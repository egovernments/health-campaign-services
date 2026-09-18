-- Adds a btree index on INDIVIDUAL.username.
-- Username searches (user-sheet validation username-uniqueness check) did a full sequential scan
-- of the individual table because this column (added in V20230515122200, varchar(64)) was never
-- indexed. The search filters it directly via "username IN (:username)".
-- Measured: username batch searches ran ~1s (seq scan) vs mobileNumber ~100ms after its index.
CREATE INDEX IF NOT EXISTS idx_individual_username ON INDIVIDUAL (username);
