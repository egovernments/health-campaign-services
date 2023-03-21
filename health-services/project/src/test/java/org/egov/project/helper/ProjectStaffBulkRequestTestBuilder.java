package org.egov.project.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.project.ProjectStaff;
import org.egov.common.models.project.ProjectStaffBulkRequest;

import java.util.ArrayList;
import java.util.List;

public class ProjectStaffBulkRequestTestBuilder {
    private ProjectStaffBulkRequest.ProjectStaffBulkRequestBuilder builder;
    private ArrayList projectStaffs = new ArrayList();
    public ProjectStaffBulkRequestTestBuilder() {
        this.builder = ProjectStaffBulkRequest.builder();
    }

    public static ProjectStaffBulkRequestTestBuilder builder() {
        return new ProjectStaffBulkRequestTestBuilder();
    }

    public ProjectStaffBulkRequest build() {
        return this.builder.build();
    }

    public ProjectStaffBulkRequestTestBuilder withOneProjectStaff() {
        List<ProjectStaff> projectStaffs = new ArrayList<>();
        projectStaffs.add(ProjectStaffTestBuilder.builder().withIdNull().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectStaff(projectStaffs);
        return this;
    }

    public ProjectStaffBulkRequestTestBuilder withOneProjectStaffHavingId() {
        List<ProjectStaff> projectStaffs = new ArrayList<>();
        projectStaffs.add(ProjectStaffTestBuilder.builder().withId().withAuditDetails().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectStaff(projectStaffs);
        return this;
    }

    public ProjectStaffBulkRequestTestBuilder withBadTenantIdInOneProjectStaff() {
        List<ProjectStaff> projectStaffs = new ArrayList<>();
        projectStaffs.add(ProjectStaffTestBuilder.builder().withIdNull().withBadTenantId().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectStaff(projectStaffs);
        return this;
    }

    public ProjectStaffBulkRequestTestBuilder withRequestInfo(){
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }

    public ProjectStaffBulkRequestTestBuilder addGoodProjectStaff(){
        projectStaffs.add(ProjectStaffTestBuilder.builder().goodProjectStaff().build());
        this.builder.projectStaff(projectStaffs);
        return this;
    }
}