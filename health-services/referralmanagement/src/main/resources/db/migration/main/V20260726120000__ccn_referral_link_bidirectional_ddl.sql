-- Extend the ccn correlation table to track BOTH directions in one place:
--   OUTBOUND = referral we started (HCM BAP -> SPICE)
--   INBOUND  = referral/task that came from another system (SPICE BAP -> HCM BPP)
-- Existing rows default to OUTBOUND/BAP (no data loss).
ALTER TABLE ccn_referral_link
  ADD COLUMN IF NOT EXISTS direction                  varchar(16)  DEFAULT 'OUTBOUND',
  ADD COLUMN IF NOT EXISTS local_role                 varchar(8)   DEFAULT 'BAP',
  ADD COLUMN IF NOT EXISTS initiator_subscriber_id    varchar(128),
  ADD COLUMN IF NOT EXISTS counterparty_subscriber_id varchar(128),
  ADD COLUMN IF NOT EXISTS contract_type              varchar(32),
  ADD COLUMN IF NOT EXISTS service_category           varchar(64),
  ADD COLUMN IF NOT EXISTS target_booking_ref         varchar(128),
  ADD COLUMN IF NOT EXISTS last_payload               text;

CREATE INDEX IF NOT EXISTS idx_ccn_link_direction ON ccn_referral_link (direction);
