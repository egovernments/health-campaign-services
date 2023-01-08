package org.egov.project.repository.rowmapper;

import org.egov.project.web.models.TaskResource;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class TaskResourceRowMapper implements RowMapper<TaskResource> {

    @Override
    public TaskResource mapRow(ResultSet resultSet, int i) throws SQLException {
        return TaskResource.builder().build();
    }
}
