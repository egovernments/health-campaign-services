package org.egov.web.notification.push.web.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.egov.web.notification.push.service.DeviceTokenService;
import org.egov.web.notification.push.utils.ResponseInfoFactory;
import org.egov.web.notification.push.web.contract.DeviceToken;
import org.egov.web.notification.push.web.contract.DeviceTokenRequest;
import org.egov.web.notification.push.web.contract.DeviceTokenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping(value = "/device-token/v1/")
public class DeviceTokenController {

	@Autowired
	private DeviceTokenService deviceTokenService;

	@Autowired
	private ResponseInfoFactory responseInfoFactory;

	@PostMapping("_register")
	@ResponseBody
	public ResponseEntity<?> registerDeviceToken(@RequestBody @Valid DeviceTokenRequest request) {
		List<DeviceToken> tokens = deviceTokenService.registerDeviceTokens(
				request.getRequestInfo(), request.getDeviceTokens());
		DeviceTokenResponse response = DeviceTokenResponse.builder()
				.responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), true))
				.deviceTokens(tokens).build();
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("_delete")
	@ResponseBody
	public ResponseEntity<?> deleteDeviceToken(@RequestBody @Valid DeviceTokenRequest request) {
		deviceTokenService.deleteDeviceTokens(request.getRequestInfo(), request.getDeviceTokens());
		DeviceTokenResponse response = DeviceTokenResponse.builder()
				.responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), true))
				.build();
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

}
