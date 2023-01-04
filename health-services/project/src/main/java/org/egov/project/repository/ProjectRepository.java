package org.egov.project.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.producer.Producer;
import org.egov.project.repository.rowmapper.ProjectStaffRowMapper;
import org.egov.project.web.models.ProjectStaff;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class ProjectRepository extends GenericRepository<ProjectStaff> {

    @Autowired
    public ProjectRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                  RedisTemplate<String, Object> redisTemplate,
                                  SelectQueryBuilder selectQueryBuilder, ProjectStaffRowMapper projectStaffRowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder,
                projectStaffRowMapper, Optional.of("project"));
    }

    public List<String> validateProjectIds(List<String> ids) {
        List<String> productIds = ids.stream().filter(id -> redisTemplate.opsForHash()
                        .entries(tableName).containsKey(id))
                .collect(Collectors.toList());
        if (!productIds.isEmpty()) {
            return productIds;
        }
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("projectIds", ids);
        String query = String.format("SELECT id FROM project WHERE id IN (:projectIds) AND isDeleted = false fetch first %s rows only",
                ids.size());
        return namedParameterJdbcTemplate.queryForList(query, paramMap, String.class);
    }

}