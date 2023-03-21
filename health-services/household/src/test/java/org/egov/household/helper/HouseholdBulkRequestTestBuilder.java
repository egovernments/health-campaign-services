package org.egov.household.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;

import java.util.Arrays;
import java.util.List;

public class HouseholdBulkRequestTestBuilder {
    private HouseholdBulkRequest.HouseholdBulkRequestBuilder builder;

    public HouseholdBulkRequestTestBuilder() {
        this.builder = HouseholdBulkRequest.builder();
    }

    public static HouseholdBulkRequestTestBuilder builder() {
        return new HouseholdBulkRequestTestBuilder();
    }

    public HouseholdBulkRequest build() {
        return this.builder.build();
    }

    public HouseholdBulkRequestTestBuilder withHouseholds(){
        this.builder.households(Arrays.asList(HouseholdTestBuilder.builder().withHousehold().build()));
        return this;
    }

    public HouseholdBulkRequestTestBuilder withHouseholds(List<Household> households) {
        this.builder.households(households);
        return this;
    }

    public HouseholdBulkRequestTestBuilder withRequestInfo(){
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }
}
