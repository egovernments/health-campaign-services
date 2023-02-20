package org.egov.stock.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.stock.web.models.StockReconciliation;
import org.egov.stock.web.models.StockReconciliationBulkRequest;

import java.util.ArrayList;

public class StockReconciliationBulkRequestTestBuilder {
    private StockReconciliationBulkRequest.StockReconciliationBulkRequestBuilder builder;

    public StockReconciliationBulkRequestTestBuilder() {
        this.builder = StockReconciliationBulkRequest.builder();
    }

    ArrayList<StockReconciliation> stocks = new ArrayList<>();

    public static StockReconciliationBulkRequestTestBuilder builder() {
        return new StockReconciliationBulkRequestTestBuilder();
    }

    public StockReconciliationBulkRequest build() {
        return this.builder.build();
    }

    public StockReconciliationBulkRequestTestBuilder withStock() {
        stocks.add(StockReconciliationTestBuilder.builder().withStock().build());
        this.builder.stockReconciliation(stocks);
        return this;
    }

    public StockReconciliationBulkRequestTestBuilder withStockId(String id) {
        stocks.add(StockReconciliationTestBuilder.builder().withStock().withId(id).build());
        this.builder.stockReconciliation(stocks);
        return this;
    }

    public StockReconciliationBulkRequestTestBuilder withRequestInfo() {
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }
}
