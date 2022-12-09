package org.egov.product.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.product.web.models.ApiOperation;
import org.egov.product.web.models.ProductVariant;
import org.egov.product.web.models.ProductVariantRequest;

import java.util.ArrayList;
import java.util.List;

public class ProductVariantRequestTestBuilder {
    private ProductVariantRequest.ProductVariantRequestBuilder builder;

    public ProductVariantRequestTestBuilder() {
        this.builder = ProductVariantRequest.builder();
    }

    public static ProductVariantRequestTestBuilder builder() {
        return new ProductVariantRequestTestBuilder();
    }

    public ProductVariantRequest build() {
        return this.builder.build();
    }

    public ProductVariantRequestTestBuilder withOneProductVariant() {
        List<ProductVariant> productVariants = new ArrayList<>();
        productVariants.add(ProductVariantTestBuilder.builder().withIdNull().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .productVariant(productVariants);
        return this;
    }

    public ProductVariantRequestTestBuilder withApiOperationNotNullAndNotCreate() {
        List<ProductVariant> productVariants = new ArrayList<>();
        productVariants.add(ProductVariantTestBuilder.builder().withIdNull().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .productVariant(productVariants).apiOperation(ApiOperation.UPDATE);
        return this;
    }

    public ProductVariantRequestTestBuilder withOneProductVariantHavingId() {
        List<ProductVariant> productVariants = new ArrayList<>();
        productVariants.add(ProductVariantTestBuilder.builder().withId().withAuditDetails().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .productVariant(productVariants);
        return this;
    }

    public ProductVariantRequestTestBuilder withBadTenantIdInOneProductVariant() {
        List<ProductVariant> productVariants = new ArrayList<>();
        productVariants.add(ProductVariantTestBuilder.builder().withIdNull().withBadTenantId().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .productVariant(productVariants);
        return this;
    }
}
