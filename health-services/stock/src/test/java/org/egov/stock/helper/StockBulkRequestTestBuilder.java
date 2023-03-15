package org.egov.stock.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockBulkRequest;

import java.util.ArrayList;

public class StockBulkRequestTestBuilder {
    private StockBulkRequest.StockBulkRequestBuilder builder;

    public StockBulkRequestTestBuilder() {
        this.builder = StockBulkRequest.builder();
    }

    ArrayList<Stock> stocks = new ArrayList<>();

    public static StockBulkRequestTestBuilder builder() {
        return new StockBulkRequestTestBuilder();
    }

    public StockBulkRequest build() {
        return this.builder.build();
    }

    public StockBulkRequestTestBuilder withStock() {
        stocks.add(StockTestBuilder.builder().withStock().build());
        this.builder.stock(stocks);
        return this;
    }

    public StockBulkRequestTestBuilder withStockId(String id) {
        stocks.add(StockTestBuilder.builder().withStock().withId(id).build());
        this.builder.stock(stocks);
        return this;
    }

    public StockBulkRequestTestBuilder withRequestInfo() {
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }
}
