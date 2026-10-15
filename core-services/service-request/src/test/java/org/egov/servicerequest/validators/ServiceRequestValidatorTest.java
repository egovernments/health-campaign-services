package org.egov.servicerequest.validators;

import org.egov.common.exception.InvalidTenantIdException;
import org.egov.servicerequest.config.Configuration;
import org.egov.servicerequest.helper.ServiceRequestTestBuilder;
import org.egov.servicerequest.repository.ServiceDefinitionRequestRepository;
import org.egov.servicerequest.repository.ServiceRequestRepository;
import org.egov.servicerequest.web.models.AttributeDefinition;
import org.egov.servicerequest.web.models.ServiceDefinition;
import org.egov.servicerequest.web.models.ServiceDefinitionSearchRequest;
import org.egov.servicerequest.web.models.ServiceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ServiceRequestValidatorTest {

    @InjectMocks
    ServiceRequestValidator serviceRequestValidator;

    @Mock
    ServiceRequestRepository serviceRequestRepository;

    @Mock
    ServiceDefinitionRequestRepository serviceDefinitionRequestRepository;

    @Mock
    Configuration configuration;

    @BeforeEach
    void setUp() {
        lenient().when(configuration.getMaxStringInputSize()).thenReturn(100);
    }

    @Test
    @DisplayName("should validate service request")
    void shouldValidateServiceRequest() throws InvalidTenantIdException {
        ServiceRequest serviceRequest = ServiceRequestTestBuilder.builder().withServices().withRequestInfo().build();

        when(serviceDefinitionRequestRepository.getServiceDefinitions(any(ServiceDefinitionSearchRequest.class)))
                .thenReturn(Collections.singletonList(ServiceDefinition
                        .builder()
                        .id("id")
                        .tenantId("default")
                        .code("code")
                        .isActive(true)
                        .attributes(Collections.singletonList(AttributeDefinition
                                .builder()
                                .id("id")
                                .tenantId("default")
                                .order("order")
                                .dataType(AttributeDefinition.DataTypeEnum.STRING)
                                .values(Arrays.asList("value"))
                                .required(false)
                                .code("code")
                                .build())).build()));

        assertDoesNotThrow(() -> serviceRequestValidator.validateServiceRequest(serviceRequest));

    }
}
