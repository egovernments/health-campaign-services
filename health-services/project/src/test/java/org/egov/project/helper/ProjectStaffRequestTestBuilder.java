package org.egov.project.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.project.ProjectStaff;
import org.egov.common.models.project.ProjectStaffRequest;

import java.util.ArrayList;
import java.util.List;

public class ProjectStaffRequestTestBuilder {
    private ProjectStaffRequest.ProjectStaffRequestBuilder builder;

    public ProjectStaffRequestTestBuilder() {
        this.builder = ProjectStaffRequest.builder();
    }

    public static ProjectStaffRequestTestBuilder builder() {
        return new ProjectStaffRequestTestBuilder();
    }

    public ProjectStaffRequest build() {
        return this.builder.build();
    }

    public ProjectStaffRequestTestBuilder withOneProjectStaff() {
        List<ProjectStaff> projectStaffs = new ArrayList<>();
        projectStaffs.add(ProjectStaffTestBuilder.builder().withIdNull().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectStaff(projectStaffs.get(0));
        return this;
    }

    public ProjectStaffRequestTestBuilder withOneProjectStaffHavingId() {
        List<ProjectStaff> projectStaffs = new ArrayList<>();
        projectStaffs.add(ProjectStaffTestBuilder.builder().withId().withAuditDetails().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectStaff(projectStaffs.get(0));
        return this;
    }

    public ProjectStaffRequestTestBuilder withBadTenantIdInOneProjectStaff() {
        List<ProjectStaff> projectStaffs = new ArrayList<>();
        projectStaffs.add(ProjectStaffTestBuilder.builder().withIdNull().withBadTenantId().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectStaff(projectStaffs.get(0));
        return this;
    }

    public ProjectStaffRequestTestBuilder withRequestInfo(){
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }
}