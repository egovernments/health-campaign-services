package org.egov.product.helper;

import digit.models.coremodels.AuditDetails;
import org.egov.common.contract.request.RequestInfo;
import org.egov.product.web.models.AdditionalFields;
import org.egov.product.web.models.ApiOperation;

import org.egov.product.web.models.Product;
import org.egov.product.web.models.ProductRequest;

import java.util.ArrayList;
import java.util.List;

public class ProductRequestTestBuilder {
    private ProductRequest.ProductRequestBuilder builder;

    public ProductRequestTestBuilder(){
        builder = ProductRequest.builder();
    }

    public static ProductRequestTestBuilder builder(){
        return new ProductRequestTestBuilder();
    }

    public ProductRequest build(){
        return this.builder.build();
    }

    public ProductRequestTestBuilder withRequestInfo(){
        builder.requestInfo(RequestInfo.builder().build());
        return this;
    }

    public ProductRequestTestBuilder withValidProducts(){
        List<Product> productList = new ArrayList<>();
        productList.add(Product.builder()
                        .id("P102")
                        .tenantId("pb")
                        .name("Product 1")
                        .type("DRUG")
                        .rowVersion(1)
                        .auditDetails(AuditDetails.builder().build())
                        .additionalFields(AdditionalFields.builder().build())
                .build());

        builder.product(productList);
        return this;
    }

    public ProductRequestTestBuilder withCacheableProducts(){
        List<Product> productList = new ArrayList<>();
        productList.add(Product.builder()
                .id("P101")
                .tenantId("pb")
                .name("Product 1")
                .type("DRUG")
                .rowVersion(1)
                .auditDetails(AuditDetails.builder().build())
                .additionalFields(AdditionalFields.builder().build())
                .build());

        builder.product(productList);
        return this;
    }

    public ProductRequestTestBuilder withInValidProducts(){
        List<Product> productList = new ArrayList<>();
        productList.add(Product.builder()
                .id("P101")
                .tenantId("pb")
                .type("DRUG")
                .rowVersion(1)
                .auditDetails(AuditDetails.builder().build())
                .additionalFields(AdditionalFields.builder().build())
                .build());

        builder.product(productList);
        return this;
    }

    public ProductRequestTestBuilder withDeleteOperation(){
        builder.apiOperation(ApiOperation.DELETE);
        return this;
    }

    public ProductRequestTestBuilder withUpdateOperation(){
        builder.apiOperation(ApiOperation.UPDATE);
        return this;
    }

    public ProductRequestTestBuilder withCreateOperation(){
        builder.apiOperation(ApiOperation.CREATE);
        return this;
    }
}
