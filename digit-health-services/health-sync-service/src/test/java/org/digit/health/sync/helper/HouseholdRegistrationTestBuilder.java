package org.digit.health.sync.helper;

import org.digit.health.sync.web.models.HouseholdRegistration;

public class HouseholdRegistrationTestBuilder {
    private HouseholdRegistration.HouseholdRegistrationBuilder builder;

    public static HouseholdRegistrationTestBuilder builder() {
        return new HouseholdRegistrationTestBuilder();
    }

    public HouseholdRegistrationTestBuilder() {
        this.builder = HouseholdRegistration.builder();
    }

    public HouseholdRegistration build() {
        return builder.build();
    }

    public HouseholdRegistrationTestBuilder withDummyClientReferenceId() {
        builder.clientReferenceId("some-registration-uuid");
        return this;
    }

    public HouseholdRegistrationTestBuilder withDummyClientReferenceId(String id) {
        builder.clientReferenceId(id);
        return this;
    }
}
