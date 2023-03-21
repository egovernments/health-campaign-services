package org.egov.project.helper;

import org.egov.common.helper.AuditDetailsTestBuilder;
import org.egov.common.models.project.AdditionalFields;
import org.egov.common.models.project.ProjectStaff;


public class ProjectStaffTestBuilder {

    private ProjectStaff.ProjectStaffBuilder builder;

    public ProjectStaffTestBuilder() {
        this.builder = ProjectStaff.builder();
    }

    public static ProjectStaffTestBuilder builder() {
        return new ProjectStaffTestBuilder();
    }

    public ProjectStaff build() {
        return this.builder.hasErrors(Boolean.FALSE).build();
    }

    public ProjectStaffTestBuilder withIdNull() {
        this.builder.projectId("some-project-id")
                .id(null)
                .userId("user-id")
                .tenantId("some-tenant-id")
                .rowVersion(1);
        return this;
    }

    public ProjectStaffTestBuilder withId() {
        withIdNull().builder.id("some-id").userId("user-id");
        return this;
    }

    public ProjectStaffTestBuilder withId(String id) {
        this.builder.id(id);
        return this;
    }

    public ProjectStaffTestBuilder withBadTenantId() {
        this.builder.tenantId(null);
        return this;
    }

    public ProjectStaffTestBuilder withProjectId(String id) {
        this.builder.projectId(id);
        return this;
    }

    public ProjectStaffTestBuilder withUserId(String userId) {
        this.builder.userId(userId);
        return this;
    }

    public ProjectStaffTestBuilder goodProjectStaff() {
        this.builder.projectId("some-project-id")
                .userId("user-id")
                .endDate(11111L)
                .startDate(11111L)
                .isDeleted(false)
                .channel("channel")
                .tenantId("some-tenant-id")
                .rowVersion(1)
                .additionalFields(AdditionalFields.builder().build())
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build());
        return this;
    }

    public ProjectStaffTestBuilder withAuditDetails() {
        this.builder.auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build());
        return this;
    }

    public ProjectStaffTestBuilder withDeleted() {
        this.builder.isDeleted(true);
        return this;
    }
}
