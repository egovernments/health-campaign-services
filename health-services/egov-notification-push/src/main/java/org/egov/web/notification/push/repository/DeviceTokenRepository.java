package org.egov.web.notification.push.repository;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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

	private static final Pattern SAFE_SCHEMA_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

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
			log.error("Error while fetching device tokens", e);
			throw repositoryFailure("Error while fetching device tokens", e);
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
			log.error("Error while fetching latest device tokens", e);
			throw repositoryFailure("Error while fetching latest device tokens", e);
		}
	}

	public List<DeviceToken> fetchTokensByFacilityId(String facilityId, String tenantId) {
		if (facilityId == null || facilityId.isEmpty()) {
			return Collections.emptyList();
		}
		String schema = getSchemaFromTenantId(tenantId);
		String query = DeviceTokenQueryBuilder.fetchTokensByFacilityId(schema);
		log.info("fetchTokensByFacilityId: facilityId={}, tenantId={}, schema={}, query={}",
				facilityId, tenantId, schema, query);
		Map<String, Object> params = Collections.singletonMap("facilityId", facilityId);
		try {
			return namedParameterJdbcTemplate.query(query, params, rowMapper);
		} catch (Exception e) {
			log.error("Error while fetching device tokens by facilityId", e);
			throw repositoryFailure("Error while fetching device tokens by facilityId", e);
		}
	}

	public List<DeviceToken> fetchTokensByFacilityIdAndRole(String facilityId, String role, String tenantId) {
		if (facilityId == null || facilityId.isEmpty() || role == null || role.isEmpty()) {
			return Collections.emptyList();
		}
		String schema = getSchemaFromTenantId(tenantId);
		Map<String, Object> params = new HashMap<>();
		params.put("facilityId", facilityId);
		params.put("rolePattern", buildRolePattern(role));
		try {
			return namedParameterJdbcTemplate.query(
					DeviceTokenQueryBuilder.fetchTokensByFacilityIdAndRole(schema), params, rowMapper);
		} catch (Exception e) {
			log.error("Error while fetching device tokens by facilityId and role", e);
			throw repositoryFailure("Error while fetching device tokens by facilityId and role", e);
		}
	}

	public List<DeviceToken> fetchTokensByFacilityIdAndRoles(String facilityId, List<String> roles, String tenantId) {
		if (facilityId == null || facilityId.isEmpty() || roles == null || roles.isEmpty()) {
			return Collections.emptyList();
		}
		String schema = getSchemaFromTenantId(tenantId);
		Map<String, Object> params = new HashMap<>();
		params.put("facilityId", facilityId);
		for (int i = 0; i < roles.size(); i++) {
			params.put("rolePattern" + i, buildRolePattern(roles.get(i)));
		}
		try {
			return namedParameterJdbcTemplate.query(
					DeviceTokenQueryBuilder.fetchTokensByFacilityIdAndRoles(schema, roles.size()), params, rowMapper);
		} catch (Exception e) {
			log.error("Error while fetching device tokens by facilityId and roles", e);
			throw repositoryFailure("Error while fetching device tokens by facilityId and roles", e);
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
			log.error("Error while deleting stale device tokens", e);
			throw repositoryFailure("Error while deleting stale device tokens", e);
		}
	}

	/**
	 * Derives the DB schema name from the tenantId.
	 * Central instance: tenantId "in.statea.tenant" with position=1 → schema "statea"
	 * Non-central: tenantId is the schema (e.g., "ba" → schema "ba")
	 */
	private String getSchemaFromTenantId(String tenantId) {
		log.info("getSchemaFromTenantId: tenantId={}, isCentralInstance={}",
				tenantId, properties.getIsCentralInstance());
		if (Boolean.TRUE.equals(properties.getIsCentralInstance())) {
			if (tenantId == null || tenantId.isEmpty()) {
				throw new CustomException("MISSING_TENANT_ID", "TenantId is required for schema resolution");
			}
			if (tenantId.contains(".")) {
				String[] parts = tenantId.split("\\.");
				int position = properties.getSchemaIndexPosition();
				if (position < parts.length) {
					String schema = validateSchemaName(parts[position], tenantId);
					log.info("Central instance: derived schema='{}' from tenantId='{}'", schema, tenantId);
					return schema;
				}
				throw new CustomException("INVALID_TENANT_ID", "Cannot derive schema from tenantId: " + tenantId);
			}
			String schema = validateSchemaName(tenantId, tenantId);
			log.info("Central instance: tenantId='{}' has no dot, using as schema directly", schema);
			return schema;
		}
		// Non-central: use default (public) schema
		log.info("Non-central instance: using default (public) schema");
		return null;
	}

	private String buildRolePattern(String role) {
		return "%," + role + ",%";
	}

	private RuntimeException repositoryFailure(String message, Exception e) {
		return new RuntimeException(message, e);
	}

	private String validateSchemaName(String schema, String tenantId) {
		if (!SAFE_SCHEMA_NAME.matcher(schema).matches()) {
			throw new CustomException("INVALID_TENANT_ID", "Unsafe schema derived from tenantId: " + tenantId);
		}
		return schema;
	}

}
