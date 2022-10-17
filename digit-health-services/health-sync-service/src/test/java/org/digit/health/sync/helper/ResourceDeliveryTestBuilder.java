package org.digit.health.sync.helper;

import org.digit.health.sync.web.models.Delivery;
import org.digit.health.sync.web.models.ResourceDelivery;

public class ResourceDeliveryTestBuilder {

    private ResourceDelivery.ResourceDeliveryBuilder builder;

    public static ResourceDeliveryTestBuilder builder() {
        return new ResourceDeliveryTestBuilder();
    }

    public ResourceDeliveryTestBuilder() {
        this.builder = ResourceDelivery.builder();
    }

    public ResourceDelivery build() {
        return builder.build();
    }

    public ResourceDeliveryTestBuilder withDummyClientReferenceId() {
        builder.delivery(Delivery.builder()
                        .clientReferenceId("some-uuid")
                .build()).build();
        return this;
    }

    public ResourceDeliveryTestBuilder withRegistrationClientReferenceId() {
        builder.delivery(Delivery.builder()
                .registrationClientReferenceId("some-registration-uuid")
                .build()).build();
        return this;
    }

    public ResourceDeliveryTestBuilder withRegistrationClientReferenceId(String id) {
        builder.delivery(Delivery.builder()
                .registrationClientReferenceId(id)
                .build()).build();
        return this;
    }
}
