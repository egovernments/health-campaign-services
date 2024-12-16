package org.egov.hrms.repository;

import org.egov.hrms.model.AuditDetails;
import org.egov.hrms.model.Jurisdiction;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JurisdictionRowMapper implements ResultSetExtractor<List<Jurisdiction>> {

    @Override
    public List<Jurisdiction> extractData(ResultSet rs) throws SQLException {

        List<Jurisdiction> jurisdictions = new ArrayList<>();
        while (rs.next()) {
            AuditDetails auditDetails = AuditDetails.builder()
                    .createdBy(rs.getString("createdby"))
                    .createdDate(rs.getLong("createddate"))
                    .lastModifiedBy(rs.getString("lastmodifiedby"))
                    .lastModifiedDate(rs.getLong("lastmodifieddate"))
                    .build();
            Jurisdiction jurisdiction = Jurisdiction.builder()
                    .id(rs.getString("uuid"))
                    .employeeId(rs.getString("employeeid"))
                    .boundary(rs.getString("boundary"))
                    .boundaryType(rs.getString("boundarytype"))
                    .isActive(rs.getBoolean("isactive"))
                    .hierarchy(rs.getString("hierarchy"))
                    .tenantId(rs.getString("tenantid"))
                    .auditDetails(auditDetails)
                    .build();
            jurisdictions.add(jurisdiction);
        }
        return jurisdictions;
    }
}

