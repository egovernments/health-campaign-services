package org.egov.project.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.project.BeneficiaryBulkRequest;
import org.egov.common.models.project.ProjectBeneficiary;

import java.util.ArrayList;
import java.util.List;

public class BeneficiaryBulkRequestTestBuilder {
    private BeneficiaryBulkRequest.BeneficiaryBulkRequestBuilder builder;

    private ArrayList projectBeneficiary = new ArrayList();

    public BeneficiaryBulkRequestTestBuilder() {
        this.builder = BeneficiaryBulkRequest.builder();
    }

    public static BeneficiaryBulkRequestTestBuilder builder() {
        return new BeneficiaryBulkRequestTestBuilder();
    }

    public BeneficiaryBulkRequest build() {
        return this.builder.build();
    }

    public BeneficiaryBulkRequestTestBuilder withOneProjectBeneficiary() {
        List<ProjectBeneficiary> projectBeneficiaries = new ArrayList<>();
        projectBeneficiaries.add(ProjectBeneficiaryTestBuilder.builder().withId().withAuditDetails().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectBeneficiaries(projectBeneficiaries);
        return this;
    }

    public BeneficiaryBulkRequestTestBuilder withApiOperationNotNullAndNotCreate() {
        List<ProjectBeneficiary> projectBeneficiaries = new ArrayList<>();
        projectBeneficiaries.add(ProjectBeneficiaryTestBuilder.builder().withIdNull().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectBeneficiaries(projectBeneficiaries);
        return this;
    }

    public BeneficiaryBulkRequestTestBuilder withApiOperationNotUpdate() {
        List<ProjectBeneficiary> projectBeneficiaries = new ArrayList<>();
        projectBeneficiaries.add(ProjectBeneficiaryTestBuilder.builder().withIdNull().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectBeneficiaries(projectBeneficiaries);
        return this;
    }

    public BeneficiaryBulkRequestTestBuilder withOneProjectBeneficiaryHavingId() {
        List<ProjectBeneficiary> projectBeneficiaries = new ArrayList<>();
        projectBeneficiaries.add(ProjectBeneficiaryTestBuilder.builder().withId().withAuditDetails().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectBeneficiaries(projectBeneficiaries);
        return this;
    }

    public BeneficiaryBulkRequestTestBuilder withBadTenantIdInOneProjectBeneficiary() {
        List<ProjectBeneficiary> projectBeneficiaries = new ArrayList<>();
        projectBeneficiaries.add(ProjectBeneficiaryTestBuilder.builder().withIdNull().withBadTenantId().build());
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectBeneficiaries(projectBeneficiaries);
        return this;
    }

    public BeneficiaryBulkRequestTestBuilder withRequestInfo(){
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }

    public BeneficiaryBulkRequestTestBuilder addGoodProjectBeneficiary(){
        projectBeneficiary.add(ProjectStaffTestBuilder.builder().goodProjectStaff().build());
        this.builder.projectBeneficiaries(projectBeneficiary);
        return this;
    }
}