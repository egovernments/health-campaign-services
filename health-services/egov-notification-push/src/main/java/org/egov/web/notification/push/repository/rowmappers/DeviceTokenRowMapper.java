package org.egov.web.notification.push.repository.rowmappers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.egov.web.notification.push.web.contract.AuditDetails;
import org.egov.web.notification.push.web.contract.DeviceToken;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DeviceTokenRowMapper implements ResultSetExtractor<List<DeviceToken>> {

	@Override
	public List<DeviceToken> extractData(ResultSet resultSet) throws SQLException, DataAccessException {
		Map<String, DeviceToken> tokenMap = new LinkedHashMap<>();
		while (resultSet.next()) {
			String deviceTokenStr = resultSet.getString("devicetoken");
			DeviceToken token = tokenMap.get(deviceTokenStr);
			if (token == null) {
				AuditDetails audit = AuditDetails.builder()
						.createdBy(resultSet.getString("createdby"))
						.createdTime(resultSet.getLong("createdtime"))
						.lastModifiedBy(resultSet.getString("lastmodifiedby"))
						.lastModifiedTime(resultSet.getLong("lastmodifiedtime")).build();

				token = DeviceToken.builder()
						.id(resultSet.getString("id"))
						.userId(resultSet.getString("userid"))
						.deviceToken(deviceTokenStr)
						.deviceType(resultSet.getString("devicetype"))
						.tenantId(resultSet.getString("tenantid"))
						.facilityIds(new ArrayList<>())
						.auditDetails(audit).build();
				tokenMap.put(deviceTokenStr, token);
			}
			String facilityId = resultSet.getString("facilityid");
			if (facilityId != null) {
				token.getFacilityIds().add(facilityId);
			}
		}
		return new ArrayList<>(tokenMap.values());
	}

}
