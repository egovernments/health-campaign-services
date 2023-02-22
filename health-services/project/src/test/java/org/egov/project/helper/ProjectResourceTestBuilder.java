package org.egov.project.helper;

import org.egov.common.helper.AuditDetailsTestBuilder;
import org.egov.project.web.models.ProjectResource;

public class ProjectResourceTestBuilder {

    private final ProjectResource.ProjectResourceBuilder builder;

    public ProjectResourceTestBuilder() {
        this.builder = ProjectResource.builder();
    }

    public static ProjectResourceTestBuilder builder() {
        return new ProjectResourceTestBuilder();
    }

    public ProjectResource build() {
        return this.builder.build();
    }

    public ProjectResourceTestBuilder withProjectResource() {
        this.builder.id("some-id")
                .tenantId("default")
                .projectId("project-id")
                .productVariantId("pv-101")
                .isBaseUnitVariant(Boolean.FALSE)
                .type("type")
                .isDeleted(false)
                .rowVersion(0)
                .hasErrors(false)
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build())
                .endDate(100L)
                .startDate(100L).build();
        return this;
    }

    public ProjectResourceTestBuilder withId(String id) {
        this.builder.id(id);
        return this;
    }
}
