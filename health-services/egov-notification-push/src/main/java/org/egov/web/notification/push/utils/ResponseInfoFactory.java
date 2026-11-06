package org.egov.web.notification.push.utils;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.stereotype.Component;

@Component
public class ResponseInfoFactory {

	public ResponseInfo createResponseInfoFromRequestInfo(RequestInfo requestInfo, Boolean success) {
		String apiId = requestInfo != null ? requestInfo.getApiId() : "";
		String ver = requestInfo != null ? requestInfo.getVer() : "";
		Long ts = null;
		String resMsgId = "";
		String msgId = requestInfo != null ? requestInfo.getMsgId() : "";
		String status = success ? "successful" : "failed";
		return ResponseInfo.builder().apiId(apiId).ver(ver).ts(ts).resMsgId(resMsgId)
				.msgId(msgId).status(status).build();
	}

}
