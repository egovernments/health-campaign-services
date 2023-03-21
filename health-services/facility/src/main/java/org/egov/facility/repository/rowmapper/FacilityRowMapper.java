package org.egov.facility.repository.rowmapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.common.models.facility.AdditionalFields;
import org.egov.common.models.facility.Address;
import org.egov.common.models.facility.AddressType;
import org.egov.common.models.facility.Boundary;
import org.egov.common.models.facility.Facility;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class FacilityRowMapper implements RowMapper<Facility> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Facility mapRow(ResultSet resultSet, int i) throws SQLException {
        try {
            return Facility.builder()
                    .id(resultSet.getString("id"))
                    .tenantId(resultSet.getString("tenantId"))
                    .isPermanent(resultSet.getBoolean("isPermanent"))
                    .name(resultSet.getString("name"))
                    .usage(resultSet.getString("usage"))
                    .storageCapacity(resultSet.getInt("storageCapacity"))
                    .address(Address.builder()
                            .id(resultSet.getString(15))
                            .tenantId(resultSet.getString(16))
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
                    .additionalFields(resultSet.getString("additionalDetails") == null ? null : objectMapper
                        .readValue(resultSet.getString("additionalDetails"), AdditionalFields.class))
                    .auditDetails(AuditDetails.builder()
                            .createdBy(resultSet.getString("createdBy"))
                            .createdTime(resultSet.getLong("createdTime"))
                            .lastModifiedBy(resultSet.getString("lastModifiedBy"))
                            .lastModifiedTime(resultSet.getLong("lastModifiedTime"))
                            .build())
                    .rowVersion(resultSet.getInt("rowVersion"))
                    .isDeleted(resultSet.getBoolean("isDeleted"))
                    .build();
        } catch (JsonProcessingException e) {
            throw new SQLException(e);
        }
    }
}
