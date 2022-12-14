package org.egov.project.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.project.web.models.ApiOperation;
import org.egov.project.web.models.ProjectStaff;
import org.egov.project.web.models.ProjectStaffRequest;

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
                .projectStaff(projectStaffs);
        return this;
    }

    public ProjectStaffRequestTestBuilder withApiOperationNotNullAndNotCreate() {
        List<ProjectStaff> projectStaffs = new ArrayList<>();
        projectStaffs.add(ProjectStaffTestBuilder.builder().withIdNull().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectStaff(projectStaffs)
                .apiOperation(ApiOperation.UPDATE);
        return this;
    }

    public ProjectStaffRequestTestBuilder withApiOperationNotUpdate() {
        List<ProjectStaff> projectStaffs = new ArrayList<>();
        projectStaffs.add(ProjectStaffTestBuilder.builder().withIdNull().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectStaff(projectStaffs)
                .apiOperation(ApiOperation.CREATE);
        return this;
    }

    public ProjectStaffRequestTestBuilder withOneProjectHavingId() {
        List<ProjectStaff> projectStaffs = new ArrayList<>();
        projectStaffs.add(ProjectStaffTestBuilder.builder().withId().withAuditDetails().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectStaff(projectStaffs);
        return this;
    }

    public ProjectStaffRequestTestBuilder withBadTenantIdInOneProjectStaff() {
        List<ProjectStaff> projectStaffs = new ArrayList<>();
        projectStaffs.add(ProjectStaffTestBuilder.builder().withIdNull().withBadTenantId().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectStaff(projectStaffs);
        return this;
    }
}