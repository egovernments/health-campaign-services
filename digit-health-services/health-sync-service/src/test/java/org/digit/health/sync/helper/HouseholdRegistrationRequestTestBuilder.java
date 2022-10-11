package org.digit.health.sync.helper;

import org.digit.health.sync.web.models.request.HouseholdRegistrationRequest;

public class HouseholdRegistrationRequestTestBuilder {
    private HouseholdRegistrationRequest.HouseholdRegistrationRequestBuilder builder;

    public static HouseholdRegistrationRequestTestBuilder builder() {
        return new HouseholdRegistrationRequestTestBuilder();
    }

    public HouseholdRegistrationRequestTestBuilder() {
        this.builder = HouseholdRegistrationRequest.builder();
    }

    public HouseholdRegistrationRequest build() {
        return builder.build();
    }

    public HouseholdRegistrationRequestTestBuilder withDummyClientReferenceId() {
        builder.clientReferenceId("some-uuid");
        return this;
    }
}
