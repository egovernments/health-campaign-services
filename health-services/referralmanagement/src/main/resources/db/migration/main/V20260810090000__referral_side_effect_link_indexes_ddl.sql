-- Index the clientReferenceId link columns on REFERRAL and SIDE_EFFECT.
--
-- These four columns are join/lookup keys for downsync and search, but carried no index:
--   REFERRAL.projectBeneficiaryClientReferenceId
--   REFERRAL.sideEffectClientReferenceId
--   SIDE_EFFECT.taskClientReferenceId
--   SIDE_EFFECT.projectBeneficiaryClientReferenceId
--
-- The equivalent link columns on the sibling tables are already indexed
-- (household_member.householdClientReferenceId / individualClientReferenceId,
--  project_task.projectBeneficiaryClientReferenceId,
--  project_beneficiary.beneficiaryClientReferenceId), so referral and side_effect
-- were the outliers. This presents as downsync/search slowdown at scale rather than
-- as an error, which is why it stayed invisible.
--
-- IF NOT EXISTS keeps this safe to re-run and safe on environments where an index
-- was already added out of band.

CREATE INDEX IF NOT EXISTS idx_referral_projectBeneficiaryClientReferenceId
    ON REFERRAL (projectBeneficiaryClientReferenceId);

CREATE INDEX IF NOT EXISTS idx_referral_sideEffectClientReferenceId
    ON REFERRAL (sideEffectClientReferenceId);

CREATE INDEX IF NOT EXISTS idx_side_effect_taskClientReferenceId
    ON SIDE_EFFECT (taskClientReferenceId);

CREATE INDEX IF NOT EXISTS idx_side_effect_projectBeneficiaryClientReferenceId
    ON SIDE_EFFECT (projectBeneficiaryClientReferenceId);
