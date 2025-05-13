package org.egov.project.helper;

import org.egov.common.helper.AuditDetailsTestBuilder;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.project.ProjectBeneficiary;


public class ProjectBeneficiaryTestBuilder {

    private ProjectBeneficiary.ProjectBeneficiaryBuilder<ProjectBeneficiary, ?> builder;

    public ProjectBeneficiaryTestBuilder() {
        this.builder = (ProjectBeneficiary.ProjectBeneficiaryBuilder<ProjectBeneficiary, ?>) ProjectBeneficiary.builder();
    }

    public static ProjectBeneficiaryTestBuilder builder() {
        return new ProjectBeneficiaryTestBuilder();
    }

    public ProjectBeneficiary build() {
        return this.builder.hasErrors(Boolean.FALSE).build();
    }

    public ProjectBeneficiaryTestBuilder withIdNull() {
        this.builder.projectId("some-project-id")
                .beneficiaryId("beneficiary-id")
                .dateOfRegistration(Long.valueOf(1673577580L))
                .clientReferenceId("beneficiaryClientReferenceId")
                .id(null)
                .tenantId("some-tenant-id")
                .rowVersion(Integer.valueOf(1));
        return this;
    }

    public ProjectBeneficiaryTestBuilder withId() {
        withIdNull().builder
                .beneficiaryId("beneficiary-id")
                .dateOfRegistration(Long.valueOf(1673577580L))
                .projectId("some-project-id")
                .clientReferenceId("beneficiaryClientReferenceId")
                .id("some-id")
                .tenantId("some-tenant-id");
        return this;
    }

    public ProjectBeneficiaryTestBuilder withId(String id) {
        this.builder.id(id);
        return this;
    }

    public ProjectBeneficiaryTestBuilder withBadTenantId() {
        this.builder.tenantId(null);
        return this;
    }

    public ProjectBeneficiaryTestBuilder goodProjectBeneficiary() {
        this.builder.projectId("some-project-id")
                .beneficiaryId("beneficiary-id")
                .clientReferenceId("beneficiaryClientReferenceId")
                .dateOfRegistration(Long.valueOf(1673577580L))
                .tenantId("some-tenant-id")
                .rowVersion(Integer.valueOf(1))
                .additionalFields(AdditionalFields.builder().build())
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build());
        return this;
    }

    public ProjectBeneficiaryTestBuilder withAuditDetails() {
        this.builder.auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build());
        return this;
    }

    public ProjectBeneficiaryTestBuilder withDeleted() {
        this.builder.isDeleted(Boolean.TRUE);
        return this;
    }
}
