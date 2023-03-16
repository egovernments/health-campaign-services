package org.egov.project.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.project.ProjectFacility;
import org.egov.common.models.project.ProjectFacilityBulkRequest;

import java.util.ArrayList;
import java.util.List;

public class ProjectFacilityBulkRequestTestBuilder {
    private ProjectFacilityBulkRequest.ProjectFacilityBulkRequestBuilder builder;
    private ArrayList projectFacilities = new ArrayList();
    public ProjectFacilityBulkRequestTestBuilder() {
        this.builder = ProjectFacilityBulkRequest.builder();
    }

    public static ProjectFacilityBulkRequestTestBuilder builder() {
        return new ProjectFacilityBulkRequestTestBuilder();
    }

    public ProjectFacilityBulkRequest build() {
        return this.builder.build();
    }

    public ProjectFacilityBulkRequestTestBuilder withOneProjectFacility() {
        List<ProjectFacility> projectFacilities = new ArrayList<>();
        projectFacilities.add(ProjectFacilityTestBuilder.builder().withIdNull().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectFacilities(projectFacilities);
        return this;
    }

    public ProjectFacilityBulkRequestTestBuilder withProjectFacility() {
        projectFacilities.add(ProjectFacilityTestBuilder.builder().withIdNull().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectFacilities(projectFacilities);
        return this;
    }

    public ProjectFacilityBulkRequestTestBuilder withOneProjectFacilityHavingId(String id) {
        List<ProjectFacility> projectFacilities = new ArrayList<>();
        projectFacilities.add(ProjectFacilityTestBuilder.builder().withId(id).withAuditDetails().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectFacilities(projectFacilities);
        return this;
    }

    public ProjectFacilityBulkRequestTestBuilder withBadTenantIdInOneProjectFacility() {
        List<ProjectFacility> projectFacilities = new ArrayList<>();
        projectFacilities.add(ProjectFacilityTestBuilder.builder().withIdNull().withBadTenantId().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectFacilities(projectFacilities);
        return this;
    }

    public ProjectFacilityBulkRequestTestBuilder withRequestInfo(){
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }

    public ProjectFacilityBulkRequestTestBuilder addGoodProjectFacility(){
        projectFacilities.add(ProjectFacilityTestBuilder.builder().goodProjectFacility().build());
        this.builder.projectFacilities(projectFacilities);
        return this;
    }
}