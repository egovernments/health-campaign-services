package org.digit.health.sync.helper;

import org.digit.health.sync.web.models.Delivery;
import org.digit.health.sync.web.models.request.ResourceDeliveryRequest;

public class ResourceDeliveryRequestTestBuilder {

    private ResourceDeliveryRequest.ResourceDeliveryRequestBuilder builder;

    public static ResourceDeliveryRequestTestBuilder builder() {
        return new ResourceDeliveryRequestTestBuilder();
    }

    public ResourceDeliveryRequestTestBuilder() {
        this.builder = ResourceDeliveryRequest.builder();
    }

    public ResourceDeliveryRequest build() {
        return builder.build();
    }

    public ResourceDeliveryRequestTestBuilder withDummyClientReferenceId() {
        builder.delivery(Delivery.builder()
                        .clientReferenceId("some-uuid")
                .build()).build();
        return this;
    }

    public ResourceDeliveryRequestTestBuilder withRegistrationClientReferenceId() {
        builder.delivery(Delivery.builder()
                        .registrationClientReferenceId("some-registration-uuid")
                .build()).build();
        return this;
    }
}
