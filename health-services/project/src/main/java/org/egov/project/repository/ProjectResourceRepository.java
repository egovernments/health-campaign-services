package org.egov.project.repository;

import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.models.project.ProjectResource;
import org.egov.common.producer.Producer;
import org.egov.project.repository.rowmapper.ProjectResourceRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ProjectResourceRepository extends GenericRepository<ProjectResource> {

    @Autowired
    private ProjectResourceRowMapper rowMapper;

    @Autowired
    protected ProjectResourceRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                    RedisTemplate<String, Object> redisTemplate, SelectQueryBuilder selectQueryBuilder,
                                    ProjectResourceRowMapper rowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder, rowMapper, Optional.of("project_resource"));
    }
}
