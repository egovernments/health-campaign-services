package org.egov.project.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
public class ProjectRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    public ProjectRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public List<String> validateProjectIds(List<String> projectIds) {
        String query = String.format(
                "SELECT id FROM project WHERE id IN (:projectIds) AND isDeleted = false fetch first %s rows only",
                projectIds.size());
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("projectIds", projectIds);
        try {
            return namedParameterJdbcTemplate.queryForList(query, paramMap, String.class);
        } catch (EmptyResultDataAccessException e) {
            return Collections.emptyList();
        }
    }
}

