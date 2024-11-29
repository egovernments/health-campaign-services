package org.egov.hrms.repository;

import org.egov.hrms.model.ServiceHistory;
import org.egov.hrms.model.AuditDetails;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Component
public class ServiceHistoryRowMapper implements ResultSetExtractor<List<ServiceHistory>> {

    @Override
    public List<ServiceHistory> extractData(ResultSet rs) throws SQLException {
        List<ServiceHistory> serviceHistories = new ArrayList<>();
        while (rs.next()) {
            AuditDetails auditDetails = AuditDetails.builder()
                    .createdBy(rs.getString("createdby"))
                    .createdDate(rs.getLong("createddate"))
                    .lastModifiedBy(rs.getString("lastmodifiedby"))
                    .lastModifiedDate(rs.getLong("lastmodifieddate"))
                    .build();
            ServiceHistory serviceHistory = ServiceHistory.builder()
                    .id(rs.getString("history_uuid"))
                    .serviceStatus(rs.getString("servicestatus"))
                    .serviceFrom(rs.getLong("servicefrom"))
                    .serviceTo(rs.getLong("serviceto"))
                    .isCurrentPosition(rs.getBoolean("iscurrentposition"))
                    .orderNo(rs.getString("ordernumber"))
                    .location(rs.getString("location"))
                    .employeeId(rs.getString("employeeid"))
                    .tenantId(rs.getString("tenantid"))
                    .auditDetails(auditDetails)
                    .build();
            serviceHistories.add(serviceHistory);
        }
        return serviceHistories;
    }


}
