package org.egov.web.notification.push.repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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

	public List<DeviceToken> fetchTokensByUserIds(List<String> userIds) {
		if (userIds == null || userIds.isEmpty()) {
			return Collections.emptyList();
		}
		Map<String, Object> params = Collections.singletonMap("userIds", userIds);
		try {
			return namedParameterJdbcTemplate.query(
					DeviceTokenQueryBuilder.FETCH_TOKENS_BY_USERIDS, params, rowMapper);
		} catch (Exception e) {
			log.error("Error while fetching device tokens: ", e);
			return Collections.emptyList();
		}
	}

	public List<DeviceToken> fetchTokensByFacilityId(String facilityId) {
		if (facilityId == null || facilityId.isEmpty()) {
			return Collections.emptyList();
		}
		Map<String, Object> params = Collections.singletonMap("facilityId", facilityId);
		try {
			return namedParameterJdbcTemplate.query(
					DeviceTokenQueryBuilder.FETCH_TOKENS_BY_FACILITY_ID, params, rowMapper);
		} catch (Exception e) {
			log.error("Error while fetching device tokens by facilityId: ", e);
			return Collections.emptyList();
		}
	}

}
