package org.egov.household.repository.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.models.household.Relationship;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class RelationshipRowMapper implements RowMapper<Relationship> {
    @Override
    public Relationship mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        AuditDetails auditDetails = AuditDetails.builder()
                .createdBy(resultSet.getString("createdBy"))
                .createdTime(resultSet.getLong("createdTime"))
                .lastModifiedBy(resultSet.getString("lastModifiedBy"))
                .lastModifiedTime(resultSet.getLong("lastModifiedTime"))
                .build();
        AuditDetails clientAuditDetails = AuditDetails.builder()
                .createdTime(resultSet.getLong("clientCreatedTime"))
                .createdBy(resultSet.getString("clientCreatedBy"))
                .lastModifiedTime(resultSet.getLong("clientLastModifiedTime"))
                .lastModifiedBy(resultSet.getString("clientLastModifiedBy"))
                .build();
        return Relationship.builder()
                .id(resultSet.getString("id"))
                .clientReferenceId(resultSet.getString("clientReferenceId"))
                .selfId(resultSet.getString("selfId"))
                .selfClientReferenceId(resultSet.getString("selfClientReferenceId"))
                .relativeClientReferenceId(resultSet.getString("relativeClientReferenceId"))
                .relativeId(resultSet.getString("relativeId"))
                .tenantId(resultSet.getString("tenantId"))
                .relationshipType(resultSet.getString("relationshipType"))
                .isDeleted(resultSet.getBoolean("isDeleted"))
                .rowVersion(resultSet.getInt("rowVersion"))
                .auditDetails(auditDetails)
                .clientAuditDetails(clientAuditDetails)
                .build();
    }
}
