package org.egov.referralmanagement.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectRequest;

import java.util.ArrayList;

public class SideEffectRequestTestBuilder {
    private SideEffectRequest.SideEffectRequestBuilder builder;

    private ArrayList sideEffect = new ArrayList();

    public SideEffectRequestTestBuilder() {
        this.builder = SideEffectRequest.builder();
    }

    public static SideEffectRequestTestBuilder builder() {
        return new SideEffectRequestTestBuilder();
    }

    public SideEffectRequest build() {
        return this.builder.build();
    }

    public SideEffectRequestTestBuilder withOneSideEffect() {
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .sideEffect(SideEffectTestBuilder.builder().withId().withAuditDetails().build());
        return this;
    }

    public SideEffectRequestTestBuilder withApiOperationNotNullAndNotCreate() {
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .sideEffect(SideEffectTestBuilder.builder().withIdNull().build());
        return this;
    }

    public SideEffectRequestTestBuilder withApiOperationNotUpdate() {
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .sideEffect(SideEffectTestBuilder.builder().withIdNull().build());
        return this;
    }

    public SideEffectRequestTestBuilder withOneSideEffectHavingId() {
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .sideEffect(SideEffectTestBuilder.builder().withId().withAuditDetails().build());
        return this;
    }

    public SideEffectRequestTestBuilder withBadTenantIdInOneSideEffect() {

        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .sideEffect(SideEffectTestBuilder.builder().withIdNull().withBadTenantId().build());
        return this;
    }

    public SideEffectRequestTestBuilder withRequestInfo(){
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }
}
