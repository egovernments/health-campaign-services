package org.egov.servicerequest.service;

import org.egov.common.exception.InvalidTenantIdException;
import org.egov.servicerequest.config.Configuration;
import org.egov.servicerequest.helper.ServiceRequestTestBuilder;
import org.egov.servicerequest.kafka.Producer;
import org.egov.servicerequest.repository.ServiceRequestRepository;
import org.egov.servicerequest.validators.ServiceRequestValidator;
import org.egov.servicerequest.web.models.Service;
import org.egov.servicerequest.web.models.ServiceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ServiceRequestServiceTest {


    @InjectMocks
    private ServiceRequestService serviceRequestService;

    @Mock
    private ServiceRequestRepository repository;


    @Mock
    private ServiceRequestEnrichmentService enrichmentService;

    @Mock
    private ServiceRequestValidator serviceRequestValidator;

    @Mock
    private Configuration configuration;

    @Mock
    private Producer producer;


    @BeforeEach
    void setUp() {
        lenient().when(configuration.getServiceCreateTopic()).thenReturn("save-service");
    }
    @Test
    @DisplayName("should call kafka topic if valid service request found for create")
    void shouldCallKafkaTopicCreate() throws InvalidTenantIdException {
        ServiceRequest serviceRequest = ServiceRequestTestBuilder.builder().withServices().withRequestInfo().build();

        Service service = serviceRequestService.createService(serviceRequest);

        assertEquals(service,serviceRequest.getService());
        verify(producer,times(1)).push(anyString(), eq("save-service"),any(ServiceRequest.class));

    }


}
