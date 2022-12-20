package org.egov.product.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.product.web.models.ApiOperation;
import org.egov.product.web.models.Product;
import org.egov.product.web.models.ProductRequest;

import java.util.ArrayList;

public class ProductRequestTestBuilder {

    private ProductRequest.ProductRequestBuilder builder;

    private ArrayList products = new ArrayList();

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
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }

    public ProductRequestTestBuilder addGoodProduct(){
        products.add(ProductTestBuilder.builder().goodProduct().build());
        this.builder.product(products);
        return this;
    }

    public ProductRequestTestBuilder addGoodProductWithId(String id) {
        products.add(ProductTestBuilder.builder().goodProduct().withId(id).build());
        this.builder.product(products);
        return this;
    }
    public ProductRequestTestBuilder addBadProduct(){
        products.add(ProductTestBuilder.builder().badProduct().build());
        this.builder.product(products);
        return this;
    }
    public ProductRequestTestBuilder addGoodProductWithNullTenant(){
        products.add(ProductTestBuilder.builder().goodProductWithNullTenant().build());
        this.builder.product(products);
        return this;
    }

    public ProductRequestTestBuilder add(Product product){
        this.products.add(product);
        this.builder.product(products);
        return this;
    }

    public ProductRequestTestBuilder withApiOperationCreate(){
        this.builder.apiOperation(ApiOperation.CREATE);
        return this;
    }
    public ProductRequestTestBuilder withApiOperationDelete(){
        this.builder.apiOperation(ApiOperation.DELETE);
        return this;
    }
    public ProductRequestTestBuilder withApiOperationUpdate(){
        this.builder.apiOperation(ApiOperation.UPDATE);
        return this;
    }

}
