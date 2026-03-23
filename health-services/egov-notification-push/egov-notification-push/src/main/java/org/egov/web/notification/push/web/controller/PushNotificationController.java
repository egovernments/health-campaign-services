package org.egov.web.notification.push.web.controller;

import jakarta.validation.Valid;

import org.egov.web.notification.push.service.PushNotificationApiService;
import org.egov.web.notification.push.utils.ResponseInfoFactory;
import org.egov.web.notification.push.web.contract.PushNotificationApiRequest;
import org.egov.web.notification.push.web.contract.PushNotificationApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping(value = "/v1/push/")
public class PushNotificationController {

	@Autowired
	private PushNotificationApiService pushNotificationApiService;

	@Autowired
	private ResponseInfoFactory responseInfoFactory;

	@PostMapping("_send")
	@ResponseBody
	public ResponseEntity<?> sendPushNotification(@RequestBody @Valid PushNotificationApiRequest request) {
		int deviceCount = pushNotificationApiService.sendNotification(request);
		PushNotificationApiResponse response = PushNotificationApiResponse.builder()
				.responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), true))
				.message("Push notification sent to " + deviceCount + " devices")
				.build();
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

}
