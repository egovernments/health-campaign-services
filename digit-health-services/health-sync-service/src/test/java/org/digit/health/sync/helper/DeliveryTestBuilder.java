package org.digit.health.sync.helper;

import org.digit.health.sync.web.models.Delivery;

public class DeliveryTestBuilder {

    private Delivery.DeliveryBuilder builder;

    public static DeliveryTestBuilder builder() {
        return new DeliveryTestBuilder();
    }

    public DeliveryTestBuilder() {
        this.builder = Delivery.builder();
    }

    public Delivery build() {
        return builder.build();
    }

    public DeliveryTestBuilder withDummyClientReferenceId() {
        builder.clientReferenceId("some-uuid");
        return this;
    }

    public DeliveryTestBuilder withRegistrationClientReferenceId() {
        builder.registrationClientReferenceId("some-registration-uuid");
        return this;
    }

    public DeliveryTestBuilder withRegistrationClientReferenceId(String id) {
        builder.registrationClientReferenceId(id);
        return this;
    }
}
