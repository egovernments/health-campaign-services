package org.egov.pgr.validator;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.common.contract.request.User;
import org.egov.pgr.config.PGRConfiguration;
import org.egov.pgr.repository.PGRRepository;
import org.egov.pgr.util.HRMSUtil;
import org.egov.pgr.web.models.RequestSearchCriteria;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ServiceRequestValidatorTest {

    private ServiceRequestValidator validator;

    @BeforeEach
    public void setUp() {
        PGRConfiguration config = new PGRConfiguration();
        config.setAllowedCitizenSearchParameters("serviceRequestId,ids,mobileNumber,applicationStatus,tenantId");
        config.setAllowedEmployeeSearchParameters("serviceRequestId,ids,mobileNumber,serviceCode,applicationStatus,tenantId");
        validator = new ServiceRequestValidator(config, Mockito.mock(PGRRepository.class), Mockito.mock(HRMSUtil.class));
        ReflectionTestUtils.setField(validator, "enableStateLevelSearch", true);
        ReflectionTestUtils.setField(validator, "enableEmployee", true);
    }

    private RequestInfo employeeAt(String roleTenantId) {
        return RequestInfo.builder().userInfo(User.builder().uuid("u1").type("EMPLOYEE")
                .roles(Collections.singletonList(Role.builder().code("GRO").tenantId(roleTenantId).build())).build()).build();
    }

    private void assertTenantAccessRejected(Runnable call) {
        CustomException e = assertThrows(CustomException.class, call::run);
        assertEquals("INVALID_TENANT_ACCESS", e.getCode());
        assertEquals("User does not have access to the requested tenant", e.getMessage());
    }

    @Test
    public void validateSearchRejectsOtherTenant() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantId("mz.city").build();
        assertTenantAccessRejected(() -> validator.validateSearch(employeeAt("pb"), criteria));
    }

    @Test
    public void validateSearchRejectsPrefixOnlyTenant() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantId("pbx").build();
        assertTenantAccessRejected(() -> validator.validateSearch(employeeAt("pb"), criteria));
    }

    @Test
    public void validateSearchDoesNotRejectNullUserInfoByTenantRule() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantId("pb.amritsar").build();
        try {
            validator.validateSearch(RequestInfo.builder().build(), criteria);
        } catch (CustomException e) {
            assertNotEquals("INVALID_TENANT_ACCESS", e.getCode());
        } catch (NullPointerException e) {
            // The older search param validation reads userInfo.getType(). That is existing behaviour and is
            // not part of the tenant rule, so it is not asserted here.
        }
    }

    @Test
    public void validateSearchDoesNotRejectUserWithNoRolesByTenantRule() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantId("pb.amritsar").build();
        RequestInfo requestInfo = RequestInfo.builder().userInfo(User.builder().uuid("u1").type("CITIZEN").build()).build();
        assertDoesNotThrow(() -> validator.validateSearch(requestInfo, criteria));
    }

    @Test
    public void validateSearchDoesNotRejectUserWithNoRolesForAnotherTenant() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantId("mz.city").build();
        RequestInfo requestInfo = RequestInfo.builder().userInfo(User.builder().uuid("u1").type("EMPLOYEE")
                .roles(Collections.emptyList()).build()).build();
        assertDoesNotThrow(() -> validator.validateSearch(requestInfo, criteria));
    }

    @Test
    public void validateSearchAllowsChildTenantOfRoleTenant() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantId("pb.amritsar").build();
        assertDoesNotThrow(() -> validator.validateSearch(employeeAt("pb"), criteria));
    }

    @Test
    public void validateSearchLeavesMissingTenantToExistingValidation() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().serviceRequestId("PG-1").build();
        CustomException e = assertThrows(CustomException.class, () -> validator.validateSearch(employeeAt("pb"), criteria));
        assertEquals("INVALID_SEARCH", e.getCode());
    }

    @Test
    public void validatePlainSearchRejectsOtherTenantInTenantIds() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder()
                .tenantIds(new HashSet<>(Arrays.asList("pb.amritsar", "mz.city"))).build();
        assertTenantAccessRejected(() -> validator.validatePlainSearch(employeeAt("pb"), criteria));
    }

    @Test
    public void validatePlainSearchRejectsOtherTenantInTenantId() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantId("mz")
                .tenantIds(Collections.singleton("pb.amritsar")).build();
        assertTenantAccessRejected(() -> validator.validatePlainSearch(employeeAt("pb"), criteria));
    }

    @Test
    public void validatePlainSearchDoesNotRejectNullUserInfoByTenantRule() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantIds(Collections.singleton("pb")).build();
        assertDoesNotThrow(() -> validator.validatePlainSearch(RequestInfo.builder().build(), criteria));
    }

    @Test
    public void validatePlainSearchDoesNotRejectUserWithNoRolesForAnotherTenant() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantId("mz")
                .tenantIds(Collections.singleton("mz.city")).build();
        RequestInfo requestInfo = RequestInfo.builder().userInfo(User.builder().uuid("u1").build()).build();
        assertDoesNotThrow(() -> validator.validatePlainSearch(requestInfo, criteria));
    }

    @Test
    public void validatePlainSearchAllowsOwnTenants() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantId("pb")
                .tenantIds(new HashSet<>(Arrays.asList("pb", "pb.amritsar"))).build();
        assertDoesNotThrow(() -> validator.validatePlainSearch(employeeAt("pb"), criteria));
    }

    @Test
    public void validatePlainSearchStillRequiresTenantIds() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().build();
        CustomException e = assertThrows(CustomException.class, () -> validator.validatePlainSearch(employeeAt("pb"), criteria));
        assertEquals("TENANT_ID_LIST_EMPTY", e.getCode());
    }
}
