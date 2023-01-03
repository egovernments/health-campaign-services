package org.egov.individual.repository.rowmapper;

import org.egov.individual.web.models.Address;
import org.egov.individual.web.models.Boundary;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class AddressRowMapper implements RowMapper<Address> {
    @Override
    public Address mapRow(ResultSet resultSet, int i) throws SQLException {
        return Address.builder()
                .id(resultSet.getString("id"))
                .tenantId(resultSet.getString("tenantId"))
                .doorNo(resultSet.getString("doorNo"))
                .latitude(resultSet.getDouble("latitude"))
                .longitude(resultSet.getDouble("longitude"))
                .locationAccuracy(resultSet.getDouble("locationAccuracy"))
                .type(resultSet.getString("type"))
                .addressLine1(resultSet.getString("addressLine1"))
                .addressLine2(resultSet.getString("addressLine2"))
                .landmark(resultSet.getString("landmark"))
                .city(resultSet.getString("city"))
                .pincode(resultSet.getString("pinCode"))
                .buildingName(resultSet.getString("buildingName"))
                .street(resultSet.getString("street"))
                .locality(Boundary.builder().code(resultSet.getString("localityCode")).build())
                        .build();
    }
}
