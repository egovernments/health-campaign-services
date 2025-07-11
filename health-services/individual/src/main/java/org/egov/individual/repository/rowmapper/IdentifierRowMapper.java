package org.egov.individual.repository.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.egov.common.contract.models.AuditDetails;
import org.egov.common.models.individual.Identifier;
import org.springframework.jdbc.core.RowMapper;

public class IdentifierRowMapper implements RowMapper<Identifier> {
    @Override
    public Identifier mapRow(ResultSet resultSet, int i) throws SQLException {
        return Identifier.builder()
                .id(resultSet.getString("id"))
                .individualId(resultSet.getString("individualId"))
                .individualClientReferenceId(resultSet.getString("individualClientReferenceId"))
                .clientReferenceId(resultSet.getString("clientReferenceId"))
                .identifierType(resultSet.getString("identifierType"))
                .identifierId(resultSet.getString("identifierId"))
                .auditDetails(AuditDetails.builder().createdBy(resultSet.getString("createdBy"))
                        .lastModifiedBy(resultSet.getString("lastModifiedBy"))
                        .createdTime(resultSet.getLong("createdTime"))
                        .lastModifiedTime(resultSet.getLong("lastModifiedTime")).build())
                .isDeleted(resultSet.getBoolean("isDeleted"))
                .build();
    }
}
