package org.egov.excelingestion.util;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RequestInfoUtilTest {

    private static final String TENANT = "demo";
    private static final String SYSTEM_UUID = "excel-ingestion-system-user";

    @Test
    void nullRequestInfo_returnsSystemRequestInfo() {
        RequestInfo ri = RequestInfoUtil.ensureUserInfo(null, TENANT);

        assertNotNull(ri, "null input must be replaced with a usable RequestInfo, not left null");
        assertNotNull(ri.getUserInfo());
        assertEquals(SYSTEM_UUID, ri.getUserInfo().getUuid());
        assertEquals("SYSTEM", ri.getUserInfo().getType());
        assertEquals(TENANT, ri.getUserInfo().getTenantId());
    }

    @Test
    void nullUserInfo_getsSystemUserWithUuidTypeAndTenant() {
        RequestInfo ri = RequestInfoUtil.ensureUserInfo(RequestInfo.builder().apiId("pf").build(), TENANT);

        assertNotNull(ri.getUserInfo());
        assertEquals(SYSTEM_UUID, ri.getUserInfo().getUuid());
        assertEquals("SYSTEM", ri.getUserInfo().getType());
        assertEquals(TENANT, ri.getUserInfo().getTenantId());
        assertEquals("pf", ri.getApiId(), "other RequestInfo fields are preserved");
    }

    @Test
    void blankUuid_isBackfilledWithSystemUser() {
        RequestInfo ri = RequestInfoUtil.ensureUserInfo(
                RequestInfo.builder().userInfo(User.builder().uuid("   ").tenantId(TENANT).build()).build(), TENANT);

        assertEquals(SYSTEM_UUID, ri.getUserInfo().getUuid());
        assertEquals(TENANT, ri.getUserInfo().getTenantId());
    }

    @Test
    void existingRealUuid_isPreserved() {
        RequestInfo ri = RequestInfoUtil.ensureUserInfo(
                RequestInfo.builder().userInfo(User.builder().uuid("real-user-uuid").tenantId(TENANT).build()).build(), TENANT);

        assertEquals("real-user-uuid", ri.getUserInfo().getUuid(), "a real user must never be overwritten");
    }

    @Test
    void blankTenant_isBackfilledWhileKeepingRealUuid() {
        RequestInfo ri = RequestInfoUtil.ensureUserInfo(
                RequestInfo.builder().userInfo(User.builder().uuid("real-user-uuid").build()).build(), TENANT);

        assertEquals("real-user-uuid", ri.getUserInfo().getUuid());
        assertEquals(TENANT, ri.getUserInfo().getTenantId());
    }
}
