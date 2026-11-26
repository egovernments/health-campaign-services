package org.egov.hrms.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Collections;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.common.contract.request.User;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.Test;

class TenantAccessUtilTest {

	private static RequestInfo requestInfoWithRoleTenants(String... roleTenantIds) {
		Role[] roles = new Role[roleTenantIds.length];
		for (int i = 0; i < roleTenantIds.length; i++)
			roles[i] = Role.builder().code("EMPLOYEE").tenantId(roleTenantIds[i]).build();
		return RequestInfo.builder()
				.userInfo(User.builder().uuid("u1").roles(Arrays.asList(roles)).build())
				.build();
	}

	private static void assertDenied(RequestInfo requestInfo, String tenantId) {
		CustomException e = assertThrows(CustomException.class,
				() -> TenantAccessUtil.validateTenantAccess(requestInfo, tenantId));
		assertEquals("INVALID_TENANT_ACCESS", e.getCode());
		assertEquals("User does not have access to the requested tenant", e.getMessage());
	}

	@Test
	void sameTenantIsAllowed() {
		assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(requestInfoWithRoleTenants("pb"), "pb"));
	}

	@Test
	void childTenantIsAllowedForStateLevelRole() {
		assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(requestInfoWithRoleTenants("pb"), "pb.amritsar"));
	}

	@Test
	void otherTenantIsDenied() {
		assertDenied(requestInfoWithRoleTenants("pb"), "mz");
	}

	@Test
	void prefixWithoutDotIsDenied() {
		assertDenied(requestInfoWithRoleTenants("pb"), "pbx");
	}

	@Test
	void cityLevelRoleDoesNotAllowStateTenant() {
		assertDenied(requestInfoWithRoleTenants("pb.amritsar"), "pb");
	}

	@Test
	void anyMatchingRoleIsEnough() {
		assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(requestInfoWithRoleTenants("mz", "pb"), "pb.amritsar"));
	}

	@Test
	void nullUserInfoIsAllowedAsInternalCall() {
		RequestInfo noUser = RequestInfo.builder().build();
		assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(noUser, "pb"));
		assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(noUser, "mz"));
	}

	@Test
	void nullRequestInfoIsAllowedAsInternalCall() {
		assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(null, "pb"));
		assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(null, "mz"));
	}

	@Test
	void noRolesIsAllowedAsInternalCall() {
		RequestInfo emptyRoles = RequestInfo.builder()
				.userInfo(User.builder().uuid("u1").roles(Collections.emptyList()).build()).build();
		RequestInfo nullRoles = RequestInfo.builder().userInfo(User.builder().uuid("u1").build()).build();
		assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(emptyRoles, "pb"));
		assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(emptyRoles, "mz"));
		assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(nullRoles, "pb"));
		assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(nullRoles, "mz"));
	}

	@Test
	void roleWithNullTenantIdIsIgnored() {
		assertDenied(requestInfoWithRoleTenants((String) null), "pb");
		assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(requestInfoWithRoleTenants(null, "pb"), "pb.amritsar"));
	}

	@Test
	void blankRequestTenantIsNotRejectedByThisRule() {
		RequestInfo noUser = RequestInfo.builder().build();
		assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(noUser, null));
		assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(noUser, ""));
		assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(requestInfoWithRoleTenants("pb"), "  "));
	}

	@Test
	void errorMessageNeverCarriesRequestValues() {
		CustomException e = assertThrows(CustomException.class,
				() -> TenantAccessUtil.validateTenantAccess(requestInfoWithRoleTenants("pb"), "secret-tenant"));
		assertFalse(e.getMessage().contains("secret-tenant"));
		assertFalse(e.getCode().contains("secret-tenant"));
	}
}
