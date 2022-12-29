package org.egov.repository.rowmapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.web.models.AdditionalFields;
import org.egov.web.models.Address;
import org.egov.web.models.Boundary;
import org.egov.web.models.Household;
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
                    .rowVersion(resultSet.getInt("rowversion"))
                    .isDeleted(resultSet.getBoolean("isdeleted"))
                    .tenantId(resultSet.getString("tenantid"))
                    .memberCount(resultSet.getInt("numberOfMembers"))
                    .clientReferenceId(resultSet.getString("clientreferenceid"))
                    .auditDetails(AuditDetails.builder()
                            .createdBy(resultSet.getString("createdby"))
                            .createdTime(resultSet.getLong("createdtime"))
                            .lastModifiedBy(resultSet.getString("lastmodifiedby"))
                            .lastModifiedTime(resultSet.getLong("lastmodifiedtime"))
                            .build())
                    .additionalFields(resultSet.getString("additionalDetails") == null ? null : objectMapper.readValue(resultSet.getString("additionalDetails"), AdditionalFields.class))
                    .address(Address.builder()
                            .id(resultSet.getString(13))
                            .tenantId(resultSet.getString(14))
                            .doorNo(resultSet.getString("doorno"))
                            .latitude(resultSet.getDouble("latitude"))
                            .longitude(resultSet.getDouble("longitude"))
                            .locationAccuracy(resultSet.getDouble("locationaccuracy"))
                            .type(resultSet.getString("type"))
                            .addressLine1(resultSet.getString("addressLine1"))
                            .addressLine2(resultSet.getString("addressLine2"))
                            .landmark(resultSet.getString("landmark"))
                            .city(resultSet.getString("city"))
                            .pincode(resultSet.getString("pincode"))
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
