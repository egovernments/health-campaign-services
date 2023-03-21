package org.egov.facility.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.facility.FacilityRequest;


public class FacilityRequestTestBuilder {
    private FacilityRequest.FacilityRequestBuilder builder;

    public FacilityRequestTestBuilder() {
        this.builder = FacilityRequest.builder();
    }

    public static FacilityRequestTestBuilder builder() {
        return new FacilityRequestTestBuilder();
    }

    public FacilityRequest build() {
        return this.builder.build();
    }

    public FacilityRequestTestBuilder withFacility() {
        this.builder.facility(FacilityTestBuilder.builder().withFacility().build());
        return this;
    }

    public FacilityRequestTestBuilder withRequestInfo() {
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }
}
