package org.egov.project.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.project.BeneficiaryRequest;

import java.util.ArrayList;

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
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectBeneficiary(ProjectBeneficiaryTestBuilder.builder().withId().withAuditDetails().build());
        return this;
    }

    public BeneficiaryRequestTestBuilder withApiOperationNotNullAndNotCreate() {
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectBeneficiary(ProjectBeneficiaryTestBuilder.builder().withIdNull().build());
        return this;
    }

    public BeneficiaryRequestTestBuilder withApiOperationNotUpdate() {
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectBeneficiary(ProjectBeneficiaryTestBuilder.builder().withIdNull().build());
        return this;
    }

    public BeneficiaryRequestTestBuilder withOneProjectBeneficiaryHavingId() {
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectBeneficiary(ProjectBeneficiaryTestBuilder.builder().withId().withAuditDetails().build());
        return this;
    }

    public BeneficiaryRequestTestBuilder withBadTenantIdInOneProjectBeneficiary() {

        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .projectBeneficiary(ProjectBeneficiaryTestBuilder.builder().withIdNull().withBadTenantId().build());
        return this;
    }

    public BeneficiaryRequestTestBuilder withRequestInfo(){
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }
}