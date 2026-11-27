package org.egov.web.notification.push.repository.querybuilder;

import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;

import org.springframework.stereotype.Component;

@Component
public class DeviceTokenQueryBuilder {

	private static final String COLUMNS = "id, userid, devicetoken, devicetype, tenantid, facilityid, userroles, "
			+ "createdby, createdtime, lastmodifiedby, lastmodifiedtime";

	private static final String TABLE = SCHEMA_REPLACE_STRING + ".eg_push_device_tokens";

	public static String fetchTokensByUserIds() {
		return "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE userid IN (:userIds)";
	}

	public static String fetchTokensByFacilityId() {
		return "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE facilityid = :facilityId";
	}

	public static String fetchLatestTokenByUserIds() {
		return "SELECT DISTINCT ON (userid) " + COLUMNS + " FROM " + TABLE
				+ " WHERE userid IN (:userIds) ORDER BY userid, lastmodifiedtime DESC";
	}

	public static String fetchTokensByFacilityIdAndRole() {
		return "SELECT " + COLUMNS + " FROM " + TABLE
				+ " WHERE facilityid = :facilityId AND userroles LIKE :rolePattern";
	}

	public static String fetchTokensByFacilityIdAndRoles(int roleCount) {
		StringBuilder sb = new StringBuilder();
		sb.append("SELECT ").append(COLUMNS).append(" FROM ").append(TABLE);
		sb.append(" WHERE facilityid = :facilityId AND (");
		for (int i = 0; i < roleCount; i++) {
			if (i > 0) sb.append(" OR ");
			sb.append("userroles LIKE :rolePattern").append(i);
		}
		sb.append(")");
		return sb.toString();
	}

	public static String deleteByDeviceTokens() {
		return "DELETE FROM " + TABLE + " WHERE devicetoken IN (:deviceTokens)";
	}

}
