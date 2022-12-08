package org.egov.common.helper;

import digit.models.coremodels.AuditDetails;

public class AuditDetailsTestBuilder {
    private AuditDetails.AuditDetailsBuilder builder;

    public AuditDetailsTestBuilder() {
        this.builder = AuditDetails.builder();
    }

    public static AuditDetailsTestBuilder builder() {
        return new AuditDetailsTestBuilder();
    }

    public AuditDetails build() {
        return this.builder.build();
    }

    public AuditDetailsTestBuilder withAuditDetails() {
        this.builder.createdTime(System.currentTimeMillis())
                .createdBy("some-uuid")
                .lastModifiedTime(System.currentTimeMillis())
                .lastModifiedBy("some-uuid");
        return this;
    }
}
