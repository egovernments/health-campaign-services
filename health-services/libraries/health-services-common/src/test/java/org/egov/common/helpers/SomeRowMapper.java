package org.egov.common.helpers;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class SomeRowMapper implements RowMapper<SomeObject> {

    @Override
    public SomeObject mapRow(ResultSet resultSet, int i) throws SQLException {
        return SomeObject.builder()
                .id(resultSet.getString("id"))
                .rowVersion(resultSet.getInt("rowversion"))
                .isDeleted(resultSet.getBoolean("isdeleted"))
                .tenantId(resultSet.getString("tenantid"))
                .build();
    }
}