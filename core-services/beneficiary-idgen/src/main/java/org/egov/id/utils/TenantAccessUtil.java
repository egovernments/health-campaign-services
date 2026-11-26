package org.egov.id.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.common.contract.request.User;
import org.egov.tracer.model.CustomException;

import java.util.List;

/**
 * Tenant access rule for the HTTP endpoints. A request is allowed only if the user in RequestInfo
 * has a role at the request tenant, or at its state-level parent (the request tenant starts with
 * roleTenantId + "."). Example: a role at "pb" allows "pb" and "pb.amritsar", but not "mz" or "pbx".
 * Must be called from the controller only, never from the Kafka consumer paths.
 */
@Slf4j
public final class TenantAccessUtil {

    public static final String INVALID_TENANT_ACCESS = "INVALID_TENANT_ACCESS";
    public static final String INVALID_TENANT_ACCESS_MESSAGE = "User does not have access to the requested tenant";

    private TenantAccessUtil() {
    }

    /**
     * Throws CustomException(INVALID_TENANT_ACCESS) if the user does not have access to the tenant.
     * A null or blank tenantId is not rejected here, the existing request validation handles it.
     *
     * @param requestInfo request metadata carrying the user and its roles
     * @param tenantId    tenant the request is for
     */
    public static void validateTenantAccess(RequestInfo requestInfo, String tenantId) {
        if (StringUtils.isBlank(tenantId)) {
            return;
        }

        User userInfo = requestInfo == null ? null : requestInfo.getUserInfo();
        List<Role> roles = userInfo == null ? null : userInfo.getRoles();
        // No roles: an internal call from another service.
        // Calls through the gateway always carry the logged in user's roles.
        if (roles == null || roles.isEmpty()) {
            log.debug("Tenant access check skipped: request has no user roles");
            return;
        }

        for (Role role : roles) {
            String roleTenantId = role == null ? null : role.getTenantId();
            if (roleTenantId != null
                    && (tenantId.equals(roleTenantId) || tenantId.startsWith(roleTenantId + "."))) {
                return;
            }
        }

        throw new CustomException(INVALID_TENANT_ACCESS, INVALID_TENANT_ACCESS_MESSAGE);
    }
}
