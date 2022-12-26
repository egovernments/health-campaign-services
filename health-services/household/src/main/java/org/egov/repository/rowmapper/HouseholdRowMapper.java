package org.egov.repository.rowmapper;

import org.egov.web.models.Household;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class HouseholdRowMapper implements RowMapper<Household> {
    @Override
    public Household mapRow(ResultSet resultSet, int i) throws SQLException {
        return Household.builder().build();
    }
}
