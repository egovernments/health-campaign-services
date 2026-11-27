package org.egov.pgr.util;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.tracer.model.CustomException;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Checks that the logged in user has access to the tenant sent in the request.
 * A request is allowed only if the user has a role at the same tenant, or at its state level parent
 * (role tenant "pb" allows "pb" and "pb.amritsar", but not "mz" or "pbx").
 */
@Slf4j
public final class TenantAccessUtil {

    public static final String INVALID_TENANT_ACCESS_CODE = "INVALID_TENANT_ACCESS";

    public static final String INVALID_TENANT_ACCESS_MESSAGE = "User does not have access to the requested tenant";

    private TenantAccessUtil() {
    }

    /**
     * Validates the access of the user in requestInfo to the given tenant.
     * A null or blank tenantId is not rejected here, the existing validation handles a missing tenantId.
     * @param requestInfo The requestInfo of the call
     * @param tenantId The tenantId sent in the request
     */
    public static void validateTenantAccess(RequestInfo requestInfo, String tenantId) {
        validateTenantAccess(requestInfo, tenantId, null);
    }

    /**
     * Validates the access of the user in requestInfo to the given tenantId and to every tenant in tenantIds.
     * @param requestInfo The requestInfo of the call
     * @param tenantId The tenantId sent in the request, can be null
     * @param tenantIds The tenantIds sent in the request, can be null
     */
    public static void validateTenantAccess(RequestInfo requestInfo, String tenantId, Collection<String> tenantIds) {

        // No roles: an internal call from another service.
        // Calls through the gateway always carry the logged in user's roles.
        if (isInternalCall(requestInfo)) {
            log.debug("Tenant access check skipped: request has no user roles");
            return;
        }

        List<Role> roles = requestInfo.getUserInfo().getRoles();

        if (!hasAccess(roles, tenantId))
            throw new CustomException(INVALID_TENANT_ACCESS_CODE, INVALID_TENANT_ACCESS_MESSAGE);

        if (tenantIds != null) {
            for (String requestTenantId : tenantIds) {
                if (!hasAccess(roles, requestTenantId))
                    throw new CustomException(INVALID_TENANT_ACCESS_CODE, INVALID_TENANT_ACCESS_MESSAGE);
            }
        }
    }

    /**
     * Returns true if the request has no user roles, which means it is an internal call from another service.
     * @param requestInfo The requestInfo of the call
     */
    public static boolean isInternalCall(RequestInfo requestInfo) {
        return requestInfo == null || requestInfo.getUserInfo() == null
                || CollectionUtils.isEmpty(requestInfo.getUserInfo().getRoles());
    }

    /**
     * Returns true if the role is at the given tenant or at its state level parent. A blank tenantId returns true.
     * @param role The role of the user
     * @param tenantId The tenantId sent in the request
     */
    public static boolean roleCoversTenant(Role role, String tenantId) {
        return hasAccess(Collections.singletonList(role), tenantId);
    }

    private static boolean hasAccess(List<Role> roles, String requestTenantId) {

        if (!StringUtils.hasText(requestTenantId))
            return true;

        for (Role role : roles) {
            if (role == null)
                continue;
            String roleTenantId = role.getTenantId();
            if (!StringUtils.hasText(roleTenantId))
                continue;
            if (requestTenantId.equals(roleTenantId) || requestTenantId.startsWith(roleTenantId + "."))
                return true;
        }
        return false;
    }

}
