CREATE INDEX
    idx_id_clientReferenceId_facilityId_productVariantId_referenceId_wayBillNumber_
    referenceIdType_transactionType_transactionReason_transactingPartyId_transactingPartyType
    ON
    STOCK (
    id,
    clientReferenceId,
    facilityId,
    productVariantId,
    referenceId,
    wayBillNumber,
    referenceIdType,
    transactionType,
    transactionReason,
    transactingPartyId,
    transactingPartyType);

CREATE INDEX
    idx_id_clientReferenceId_facilityId_productVariantId
    ON
    STOCK_RECONCILIATION_LOG (
    id,
    clientReferenceId,
    facilityId,
    productVariantId);

