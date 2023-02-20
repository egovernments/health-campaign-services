package org.egov.stock.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.stock.web.models.StockRequest;

public class StockRequestTestBuilder {
    private StockRequest.StockRequestBuilder builder;

    public StockRequestTestBuilder() {
        this.builder = StockRequest.builder();
    }

    public static StockRequestTestBuilder builder() {
        return new StockRequestTestBuilder();
    }

    public StockRequest build() {
        return this.builder.build();
    }

    public StockRequestTestBuilder withStock() {
        this.builder.stock(StockTestBuilder.builder().withStock().build());
        return this;
    }

    public StockRequestTestBuilder withRequestInfo() {
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }
}
