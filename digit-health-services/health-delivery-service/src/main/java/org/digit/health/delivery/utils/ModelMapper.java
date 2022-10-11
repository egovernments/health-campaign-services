package org.digit.health.delivery.utils;

import org.digit.health.delivery.web.models.ResponseStatusEnum;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.response.ResponseInfo;

public class ModelMapper {
    private ModelMapper() {
    }

    public static ResponseInfo createResponseInfoFromRequestInfo(RequestInfo requestInfo, Boolean success) {
        String apiId = requestInfo.getApiId();
        String ver = requestInfo.getVer();
        Long ts = requestInfo.getTs();
        String resMsgId = requestInfo.getCorrelationId();
        String msgId = requestInfo.getMsgId();
        ResponseStatusEnum responseStatus = Boolean.TRUE.equals(success) ?
                ResponseStatusEnum.SUCCESSFUL : ResponseStatusEnum.FAILED;
        return new ResponseInfo(apiId, ver, ts, resMsgId, msgId, responseStatus.toString());
    }
}
