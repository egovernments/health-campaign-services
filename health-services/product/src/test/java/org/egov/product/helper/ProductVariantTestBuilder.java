package org.egov.product.helper;

import org.egov.common.helper.AuditDetailsTestBuilder;
import org.egov.common.models.product.ProductVariant;


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
                .rowVersion(1).isDeleted(Boolean.valueOf("false"));
        return this;
    }

    public ProductVariantTestBuilder withId(String id101) {
        withIdNull().builder.id(id101);
        return this;
    }

    public ProductVariantTestBuilder withVariation() {
        withIdNull().builder.variation("some-variation");
        return this;
    }

    public ProductVariantTestBuilder withRowVersion() {
        this.builder.rowVersion(1);
        return this;
    }

    public ProductVariantTestBuilder withDeleted() {
        withIdNull().builder.isDeleted(true);
        return this;
    }

    public ProductVariantTestBuilder withBadTenantId() {
        this.builder.tenantId(null);
        return this;
    }

    public ProductVariantTestBuilder withAuditDetails() {
        this.builder.auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build());
        return this;
    }
}
