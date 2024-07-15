package org.egov.project.repository;

import java.util.Optional;

import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.models.project.irs.LocationPoint;
import org.egov.common.producer.Producer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class LocationPointRepository extends GenericRepository<LocationPoint> {
    protected LocationPointRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate, RedisTemplate<String, Object> redisTemplate, SelectQueryBuilder selectQueryBuilder, RowMapper<LocationPoint> rowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder, rowMapper, Optional.of("LOCATION_POINT"));
    }
}
