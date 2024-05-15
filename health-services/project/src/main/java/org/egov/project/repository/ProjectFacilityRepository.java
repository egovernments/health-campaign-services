package org.egov.project.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.models.project.ProjectFacility;
import org.egov.common.producer.Producer;
import org.egov.project.repository.rowmapper.ProjectFacilityRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository
@Slf4j
public class ProjectFacilityRepository extends GenericRepository<ProjectFacility> {
    @Autowired
    public ProjectFacilityRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                  RedisTemplate<String, Object> redisTemplate,
                                  SelectQueryBuilder selectQueryBuilder, ProjectFacilityRowMapper projectFacilityRowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder,
                projectFacilityRowMapper, Optional.of("project_facility"));
    }
}
