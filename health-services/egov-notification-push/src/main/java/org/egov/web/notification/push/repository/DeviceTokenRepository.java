package org.egov.web.notification.push.repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.egov.tracer.model.CustomException;
import org.egov.web.notification.push.config.PushProperties;
import org.egov.web.notification.push.repository.querybuilder.DeviceTokenQueryBuilder;
import org.egov.web.notification.push.repository.rowmappers.DeviceTokenRowMapper;
import org.egov.web.notification.push.web.contract.DeviceToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.extern.slf4j.Slf4j;

@Repository
@Slf4j
public class DeviceTokenRepository {

	@Autowired
	private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

	@Autowired
	private DeviceTokenRowMapper rowMapper;

	@Autowired
	private PushProperties properties;

	public List<DeviceToken> fetchTokensByUserIds(List<String> userIds, String tenantId) {
		if (userIds == null || userIds.isEmpty()) {
			return Collections.emptyList();
		}
		String schema = getSchemaFromTenantId(tenantId);
		Map<String, Object> params = Collections.singletonMap("userIds", userIds);
		try {
			return namedParameterJdbcTemplate.query(
					DeviceTokenQueryBuilder.fetchTokensByUserIds(schema), params, rowMapper);
		} catch (Exception e) {
			log.error("Error while fetching device tokens: ", e);
			return Collections.emptyList();
		}
	}

	public List<DeviceToken> fetchLatestTokenByUserIds(List<String> userIds, String tenantId) {
		if (userIds == null || userIds.isEmpty()) {
			return Collections.emptyList();
		}
		String schema = getSchemaFromTenantId(tenantId);
		Map<String, Object> params = Collections.singletonMap("userIds", userIds);
		try {
			return namedParameterJdbcTemplate.query(
					DeviceTokenQueryBuilder.fetchLatestTokenByUserIds(schema), params, rowMapper);
		} catch (Exception e) {
			log.error("Error while fetching latest device tokens: ", e);
			return Collections.emptyList();
		}
	}

	public List<DeviceToken> fetchTokensByFacilityId(String facilityId, String tenantId) {
		if (facilityId == null || facilityId.isEmpty()) {
			return Collections.emptyList();
		}
		String schema = getSchemaFromTenantId(tenantId);
		Map<String, Object> params = Collections.singletonMap("facilityId", facilityId);
		try {
			return namedParameterJdbcTemplate.query(
					DeviceTokenQueryBuilder.fetchTokensByFacilityId(schema), params, rowMapper);
		} catch (Exception e) {
			log.error("Error while fetching device tokens by facilityId: ", e);
			return Collections.emptyList();
		}
	}

	public int deleteByDeviceTokens(List<String> deviceTokens, String tenantId) {
		if (deviceTokens == null || deviceTokens.isEmpty()) {
			return 0;
		}
		String schema = getSchemaFromTenantId(tenantId);
		Map<String, Object> params = Collections.singletonMap("deviceTokens", deviceTokens);
		try {
			return namedParameterJdbcTemplate.update(
					DeviceTokenQueryBuilder.deleteByDeviceTokens(schema), params);
		} catch (Exception e) {
			log.error("Error while deleting stale device tokens: ", e);
			return 0;
		}
	}

	/**
	 * Derives the DB schema name from the tenantId.
	 * Central instance: tenantId "in.statea.tenant" with position=1 → schema "statea"
	 * Non-central: tenantId is the schema (e.g., "ba" → schema "ba")
	 */
	private String getSchemaFromTenantId(String tenantId) {
		if (properties.getIsCentralInstance()) {
			if (tenantId == null || tenantId.isEmpty()) {
				throw new CustomException("MISSING_TENANT_ID", "TenantId is required for schema resolution");
			}
			if (tenantId.contains(".")) {
				String[] parts = tenantId.split("\\.");
				int position = properties.getSchemaIndexPosition();
				if (position < parts.length) {
					return parts[position];
				}
				throw new CustomException("INVALID_TENANT_ID", "Cannot derive schema from tenantId: " + tenantId);
			}
			return tenantId;
		}
		// Non-central: use default (public) schema
		return null;
	}

}
