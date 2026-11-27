package org.egov.servicerequest.util;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.tracer.model.CustomException;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

import static org.egov.servicerequest.error.ErrorCode.INVALID_TENANT_ACCESS_ERR_CODE;
import static org.egov.servicerequest.error.ErrorCode.INVALID_TENANT_ACCESS_ERR_MSG;

@Slf4j
public class TenantAccessUtil {

    private TenantAccessUtil() {
    }

    /**
     * Allows the request only if the user has a role at the request tenant or at its state-level parent
     * (role at "pb" allows "pb" and "pb.amritsar"). A blank request tenant is left to the existing validation.
     */
    public static void validateTenantAccess(RequestInfo requestInfo, String tenantId) {
        if (!StringUtils.hasText(tenantId))
            return;

        List<Role> roles = (requestInfo == null || requestInfo.getUserInfo() == null) ? null : requestInfo.getUserInfo().getRoles();
        // No roles: an internal call from another service (for example the transformer).
        // Calls through the gateway always carry the logged in user's roles.
        if (CollectionUtils.isEmpty(roles)) {
            log.debug("Tenant access check skipped: request has no user roles");
            return;
        }

        boolean hasAccess = roles.stream()
                .filter(role -> role != null && role.getTenantId() != null)
                .anyMatch(role -> tenantId.equals(role.getTenantId()) || tenantId.startsWith(role.getTenantId() + "."));
        if (!hasAccess)
            throw new CustomException(INVALID_TENANT_ACCESS_ERR_CODE, INVALID_TENANT_ACCESS_ERR_MSG);
    }
}
