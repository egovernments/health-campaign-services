package org.egov.stock.helper;

import org.egov.common.helper.AuditDetailsTestBuilder;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.TransactionReason;
import org.egov.common.models.stock.TransactionType;


public class StockTestBuilder {

    private final Stock.StockBuilder builder;

    public StockTestBuilder() {
        this.builder = Stock.builder();
    }

    public static StockTestBuilder builder() {
        return new StockTestBuilder();
    }

    public Stock build() {
        return this.builder.build();
    }

    public StockTestBuilder withStock() {
        this.builder.facilityId("facility-id").productVariantId("pv-id").quantity(0).referenceId("reference-id")
                .referenceIdType("PROJECT").rowVersion(1).tenantId("default").transactingPartyId("transaction-party-id")
                .transactionType(TransactionType.DISPATCHED).transactionReason(TransactionReason.RECEIVED)
                .transactingPartyType("WAREHOUSE").hasErrors(false).isDeleted(Boolean.FALSE)
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build());
        return this;
    }

    public StockTestBuilder withId(String id) {
        this.builder.id(id);
        return this;
    }
}
