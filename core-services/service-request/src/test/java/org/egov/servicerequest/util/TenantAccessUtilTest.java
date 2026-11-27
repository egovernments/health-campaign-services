package org.egov.servicerequest.util;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.common.contract.request.User;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TenantAccessUtilTest {

    private RequestInfo requestInfoWithRoleTenants(String... roleTenants) {
        List<Role> roles = new ArrayList<>();
        for (String roleTenant : roleTenants) {
            roles.add(Role.builder().code("EMPLOYEE").tenantId(roleTenant).build());
        }
        return RequestInfo.builder().userInfo(User.builder().roles(roles).build()).build();
    }

    private void assertDenied(RequestInfo requestInfo, String tenantId) {
        CustomException exception = assertThrows(CustomException.class,
                () -> TenantAccessUtil.validateTenantAccess(requestInfo, tenantId));
        assertEquals("INVALID_TENANT_ACCESS", exception.getCode());
        assertEquals("User does not have access to the requested tenant", exception.getMessage());
        if (tenantId != null && !tenantId.trim().isEmpty())
            assertFalse(exception.getMessage().contains(tenantId));
    }

    @Test
    @DisplayName("should allow the same tenant")
    void shouldAllowSameTenant() {
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(requestInfoWithRoleTenants("pb"), "pb"));
    }

    @Test
    @DisplayName("should allow a child tenant of the role tenant")
    void shouldAllowChildTenant() {
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(requestInfoWithRoleTenants("pb"), "pb.amritsar"));
    }

    @Test
    @DisplayName("should allow when any one of the roles covers the tenant")
    void shouldAllowWhenAnyRoleMatches() {
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(requestInfoWithRoleTenants("mz", "pb"), "pb.amritsar"));
    }

    @Test
    @DisplayName("should reject another tenant")
    void shouldRejectOtherTenant() {
        assertDenied(requestInfoWithRoleTenants("pb"), "mz");
    }

    @Test
    @DisplayName("should reject a tenant that only shares the role tenant as a text prefix")
    void shouldRejectTenantWithSamePrefixButNotChild() {
        assertDenied(requestInfoWithRoleTenants("pb"), "pbx");
    }

    @Test
    @DisplayName("should reject a parent tenant when the role is at the child tenant")
    void shouldRejectParentTenant() {
        assertDenied(requestInfoWithRoleTenants("pb.amritsar"), "pb");
    }

    @Test
    @DisplayName("should allow when userInfo is null, it is an internal call from another service")
    void shouldAllowNullUserInfoAsInternalCall() {
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(RequestInfo.builder().build(), "pb"));
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(RequestInfo.builder().build(), "mz"));
    }

    @Test
    @DisplayName("should allow when requestInfo is null, it is an internal call from another service")
    void shouldAllowNullRequestInfoAsInternalCall() {
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(null, "pb"));
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(null, "mz"));
    }

    @Test
    @DisplayName("should allow when the user has no roles, it is an internal call from another service")
    void shouldAllowNoRolesAsInternalCall() {
        RequestInfo emptyRoles = RequestInfo.builder().userInfo(User.builder().roles(Collections.emptyList()).build()).build();
        RequestInfo nullRoles = RequestInfo.builder().userInfo(User.builder().build()).build();
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(emptyRoles, "pb"));
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(emptyRoles, "mz"));
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(nullRoles, "pb"));
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(nullRoles, "mz"));
    }

    @Test
    @DisplayName("should allow a transformer like user with only a uuid and no roles")
    void shouldAllowUserWithUuidAndNoRoles() {
        RequestInfo transformerLike = RequestInfo.builder().userInfo(User.builder().uuid("transformer-uuid").build()).build();
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(transformerLike, "pb.amritsar"));
    }

    @Test
    @DisplayName("should ignore roles whose tenantId is null")
    void shouldIgnoreRoleWithNullTenant() {
        assertDenied(requestInfoWithRoleTenants((String) null), "pb");
        assertDenied(RequestInfo.builder().userInfo(User.builder().roles(Arrays.asList(null, Role.builder().build())).build()).build(), "pb");
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(requestInfoWithRoleTenants(null, "pb"), "pb"));
    }

    @Test
    @DisplayName("should not reject a null or blank request tenant, existing validation handles it")
    void shouldNotRejectBlankRequestTenant() {
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(requestInfoWithRoleTenants("pb"), null));
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(requestInfoWithRoleTenants("pb"), ""));
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(requestInfoWithRoleTenants("pb"), "   "));
    }
}
