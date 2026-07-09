package org.egov.excelingestion.util;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;

// Ensures RequestInfo.userInfo is set before calls to services that reject a null userInfo (attendance, individual -> 400 USERINFO).
public final class RequestInfoUtil {

    private static final String SYSTEM_USER_UUID = "excel-ingestion-system-user";
    private static final String SYSTEM_USER_TYPE = "SYSTEM";

    private RequestInfoUtil() {
    }

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
