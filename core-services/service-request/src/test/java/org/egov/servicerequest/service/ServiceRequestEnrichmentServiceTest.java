package org.egov.servicerequest.service;

import org.egov.servicerequest.config.Configuration;
import org.egov.servicerequest.helper.ServiceDefinitionRequestTestBuilder;
import org.egov.servicerequest.helper.ServiceRequestTestBuilder;
import org.egov.servicerequest.web.models.ServiceDefinitionRequest;
import org.egov.servicerequest.web.models.ServiceRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
public class ServiceRequestEnrichmentServiceTest {

    @InjectMocks
    ServiceRequestEnrichmentService serviceRequestEnrichmentService;

    @Mock
    Configuration Configuration;



    @Test
    @DisplayName("should enrich service request with audit details")
    void shouldEnrichServiceRequestWithAuditDetails() throws Exception {
        ServiceRequest serviceRequest = ServiceRequestTestBuilder.builder().withServices().withRequestInfo()
                .build();

        Map<String, Object> convertAttributeValuesIntoJson = serviceRequestEnrichmentService
                .enrichServiceRequest(serviceRequest);

        assertNotNull(serviceRequest.getService().getAuditDetails().getCreatedBy());
        assertNotNull(serviceRequest.getService().getAuditDetails().getCreatedTime());
        assertNotNull(serviceRequest.getService().getAuditDetails().getLastModifiedBy());
        assertNotNull(serviceRequest.getService().getAuditDetails().getLastModifiedTime());
        assertNotNull(serviceRequest.getService().getAttributes().get(0).getId());
        assertNotNull(serviceRequest.getService().getAttributes().get(0).getAuditDetails());
        assertNotNull(serviceRequest.getService().getAttributes().get(0).getReferenceId());
        assertNotNull(convertAttributeValuesIntoJson);

    }

    @Test
    @DisplayName("should enrich service definition request with audit details")
    void shouldEnrichServiceDefinitionRequestWithAuditDetails() throws Exception {
        ServiceDefinitionRequest serviceDefinitionRequest = ServiceDefinitionRequestTestBuilder.builder()
                .withServiceDefinition()
                .withRequestInfo()
                .build();

        serviceRequestEnrichmentService.enrichServiceDefinitionRequest(serviceDefinitionRequest);

        assertNotNull(serviceDefinitionRequest.getServiceDefinition().getAuditDetails().getCreatedBy());
        assertNotNull(serviceDefinitionRequest.getServiceDefinition().getAuditDetails().getCreatedTime());
        assertNotNull(serviceDefinitionRequest.getServiceDefinition().getAuditDetails().getLastModifiedBy());
        assertNotNull(serviceDefinitionRequest.getServiceDefinition().getAuditDetails().getLastModifiedTime());
        assertNotNull(serviceDefinitionRequest.getServiceDefinition().getAttributes().get(0).getId());
        assertNotNull(serviceDefinitionRequest.getServiceDefinition().getAttributes().get(0).getAuditDetails());
        assertNotNull(serviceDefinitionRequest.getServiceDefinition().getAttributes().get(0).getReferenceId());

    }

    @Test
    @DisplayName("should set attribute definition values back to native state")
    void shouldSetAttributeDefinitionValuesBackToNativeState() {
        ServiceDefinitionRequest serviceDefinitionRequest = ServiceDefinitionRequestTestBuilder.builder()
                .withServiceDefinition()
                .withRequestInfo()
                .build();

        serviceRequestEnrichmentService.setAttributeDefinitionValuesBackToNativeState(serviceDefinitionRequest
                .getServiceDefinition());
        Object dummyValue=null;

        assertEquals(dummyValue,serviceDefinitionRequest.getServiceDefinition().getAttributes().get(0).getValues());

    }

    @Test
    @DisplayName("should set attribute values back to native state")
    void shouldSetAttributeValuesBackToNativeState() {
        ServiceRequest serviceRequest = ServiceRequestTestBuilder.builder()
                .withServices()
                .withRequestInfo()
                .build();
        Map<String, Object> attributeCodeVsValueMap = serviceRequestEnrichmentService.enrichServiceRequest(serviceRequest);

        serviceRequestEnrichmentService.setAttributeValuesBackToNativeState(serviceRequest,attributeCodeVsValueMap);
        assertNotNull(serviceRequest.getService().getAttributes().get(0).getValue());
    }
}
