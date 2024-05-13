package org.egov.facility.repository.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.facility.Address;
import org.egov.common.models.facility.AddressType;
import org.egov.common.models.core.Boundary;
import org.egov.common.models.facility.Facility;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class FacilityRowMapper implements RowMapper<Facility> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Facility mapRow(ResultSet resultSet, int i) throws SQLException {
        try {
            Facility facility = Facility.builder()
                    .id(resultSet.getString("id"))
                    .clientReferenceId(resultSet.getString("clientReferenceId"))
                    .tenantId(resultSet.getString("tenantId"))
                    .isPermanent(resultSet.getBoolean("isPermanent"))
                    .name(resultSet.getString("name"))
                    .usage(resultSet.getString("usage"))
                    .storageCapacity(resultSet.getInt("storageCapacity"))
                    .address(Address.builder()
                            .id(resultSet.getString("aid"))
                            .tenantId(resultSet.getString("atenantid"))
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
                            .locality(resultSet.getString("localityCode") != null ? Boundary.builder().code(resultSet.getString("localityCode")).build() : null)
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
            if (facility.getAddress().getId() == null) {
                facility.setAddress(null);
            }
            return facility;
        } catch (JsonProcessingException e) {
            throw new SQLException(e);
        }
    }
}
