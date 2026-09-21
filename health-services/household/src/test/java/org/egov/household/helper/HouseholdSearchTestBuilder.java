package org.egov.household.helper;

import java.util.Collections;

import org.egov.common.models.household.HouseholdSearch;


public class HouseholdSearchTestBuilder {

    private HouseholdSearch.HouseholdSearchBuilder<HouseholdSearch, ?> builder;

    public HouseholdSearchTestBuilder() {
        this.builder = (HouseholdSearch.HouseholdSearchBuilder<HouseholdSearch, ?>) HouseholdSearch.builder();
    }

    public static HouseholdSearchTestBuilder builder() {
        return new HouseholdSearchTestBuilder();
    }

    public HouseholdSearch build() {
        return this.builder.build();
    }

    public HouseholdSearchTestBuilder withHouseholdSearch(){
        this.builder.id(Collections.singletonList("some-id"))
                .clientReferenceId(Collections.singletonList("some-id"));
        return this;
    }

    public HouseholdSearchTestBuilder withId(String id) {
        this.builder.id(Collections.singletonList(id));
        return this;
    }

    public HouseholdSearchTestBuilder withClientReferenceId(String clientReferenceId) {
        this.builder.clientReferenceId(Collections.singletonList(clientReferenceId));
        return this;
    }
}
