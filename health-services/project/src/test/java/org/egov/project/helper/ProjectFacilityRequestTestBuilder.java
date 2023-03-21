package org.egov.project.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.project.ProjectFacility;
import org.egov.common.models.project.ProjectFacilityRequest;

import java.util.ArrayList;
import java.util.List;

public class ProjectFacilityRequestTestBuilder {
    private ProjectFacilityRequest.ProjectFacilityRequestBuilder builder;

    public ProjectFacilityRequestTestBuilder() {
        this.builder = ProjectFacilityRequest.builder();
    }

    public static ProjectFacilityRequestTestBuilder builder() {
        return new ProjectFacilityRequestTestBuilder();
    }

    public ProjectFacilityRequest build() {
        return this.builder.build();
    }

    public ProjectFacilityRequestTestBuilder withOneProjectFacility() {
        List<ProjectFacility> projectFacilities = new ArrayList<>();
        projectFacilities.add(ProjectFacilityTestBuilder.builder().withIdNull().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectFacility(projectFacilities.get(0));
        return this;
    }

    public ProjectFacilityRequestTestBuilder withOneProjectFacilityHavingId() {
        List<ProjectFacility> projectFacilities = new ArrayList<>();
        projectFacilities.add(ProjectFacilityTestBuilder.builder().withId().withAuditDetails().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectFacility(projectFacilities.get(0));
        return this;
    }

    public ProjectFacilityRequestTestBuilder withBadTenantIdInOneProjectFacility() {
        List<ProjectFacility> projectFacilities = new ArrayList<>();
        projectFacilities.add(ProjectFacilityTestBuilder.builder().withIdNull().withBadTenantId().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectFacility(projectFacilities.get(0));
        return this;
    }

    public ProjectFacilityRequestTestBuilder withRequestInfo(){
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }
}