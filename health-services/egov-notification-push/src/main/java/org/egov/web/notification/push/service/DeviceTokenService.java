package org.egov.web.notification.push.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
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

		// Extract role codes from RequestInfo and store as comma-separated string
		String userRoles = extractRoleCodes(requestInfo);

		for (DeviceToken token : deviceTokens) {
			if (StringUtils.isEmpty(token.getDeviceToken())) {
				throw new CustomException(ErrorConstants.MISSING_DEVICE_TOKEN_CODE, ErrorConstants.MISSING_DEVICE_TOKEN_MSG);
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
							.userRoles(userRoles)
							.auditDetails(audit)
							.build());
				}
			} else {
				token.setId(UUID.randomUUID().toString());
				token.setUserRoles(userRoles);
				expandedTokens.add(token);
			}
		}

		// Send expanded (one row per facilityId) to persister via Kafka
		DeviceTokenRequest request = DeviceTokenRequest.builder()
				.requestInfo(requestInfo)
				.deviceTokens(expandedTokens)
				.build();
		String tenantId = deviceTokens.get(0).getTenantId();
		for (DeviceToken t : expandedTokens) {
			log.info("PUSH_REGISTER user={} tenantId={} facilityId={} roles={} tokenSuffix={}",
					t.getUserId(), t.getTenantId(), t.getFacilityId(), t.getUserRoles(),
					tokenSuffix(t.getDeviceToken()));
		}
		if (userRoles == null || userRoles.isBlank()) {
			log.warn("PUSH_REGISTER_NO_ROLES user={} tenantId={} rows={} — userroles will be stored NULL and role-filtered notifications will never match these rows",
					userUuid, tenantId, expandedTokens.size());
		}
		producer.push(tenantId, properties.getSaveDeviceTokenTopic(), deviceTokens.get(0).getDeviceToken(), request);

		// Return original tokens with facilityIds intact
		return deviceTokens;
	}

	public void deleteDeviceTokens(RequestInfo requestInfo, List<DeviceToken> deviceTokens) {
		String userUuid = requestInfo.getUserInfo().getUuid();
		AuditDetails audit = changeAudit(userUuid);
		List<DeviceToken> perFacility = new ArrayList<>();
		List<DeviceToken> allFacilities = new ArrayList<>();

		for (DeviceToken token : deviceTokens) {
			if (StringUtils.isEmpty(token.getDeviceToken())) {
				throw new CustomException(ErrorConstants.MISSING_DEVICE_TOKEN_CODE, ErrorConstants.MISSING_DEVICE_TOKEN_MSG);
			}
			if (StringUtils.isEmpty(token.getUserId())) {
				token.setUserId(userUuid);
			}
			token.setAuditDetails(audit);

			List<String> facilityIds = token.getFacilityIds();
			if (facilityIds != null && !facilityIds.isEmpty()) {
				for (String facilityId : facilityIds) {
					perFacility.add(DeviceToken.builder()
							.userId(token.getUserId())
							.deviceToken(token.getDeviceToken())
							.tenantId(token.getTenantId())
							.facilityId(facilityId)
							.auditDetails(audit)
							.build());
				}
			} else {
				allFacilities.add(token);
			}
		}

		String tenantId = deviceTokens.get(0).getTenantId();
		String key = deviceTokens.get(0).getDeviceToken();
		for (DeviceToken t : perFacility) {
			log.info("PUSH_DELETE user={} tenantId={} facilityId={} tokenSuffix={}",
					t.getUserId(), t.getTenantId(), t.getFacilityId(), tokenSuffix(t.getDeviceToken()));
		}
		if (!perFacility.isEmpty()) {
			producer.push(tenantId, properties.getDeleteDeviceTokenTopic(), key,
					DeviceTokenRequest.builder().requestInfo(requestInfo).deviceTokens(perFacility).build());
		}
		if (!allFacilities.isEmpty()) {
			for (DeviceToken t : allFacilities) {
				log.info("PUSH_DELETE user={} tenantId={} facilityId=ALL tokenSuffix={}",
						t.getUserId(), t.getTenantId(), tokenSuffix(t.getDeviceToken()));
			}
			producer.push(tenantId, properties.getUnregisterDeviceTokenTopic(), key,
					DeviceTokenRequest.builder().requestInfo(requestInfo).deviceTokens(allFacilities).build());
		}
	}

	public void unregisterDeviceTokens(RequestInfo requestInfo, List<DeviceToken> deviceTokens) {
		String userUuid = requestInfo.getUserInfo().getUuid();
		AuditDetails audit = changeAudit(userUuid);

		for (DeviceToken token : deviceTokens) {
			if (StringUtils.isEmpty(token.getDeviceToken())) {
				throw new CustomException(ErrorConstants.MISSING_DEVICE_TOKEN_CODE, ErrorConstants.MISSING_DEVICE_TOKEN_MSG);
			}
			if (StringUtils.isEmpty(token.getUserId())) {
				token.setUserId(userUuid);
			}
			token.setAuditDetails(audit);
		}

		DeviceTokenRequest request = DeviceTokenRequest.builder()
				.requestInfo(requestInfo)
				.deviceTokens(deviceTokens)
				.build();
		String tenantId = deviceTokens.get(0).getTenantId();
		producer.push(tenantId, properties.getUnregisterDeviceTokenTopic(), deviceTokens.get(0).getDeviceToken(), request);
		for (DeviceToken t : deviceTokens) {
			log.info("PUSH_UNREGISTER user={} tenantId={} tokenSuffix={} — soft-deletes ALL facility rows for this user+token",
					t.getUserId(), t.getTenantId(), tokenSuffix(t.getDeviceToken()));
		}
	}

	private static AuditDetails changeAudit(String userUuid) {
		return AuditDetails.builder().lastModifiedBy(userUuid).lastModifiedTime(new Date().getTime()).build();
	}

	static String tokenSuffix(String deviceToken) {
		if (deviceToken == null) {
			return "null";
		}
		return deviceToken.length() <= 12 ? deviceToken : "..." + deviceToken.substring(deviceToken.length() - 12);
	}

	public List<DeviceToken> getActiveTokensForUsers(List<String> userIds, String tenantId) {
		return repository.fetchTokensByUserIds(userIds, tenantId);
	}

	public List<DeviceToken> getLatestTokenForUsers(List<String> userIds, String tenantId) {
		return repository.fetchLatestTokenByUserIds(userIds, tenantId);
	}

	public List<DeviceToken> getTokensByFacilityId(String facilityId, String tenantId) {
		return repository.fetchTokensByFacilityId(facilityId, tenantId);
	}

	public List<DeviceToken> getTokensByFacilityIdAndRole(String facilityId, String role, String tenantId) {
		return repository.fetchTokensByFacilityIdAndRole(facilityId, role, tenantId);
	}

	public List<DeviceToken> getTokensByFacilityIdAndRoles(String facilityId, List<String> roles, String tenantId) {
		return repository.fetchTokensByFacilityIdAndRoles(facilityId, roles, tenantId);
	}

	public void deleteStaleTokens(List<String> deviceTokens, String tenantId) {
		int deleted = repository.deleteByDeviceTokens(deviceTokens, tenantId);
		log.info("Deleted {} stale device token row(s) for tokenSuffixes: {}", deleted,
				deviceTokens.stream().map(DeviceTokenService::tokenSuffix).toList());
	}

	/**
	 * Extracts role codes from RequestInfo.userInfo.roles and returns as comma-separated string.
	 * e.g. "WAREHOUSE_MANAGER,DISTRIBUTOR,SYSTEM_ADMINISTRATOR"
	 */
	private String extractRoleCodes(RequestInfo requestInfo) {
		if (requestInfo.getUserInfo() == null || requestInfo.getUserInfo().getRoles() == null
				|| requestInfo.getUserInfo().getRoles().isEmpty()) {
			return null;
		}
		return requestInfo.getUserInfo().getRoles().stream()
				.map(Role::getCode)
				.filter(code -> code != null && !code.isBlank())
				.collect(Collectors.joining(","));
	}

	private boolean isValidDeviceType(String deviceType) {
		return "ANDROID".equalsIgnoreCase(deviceType)
				|| "IOS".equalsIgnoreCase(deviceType)
				|| "WEB".equalsIgnoreCase(deviceType);
	}

}
