package org.egov.hrms.repository;

import org.egov.hrms.model.AuditDetails;
import org.egov.hrms.model.DepartmentalTest;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Component
public class DepartmentalTestRowMapper implements ResultSetExtractor<List<DepartmentalTest>> {

    @Override
    public List<DepartmentalTest> extractData(ResultSet rs) throws SQLException {
        List<DepartmentalTest> departmentalTests = new ArrayList<>();
        while (rs.next()) {
            AuditDetails auditDetails = AuditDetails.builder()
                    .createdBy(rs.getString("createdby"))
                    .createdDate(rs.getLong("createddate"))
                    .lastModifiedBy(rs.getString("lastmodifiedby"))
                    .lastModifiedDate(rs.getLong("lastmodifieddate"))
                    .build();
            DepartmentalTest departmentalTest = DepartmentalTest.builder()
                    .id(rs.getString("uuid"))
                    .test(rs.getString("testname"))
                    .yearOfPassing(rs.getLong("yearofpassing"))
                    .remarks(rs.getString("remarks"))
                    .employeeId(rs.getString("employeeid"))
                    .tenantId(rs.getString("tenantid"))
                    .auditDetails(auditDetails)
                    .build();
            departmentalTests.add(departmentalTest);
        }
        return departmentalTests;
    }

}
