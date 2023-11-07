package org.egov.household.repository.rowmapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.common.models.household.AdditionalFields;
import org.egov.common.models.household.HouseholdMember;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class HouseholdMemberRowMapper implements RowMapper<HouseholdMember> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public HouseholdMember mapRow(ResultSet resultSet, int i) throws SQLException {
        try {
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
            return HouseholdMember.builder()
                    .id(resultSet.getString("id"))
                    .householdId(resultSet.getString("householdId"))
                    .clientReferenceId(resultSet.getString("clientReferenceId"))
                    .householdClientReferenceId(resultSet.getString("householdClientReferenceId"))
                    .individualClientReferenceId(resultSet.getString("individualClientReferenceId"))
                    .individualId(resultSet.getString("individualId"))
                    .tenantId(resultSet.getString("tenantId"))
                    .isHeadOfHousehold(resultSet.getBoolean("isHeadOfHousehold"))
                    .additionalFields(resultSet.getString("additionalDetails") == null ? null : objectMapper.readValue(resultSet
                            .getString("additionalDetails"), AdditionalFields.class))
                    .isDeleted(resultSet.getBoolean("isDeleted"))
                    .rowVersion(resultSet.getInt("rowVersion"))
                    .auditDetails(auditDetails)
                    .clientAuditDetails(clientAuditDetails)
                    .build();
        } catch (JsonProcessingException e) {
            throw new SQLException(e);
        }

    }
}
