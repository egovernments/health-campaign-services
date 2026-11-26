package org.egov.pgr.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.common.contract.request.User;
import org.egov.pgr.config.PGRConfiguration;
import org.egov.pgr.producer.Producer;
import org.egov.pgr.repository.PGRRepository;
import org.egov.pgr.util.HRMSUtil;
import org.egov.pgr.util.MDMSUtils;
import org.egov.pgr.validator.ServiceRequestValidator;
import org.egov.pgr.web.models.RequestSearchCriteria;
import org.egov.pgr.web.models.Service;
import org.egov.pgr.web.models.ServiceRequest;
import org.egov.pgr.web.models.Workflow;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class PGRServiceTest {

    private EnrichmentService enrichmentService;
    private UserService userService;
    private WorkflowService workflowService;
    private Producer producer;
    private PGRConfiguration config;
    private PGRRepository repository;
    private MDMSUtils mdmsUtils;
    private ServiceRequestValidator realValidator;
    private ServiceRequestValidator mockValidator;

    private PGRService serviceWithRealValidator;
    private PGRService serviceWithMockValidator;

    @BeforeEach
    public void setUp() {
        enrichmentService = mock(EnrichmentService.class);
        userService = mock(UserService.class);
        workflowService = mock(WorkflowService.class);
        producer = mock(Producer.class);
        config = new PGRConfiguration();
        config.setAllowedCitizenSearchParameters("serviceRequestId,ids,mobileNumber,applicationStatus,tenantId");
        config.setAllowedEmployeeSearchParameters("serviceRequestId,ids,mobileNumber,serviceCode,applicationStatus,tenantId");
        config.setDefaultLimit(100);
        config.setDefaultOffset(0);
        config.setMaxLimit(200);
        repository = mock(PGRRepository.class);
        mdmsUtils = mock(MDMSUtils.class);

        realValidator = new ServiceRequestValidator(config, repository, mock(HRMSUtil.class));
        ReflectionTestUtils.setField(realValidator, "enableStateLevelSearch", true);
        ReflectionTestUtils.setField(realValidator, "enableEmployee", true);
        mockValidator = mock(ServiceRequestValidator.class);

        serviceWithRealValidator = new PGRService(enrichmentService, userService, workflowService, realValidator, realValidator,
                producer, config, repository, mdmsUtils);
        serviceWithMockValidator = new PGRService(enrichmentService, userService, workflowService, mockValidator, mockValidator,
                producer, config, repository, mdmsUtils);
    }

    private RequestInfo requestInfoWithRoleAt(String roleTenantId) {
        return RequestInfo.builder().userInfo(User.builder().uuid("u1").type("EMPLOYEE")
                .roles(Collections.singletonList(Role.builder().code("GRO").tenantId(roleTenantId).build())).build()).build();
    }

    private ServiceRequest serviceRequest(RequestInfo requestInfo, String tenantId) {
        return ServiceRequest.builder().requestInfo(requestInfo)
                .service(Service.builder().tenantId(tenantId).build())
                .workflow(Workflow.builder().build()).build();
    }

    private void assertTenantAccessRejected(Runnable call) {
        CustomException e = assertThrows(CustomException.class, call::run);
        assertEquals("INVALID_TENANT_ACCESS", e.getCode());
        assertEquals("User does not have access to the requested tenant", e.getMessage());
    }

    @Test
    public void createRejectsOtherTenantBeforeAnyWork() {
        ServiceRequest request = serviceRequest(requestInfoWithRoleAt("pb"), "mz.city");

        assertTenantAccessRejected(() -> serviceWithRealValidator.create(request));

        verifyNoInteractions(mdmsUtils, enrichmentService, workflowService, producer, repository);
    }

    @Test
    public void createWithNullUserInfoIsNotRejectedByTenantRule() {
        // no user roles means an internal call from another service: the tenant check is skipped
        ServiceRequest request = serviceRequest(RequestInfo.builder().build(), "pb.amritsar");

        assertDoesNotThrow(() -> serviceWithMockValidator.create(request));

        verify(mdmsUtils).mDMSCall(request);
        verify(mockValidator).validateCreate(any(), any());
        verify(producer, Mockito.times(2)).push(any(), any(), any());
    }

    @Test
    public void createProceedsForChildTenantOfRoleTenant() {
        ServiceRequest request = serviceRequest(requestInfoWithRoleAt("pb"), "pb.amritsar");
        when(mdmsUtils.mDMSCall(request)).thenReturn(new Object());

        serviceWithMockValidator.create(request);

        verify(mockValidator).validateCreate(any(), any());
        verify(producer, Mockito.times(2)).push(any(), any(), any());
    }

    @Test
    public void updateRejectsOtherTenantBeforeAnyWork() {
        ServiceRequest request = serviceRequest(requestInfoWithRoleAt("pb"), "mz.city");

        assertTenantAccessRejected(() -> serviceWithRealValidator.update(request));

        verifyNoInteractions(mdmsUtils, enrichmentService, workflowService, producer, repository);
    }

    @Test
    public void updateWithNoRolesIsNotRejectedByTenantRuleEvenForAnotherTenant() {
        RequestInfo noRoles = RequestInfo.builder().userInfo(User.builder().uuid("u1").type("EMPLOYEE")
                .roles(Collections.emptyList()).build()).build();
        ServiceRequest request = serviceRequest(noRoles, "mz.city");

        assertDoesNotThrow(() -> serviceWithMockValidator.update(request));

        verify(mockValidator).validateUpdate(any(), any());
        verify(producer).push(any(), any(), any());
    }

    @Test
    public void updateProceedsForOwnTenant() {
        ServiceRequest request = serviceRequest(requestInfoWithRoleAt("pb.amritsar"), "pb.amritsar");

        serviceWithMockValidator.update(request);

        verify(mockValidator).validateUpdate(any(), any());
        verify(producer).push(any(), any(), any());
    }

    @Test
    public void searchRejectsOtherTenantBeforeEnrichmentAndQuery() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantId("mz.city").build();

        assertTenantAccessRejected(() -> {
            try {
                serviceWithRealValidator.search(requestInfoWithRoleAt("pb"), criteria);
            } catch (org.egov.common.exception.InvalidTenantIdException e) {
                throw new IllegalStateException(e);
            }
        });

        verifyNoInteractions(enrichmentService, repository);
    }

    @Test
    public void plainSearchRejectsOtherTenantBeforeQuery() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder()
                .tenantIds(new HashSet<>(Arrays.asList("pb.amritsar", "mz.city"))).build();

        assertTenantAccessRejected(() -> {
            try {
                serviceWithRealValidator.plainSearch(requestInfoWithRoleAt("pb"), criteria);
            } catch (org.egov.common.exception.InvalidTenantIdException e) {
                throw new IllegalStateException(e);
            }
        });

        verifyNoInteractions(repository);
    }

    @Test
    public void countRejectsOtherTenantBeforeQuery() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantId("mz.city").build();

        assertTenantAccessRejected(() -> {
            try {
                serviceWithRealValidator.count(requestInfoWithRoleAt("pb"), criteria);
            } catch (org.egov.common.exception.InvalidTenantIdException e) {
                throw new IllegalStateException(e);
            }
        });

        verifyNoInteractions(repository, enrichmentService);
    }

    @Test
    public void countRejectsRoleAtSiblingTenantAndNeverQueries() throws Exception {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantId("pb.jalandhar").build();

        assertTenantAccessRejected(() -> {
            try {
                serviceWithRealValidator.count(requestInfoWithRoleAt("pb.amritsar"), criteria);
            } catch (org.egov.common.exception.InvalidTenantIdException e) {
                throw new IllegalStateException(e);
            }
        });

        verify(repository, never()).getCount(any());
        verify(enrichmentService, never()).restrictToOwnComplaints(any(), any());
    }

    @Test
    public void countRestrictsToOwnComplaintsBeforeQuery() throws Exception {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantId("pb.amritsar").build();
        RequestInfo requestInfo = requestInfoWithRoleAt("pb");
        when(repository.getCount(criteria)).thenReturn(5);

        Integer count = serviceWithRealValidator.count(requestInfo, criteria);

        assertEquals(5, count);
        org.mockito.InOrder inOrder = Mockito.inOrder(enrichmentService, repository);
        inOrder.verify(enrichmentService).restrictToOwnComplaints(requestInfo, criteria);
        inOrder.verify(repository).getCount(criteria);
    }

    @Test
    public void countWithoutTenantIsRejectedBeforeQuery() throws Exception {
        for (String tenant : new String[]{null, ""}) {
            RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantId(tenant).build();
            CustomException e = assertThrows(CustomException.class,
                    () -> serviceWithRealValidator.count(requestInfoWithRoleAt("pb"), criteria));
            assertEquals("INVALID_SEARCH", e.getCode());
        }
        Mockito.verify(repository, never()).getCount(any());
    }

    @Test
    public void countDoesNotCallValidateSearch() throws Exception {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantId("pb.amritsar").build();
        when(repository.getCount(criteria)).thenReturn(3);

        Integer count = serviceWithMockValidator.count(requestInfoWithRoleAt("pb"), criteria);

        assertEquals(3, count);
        verify(mockValidator, never()).validateSearch(any(), any());
    }

    @Test
    public void countDoesNotApplySearchParamRules() throws Exception {
        // serviceCode is not allowed for a citizen in validateSearch: count no longer goes through that validation
        RequestInfo citizen = RequestInfo.builder().userInfo(User.builder().uuid("c1").type("CITIZEN")
                .roles(Collections.singletonList(Role.builder().code("CITIZEN").tenantId("pb").build())).build()).build();
        config.setAllowedCitizenSearchParameters("tenantId");
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantId("pb.amritsar")
                .serviceCode(Collections.singleton("X")).build();
        when(repository.getCount(criteria)).thenReturn(2);

        Integer count = serviceWithRealValidator.count(citizen, criteria);

        assertEquals(2, count);
        verify(enrichmentService).restrictToOwnComplaints(citizen, criteria);
    }

    @Test
    public void countWithNoRolesIsNotRejectedByTenantRuleEvenForAnotherTenant() throws Exception {
        RequestInfo internalCall = RequestInfo.builder().build();
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantId("mz.city").build();
        when(repository.getCount(criteria)).thenReturn(7);

        Integer count = serviceWithRealValidator.count(internalCall, criteria);

        assertEquals(7, count);
        verify(enrichmentService).restrictToOwnComplaints(internalCall, criteria);
    }
}
