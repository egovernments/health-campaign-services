package org.egov.project.repository.rowmapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.project.web.models.ProjectResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;


@Component
public class ProjectResourceRowMapper implements RowMapper<ProjectResource> {

    @Autowired
    ObjectMapper objectMapper;

    @Override
    public ProjectResource mapRow(ResultSet resultSet, int i) throws SQLException {
        return ProjectResource.builder().build();
    }
}
