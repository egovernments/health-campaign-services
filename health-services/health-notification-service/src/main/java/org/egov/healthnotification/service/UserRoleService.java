package org.egov.healthnotification.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.Role;
import org.egov.common.contract.request.User;
import org.egov.common.contract.user.UserSearchRequest;
import org.egov.common.service.UserService;
import org.egov.healthnotification.util.RequestInfoUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fetches role codes for a user UUID by calling the egov user service.
 *
 * <p>Used by {@link org.egov.healthnotification.service.hfreferral.HFReferralNotificationAdapter}
 * to determine whether the creator of an HFReferral has a role that is allowed
 * to trigger push notifications (as configured in the MDMS {@code senderRoles} list).
 */
@Service
@Slf4j
public class UserRoleService {

    private final UserService userService;

    @Autowired
    public UserRoleService(UserService userService) {
        this.userService = userService;
    }

    /**
     * Returns the set of role codes held by the user identified by {@code userUuid}.
     *
     * @param userUuid the UUID from {@code auditDetails.createdBy} of the HFReferral record
     * @param tenantId tenant ID (state-level)
     * @return set of role codes, e.g. {@code {"DISTRIBUTOR", "REGISTRAR"}}; empty set if not found
     */
    public Set<String> getRoleCodesForUser(String userUuid, String tenantId) {
        if (userUuid == null || userUuid.isBlank()) {
            log.warn("userUuid is null/blank. Cannot fetch roles.");
            return Collections.emptySet();
        }

        try {
            UserSearchRequest searchRequest = new UserSearchRequest();
            searchRequest.setRequestInfo(RequestInfoUtil.buildSystemRequestInfo());
            searchRequest.setUuid(List.of(userUuid));
            searchRequest.setTenantId(tenantId.split("\\.")[0]);

            List<User> users = userService.search(searchRequest);

            if (users == null || users.isEmpty()) {
                log.warn("No user found for uuid={}, tenantId={}", userUuid, tenantId);
                return Collections.emptySet();
            }

            User user = users.get(0);
            List<Role> roles = user.getRoles();

            if (roles == null || roles.isEmpty()) {
                log.info("User uuid={} has no roles assigned.", userUuid);
                return Collections.emptySet();
            }

            Set<String> roleCodes = roles.stream()
                    .filter(role -> role.getCode() != null && !role.getCode().isBlank())
                    .map(Role::getCode)
                    .collect(Collectors.toSet());

            log.debug("User uuid={} has roles: {}", userUuid, roleCodes);
            return roleCodes;
        } catch (Exception e) {
            log.error("Failed to fetch roles for user uuid={}: {}", userUuid, e.getMessage());
            return Collections.emptySet();
        }
    }
}
