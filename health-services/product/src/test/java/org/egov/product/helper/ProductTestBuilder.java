package org.egov.product.helper;

import org.egov.common.helper.AuditDetailsTestBuilder;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.product.Product;

public class ProductTestBuilder {
    private Product.ProductBuilder builder;

    public ProductTestBuilder() {
        this.builder = Product.builder();
    }

    public static ProductTestBuilder builder() {
        return new ProductTestBuilder();
    }

    public Product build() {
        return this.builder.build();
    }

    public ProductTestBuilder badProduct() {
        this.builder.name("Product-1")
                .manufacturer("MANU")
                .additionalFields(AdditionalFields.builder().build())
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build());
        return this;
    }

    public ProductTestBuilder goodProduct() {
        this.builder.name("Product-1")
                .manufacturer("MANU")
                .type("some-type")
                .tenantId("default")
                .additionalFields(AdditionalFields.builder().build())
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build());
        return this;
    }

    public ProductTestBuilder withId(String id) {
        this.builder.id(id);
        return this;
    }

    public ProductTestBuilder withIsDeleted() {
        this.builder.isDeleted(true);
        return this;
    }

    public ProductTestBuilder goodProductWithNullTenant() {
        this.builder.name("Product-1")
                .manufacturer("MANU")
                .type("DRUG")
                .id("some-id")
                .additionalFields(AdditionalFields.builder().build())
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build());
        return this;
    }
}
