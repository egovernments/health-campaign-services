package org.egov.product.helper;

import org.egov.common.helper.AuditDetailsTestBuilder;
import org.egov.common.models.product.ProductVariant;

public class ProductVariantTestBuilder {
    private ProductVariant.ProductVariantBuilder builder;

    public ProductVariantTestBuilder() {
        this.builder = ProductVariant.builder();
    }

    public static ProductVariantTestBuilder builder() {
        return new ProductVariantTestBuilder().withIdNull();
    }

    public ProductVariant build() {
        return this.builder.build();
    }

    public ProductVariantTestBuilder withIdNull() {
        this.builder.productId("some-product-id")
                .id(null)
                .tenantId("some-tenant-id")
                .rowVersion(1);
        return this;
    }

    public ProductVariantTestBuilder withId() {
        withIdNull().builder.id("some-id");
        return this;
    }

    public ProductVariantTestBuilder withVariation() {
        this.builder.variation("some-variation");
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

    public ProductVariantTestBuilder withSupplyChainFields() {
        this.builder.gtin("12345678901234")
                .batchNumber("BATCH-001")
                .serialNumber("SERIAL-001")
                .expiryDate(1735689600000L)
                .baseUnit("TAB")
                .netContent(100L);
        return this;
    }

    public ProductVariantTestBuilder withGtin(String gtin) {
        this.builder.gtin(gtin);
        return this;
    }

    public ProductVariantTestBuilder withBatchNumber(String batchNumber) {
        this.builder.batchNumber(batchNumber);
        return this;
    }

    public ProductVariantTestBuilder withSerialNumber(String serialNumber) {
        this.builder.serialNumber(serialNumber);
        return this;
    }

    public ProductVariantTestBuilder withExpiryDate(Long expiryDate) {
        this.builder.expiryDate(expiryDate);
        return this;
    }

    public ProductVariantTestBuilder withBaseUnit(String baseUnit) {
        this.builder.baseUnit(baseUnit);
        return this;
    }

    public ProductVariantTestBuilder withNetContent(Long netContent) {
        this.builder.netContent(netContent);
        return this;
    }
}
