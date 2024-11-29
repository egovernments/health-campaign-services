package org.egov.hrms.repository;

import org.egov.hrms.model.AuditDetails;
import org.egov.hrms.model.ReactivationDetails;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
@Component
public class ReactivationDetailsRowMapper implements ResultSetExtractor<List<ReactivationDetails>> {

    @Override
    public List<ReactivationDetails> extractData(ResultSet rs) throws SQLException {
        List<ReactivationDetails> reactivationDetails = new ArrayList<>();
        while (rs.next()) {
            AuditDetails auditDetails = AuditDetails.builder()
                    .createdBy(rs.getString("createdby"))
                    .createdDate(rs.getLong("createddate"))
                    .lastModifiedBy(rs.getString("lastmodifiedby"))
                    .lastModifiedDate(rs.getLong("lastmodifieddate"))
                    .build();

            ReactivationDetails reactivationDetail = ReactivationDetails.builder()
                    .id(rs.getString("uuid"))
                    .reasonForReactivation(rs.getString("reasonforreactivation"))
                    .orderNo(rs.getString("ordernumber"))
                    .remarks(rs.getString("remarks"))
                    .effectiveFrom(rs.getLong("effectivefrom"))
                    .employeeId(rs.getString("employeeid"))
                    .tenantId(rs.getString("tenantid"))
                    .auditDetails(auditDetails)
                    .build();
            reactivationDetails.add(reactivationDetail);
        }
        return reactivationDetails;
    }
}
