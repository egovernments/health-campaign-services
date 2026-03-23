package org.egov.web.notification.push.repository.rowmappers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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
		List<DeviceToken> deviceTokens = new ArrayList<>();
		while (resultSet.next()) {
			AuditDetails audit = AuditDetails.builder()
					.createdBy(resultSet.getString("createdby"))
					.createdTime(resultSet.getLong("createdtime"))
					.lastModifiedBy(resultSet.getString("lastmodifiedby"))
					.lastModifiedTime(resultSet.getLong("lastmodifiedtime")).build();

			DeviceToken token = DeviceToken.builder()
					.id(resultSet.getString("id"))
					.userId(resultSet.getString("userid"))
					.deviceToken(resultSet.getString("devicetoken"))
					.deviceType(resultSet.getString("devicetype"))
					.tenantId(resultSet.getString("tenantid"))
					.facilityId(resultSet.getString("facilityid")) // HRUTHVIK: Added facilityId mapping
					.auditDetails(audit).build();

			deviceTokens.add(token);
		}
		return deviceTokens;
	}

}
