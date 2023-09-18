package org.egov.project.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.adrm.adverseevent.AdverseEventRequest;

import java.util.ArrayList;

public class AdverseEventRequestTestBuilder {
    private AdverseEventRequest.AdverseEventRequestBuilder builder;

    private ArrayList adverseEvent = new ArrayList();

    public AdverseEventRequestTestBuilder() {
        this.builder = AdverseEventRequest.builder();
    }

    public static AdverseEventRequestTestBuilder builder() {
        return new AdverseEventRequestTestBuilder();
    }

    public AdverseEventRequest build() {
        return this.builder.build();
    }

    public AdverseEventRequestTestBuilder withOneAdverseEvent() {
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .adverseEvent(AdverseEventTestBuilder.builder().withId().withAuditDetails().build());
        return this;
    }

    public AdverseEventRequestTestBuilder withApiOperationNotNullAndNotCreate() {
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .adverseEvent(AdverseEventTestBuilder.builder().withIdNull().build());
        return this;
    }

    public AdverseEventRequestTestBuilder withApiOperationNotUpdate() {
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .adverseEvent(AdverseEventTestBuilder.builder().withIdNull().build());
        return this;
    }

    public AdverseEventRequestTestBuilder withOneAdverseEventHavingId() {
        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .adverseEvent(AdverseEventTestBuilder.builder().withId().withAuditDetails().build());
        return this;
    }

    public AdverseEventRequestTestBuilder withBadTenantIdInOneAdverseEvent() {

        builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .adverseEvent(AdverseEventTestBuilder.builder().withIdNull().withBadTenantId().build());
        return this;
    }

    public AdverseEventRequestTestBuilder withRequestInfo(){
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }
}
