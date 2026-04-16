package org.egov.web.notification.push.repository.querybuilder;

import org.springframework.stereotype.Component;

@Component
public class DeviceTokenQueryBuilder {

	private static final String COLUMNS = "id, userid, devicetoken, devicetype, tenantid, facilityid, userroles, "
			+ "createdby, createdtime, lastmodifiedby, lastmodifiedtime";
	private static final String DELIMITED_USER_ROLES = "(',' || COALESCE(userroles, '') || ',')";

	private static String table(String schema) {
		return schema == null ? "eg_push_device_tokens" : schema + ".eg_push_device_tokens";
	}

	public static String fetchTokensByUserIds(String schema) {
		return "SELECT " + COLUMNS + " FROM " + table(schema) + " WHERE userid IN (:userIds)";
	}

	public static String fetchTokensByFacilityId(String schema) {
		return "SELECT " + COLUMNS + " FROM " + table(schema) + " WHERE facilityid = :facilityId";
	}

	public static String fetchLatestTokenByUserIds(String schema) {
		return "SELECT DISTINCT ON (userid) " + COLUMNS + " FROM " + table(schema)
				+ " WHERE userid IN (:userIds) ORDER BY userid, lastmodifiedtime DESC";
	}

	public static String fetchTokensByFacilityIdAndRole(String schema) {
		return "SELECT " + COLUMNS + " FROM " + table(schema)
				+ " WHERE facilityid = :facilityId AND " + DELIMITED_USER_ROLES + " LIKE :rolePattern";
	}

	public static String fetchTokensByFacilityIdAndRoles(String schema, int roleCount) {
		StringBuilder sb = new StringBuilder();
		sb.append("SELECT ").append(COLUMNS).append(" FROM ").append(table(schema));
		sb.append(" WHERE facilityid = :facilityId AND (");
		for (int i = 0; i < roleCount; i++) {
			if (i > 0) sb.append(" OR ");
			sb.append(DELIMITED_USER_ROLES).append(" LIKE :rolePattern").append(i);
		}
		sb.append(")");
		return sb.toString();
	}

	public static String deleteByDeviceTokens(String schema) {
		return "DELETE FROM " + table(schema) + " WHERE devicetoken IN (:deviceTokens)";
	}

}
