-- CCN referral correlation. Links a DIGIT HFReferral to its Beckn coordination
-- (transaction/coordination id) and tracks the lifecycle state SPICE returns via ONIX.
-- Owned entirely by the ccn flow; no change to hf_referral or any existing table.
CREATE TABLE IF NOT EXISTS ccn_referral_link (
    coordination_id                 varchar(128) PRIMARY KEY,
    transaction_id                  varchar(128),
    hf_referral_id                  varchar(128),
    hf_referral_client_reference_id varchar(128),
    beneficiary_id                  varchar(128),
    lifecycle_state                 varchar(64),
    last_action                     varchar(64),
    tenant_id                       varchar(128),
    created_time                    bigint,
    last_modified_time              bigint
);

CREATE INDEX IF NOT EXISTS idx_ccn_link_hf_referral_id ON ccn_referral_link (hf_referral_id);
CREATE INDEX IF NOT EXISTS idx_ccn_link_transaction_id ON ccn_referral_link (transaction_id);
