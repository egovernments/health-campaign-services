package org.egov.household.repository.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberRelationship;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class HouseholdMemberRelationshipMapper implements RowMapper<HouseholdMemberRelationship> {
    @Override
    public HouseholdMemberRelationship mapRow(ResultSet resultSet, int rowNum) throws SQLException {
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
        return HouseholdMemberRelationship.builder()
                .id(resultSet.getString("id"))
                .clientReferenceId(resultSet.getString("clientReferenceId"))
                .householdMemberId(resultSet.getString("householdMemberId"))
                .householdMemberClientReferenceId(resultSet.getString("householdMemberClientReferenceId"))
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
