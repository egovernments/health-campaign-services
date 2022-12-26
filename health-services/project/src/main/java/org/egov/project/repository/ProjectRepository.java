package org.egov.project.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class ProjectRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String HASH_KEY = "project";

    @Autowired
    public ProjectRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                             RedisTemplate<String, Object> redisTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    public List<String> validateProjectIds(List<String> ids) {
        List<String> productIds = ids.stream().filter(id -> redisTemplate.opsForHash()
                        .entries(HASH_KEY).containsKey(id))
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