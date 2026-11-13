package org.egov.web.notification.push.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.egov.tracer.model.CustomException;
import org.egov.web.notification.push.consumer.contract.PushNotificationRequest;
import org.egov.web.notification.push.utils.ErrorConstants;
import org.egov.web.notification.push.web.contract.DeviceToken;
import org.egov.web.notification.push.web.contract.PushNotificationApiRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PushNotificationApiService {

	@Autowired
	private DeviceTokenService deviceTokenService;

	@Autowired
	private PushNotificationService pushNotificationService;

	public int sendNotification(PushNotificationApiRequest request) {
		validateRequest(request);

		Set<String> tokenSet = new LinkedHashSet<>();

		if (!CollectionUtils.isEmpty(request.getUserUuids())) {
			List<DeviceToken> resolved = deviceTokenService.getActiveTokensForUsers(request.getUserUuids(), request.getTenantId());
			List<String> resolvedTokens = resolved.stream()
					.map(DeviceToken::getDeviceToken)
					.collect(Collectors.toList());
			tokenSet.addAll(resolvedTokens);
			log.info("Resolved {} device tokens for {} user UUIDs",
					resolvedTokens.size(), request.getUserUuids().size());
		}

		if (!CollectionUtils.isEmpty(request.getDeviceTokens())) {
			tokenSet.addAll(request.getDeviceTokens());
		}

		if (tokenSet.isEmpty()) {
			log.warn("No device tokens resolved for push notification request");
			return 0;
		}

		List<String> allTokens = new ArrayList<>(tokenSet);

		PushNotificationRequest pushRequest = PushNotificationRequest.builder()
				.title(request.getTitle())
				.body(request.getBody())
				.data(request.getData())
				.deviceTokens(allTokens)
				.tenantId(request.getTenantId())
				.build();

		pushNotificationService.sendPushNotification(pushRequest);
		log.info("Push notification sent to {} devices", allTokens.size());

		return allTokens.size();
	}

	private void validateRequest(PushNotificationApiRequest request) {
		if (!StringUtils.hasText(request.getTitle())) {
			throw new CustomException(ErrorConstants.PUSH_MISSING_TITLE_CODE, ErrorConstants.PUSH_MISSING_TITLE_MSG);
		}
		if (!StringUtils.hasText(request.getBody())) {
			throw new CustomException(ErrorConstants.PUSH_MISSING_BODY_CODE, ErrorConstants.PUSH_MISSING_BODY_MSG);
		}
		if (CollectionUtils.isEmpty(request.getUserUuids()) && CollectionUtils.isEmpty(request.getDeviceTokens())) {
			throw new CustomException(ErrorConstants.PUSH_MISSING_RECIPIENTS_CODE, ErrorConstants.PUSH_MISSING_RECIPIENTS_MSG);
		}
	}

}
