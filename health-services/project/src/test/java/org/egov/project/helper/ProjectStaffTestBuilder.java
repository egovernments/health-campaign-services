package org.egov.project.helper;

import org.egov.common.helper.AuditDetailsTestBuilder;
import org.egov.project.web.models.ProjectStaff;

public class ProjectStaffTestBuilder {

    private ProjectStaff.ProjectStaffBuilder builder;

    public ProjectStaffTestBuilder() {
        this.builder = ProjectStaff.builder();
    }

    public static ProjectStaffTestBuilder builder() {
        return new ProjectStaffTestBuilder();
    }

    public ProjectStaff build() {
        return this.builder.build();
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

    public ProjectStaffTestBuilder withBadTenantId() {
        this.builder.tenantId(null);
        return this;
    }

    public ProjectStaffTestBuilder withAuditDetails() {
        this.builder.auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build());
        return this;
    }
}
