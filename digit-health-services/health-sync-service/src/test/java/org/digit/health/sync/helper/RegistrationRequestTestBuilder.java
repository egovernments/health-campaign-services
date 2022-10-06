package org.digit.health.sync.helper;

import org.digit.health.sync.web.models.request.RegistrationRequest;

public class RegistrationRequestTestBuilder {
    private RegistrationRequest.RegistrationRequestBuilder builder;

    public static RegistrationRequestTestBuilder builder() {
        return new RegistrationRequestTestBuilder();
    }

    public RegistrationRequestTestBuilder() {
        this.builder = RegistrationRequest.builder();
    }

    public RegistrationRequest build() {
        return builder.build();
    }

    public RegistrationRequestTestBuilder withDummyClientReferenceId() {
        builder.clientReferenceId("some-uuid");
        return this;
    }
}
