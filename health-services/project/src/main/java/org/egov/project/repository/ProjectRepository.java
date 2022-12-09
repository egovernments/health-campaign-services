package org.egov.project.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Repository
public class ProjectRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    public ProjectRepository( NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public boolean checkIfProjectExists(String projectId) {
        String query = "SELECT COUNT(*) FROM PROJECT WHERE id=:projectId";
        Map<String, String> params = Collections.singletonMap("projectId", projectId);
        return namedParameterJdbcTemplate.queryForObject(query, params, Integer.class) > 0;
    }
}

