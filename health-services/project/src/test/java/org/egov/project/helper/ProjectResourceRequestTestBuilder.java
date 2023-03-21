package org.egov.project.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.project.ProjectResourceRequest;


public class ProjectResourceRequestTestBuilder {

    private final ProjectResourceRequest.ProjectResourceRequestBuilder builder;

    public ProjectResourceRequestTestBuilder() {
        this.builder = ProjectResourceRequest.builder();
    }

    public static ProjectResourceRequestTestBuilder builder() {
        return new ProjectResourceRequestTestBuilder();
    }

    public ProjectResourceRequest build() {
        return this.builder.build();
    }

    public ProjectResourceRequestTestBuilder withProjectResource() {
        this.builder.projectResource(ProjectResourceTestBuilder.builder().withProjectResource().build());
        return this;
    }

    public ProjectResourceRequestTestBuilder withRequestInfo() {
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }
}
