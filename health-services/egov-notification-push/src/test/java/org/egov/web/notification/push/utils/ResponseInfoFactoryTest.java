package org.egov.web.notification.push.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.response.ResponseInfo;
import org.junit.jupiter.api.Test;

class ResponseInfoFactoryTest {

    private final ResponseInfoFactory factory = new ResponseInfoFactory();

    @Test
    void createResponseInfo_success_returnsSuccessfulStatus() {
        RequestInfo requestInfo = RequestInfo.builder()
                .apiId("api-1")
                .ver("1.0")
                .msgId("msg-123")
                .build();

        ResponseInfo result = factory.createResponseInfoFromRequestInfo(requestInfo, true);

        assertEquals("api-1", result.getApiId());
        assertEquals("1.0", result.getVer());
        assertEquals("msg-123", result.getMsgId());
        assertEquals("successful", result.getStatus());
    }

    @Test
    void createResponseInfo_failure_returnsFailedStatus() {
        RequestInfo requestInfo = RequestInfo.builder().build();

        ResponseInfo result = factory.createResponseInfoFromRequestInfo(requestInfo, false);

        assertEquals("failed", result.getStatus());
    }

    @Test
    void createResponseInfo_nullRequestInfo_handlesGracefully() {
        ResponseInfo result = factory.createResponseInfoFromRequestInfo(null, true);

        assertEquals("", result.getApiId());
        assertEquals("", result.getVer());
        assertEquals("successful", result.getStatus());
    }
}