package org.egov.product.util;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.response.ResponseInfo;

public class ResponseInfoFactory {

    private ResponseInfoFactory() {}

    public static ResponseInfo createResponseInfo(final RequestInfo requestInfo,
                                                  final boolean status) {

        final String apiId = requestInfo != null ? requestInfo.getApiId() : "";
        final String ver = requestInfo != null ? requestInfo.getVer() : "";
        final long ts = System.currentTimeMillis();
        final String resMsgId = requestInfo != null ? requestInfo.getMsgId() : "";
        final String msgId = requestInfo != null ? requestInfo.getMsgId() : "";
        final String responseStatus = status ? "successful" : "failed";

        return ResponseInfo.builder().apiId(apiId).ver(ver).ts(ts).resMsgId(resMsgId).msgId(msgId).resMsgId(resMsgId)
                .status(responseStatus).build();
    }

}