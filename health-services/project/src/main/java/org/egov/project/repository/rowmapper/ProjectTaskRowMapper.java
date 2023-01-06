package org.egov.project.repository.rowmapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.project.web.models.Task;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class ProjectTaskRowMapper implements RowMapper<Task> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Task mapRow(ResultSet resultSet, int i) throws SQLException {
         return Task.builder().build();
    }
}