package org.egov.web.notification.push.repository.querybuilder;

import org.springframework.stereotype.Component;

@Component
public class DeviceTokenQueryBuilder {

	public static final String FETCH_TOKENS_BY_USERIDS = "SELECT id, userid, devicetoken, devicetype, tenantid, facilityid, "
			+ "createdby, createdtime, lastmodifiedby, lastmodifiedtime "
			+ "FROM eg_push_device_tokens WHERE userid IN (:userIds)";

	// HRUTHVIK: Query to fetch device tokens by facilityId for facility-based push notifications
	public static final String FETCH_TOKENS_BY_FACILITY_ID = "SELECT id, userid, devicetoken, devicetype, tenantid, facilityid, "
			+ "createdby, createdtime, lastmodifiedby, lastmodifiedtime "
			+ "FROM eg_push_device_tokens WHERE facilityid = :facilityId";

}
