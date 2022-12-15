package org.egov.project.mapper;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ProjectIdMapper implements RowMapper<List<String>> {

    @Override
    public List<String> mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        List<String> validProjectIds = new ArrayList<>();
        validProjectIds.add(resultSet.getString(1));
        while (resultSet.next()) {
            validProjectIds.add(resultSet.getString(1));
        }
        return validProjectIds;
    }
}