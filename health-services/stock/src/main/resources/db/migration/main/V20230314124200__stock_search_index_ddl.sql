CREATE INDEX idx_id ON STOCK (id);
CREATE INDEX idx_clientReferenceId ON STOCK (clientReferenceId);
CREATE INDEX idx_facilityId ON STOCK (facilityId);
CREATE INDEX idx_productVariantId ON STOCK (productVariantId);
CREATE INDEX idx_referenceId ON STOCK (referenceId);
CREATE INDEX idx_wayBillNumber ON STOCK (wayBillNumber);
CREATE INDEX idx_referenceIdType ON STOCK (referenceIdType);
CREATE INDEX idx_transactionType ON STOCK (transactionType);
CREATE INDEX idx_transactionReason ON STOCK (transactionReason);
CREATE INDEX idx_transactingPartyId ON STOCK (transactingPartyId);
CREATE INDEX idx_transactingPartyType ON STOCK (transactingPartyType);

CREATE INDEX idx_id ON STOCK_RECONCILIATION_LOG (id);
CREATE INDEX idx_clientReferenceId ON STOCK_RECONCILIATION_LOG (clientReferenceId);
CREATE INDEX idx_facilityId ON STOCK_RECONCILIATION_LOG (facilityId);
CREATE INDEX idx_productVariantId ON STOCK_RECONCILIATION_LOG (productVariantId);