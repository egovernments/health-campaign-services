package org.egov.project.helper;

import org.egov.common.helper.AuditDetailsTestBuilder;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.project.ProjectFacility;


public class ProjectFacilityTestBuilder {

    private ProjectFacility.ProjectFacilityBuilder<ProjectFacility, ?> builder;

    public ProjectFacilityTestBuilder() {
        this.builder = (ProjectFacility.ProjectFacilityBuilder<ProjectFacility, ?>) ProjectFacility.builder();
    }

    public static ProjectFacilityTestBuilder builder() {
        return new ProjectFacilityTestBuilder();
    }

    public ProjectFacility build() {
        this.builder.hasErrors(Boolean.FALSE);
        this.builder.isDeleted(Boolean.FALSE);
        return this.builder.build();
    }

    public ProjectFacilityTestBuilder withIdNull() {
        this.builder.projectId("some-project-id")
                .id(null)
                .facilityId("facility-id")
                .tenantId("some-tenant-id")
                .rowVersion(1);
        return this;
    }

    public ProjectFacilityTestBuilder withId() {
        withIdNull().builder
                .facilityId("facility-id")
                .id("some-id");
        return this;
    }

    public ProjectFacilityTestBuilder withId(String id) {
        this.builder.id(id);
        return this;
    }

    public ProjectFacilityTestBuilder withTenantId(String tenantId) {
        this.builder.tenantId(tenantId);
        return this;
    }

    public ProjectFacilityTestBuilder withBadTenantId() {
        this.builder.tenantId(null);
        return this;
    }

    public ProjectFacilityTestBuilder goodProjectFacility() {
        this.builder.projectId("some-project-id")
                .facilityId("facility-id")
                .isDeleted(false)
                .tenantId("some-tenant-id")
                .rowVersion(1)
                .additionalFields(AdditionalFields.builder().build())
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build());
        return this;
    }

    public ProjectFacilityTestBuilder withAuditDetails() {
        this.builder.auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build());
        return this;
    }

    public ProjectFacilityTestBuilder withDeleted() {
        this.builder.isDeleted(true);
        return this;
    }
}
