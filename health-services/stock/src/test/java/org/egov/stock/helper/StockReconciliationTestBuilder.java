package org.egov.stock.helper;

import org.egov.common.helper.AuditDetailsTestBuilder;
import org.egov.common.models.stock.StockReconciliation;


public class StockReconciliationTestBuilder {

    private final StockReconciliation.StockReconciliationBuilder builder;

    public StockReconciliationTestBuilder() {
        this.builder = StockReconciliation.builder();
    }

    public static StockReconciliationTestBuilder builder() {
        return new StockReconciliationTestBuilder();
    }

    public StockReconciliation build() {
        return this.builder.build();
    }

    public StockReconciliationTestBuilder withStock() {
        this.builder.facilityId("sender-id").productVariantId("pv-id").physicalCount(10)
                .calculatedCount(100).referenceId("reference-id")
                .referenceIdType("PROJECT").rowVersion(1).tenantId("default").hasErrors(false).isDeleted(Boolean.FALSE)
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build());
        return this;
    }

    public StockReconciliationTestBuilder withId(String id) {
        this.builder.id(id);
        return this;
    }
}
