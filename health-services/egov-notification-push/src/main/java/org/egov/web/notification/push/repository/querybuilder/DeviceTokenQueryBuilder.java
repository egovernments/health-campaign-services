package org.egov.web.notification.push.repository.querybuilder;

import org.springframework.stereotype.Component;

@Component
public class DeviceTokenQueryBuilder {

	private static final String COLUMNS = "id, userid, devicetoken, devicetype, tenantid, facilityid, "
			+ "createdby, createdtime, lastmodifiedby, lastmodifiedtime";

	public static String fetchTokensByUserIds(String schema) {
		return "SELECT " + COLUMNS + " FROM " + schema + ".eg_push_device_tokens WHERE userid IN (:userIds)";
	}

	public static String fetchTokensByFacilityId(String schema) {
		return "SELECT " + COLUMNS + " FROM " + schema + ".eg_push_device_tokens WHERE facilityid = :facilityId";
	}

}
