package org.egov.product.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.product.ApiOperation;
import org.egov.common.models.product.ProductVariant;
import org.egov.common.models.product.ProductVariantRequest;

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
        productVariants.add(ProductVariantTestBuilder.builder().withId().withVariation().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .productVariant(productVariants);
        return this;
    }

    public ProductVariantRequestTestBuilder withApiOperationNotNullAndNotCreate() {
        List<ProductVariant> productVariants = new ArrayList<>();
        productVariants.add(ProductVariantTestBuilder.builder().withId().withVariation().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .productVariant(productVariants).apiOperation(ApiOperation.UPDATE);
        return this;
    }

    public ProductVariantRequestTestBuilder withApiOperationNotUpdate() {
        List<ProductVariant> productVariants = new ArrayList<>();
        productVariants.add(ProductVariantTestBuilder.builder().withId().withVariation().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .productVariant(productVariants).apiOperation(ApiOperation.CREATE);
        return this;
    }

    public ProductVariantRequestTestBuilder withOneProductVariantHavingId() {
        List<ProductVariant> productVariants = new ArrayList<>();
        productVariants.add(ProductVariantTestBuilder.builder().withId().withAuditDetails().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .productVariant(productVariants);
        return this;
    }

    public ProductVariantRequestTestBuilder withOneProductVariantHavingIdAndRowVersion() {
        List<ProductVariant> productVariants = new ArrayList<>();
        productVariants.add(ProductVariantTestBuilder.builder().withId().withRowVersion().withAuditDetails().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .productVariant(productVariants);
        return this;
    }

    public ProductVariantRequestTestBuilder withBadTenantIdInOneProductVariant() {
        List<ProductVariant> productVariants = new ArrayList<>();
        productVariants.add(ProductVariantTestBuilder.builder().withIdNull().withVariation().withBadTenantId().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .productVariant(productVariants);
        return this;
    }
}
