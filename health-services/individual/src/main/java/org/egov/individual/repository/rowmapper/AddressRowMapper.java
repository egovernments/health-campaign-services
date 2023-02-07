package org.egov.individual.repository.rowmapper;

import digit.models.coremodels.AuditDetails;
import org.egov.individual.web.models.Address;
import org.egov.individual.web.models.AddressType;
import org.egov.individual.web.models.Boundary;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class AddressRowMapper implements RowMapper<Address> {
    @Override
    public Address mapRow(ResultSet resultSet, int i) throws SQLException {
        return Address.builder()
                .id(resultSet.getString("id"))
                .individualId(resultSet.getString("individualId"))
                .tenantId(resultSet.getString("tenantId"))
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
                .auditDetails(AuditDetails.builder().createdBy(resultSet.getString("createdBy"))
                        .lastModifiedBy(resultSet.getString("lastModifiedBy"))
                        .createdTime(resultSet.getLong("createdTime"))
                        .lastModifiedTime(resultSet.getLong("lastModifiedTime")).build())
                .isDeleted(resultSet.getBoolean("isDeleted"))
                        .build();
    }
}
