package org.egov.servicerequest.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.common.contract.request.User;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.servicerequest.config.Configuration;
import org.egov.servicerequest.helper.ServiceRequestTestBuilder;
import org.egov.servicerequest.kafka.Producer;
import org.egov.servicerequest.repository.ServiceRequestRepository;
import org.egov.servicerequest.validators.ServiceRequestValidator;
import org.egov.servicerequest.web.models.Service;
import org.egov.servicerequest.web.models.ServiceCriteria;
import org.egov.servicerequest.web.models.ServiceRequest;
import org.egov.servicerequest.web.models.ServiceSearchRequest;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        lenient().when(configuration.getServiceUpdateTopic()).thenReturn("update-service");
    }

    private RequestInfo requestInfoWithRoleTenant(String roleTenant) {
        return RequestInfo.builder().userInfo(User.builder()
                .roles(Collections.singletonList(Role.builder().code("EMPLOYEE").tenantId(roleTenant).build())).build()).build();
    }

    @Test
    @DisplayName("should call kafka topic if valid service request found for create")
    void shouldCallKafkaTopicCreate() throws InvalidTenantIdException {
        ServiceRequest serviceRequest = ServiceRequestTestBuilder.builder().withServices().withRequestInfo().build();

        Service service = serviceRequestService.createService(serviceRequest);

        assertEquals(service,serviceRequest.getService());
        verify(producer,times(1)).push(anyString(), eq("save-service"),any(ServiceRequest.class));

    }

    @Test
    @DisplayName("should reject create when the user has no role for the service tenant")
    void shouldRejectCreateForOtherTenant() throws InvalidTenantIdException {
        ServiceRequest serviceRequest = ServiceRequestTestBuilder.builder().withServices()
                .withRequestInfo(requestInfoWithRoleTenant("other")).build();

        CustomException exception = assertThrows(CustomException.class, () -> serviceRequestService.createService(serviceRequest));

        assertEquals("INVALID_TENANT_ACCESS", exception.getCode());
        verify(serviceRequestValidator, never()).validateServiceRequest(any(ServiceRequest.class));
        verify(producer, never()).push(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("should call kafka topic for update if the user has a role for the service tenant")
    void shouldCallKafkaTopicUpdate() throws InvalidTenantIdException {
        ServiceRequest serviceRequest = ServiceRequestTestBuilder.builder().withServices()
                .withRequestInfo(requestInfoWithRoleTenant("default")).build();

        Service service = serviceRequestService.updateService(serviceRequest);

        assertEquals(service, serviceRequest.getService());
        verify(producer, times(1)).push(anyString(), eq("update-service"), any(ServiceRequest.class));
    }

    @Test
    @DisplayName("should reject update when the user has no role for the service tenant")
    void shouldRejectUpdateForOtherTenant() throws InvalidTenantIdException {
        ServiceRequest serviceRequest = ServiceRequestTestBuilder.builder().withServices()
                .withRequestInfo(requestInfoWithRoleTenant("other")).build();

        CustomException exception = assertThrows(CustomException.class, () -> serviceRequestService.updateService(serviceRequest));

        assertEquals("INVALID_TENANT_ACCESS", exception.getCode());
        verify(serviceRequestValidator, never()).validateServiceUpdateRequest(any(ServiceRequest.class));
        verify(producer, never()).push(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("should search services if the user has a role for the criteria tenant")
    void shouldSearchServiceForOwnTenant() throws InvalidTenantIdException {
        ServiceSearchRequest searchRequest = ServiceSearchRequest.builder()
                .requestInfo(requestInfoWithRoleTenant("default"))
                .serviceCriteria(ServiceCriteria.builder().tenantId("default.child").build()).build();
        Service found = Service.builder().id("some-id").tenantId("default.child").build();
        when(repository.getService(searchRequest)).thenReturn(Collections.singletonList(found));

        List<Service> result = serviceRequestService.searchService(searchRequest);

        assertEquals(Collections.singletonList(found), result);
    }

    @Test
    @DisplayName("should reject search when the user has no role for the criteria tenant")
    void shouldRejectSearchForOtherTenant() throws InvalidTenantIdException {
        ServiceSearchRequest searchRequest = ServiceSearchRequest.builder()
                .requestInfo(requestInfoWithRoleTenant("other"))
                .serviceCriteria(ServiceCriteria.builder().tenantId("default").build()).build();

        CustomException exception = assertThrows(CustomException.class, () -> serviceRequestService.searchService(searchRequest));

        assertEquals("INVALID_TENANT_ACCESS", exception.getCode());
        verify(repository, never()).getService(any(ServiceSearchRequest.class));
    }

}
