-- Track what CCN/SPICE returns AFTER an HCM-initiated update (accept/reject/complete/cancel):
--   post_update_ack   = the immediate ACK / NACK(+error) CCN returns to our on_update / update push
--   post_update_state = the lifecycleState SPICE reports in its next callback after our update
-- Both are separate from lifecycle_state so the pre-update and post-update views are distinguishable.
ALTER TABLE ccn_referral_link
  ADD COLUMN IF NOT EXISTS post_update_ack   text,
  ADD COLUMN IF NOT EXISTS post_update_state varchar(64);
