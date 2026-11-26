package org.egov.web.notification.push.repository;

import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.exception.InvalidTenantIdFormatException;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.tracer.model.CustomException;
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
	private MultiStateInstanceUtil multiStateInstanceUtil;

	public List<DeviceToken> fetchTokensByUserIds(List<String> userIds, String tenantId) {
		if (userIds == null || userIds.isEmpty()) {
			return Collections.emptyList();
		}
		String query = resolveSchema(DeviceTokenQueryBuilder.fetchTokensByUserIds(), tenantId);
		Map<String, Object> params = Collections.singletonMap("userIds", userIds);
		try {
			return namedParameterJdbcTemplate.query(query, params, rowMapper);
		} catch (Exception e) {
			log.error("Error while fetching device tokens: ", e);
			return Collections.emptyList();
		}
	}

	public List<DeviceToken> fetchLatestTokenByUserIds(List<String> userIds, String tenantId) {
		if (userIds == null || userIds.isEmpty()) {
			return Collections.emptyList();
		}
		String query = resolveSchema(DeviceTokenQueryBuilder.fetchLatestTokenByUserIds(), tenantId);
		Map<String, Object> params = Collections.singletonMap("userIds", userIds);
		try {
			return namedParameterJdbcTemplate.query(query, params, rowMapper);
		} catch (Exception e) {
			log.error("Error while fetching latest device tokens: ", e);
			return Collections.emptyList();
		}
	}

	public List<DeviceToken> fetchTokensByFacilityId(String facilityId, String tenantId) {
		if (facilityId == null || facilityId.isEmpty()) {
			return Collections.emptyList();
		}
		String query = resolveSchema(DeviceTokenQueryBuilder.fetchTokensByFacilityId(), tenantId);
		log.info("fetchTokensByFacilityId: facilityId={}, tenantId={}, query={}", facilityId, tenantId, query);
		Map<String, Object> params = Collections.singletonMap("facilityId", facilityId);
		try {
			return namedParameterJdbcTemplate.query(query, params, rowMapper);
		} catch (Exception e) {
			log.error("Error while fetching device tokens by facilityId: ", e);
			return Collections.emptyList();
		}
	}

	public List<DeviceToken> fetchTokensByFacilityIdAndRole(String facilityId, String role, String tenantId) {
		if (facilityId == null || facilityId.isEmpty() || role == null || role.isEmpty()) {
			return Collections.emptyList();
		}
		String query = resolveSchema(DeviceTokenQueryBuilder.fetchTokensByFacilityIdAndRole(), tenantId);
		Map<String, Object> params = new HashMap<>();
		params.put("facilityId", facilityId);
		params.put("rolePattern", "%" + role + "%");
		try {
			return namedParameterJdbcTemplate.query(query, params, rowMapper);
		} catch (Exception e) {
			log.error("Error while fetching device tokens by facilityId and role: ", e);
			return Collections.emptyList();
		}
	}

	public List<DeviceToken> fetchTokensByFacilityIdAndRoles(String facilityId, List<String> roles, String tenantId) {
		if (facilityId == null || facilityId.isEmpty() || roles == null || roles.isEmpty()) {
			return Collections.emptyList();
		}
		String query = resolveSchema(DeviceTokenQueryBuilder.fetchTokensByFacilityIdAndRoles(roles.size()), tenantId);
		Map<String, Object> params = new HashMap<>();
		params.put("facilityId", facilityId);
		for (int i = 0; i < roles.size(); i++) {
			params.put("rolePattern" + i, "%" + roles.get(i) + "%");
		}
		try {
			return namedParameterJdbcTemplate.query(query, params, rowMapper);
		} catch (Exception e) {
			log.error("Error while fetching device tokens by facilityId and roles: ", e);
			return Collections.emptyList();
		}
	}

	public int deleteByDeviceTokens(List<String> deviceTokens, String tenantId) {
		if (deviceTokens == null || deviceTokens.isEmpty()) {
			return 0;
		}
		String query = resolveSchema(DeviceTokenQueryBuilder.deleteByDeviceTokens(), tenantId);
		Map<String, Object> params = Collections.singletonMap("deviceTokens", deviceTokens);
		try {
			return namedParameterJdbcTemplate.update(query, params);
		} catch (Exception e) {
			log.error("Error while deleting stale device tokens: ", e);
			return 0;
		}
	}

	/**
	 * Non-central: the tenantId is not used in the query, so the default schema is used for any tenantId.
	 * Central: fills the {schema} placeholder through DIGIT's MultiStateInstanceUtil, which validates the
	 * tenantId before it is used as a schema name; the service's existing error codes are kept.
	 */
	private String resolveSchema(String query, String tenantId) {
		if (!Boolean.TRUE.equals(multiStateInstanceUtil.getIsEnvironmentCentralInstance())) {
			return query.replace(SCHEMA_REPLACE_STRING + ".", "");
		}
		if (tenantId == null || tenantId.isEmpty()) {
			throw new CustomException("MISSING_TENANT_ID", "TenantId is required for schema resolution");
		}
		try {
			return multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
		} catch (InvalidTenantIdException | InvalidTenantIdFormatException e) {
			throw new CustomException("INVALID_TENANT_ID", "Invalid tenantId");
		}
	}

}
