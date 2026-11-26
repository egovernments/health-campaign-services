package org.egov.id.utils;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.common.contract.request.User;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class TenantAccessUtilTest {

    private static Role role(String tenantId) {
        return Role.builder().code("SUPERUSER").tenantId(tenantId).build();
    }

    private static RequestInfo requestInfoWithRoles(Role... roles) {
        return RequestInfo.builder()
                .userInfo(User.builder().uuid("user-1").roles(Arrays.asList(roles)).build())
                .build();
    }

    private static void assertRejected(RequestInfo requestInfo, String tenantId) {
        CustomException exception = assertThrows(CustomException.class,
                () -> TenantAccessUtil.validateTenantAccess(requestInfo, tenantId));
        assertEquals("INVALID_TENANT_ACCESS", exception.getCode());
        assertEquals("User does not have access to the requested tenant", exception.getMessage());
    }

    @Test
    void sameTenantIsAllowed() {
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(requestInfoWithRoles(role("pb")), "pb"));
    }

    @Test
    void childTenantOfRoleTenantIsAllowed() {
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(requestInfoWithRoles(role("pb")), "pb.amritsar"));
    }

    @Test
    void otherTenantIsRejected() {
        assertRejected(requestInfoWithRoles(role("pb")), "mz");
    }

    @Test
    void tenantWithSamePrefixButNoDotIsRejected() {
        assertRejected(requestInfoWithRoles(role("pb")), "pbx");
    }

    @Test
    void parentTenantIsRejectedForChildLevelRole() {
        assertRejected(requestInfoWithRoles(role("pb.amritsar")), "pb");
    }

    @Test
    void anyMatchingRoleIsEnough() {
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(
                requestInfoWithRoles(role("mz"), role("pb")), "pb.amritsar"));
    }

    // No user roles means an internal call from another service, so the tenant check is skipped.

    @Test
    void nullRequestInfoIsAllowedAsInternalCall() {
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(null, "pb"));
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(null, "mz"));
    }

    @Test
    void nullUserInfoIsAllowedAsInternalCall() {
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(RequestInfo.builder().build(), "pb"));
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(RequestInfo.builder().build(), "mz"));
    }

    @Test
    void nullRolesAreAllowedAsInternalCall() {
        RequestInfo requestInfo = RequestInfo.builder().userInfo(User.builder().uuid("user-1").build()).build();
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(requestInfo, "pb"));
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(requestInfo, "mz"));
    }

    @Test
    void emptyRolesAreAllowedAsInternalCall() {
        RequestInfo requestInfo = RequestInfo.builder()
                .userInfo(User.builder().uuid("user-1").roles(Collections.emptyList()).build())
                .build();
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(requestInfo, "pb"));
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(requestInfo, "mz"));
    }

    @Test
    void roleWithNullTenantIsIgnored() {
        assertRejected(requestInfoWithRoles(role(null)), "pb");
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(
                requestInfoWithRoles(role(null), role("pb")), "pb"));
    }

    @Test
    void nullRoleEntryIsIgnored() {
        assertRejected(requestInfoWithRoles((Role) null), "pb");
    }

    @Test
    void blankRequestTenantIsNotRejected() {
        RequestInfo withRoles = requestInfoWithRoles(role("pb"));
        RequestInfo withoutUser = RequestInfo.builder().build();
        for (String blank : new String[]{null, "", "   "}) {
            assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(withRoles, blank));
            assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(withoutUser, blank));
            assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(null, blank));
        }
    }

    @Test
    void rejectionMessageDoesNotContainRequestTenant() {
        CustomException exception = assertThrows(CustomException.class,
                () -> TenantAccessUtil.validateTenantAccess(requestInfoWithRoles(role("pb")), "secret-tenant"));
        assertFalse(exception.getMessage().contains("secret-tenant"));
        assertFalse(exception.getCode().contains("secret-tenant"));
    }
}
