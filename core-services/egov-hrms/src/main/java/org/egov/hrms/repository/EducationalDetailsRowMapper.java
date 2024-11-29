package org.egov.hrms.repository;

import org.egov.hrms.model.AuditDetails;
import org.egov.hrms.model.EducationalQualification;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Component
public class EducationalDetailsRowMapper implements ResultSetExtractor<List<EducationalQualification>> {

    @Override
    public List<EducationalQualification> extractData(ResultSet rs) throws SQLException {
        List<EducationalQualification> educationalQualifications = new ArrayList<>();
        while (rs.next()) {
            AuditDetails auditDetails = AuditDetails.builder()
                    .createdBy(rs.getString("createdby"))
                    .createdDate(rs.getLong("createddate"))
                    .lastModifiedBy(rs.getString("lastmodifiedby"))
                    .lastModifiedDate(rs.getLong("lastmodifieddate"))
                    .build();
            educationalQualifications.add(EducationalQualification.builder()
                    .id(rs.getString("uuid"))
                    .qualification(rs.getString("qualification"))
                    .stream(rs.getString("stream"))
                    .yearOfPassing(rs.getLong("yearofpassing"))
                    .university(rs.getString("university"))
                    .remarks(rs.getString("remarks"))
                    .employeeId(rs.getString("employeeid"))
                    .tenantId(rs.getString("tenantid"))
                    .isActive(rs.getBoolean("isactive"))
                    .auditDetails(auditDetails)
                    .build());
        }
        return educationalQualifications;
    }
}

