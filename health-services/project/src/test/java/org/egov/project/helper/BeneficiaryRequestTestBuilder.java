package org.egov.project.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.project.web.models.ApiOperation;
import org.egov.project.web.models.ProjectBeneficiary;
import org.egov.project.web.models.ProjectStaff;
import org.egov.project.web.models.BeneficiaryRequest;

import java.util.ArrayList;
import java.util.List;

public class BeneficiaryRequestTestBuilder {
    private BeneficiaryRequest.BeneficiaryRequestBuilder builder;

    private ArrayList projectBeneficiary = new ArrayList();

    public BeneficiaryRequestTestBuilder() {
        this.builder = BeneficiaryRequest.builder();
    }

    public static BeneficiaryRequestTestBuilder builder() {
        return new BeneficiaryRequestTestBuilder();
    }

    public BeneficiaryRequest build() {
        return this.builder.build();
    }

    public BeneficiaryRequestTestBuilder withOneProjectBeneficiary() {
        List<ProjectBeneficiary> projectBeneficiaries = new ArrayList<>();
        projectBeneficiaries.add(ProjectBeneficiaryTestBuilder.builder().withId().withAuditDetails().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectBeneficiary(projectBeneficiaries);
        return this;
    }

    public BeneficiaryRequestTestBuilder withApiOperationNotNullAndNotCreate() {
        List<ProjectBeneficiary> projectBeneficiaries = new ArrayList<>();
        projectBeneficiaries.add(ProjectBeneficiaryTestBuilder.builder().withIdNull().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectBeneficiary(projectBeneficiaries)
                .apiOperation(ApiOperation.UPDATE);
        return this;
    }

    public BeneficiaryRequestTestBuilder withApiOperationNotUpdate() {
        List<ProjectBeneficiary> projectBeneficiaries = new ArrayList<>();
        projectBeneficiaries.add(ProjectBeneficiaryTestBuilder.builder().withIdNull().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectBeneficiary(projectBeneficiaries)
                .apiOperation(ApiOperation.CREATE);
        return this;
    }

    public BeneficiaryRequestTestBuilder withOneProjectBeneficiaryHavingId() {
        List<ProjectBeneficiary> projectBeneficiaries = new ArrayList<>();
        projectBeneficiaries.add(ProjectBeneficiaryTestBuilder.builder().withId().withAuditDetails().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectBeneficiary(projectBeneficiaries);
        return this;
    }

    public BeneficiaryRequestTestBuilder withBadTenantIdInOneProjectBeneficiary() {
        List<ProjectBeneficiary> projectBeneficiaries = new ArrayList<>();
        projectBeneficiaries.add(ProjectBeneficiaryTestBuilder.builder().withIdNull().withBadTenantId().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectBeneficiary(projectBeneficiaries);
        return this;
    }

    public BeneficiaryRequestTestBuilder withRequestInfo(){
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }

    public BeneficiaryRequestTestBuilder addGoodProjectBeneficiary(){
        projectBeneficiary.add(ProjectStaffTestBuilder.builder().goodProjectStaff().build());
        this.builder.projectBeneficiary(projectBeneficiary);
        return this;
    }

    public BeneficiaryRequestTestBuilder withApiOperationCreate(){
        this.builder.apiOperation(ApiOperation.CREATE);
        return this;
    }
}