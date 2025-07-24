package org.egov.individual.helper;

import org.egov.common.models.household.HouseholdMemberSearchRequest;

public class HouseholdMemberSearchRequestTestBuilder {
    private HouseholdMemberSearchRequest.HouseholdMemberSearchRequestBuilder builder;

    public HouseholdMemberSearchRequestTestBuilder() {
        this.builder = HouseholdMemberSearchRequest.builder();
    }
    public static HouseholdMemberSearchRequestTestBuilder builder() {
        return new HouseholdMemberSearchRequestTestBuilder();
    }

    public HouseholdMemberSearchRequest build() {
        return this.builder.build();
    }


}
