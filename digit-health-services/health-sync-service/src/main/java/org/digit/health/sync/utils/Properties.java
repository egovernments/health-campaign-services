package org.digit.health.sync.utils;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
@NoArgsConstructor
public class Properties {

    @Value("${health.registration.base.url}")
    private String registrationBaseUrl;

    @Value("${health.registration.create.endpoint}")
    private String registrationCreateEndpoint;

    @Value("${health.registration.base.url}")
    private String deliveryBaseUrl;

    @Value("${health.registration.create.endpoint}")
    private String deliveryCreateEndpoint;
}
