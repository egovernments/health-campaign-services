package org.egov.web.notification.push.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.tracer.model.CustomException;
import org.egov.web.notification.push.config.PushProperties;
import org.egov.web.notification.push.producer.DeviceTokenProducer;
import org.egov.web.notification.push.repository.DeviceTokenRepository;
import org.egov.web.notification.push.utils.ErrorConstants;
import org.egov.web.notification.push.web.contract.AuditDetails;
import org.egov.web.notification.push.web.contract.DeviceToken;
import org.egov.web.notification.push.web.contract.DeviceTokenRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DeviceTokenService {

	@Autowired
	private DeviceTokenRepository repository;

	@Autowired
	private DeviceTokenProducer producer;

	@Autowired
	private PushProperties properties;

	public List<DeviceToken> registerDeviceTokens(RequestInfo requestInfo, List<DeviceToken> deviceTokens) {
		String userUuid = requestInfo.getUserInfo().getUuid();
		Long now = new Date().getTime();
		List<DeviceToken> expandedTokens = new ArrayList<>();

		for (DeviceToken token : deviceTokens) {
			if (StringUtils.isEmpty(token.getDeviceToken())) {
				throw new CustomException(ErrorConstants.MISSING_DEVICE_TOKEN_CODE, ErrorConstants.MISSING_DEVICE_TOKEN_MSG);
			}
			if (StringUtils.isEmpty(token.getDeviceType())
					|| !isValidDeviceType(token.getDeviceType())) {
				throw new CustomException(ErrorConstants.INVALID_DEVICE_TYPE_CODE, ErrorConstants.INVALID_DEVICE_TYPE_MSG);
			}

			if (StringUtils.isEmpty(token.getUserId())) {
				token.setUserId(userUuid);
			}
			AuditDetails audit = AuditDetails.builder()
					.createdBy(userUuid).createdTime(now)
					.lastModifiedBy(userUuid).lastModifiedTime(now).build();
			token.setAuditDetails(audit);

			// Expand facilityIds into individual rows for the persister
			List<String> facilityIds = token.getFacilityIds();
			if (facilityIds != null && !facilityIds.isEmpty()) {
				for (String facilityId : facilityIds) {
					expandedTokens.add(DeviceToken.builder()
							.id(UUID.randomUUID().toString())
							.userId(token.getUserId())
							.deviceToken(token.getDeviceToken())
							.deviceType(token.getDeviceType())
							.tenantId(token.getTenantId())
							.facilityId(facilityId)
							.auditDetails(audit)
							.build());
				}
			} else {
				token.setId(UUID.randomUUID().toString());
				expandedTokens.add(token);
			}
		}

		// Send expanded (one row per facilityId) to persister via Kafka
		DeviceTokenRequest request = DeviceTokenRequest.builder()
				.requestInfo(requestInfo)
				.deviceTokens(expandedTokens)
				.build();
		String tenantId = deviceTokens.get(0).getTenantId();
		producer.push(tenantId, properties.getSaveDeviceTokenTopic(), request);

		// Return original tokens with facilityIds intact
		return deviceTokens;
	}

	public void deleteDeviceTokens(RequestInfo requestInfo, List<DeviceToken> deviceTokens) {
		String userUuid = requestInfo.getUserInfo().getUuid();
		List<DeviceToken> expandedTokens = new ArrayList<>();

		for (DeviceToken token : deviceTokens) {
			if (StringUtils.isEmpty(token.getDeviceToken())) {
				throw new CustomException(ErrorConstants.MISSING_DEVICE_TOKEN_CODE, ErrorConstants.MISSING_DEVICE_TOKEN_MSG);
			}
			if (StringUtils.isEmpty(token.getUserId())) {
				token.setUserId(userUuid);
			}

			// If facilityIds provided, expand into individual deletes per facilityId.
			// If not provided, facilityId stays null → persister deletes all rows for the device token.
			List<String> facilityIds = token.getFacilityIds();
			if (facilityIds != null && !facilityIds.isEmpty()) {
				for (String facilityId : facilityIds) {
					expandedTokens.add(DeviceToken.builder()
							.userId(token.getUserId())
							.deviceToken(token.getDeviceToken())
							.tenantId(token.getTenantId())
							.facilityId(facilityId)
							.build());
				}
			} else {
				expandedTokens.add(token);
			}
		}

		DeviceTokenRequest request = DeviceTokenRequest.builder()
				.requestInfo(requestInfo)
				.deviceTokens(expandedTokens)
				.build();
		String tenantId = deviceTokens.get(0).getTenantId();
		producer.push(tenantId, properties.getDeleteDeviceTokenTopic(), request);
	}

	public List<DeviceToken> getActiveTokensForUsers(List<String> userIds, String tenantId) {
		return repository.fetchTokensByUserIds(userIds, tenantId);
	}

	public List<DeviceToken> getTokensByFacilityId(String facilityId, String tenantId) {
		return repository.fetchTokensByFacilityId(facilityId, tenantId);
	}

	private boolean isValidDeviceType(String deviceType) {
		return "ANDROID".equalsIgnoreCase(deviceType)
				|| "IOS".equalsIgnoreCase(deviceType)
				|| "WEB".equalsIgnoreCase(deviceType);
	}

}
