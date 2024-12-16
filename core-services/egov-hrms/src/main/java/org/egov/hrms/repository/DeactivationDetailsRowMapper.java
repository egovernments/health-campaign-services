package org.egov.hrms.repository;

import org.egov.hrms.model.AuditDetails;
import org.egov.hrms.model.DeactivationDetails;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Component
public class DeactivationDetailsRowMapper  implements ResultSetExtractor<List<DeactivationDetails>> {

    @Override
    public List<DeactivationDetails> extractData(ResultSet rs) throws SQLException {

        List<DeactivationDetails> deactivationDetails = new ArrayList<>();
        while (rs.next()) {
            AuditDetails auditDetails = AuditDetails.builder()
                    .createdBy(rs.getString("createdby"))
                    .createdDate(rs.getLong("createddate"))
                    .lastModifiedBy(rs.getString("lastmodifiedby"))
                    .lastModifiedDate(rs.getLong("lastmodifieddate"))
                    .build();

            DeactivationDetails deactivationDetail = DeactivationDetails.builder()
                    .id(rs.getString("deact_uuid"))
                    .reasonForDeactivation(rs.getString("reasonfordeactivation"))
                    .orderNo(rs.getString("ordernumber"))
                    .remarks(rs.getString("remarks"))
                    .effectiveFrom(rs.getLong("effectivefrom"))
                    .employeeId(rs.getString("employeeid"))
                    .tenantId(rs.getString("tenantid"))
                    .auditDetails(auditDetails)
                    .build();
            deactivationDetails.add(deactivationDetail);
        }
        return deactivationDetails;
    }

}
