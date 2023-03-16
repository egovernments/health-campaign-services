package org.egov.servicerequest.validators;

import org.egov.servicerequest.helper.ServiceDefinitionRequestTestBuilder;
import org.egov.servicerequest.repository.ServiceDefinitionRequestRepository;
import org.egov.servicerequest.web.models.ServiceDefinitionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@ExtendWith(MockitoExtension.class)
public class ServiceDefinitionRequestValidatorTest {

    @InjectMocks
    ServiceDefinitionRequestValidator serviceDefinitionRequestValidator;

    @Mock
    ServiceDefinitionRequestRepository serviceDefinitionRequestRepository;

    @Test
    @DisplayName("should validate service definition request")
    void shouldValidateServiceDefinitionRequest() {
        ServiceDefinitionRequest serviceDefinitionRequest = ServiceDefinitionRequestTestBuilder.builder().withServiceDefinition()
                .withRequestInfo()
                .build();

        assertDoesNotThrow(() -> serviceDefinitionRequestValidator.validateServiceDefinitionRequest(serviceDefinitionRequest));
    }
}
