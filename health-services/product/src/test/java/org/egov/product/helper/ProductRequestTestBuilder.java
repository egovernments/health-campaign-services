package org.egov.product.helper;

import org.egov.product.web.models.Product;
import org.egov.product.web.models.ProductRequest;

public class ProductRequestTestBuilder {

    private ProductRequest.ProductRequestBuilder builder;

    public ProductRequestTestBuilder() {
        this.builder = ProductRequest.builder();
    }

    public static ProductRequestTestBuilder builder() {
        return new ProductRequestTestBuilder();
    }

    public ProductRequest build() {
        return this.builder.build();
    }

    public ProductRequestTestBuilder withRequestInfo(){
        //this.withRequestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
    }

}
