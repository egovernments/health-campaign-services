package org.egov.stock.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.stock.StockReconciliationRequest;


public class StockReconciliationRequestTestBuilder {
    private StockReconciliationRequest.StockReconciliationRequestBuilder builder;

    public StockReconciliationRequestTestBuilder() {
        this.builder = StockReconciliationRequest.builder();
    }

    public static StockReconciliationRequestTestBuilder builder() {
        return new StockReconciliationRequestTestBuilder();
    }

    public StockReconciliationRequest build() {
        return this.builder.build();
    }

    public StockReconciliationRequestTestBuilder withReconciliation() {
        this.builder.stockReconciliation(StockReconciliationTestBuilder.builder().withStock().build());
        return this;
    }

    public StockReconciliationRequestTestBuilder withRequestInfo() {
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }
}
