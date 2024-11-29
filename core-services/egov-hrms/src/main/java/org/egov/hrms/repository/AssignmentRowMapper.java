package org.egov.hrms.repository;

import org.egov.hrms.model.Assignment;
import org.egov.hrms.model.AuditDetails;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Component
public class AssignmentRowMapper implements ResultSetExtractor<List<Assignment>> {

    @Override
    public List<Assignment> extractData(ResultSet rs) throws SQLException {
        List<Assignment> assignments = new ArrayList<>();
        while (rs.next()) {
            AuditDetails auditDetails = AuditDetails.builder()
                    .createdBy(rs.getString("createdby"))
                    .createdDate(rs.getLong("createddate"))
                    .lastModifiedBy(rs.getString("lastmodifiedby"))
                    .lastModifiedDate(rs.getLong("lastmodifieddate"))
                    .build();

            Assignment assignment = Assignment.builder()
                    .id(rs.getString("assignment_uuid"))
                    .employeeId(rs.getString("employeeid"))
                    .position(rs.getLong("position"))
                    .department(rs.getString("department"))
                    .designation(rs.getString("designation"))
                    .fromDate(rs.getLong("fromdate"))
                    .toDate(rs.getObject("todate") != null ? rs.getLong("todate") : null)
                    .govtOrderNumber(rs.getString("govtordernumber"))
                    .reportingTo(rs.getString("reportingto"))
                    .isHOD(rs.getBoolean("ishod"))
                    .isCurrentAssignment(rs.getBoolean("iscurrentassignment"))
                    .tenantid(rs.getString("tenantid"))
                    .auditDetails(auditDetails)
                    .build();
            assignments.add(assignment);
        }
        return assignments;
    }
}
