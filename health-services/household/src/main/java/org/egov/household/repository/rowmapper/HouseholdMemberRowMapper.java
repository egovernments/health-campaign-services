package org.egov.household.repository.rowmapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.household.web.models.AdditionalFields;
import org.egov.household.web.models.HouseholdMember;
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
            return HouseholdMember.builder()
                    .id(resultSet.getString("id"))
                    .householdId(resultSet.getString("householdId"))
                    .householdClientReferenceId(resultSet.getString("householdClientReferenceId"))
                    .individualClientReferenceId(resultSet.getString("individualClientReferenceId"))
                    .individualId(resultSet.getString("individualId"))
                    .tenantId(resultSet.getString("tenantId"))
                    .isHeadOfHousehold(resultSet.getBoolean("isHeadOfHousehold"))
                    .additionalFields(resultSet.getString("additionalDetails") == null ? null : objectMapper.readValue(resultSet.getString("additionalDetails"), AdditionalFields.class))
                    .isDeleted(resultSet.getBoolean("isDeleted"))
                    .rowVersion(resultSet.getInt("rowVersion"))
                    .auditDetails(AuditDetails.builder()
                            .createdBy(resultSet.getString("createdBy"))
                            .createdTime(resultSet.getLong("createdTime"))
                            .lastModifiedBy(resultSet.getString("lastModifiedBy"))
                            .lastModifiedTime(resultSet.getLong("lastModifiedTime"))
                            .build())
                    .build();
        } catch (JsonProcessingException e) {
            throw new SQLException(e);
        }

    }
}
