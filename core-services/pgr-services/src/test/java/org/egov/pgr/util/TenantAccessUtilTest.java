package org.egov.pgr.util;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.common.contract.request.User;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TenantAccessUtilTest {

    private RequestInfo requestInfoWithRoleAt(String... roleTenantIds) {
        List<Role> roles = new ArrayList<>();
        for (String roleTenantId : roleTenantIds)
            roles.add(Role.builder().code("CITIZEN").tenantId(roleTenantId).build());
        return RequestInfo.builder().userInfo(User.builder().uuid("u1").roles(roles).build()).build();
    }

    private void assertRejected(RequestInfo requestInfo, String tenantId) {
        CustomException e = assertThrows(CustomException.class,
                () -> TenantAccessUtil.validateTenantAccess(requestInfo, tenantId));
        assertEquals("INVALID_TENANT_ACCESS", e.getCode());
        assertEquals("User does not have access to the requested tenant", e.getMessage());
    }

    @Test
    public void sameTenantIsAllowed() {
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(requestInfoWithRoleAt("pb.amritsar"), "pb.amritsar"));
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(requestInfoWithRoleAt("pb"), "pb"));
    }

    @Test
    public void childTenantOfStateLevelRoleIsAllowed() {
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(requestInfoWithRoleAt("pb"), "pb.amritsar"));
    }

    @Test
    public void otherTenantIsRejected() {
        assertRejected(requestInfoWithRoleAt("pb"), "mz");
        assertRejected(requestInfoWithRoleAt("pb.amritsar"), "pb.jalandhar");
    }

    @Test
    public void tenantWithSamePrefixButNotChildIsRejected() {
        assertRejected(requestInfoWithRoleAt("pb"), "pbx");
        assertRejected(requestInfoWithRoleAt("pb"), "pbx.city");
    }

    @Test
    public void cityRoleDoesNotAllowStateLevelTenant() {
        assertRejected(requestInfoWithRoleAt("pb.amritsar"), "pb");
    }

    @Test
    public void anyOneMatchingRoleIsEnough() {
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(requestInfoWithRoleAt("mz", "pb"), "pb.amritsar"));
    }

    @Test
    public void nullUserInfoIsAllowedAsInternalCall() {
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(RequestInfo.builder().build(), "pb"));
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(null, "pb"));
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(RequestInfo.builder().build(), "mz"));
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(null, "mz"));
    }

    @Test
    public void userWithNoRolesIsAllowedAsInternalCall() {
        RequestInfo nullRoles = RequestInfo.builder().userInfo(User.builder().uuid("u1").build()).build();
        RequestInfo emptyRoles = RequestInfo.builder().userInfo(User.builder().uuid("u1").roles(Collections.emptyList()).build()).build();

        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(nullRoles, "pb"));
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(emptyRoles, "pb"));
        // a foreign tenant is also skipped: without roles there is nothing to compare the tenant with
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(nullRoles, "mz"));
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(emptyRoles, "mz"));
    }

    @Test
    public void userWithNoRolesIsAllowedWhenTenantIsBlank() {
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(
                RequestInfo.builder().userInfo(User.builder().uuid("u1").build()).build(), null));
    }

    @Test
    public void internalCallSkipsEveryTenantInTheList() {
        RequestInfo noRoles = RequestInfo.builder().userInfo(User.builder().uuid("u1").build()).build();

        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(noRoles, "mz",
                new HashSet<>(Arrays.asList("pb.amritsar", "other"))));
    }

    @Test
    public void isInternalCallIsTrueWhenThereIsNoUserOrNoRoles() {
        assertTrue(TenantAccessUtil.isInternalCall(null));
        assertTrue(TenantAccessUtil.isInternalCall(RequestInfo.builder().build()));
        assertTrue(TenantAccessUtil.isInternalCall(RequestInfo.builder().userInfo(User.builder().uuid("u1").build()).build()));
        assertTrue(TenantAccessUtil.isInternalCall(
                RequestInfo.builder().userInfo(User.builder().uuid("u1").roles(Collections.emptyList()).build()).build()));
    }

    @Test
    public void isInternalCallIsFalseWhenUserHasAnyRole() {
        assertFalse(TenantAccessUtil.isInternalCall(requestInfoWithRoleAt("pb")));
        // a role without a tenant, or a null entry in the list, is still not an internal call
        assertFalse(TenantAccessUtil.isInternalCall(requestInfoWithRoleAt((String) null)));
        assertFalse(TenantAccessUtil.isInternalCall(RequestInfo.builder()
                .userInfo(User.builder().uuid("u1").roles(Collections.singletonList((Role) null)).build()).build()));
    }

    @Test
    public void userWithOnlyANullRoleEntryIsStillChecked() {
        RequestInfo requestInfo = RequestInfo.builder()
                .userInfo(User.builder().uuid("u1").roles(Collections.singletonList((Role) null)).build()).build();

        assertRejected(requestInfo, "pb");
    }

    private Role roleAt(String tenantId) {
        return Role.builder().code("GRO").tenantId(tenantId).build();
    }

    @Test
    public void roleCoversTheSameTenant() {
        assertTrue(TenantAccessUtil.roleCoversTenant(roleAt("pb.amritsar"), "pb.amritsar"));
        assertTrue(TenantAccessUtil.roleCoversTenant(roleAt("pb"), "pb"));
    }

    @Test
    public void roleCoversChildTenants() {
        assertTrue(TenantAccessUtil.roleCoversTenant(roleAt("pb"), "pb.amritsar"));
        assertTrue(TenantAccessUtil.roleCoversTenant(roleAt("pb"), "pb.amritsar.ward1"));
    }

    @Test
    public void roleDoesNotCoverOtherTenants() {
        assertFalse(TenantAccessUtil.roleCoversTenant(roleAt("pb"), "mz"));
        assertFalse(TenantAccessUtil.roleCoversTenant(roleAt("pb.amritsar"), "pb.jalandhar"));
        // a city level role does not cover its state
        assertFalse(TenantAccessUtil.roleCoversTenant(roleAt("pb.amritsar"), "pb"));
    }

    @Test
    public void roleDoesNotCoverTenantWithSamePrefixWithoutDot() {
        assertFalse(TenantAccessUtil.roleCoversTenant(roleAt("pb"), "pbx"));
        assertFalse(TenantAccessUtil.roleCoversTenant(roleAt("pb"), "pbx.city"));
    }

    @Test
    public void roleCoversBlankRequestTenant() {
        assertTrue(TenantAccessUtil.roleCoversTenant(roleAt("pb"), null));
        assertTrue(TenantAccessUtil.roleCoversTenant(roleAt("pb"), ""));
        assertTrue(TenantAccessUtil.roleCoversTenant(roleAt("pb"), "   "));
    }

    @Test
    public void roleWithoutTenantCoversOnlyABlankRequestTenant() {
        assertFalse(TenantAccessUtil.roleCoversTenant(roleAt(null), "pb"));
        assertFalse(TenantAccessUtil.roleCoversTenant(roleAt(""), "pb"));
        assertTrue(TenantAccessUtil.roleCoversTenant(roleAt(null), null));
    }

    @Test
    public void nullRoleCoversNothingUnlessRequestTenantIsBlank() {
        assertFalse(TenantAccessUtil.roleCoversTenant(null, "pb"));
        assertTrue(TenantAccessUtil.roleCoversTenant(null, null));
        assertTrue(TenantAccessUtil.roleCoversTenant(null, ""));
    }

    @Test
    public void roleWithNullTenantIsIgnored() {
        assertRejected(requestInfoWithRoleAt((String) null), "pb");
        assertRejected(requestInfoWithRoleAt((String) null, "mz"), "pb");
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(requestInfoWithRoleAt(null, "pb"), "pb"));
    }

    @Test
    public void roleWithBlankTenantDoesNotMatchAnything() {
        assertRejected(requestInfoWithRoleAt(""), ".x");
        assertRejected(requestInfoWithRoleAt(" "), "pb");
    }

    @Test
    public void blankRequestTenantIsNotRejectedByThisRule() {
        RequestInfo requestInfo = requestInfoWithRoleAt("pb");
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(requestInfo, null));
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(requestInfo, ""));
        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(requestInfo, "   "));
    }

    @Test
    public void everyTenantInTheListIsChecked() {
        RequestInfo requestInfo = requestInfoWithRoleAt("pb");

        assertDoesNotThrow(() -> TenantAccessUtil.validateTenantAccess(requestInfo, null,
                new HashSet<>(Arrays.asList("pb", "pb.amritsar"))));

        CustomException e = assertThrows(CustomException.class, () -> TenantAccessUtil.validateTenantAccess(requestInfo, "pb",
                new HashSet<>(Arrays.asList("pb.amritsar", "mz"))));
        assertEquals("INVALID_TENANT_ACCESS", e.getCode());

        assertThrows(CustomException.class, () -> TenantAccessUtil.validateTenantAccess(requestInfo, "mz",
                new HashSet<>(Arrays.asList("pb.amritsar"))));
    }

    @Test
    public void errorMessageNeverContainsTheRequestedTenant() {
        CustomException e = assertThrows(CustomException.class,
                () -> TenantAccessUtil.validateTenantAccess(requestInfoWithRoleAt("pb"), "secret-tenant"));
        assertFalse(e.getMessage().contains("secret-tenant"));
    }
}
