package org.egov.stock.helper;

import org.egov.common.helper.AuditDetailsTestBuilder;
import org.egov.common.models.stock.ReferenceIdType;
import org.egov.common.models.stock.SenderReceiverType;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.TransactionReason;
import org.egov.common.models.stock.TransactionType;


public class StockTestBuilder {

    private final Stock.StockBuilder<Stock, ?> builder;

    public StockTestBuilder() {
        this.builder = (Stock.StockBuilder<Stock, ?>) Stock.builder();
    }

    public static StockTestBuilder builder() {
        return new StockTestBuilder();
    }

    public Stock build() {
        return this.builder.build();
    }

    public StockTestBuilder withStock() {
        this.builder
        	.senderId("sender-id")
        	.receiverId("receiver-id")
        	.productVariantId("pv-id")
            .quantity(1)
        	.referenceId("reference-id")
            .referenceIdType(ReferenceIdType.PROJECT)
            .rowVersion(1)
            .tenantId("default")
            .transactionType(TransactionType.DISPATCHED)
            .transactionReason(TransactionReason.RECEIVED)
            .senderType(SenderReceiverType.WAREHOUSE)
            .receiverType(SenderReceiverType.STAFF)
            .hasErrors(false)
            .isDeleted(Boolean.FALSE)
            .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build());
        return this;
    }

    public StockTestBuilder withId(String id) {
        this.builder.id(id);
        return this;
    }
}
