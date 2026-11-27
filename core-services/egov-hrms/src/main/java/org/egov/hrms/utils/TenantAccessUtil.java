package org.egov.hrms.utils;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.common.contract.request.User;
import org.egov.tracer.model.CustomException;
import org.springframework.util.CollectionUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class TenantAccessUtil {

	private TenantAccessUtil() {
	}

	/**
	 * Allows the request only if the caller has a role in the requested tenant or in its
	 * state-level parent. A role at "pb" allows "pb" and "pb.amritsar", not "mz" or "pbx".
	 * A null or blank tenantId is not rejected here; the existing validation handles it.
	 *
	 * @param requestInfo
	 * @param tenantId
	 */
	public static void validateTenantAccess(RequestInfo requestInfo, String tenantId) {
		if (StringUtils.isBlank(tenantId))
			return;

		User userInfo = requestInfo == null ? null : requestInfo.getUserInfo();
		// No roles: an internal call from another service.
		// Calls through the gateway always carry the logged in user's roles.
		if (userInfo == null || CollectionUtils.isEmpty(userInfo.getRoles())) {
			log.debug("Tenant access check skipped: request has no user roles");
			return;
		}

		boolean allowed = userInfo.getRoles().stream()
				.filter(Objects::nonNull)
				.map(Role::getTenantId)
				.filter(Objects::nonNull)
				.anyMatch(roleTenantId -> tenantId.equals(roleTenantId) || tenantId.startsWith(roleTenantId + "."));
		if (!allowed)
			throw invalidTenantAccess();
	}

	private static CustomException invalidTenantAccess() {
		return new CustomException(ErrorConstants.INVALID_TENANT_ACCESS, ErrorConstants.INVALID_TENANT_ACCESS_MESSAGE);
	}
}
