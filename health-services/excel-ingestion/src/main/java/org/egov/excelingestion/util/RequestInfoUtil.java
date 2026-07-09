package org.egov.excelingestion.util;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;

/**
 * Shared guard for outbound calls to DIGIT services that reject a request whose
 * {@code RequestInfo.userInfo} (uuid) is absent - notably the attendance and individual
 * services, which respond 400 {@code USERINFO "UserInfo is mandatory"}. Async
 * generation/processing events can arrive without a userInfo (system-triggered or
 * central-instance flows), so callers must ensure one before invoking those services.
 */
public final class RequestInfoUtil {

    private static final String SYSTEM_USER_UUID = "excel-ingestion-system-user";
    private static final String SYSTEM_USER_TYPE = "SYSTEM";

    private RequestInfoUtil() {
    }

    /**
     * Guarantee a non-null userInfo carrying a non-blank uuid and tenantId on the RequestInfo,
     * mutating it in place. Preserves an existing user's uuid when present.
     */
    public static void ensureUserInfo(RequestInfo requestInfo, String tenantId) {
        if (requestInfo == null) {
            return;
        }
        User user = requestInfo.getUserInfo();
        if (user == null) {
            requestInfo.setUserInfo(User.builder()
                    .uuid(SYSTEM_USER_UUID)
                    .type(SYSTEM_USER_TYPE)
                    .tenantId(tenantId)
                    .build());
            return;
        }
        if (user.getUuid() == null || user.getUuid().isBlank()) {
            user.setUuid(SYSTEM_USER_UUID);
        }
        if (user.getTenantId() == null || user.getTenantId().isBlank()) {
            user.setTenantId(tenantId);
        }
    }
}
