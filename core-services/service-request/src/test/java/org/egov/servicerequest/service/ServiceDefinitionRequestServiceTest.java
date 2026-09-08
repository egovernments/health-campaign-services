package org.egov.servicerequest.service;

import org.egov.common.exception.InvalidTenantIdException;
import org.egov.servicerequest.config.Configuration;
import org.egov.servicerequest.helper.ServiceDefinitionRequestTestBuilder;
import org.egov.servicerequest.kafka.Producer;
import org.egov.servicerequest.repository.ServiceDefinitionRequestRepository;
import org.egov.servicerequest.validators.ServiceDefinitionRequestValidator;
import org.egov.servicerequest.web.models.ServiceDefinition;
import org.egov.servicerequest.web.models.ServiceDefinitionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ServiceDefinitionRequestServiceTest {
    @InjectMocks
    private ServiceDefinitionRequestService serviceDefinitionRequestService;

    @Mock
    private ServiceDefinitionRequestRepository repository;

    @Mock
    private ServiceRequestEnrichmentService enrichmentService;

    @Mock
    private ServiceDefinitionRequestValidator serviceDefinitionRequestValidator;

    @Mock
    private Configuration configuration;

    @Mock
    private Producer producer;


    @BeforeEach
    void setUp() {
        lenient().when(configuration.getServiceDefinitionCreateTopic()).thenReturn("save-service-definition");
    }

    @Test
    @DisplayName("should call kafka topic if valid service definition request found for create")
    void shouldCallKafkaTopicCreate() throws InvalidTenantIdException {
        ServiceDefinitionRequest serviceDefinitionRequest = ServiceDefinitionRequestTestBuilder.builder()
                .withServiceDefinition()
                .withRequestInfo()
                .build();

        ServiceDefinition serviceDefinition = serviceDefinitionRequestService
                .createServiceDefinition(serviceDefinitionRequest);

        assertEquals(serviceDefinition,serviceDefinitionRequest.getServiceDefinition());
        verify(producer,times(1)).push(anyString(), eq("save-service-definition")
                ,any(ServiceDefinitionRequest.class));

    }

}
