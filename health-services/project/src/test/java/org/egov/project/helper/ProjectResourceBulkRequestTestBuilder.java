package org.egov.project.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.project.ProjectResource;
import org.egov.common.models.project.ProjectResourceBulkRequest;

import java.util.ArrayList;

public class ProjectResourceBulkRequestTestBuilder {
    private ProjectResourceBulkRequest.ProjectResourceBulkRequestBuilder builder;

    public ProjectResourceBulkRequestTestBuilder() {
        this.builder = ProjectResourceBulkRequest.builder();
    }

    ArrayList<ProjectResource> projectResources = new ArrayList<>();

    public static ProjectResourceBulkRequestTestBuilder builder() {
        return new ProjectResourceBulkRequestTestBuilder();
    }

    public ProjectResourceBulkRequest build() {
        return this.builder.build();
    }

    public ProjectResourceBulkRequestTestBuilder withProjectResource() {
        projectResources.add(ProjectResourceTestBuilder.builder().withProjectResource().build());
        this.builder.projectResource(projectResources);
        return this;
    }

    public ProjectResourceBulkRequestTestBuilder withProjectResourceId(String id){
        projectResources.add(ProjectResourceTestBuilder.builder().withProjectResource().withId(id).build());
        this.builder.projectResource(projectResources);
        return this;
    }

    public ProjectResourceBulkRequestTestBuilder withRequestInfo() {
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }
}
