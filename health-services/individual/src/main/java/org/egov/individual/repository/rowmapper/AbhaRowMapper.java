package org.egov.individual.repository.rowmapper;

import org.egov.common.contract.models.AuditDetails;
import org.egov.common.models.individual.AbhaTransaction;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component("abhaTxnRowMapper")
public class AbhaRowMapper implements RowMapper<AbhaTransaction> {

    @Override
    public AbhaTransaction mapRow(ResultSet rs, int rowNum) throws SQLException {
        return AbhaTransaction.builder()
                .id(rs.getString("id"))
                .individualId(rs.getString("individualId"))
                .transactionId(rs.getString("transactionId"))
                .abhaNumber(rs.getString("abhaNumber"))
                .tenantId(rs.getString("tenantId"))
                .additionalDetails(rs.getObject("additionalDetails")) // Assuming JSONB can be read directly
                .isDeleted(rs.getObject("isDeleted") != null && rs.getBoolean("isDeleted"))
                .auditDetails(AuditDetails.builder()
                        .createdBy(rs.getString("createdBy"))
                        .lastModifiedBy(rs.getString("lastModifiedBy"))
                        .createdTime(rs.getLong("createdTime"))
                        .lastModifiedTime(rs.getLong("lastModifiedTime"))
                        .build())
                .rowVersion(rs.getInt("rowVersion"))
                .build();
    }
}
