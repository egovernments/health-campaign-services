package org.egov.facility.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.FacilityBulkRequest;

import java.util.ArrayList;
import java.util.List;

public class FacilityBulkRequestTestBuilder {
    private FacilityBulkRequest.FacilityBulkRequestBuilder builder;

    public FacilityBulkRequestTestBuilder() {
        this.builder = FacilityBulkRequest.builder();
    }

    ArrayList<Facility> facilities = new ArrayList<>();

    public static FacilityBulkRequestTestBuilder builder() {
        return new FacilityBulkRequestTestBuilder();
    }

    public FacilityBulkRequest build() {
        return this.builder.build();
    }

    public FacilityBulkRequestTestBuilder withFacility() {
        facilities.add(FacilityTestBuilder.builder().withFacility().build());
        this.builder.facilities(facilities);
        return this;
    }

    public FacilityBulkRequestTestBuilder withFacility(List<Facility> facilities) {
        this.builder.facilities(facilities);
        return this;
    }

    public FacilityBulkRequestTestBuilder withFacilityId(String id) {
        facilities.add(FacilityTestBuilder.builder().withFacility().withId(id).build());
        this.builder.facilities(facilities);
        return this;
    }

    public FacilityBulkRequestTestBuilder withRequestInfo() {
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }
}
