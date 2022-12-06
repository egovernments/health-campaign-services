package org.egov.product.helper;

import org.egov.product.web.models.ProductVariant;

public class ProductVariantTestBuilder {
    private ProductVariant.ProductVariantBuilder builder;

    public ProductVariantTestBuilder() {
        this.builder = ProductVariant.builder();
    }

    public static ProductVariantTestBuilder builder() {
        return new ProductVariantTestBuilder();
    }

    public ProductVariant build() {
        return this.builder.build();
    }

    public ProductVariantTestBuilder withIdNull() {
        this.builder.productId("some-product-id")
                .id(null)
                .tenantId("some-tenant-id")
                .variation("some-variation")
                .sku("some-sku-code")
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build());
        return this;
    }

    public ProductVariantTestBuilder withId() {
        withIdNull().builder.id("some-id");
        return this;
    }

    public ProductVariantTestBuilder withBadTenantId() {
        this.builder.tenantId(null);
        return this;
    }
}
