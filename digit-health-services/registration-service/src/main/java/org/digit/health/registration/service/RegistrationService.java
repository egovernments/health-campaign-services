package org.digit.health.registration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.digit.health.registration.kafka.Producer;
import org.digit.health.registration.web.models.RegistrationId;
import org.digit.health.registration.web.models.request.RegistrationDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Slf4j
@Service("registrationService")
public class RegistrationService {

    private final Producer producer;
    private final ObjectMapper objectMapper;


    @Autowired
    public RegistrationService(Producer producer, ObjectMapper objectMapper) {
        this.producer = producer;
        this.objectMapper = objectMapper;
    }

    public RegistrationId register(RegistrationDto registrationDto) {
        return RegistrationId.builder().registrationId("registration-id").build();
    }

}
