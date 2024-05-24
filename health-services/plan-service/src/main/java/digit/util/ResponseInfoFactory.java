package digit.util;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.stereotype.Component;

import static digit.config.ServiceConstants.*;

@Component
public class ResponseInfoFactory {

    public ResponseInfo createResponseInfoFromRequestInfo(final RequestInfo requestInfo, final Boolean success) {

        final String apiId = requestInfo != null ? requestInfo.getApiId() : "";
        final String ver = requestInfo != null ? requestInfo.getVer() : "";
        Long ts = null;
        if (requestInfo != null)
            ts = requestInfo.getTs();

        final String msgId = requestInfo != null ? requestInfo.getMsgId() : "";
        final String responseStatus = success ? SUCCESSFUL : FAILED;

        return ResponseInfo.builder().apiId(apiId).ver(ver).ts(ts).resMsgId(RES_MSG_ID).msgId(msgId).resMsgId(RES_MSG_ID)
                .status(responseStatus).build();
    }

}