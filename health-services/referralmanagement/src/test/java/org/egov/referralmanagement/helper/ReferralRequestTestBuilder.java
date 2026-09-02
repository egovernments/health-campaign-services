package org.egov.referralmanagement.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.referralmanagement.ReferralRequest;

import java.util.ArrayList;

public class ReferralRequestTestBuilder {
    private ReferralRequest.ReferralRequestBuilder builder;

    private ArrayList referral = new ArrayList();

    public ReferralRequestTestBuilder() {
        this.builder = ReferralRequest.builder();
    }

    public static ReferralRequestTestBuilder builder() {
        return new ReferralRequestTestBuilder();
    }

    public ReferralRequest build() {
        return this.builder.build();
    }

    public ReferralRequestTestBuilder withOneReferral() {
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .referral(ReferralTestBuilder.builder().withId().withAuditDetails().build());
        return this;
    }

    public ReferralRequestTestBuilder withApiOperationNotNullAndNotCreate() {
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .referral(ReferralTestBuilder.builder().withIdNull().build());
        return this;
    }

    public ReferralRequestTestBuilder withApiOperationNotUpdate() {
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .referral(ReferralTestBuilder.builder().withIdNull().build());
        return this;
    }

    public ReferralRequestTestBuilder withOneReferralHavingId() {
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .referral(ReferralTestBuilder.builder().withId().withAuditDetails().build());
        return this;
    }

    public ReferralRequestTestBuilder withBadTenantIdInOneReferral() {

        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .referral(ReferralTestBuilder.builder().withIdNull().withBadTenantId().build());
        return this;
    }

    public ReferralRequestTestBuilder withRequestInfo(){
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }
}
