package org.digit.health.sync.helper;

import org.digit.health.sync.web.models.request.DeliveryRequest;

public class DeliveryRequestTestBuilder {

    private DeliveryRequest.DeliveryRequestBuilder builder;

    public static DeliveryRequestTestBuilder builder() {
        return new DeliveryRequestTestBuilder();
    }

    public DeliveryRequestTestBuilder() {
        this.builder = DeliveryRequest.builder();
    }

    public DeliveryRequest build() {
        return builder.build();
    }

    public DeliveryRequestTestBuilder withDummyClientReferenceId() {
        builder.clientReferenceId("some-uuid");
        return this;
    }
}
