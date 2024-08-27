CREATE INDEX idx_stock_clientReferenceId ON STOCK (clientReferenceId);
CREATE INDEX idx_stock_facilityId ON STOCK (facilityId);
CREATE INDEX idx_stock_productVariantId ON STOCK (productVariantId);
CREATE INDEX idx_stock_referenceId ON STOCK (referenceId);
CREATE INDEX idx_stock_wayBillNumber ON STOCK (wayBillNumber);
CREATE INDEX idx_stock_referenceIdType ON STOCK (referenceIdType);
CREATE INDEX idx_stock_transactionType ON STOCK (transactionType);
CREATE INDEX idx_stock_transactionReason ON STOCK (transactionReason);
CREATE INDEX idx_stock_transactingPartyId ON STOCK (transactingPartyId);
CREATE INDEX idx_stock_transactingPartyType ON STOCK (transactingPartyType);

CREATE INDEX idx_stock_recondiliation_clientReferenceId ON STOCK_RECONCILIATION_LOG (clientReferenceId);
CREATE INDEX idx_stock_recondiliation_facilityId ON STOCK_RECONCILIATION_LOG (facilityId);
CREATE INDEX idx_stock_recondiliation_productVariantId ON STOCK_RECONCILIATION_LOG (productVariantId);