package org.egov.excelingestion.util;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;

// Guarantees a userInfo.uuid so attendance/individual calls don't fail with 400 USERINFO.
public final class RequestInfoUtil {

    private static final String SYSTEM_USER_UUID = "excel-ingestion-system-user";
    private static final String SYSTEM_USER_TYPE = "SYSTEM";

    private RequestInfoUtil() {
    }

    public static RequestInfo ensureUserInfo(RequestInfo requestInfo, String tenantId) {
        RequestInfo resolved = requestInfo != null ? requestInfo : RequestInfo.builder().build();
        User user = resolved.getUserInfo();
        if (user == null) {
            resolved.setUserInfo(User.builder()
                    .uuid(SYSTEM_USER_UUID)
                    .type(SYSTEM_USER_TYPE)
                    .tenantId(tenantId)
                    .build());
            return resolved;
        }
        if (isBlank(user.getUuid())) {
            user.setUuid(SYSTEM_USER_UUID);
        }
        if (isBlank(user.getTenantId())) {
            user.setTenantId(tenantId);
        }
        return resolved;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
