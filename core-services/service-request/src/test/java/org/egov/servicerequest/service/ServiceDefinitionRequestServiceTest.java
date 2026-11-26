package org.egov.servicerequest.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.common.contract.request.User;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.servicerequest.config.Configuration;
import org.egov.servicerequest.helper.ServiceDefinitionRequestTestBuilder;
import org.egov.servicerequest.kafka.Producer;
import org.egov.servicerequest.repository.ServiceDefinitionRequestRepository;
import org.egov.servicerequest.validators.ServiceDefinitionRequestValidator;
import org.egov.servicerequest.helper.ServiceDefinitionTestBuilder;
import org.egov.servicerequest.web.models.ServiceDefinition;
import org.egov.servicerequest.web.models.ServiceDefinitionCriteria;
import org.egov.servicerequest.web.models.ServiceDefinitionRequest;
import org.egov.servicerequest.web.models.ServiceDefinitionSearchRequest;
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
        lenient().when(configuration.getServiceDefinitionUpdateTopic()).thenReturn("update-service-definition");
    }

    private RequestInfo requestInfoWithRoleTenant(String roleTenant) {
        return RequestInfo.builder().userInfo(User.builder()
                .roles(Collections.singletonList(Role.builder().code("EMPLOYEE").tenantId(roleTenant).build())).build()).build();
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

    @Test
    @DisplayName("should reject create when the user has no role for the service definition tenant")
    void shouldRejectCreateForOtherTenant() throws InvalidTenantIdException {
        ServiceDefinitionRequest serviceDefinitionRequest = ServiceDefinitionRequestTestBuilder.builder()
                .withServiceDefinition()
                .withRequestInfo(requestInfoWithRoleTenant("other"))
                .build();

        CustomException exception = assertThrows(CustomException.class,
                () -> serviceDefinitionRequestService.createServiceDefinition(serviceDefinitionRequest));

        assertEquals("INVALID_TENANT_ACCESS", exception.getCode());
        verify(serviceDefinitionRequestValidator, never()).validateServiceDefinitionRequest(any(ServiceDefinitionRequest.class));
        verify(producer, never()).push(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("should call kafka topic for update if the user has a role for the service definition tenant")
    void shouldCallKafkaTopicUpdate() throws InvalidTenantIdException {
        ServiceDefinitionRequest serviceDefinitionRequest = ServiceDefinitionRequestTestBuilder.builder()
                .withServiceDefinition()
                .withRequestInfo(requestInfoWithRoleTenant("default"))
                .build();
        when(serviceDefinitionRequestValidator.validateUpdateRequest(serviceDefinitionRequest))
                .thenReturn(serviceDefinitionRequest.getServiceDefinition());

        ServiceDefinition serviceDefinition = serviceDefinitionRequestService.updateServiceDefinition(serviceDefinitionRequest);

        assertEquals(serviceDefinition, serviceDefinitionRequest.getServiceDefinition());
        verify(producer, times(1)).push(anyString(), eq("update-service-definition"), any(ServiceDefinitionRequest.class));
    }

    @Test
    @DisplayName("should reject update when the user has no role for the service definition tenant")
    void shouldRejectUpdateForOtherTenant() throws InvalidTenantIdException {
        ServiceDefinitionRequest serviceDefinitionRequest = ServiceDefinitionRequestTestBuilder.builder()
                .withServiceDefinition()
                .withRequestInfo(requestInfoWithRoleTenant("other"))
                .build();

        CustomException exception = assertThrows(CustomException.class,
                () -> serviceDefinitionRequestService.updateServiceDefinition(serviceDefinitionRequest));

        assertEquals("INVALID_TENANT_ACCESS", exception.getCode());
        verify(serviceDefinitionRequestValidator, never()).validateUpdateRequest(any(ServiceDefinitionRequest.class));
        verify(producer, never()).push(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("should search service definitions if the user has a role for the criteria tenant")
    void shouldSearchServiceDefinitionForOwnTenant() throws InvalidTenantIdException {
        ServiceDefinitionSearchRequest searchRequest = ServiceDefinitionSearchRequest.builder()
                .requestInfo(requestInfoWithRoleTenant("default"))
                .serviceDefinitionCriteria(ServiceDefinitionCriteria.builder().tenantId("default.child").build()).build();
        ServiceDefinition found = ServiceDefinitionTestBuilder.builder().withServiceDefinition().build();
        when(repository.getServiceDefinitions(searchRequest)).thenReturn(Collections.singletonList(found));

        List<ServiceDefinition> result = serviceDefinitionRequestService.searchServiceDefinition(searchRequest);

        assertEquals(Collections.singletonList(found), result);
    }

    @Test
    @DisplayName("should search service definitions for an internal call with a user uuid and no roles, like the transformer")
    void shouldSearchServiceDefinitionForInternalCallWithoutRoles() throws InvalidTenantIdException {
        RequestInfo transformerLike = RequestInfo.builder().userInfo(User.builder().uuid("transformer-uuid").build()).build();
        ServiceDefinitionSearchRequest searchRequest = ServiceDefinitionSearchRequest.builder()
                .requestInfo(transformerLike)
                .serviceDefinitionCriteria(ServiceDefinitionCriteria.builder().tenantId("pb.amritsar").build()).build();
        ServiceDefinition found = ServiceDefinitionTestBuilder.builder().withServiceDefinition().build();
        when(repository.getServiceDefinitions(searchRequest)).thenReturn(Collections.singletonList(found));

        List<ServiceDefinition> result = serviceDefinitionRequestService.searchServiceDefinition(searchRequest);

        assertEquals(Collections.singletonList(found), result);
        verify(repository, times(1)).getServiceDefinitions(searchRequest);
    }

    @Test
    @DisplayName("should reject search when the user has no role for the criteria tenant")
    void shouldRejectSearchForOtherTenant() throws InvalidTenantIdException {
        ServiceDefinitionSearchRequest searchRequest = ServiceDefinitionSearchRequest.builder()
                .requestInfo(requestInfoWithRoleTenant("other"))
                .serviceDefinitionCriteria(ServiceDefinitionCriteria.builder().tenantId("default").build()).build();

        CustomException exception = assertThrows(CustomException.class,
                () -> serviceDefinitionRequestService.searchServiceDefinition(searchRequest));

        assertEquals("INVALID_TENANT_ACCESS", exception.getCode());
        verify(repository, never()).getServiceDefinitions(any(ServiceDefinitionSearchRequest.class));
    }

}
