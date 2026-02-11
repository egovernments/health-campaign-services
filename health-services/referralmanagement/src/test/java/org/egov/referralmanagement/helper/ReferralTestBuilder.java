package org.egov.referralmanagement.helper;

import org.egov.common.helper.AuditDetailsTestBuilder;
import org.egov.common.models.referralmanagement.Referral;

import java.util.ArrayList;
import java.util.Arrays;

public class ReferralTestBuilder {

    private Referral.ReferralBuilder<Referral, ?> builder;

    public ReferralTestBuilder() {
        this.builder = (Referral.ReferralBuilder<Referral, ?>) Referral.builder();
    }

    public static ReferralTestBuilder builder() {
        return new ReferralTestBuilder();
    }

    public Referral build() {
        return this.builder.hasErrors(false).build();
    }

    public ReferralTestBuilder withIdNull() {
        this.builder.projectBeneficiaryId("some-projectBeneficiary-id")
                .clientReferenceId("ClientReferenceId")
                .id(null)
                .projectBeneficiaryClientReferenceId("projectBeneficiaryClientReferenceId")
                .reasons(new ArrayList<>(Arrays.asList("fever")))
                .tenantId("some-tenant-id")
                .rowVersion(1);
        return this;
    }

    public ReferralTestBuilder withId() {
        withIdNull().builder.id("some-id")
                .projectBeneficiaryId("some-projectBeneficiary-id")
                .clientReferenceId("ClientReferenceId")
                .projectBeneficiaryClientReferenceId("projectBeneficiaryClientReferenceId")
                .reasons(new ArrayList<>(Arrays.asList("fever")))
                .tenantId("some-tenant-id")
                .rowVersion(1);
        return this;
    }

    public ReferralTestBuilder withId(String id) {
        this.builder.id(id);
        return this;
    }

    public ReferralTestBuilder withBadTenantId() {
        this.builder.tenantId(null);
        return this;
    }

    public ReferralTestBuilder goodReferral() {
        this.builder.id("some-id")
                .projectBeneficiaryId("some-projectBeneficiary-id")
                .clientReferenceId("ClientReferenceId")
                .projectBeneficiaryClientReferenceId("projectBeneficiaryClientReferenceId")
                .reasons(new ArrayList<>(Arrays.asList("fever")))
                .tenantId("some-tenant-id")
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build())
                .clientAuditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build());
        return this;
    }

    public ReferralTestBuilder withAuditDetails() {
        this.builder.auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build());
        return this;
    }

    public ReferralTestBuilder withDeleted() {
        this.builder.isDeleted(true);
        return this;
    }
}
