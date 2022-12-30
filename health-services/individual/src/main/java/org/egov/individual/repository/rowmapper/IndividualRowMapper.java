package org.egov.individual.repository.rowmapper;

import org.egov.individual.web.models.Individual;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class IndividualRowMapper implements RowMapper<Individual> {

    @Override
    public Individual mapRow(ResultSet resultSet, int i) throws SQLException {
        return null;
    }
}
