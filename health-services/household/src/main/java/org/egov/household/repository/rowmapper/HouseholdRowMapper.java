package org.egov.household.repository.rowmapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.common.models.household.AdditionalFields;
import org.egov.common.models.household.Address;
import org.egov.common.models.household.AddressType;
import org.egov.common.models.household.Boundary;
import org.egov.common.models.household.Household;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class HouseholdRowMapper implements RowMapper<Household> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Household mapRow(ResultSet resultSet, int i) throws SQLException {
        try {
            return Household.builder()
                    .id(resultSet.getString("id"))
                    .rowVersion(resultSet.getInt("rowVersion"))
                    .isDeleted(resultSet.getBoolean("isDeleted"))
                    .tenantId(resultSet.getString("tenantId"))
                    .memberCount(resultSet.getInt("numberOfMembers"))
                    .clientReferenceId(resultSet.getString("clientReferenceId"))
                    .auditDetails(AuditDetails.builder()
                            .createdBy(resultSet.getString("createdBy"))
                            .createdTime(resultSet.getLong("createdTime"))
                            .lastModifiedBy(resultSet.getString("lastModifiedBy"))
                            .lastModifiedTime(resultSet.getLong("lastModifiedTime"))
                            .build())
                    .additionalFields(resultSet.getString("additionalDetails") == null ? null : objectMapper.readValue(resultSet
                            .getString("additionalDetails"), AdditionalFields.class))
                    .address(Address.builder()
                            .id(resultSet.getString(13))
                            .clientReferenceId(resultSet.getString(28))
                            .tenantId(resultSet.getString(14))
                            .doorNo(resultSet.getString("doorNo"))
                            .latitude(resultSet.getDouble("latitude"))
                            .longitude(resultSet.getDouble("longitude"))
                            .locationAccuracy(resultSet.getDouble("locationAccuracy"))
                            .type(AddressType.fromValue(resultSet.getString("type")))
                            .addressLine1(resultSet.getString("addressLine1"))
                            .addressLine2(resultSet.getString("addressLine2"))
                            .landmark(resultSet.getString("landmark"))
                            .city(resultSet.getString("city"))
                            .pincode(resultSet.getString("pinCode"))
                            .buildingName(resultSet.getString("buildingName"))
                            .street(resultSet.getString("street"))
                            .locality(Boundary.builder().code(resultSet.getString("localityCode")).build())
                            .build())
                    .build();
        } catch (JsonProcessingException e) {
            throw new SQLException(e);
        }

    }
}
