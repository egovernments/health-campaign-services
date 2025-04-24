package org.egov.common.helpers;

import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.producer.Producer;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class SomeRepository extends GenericRepository<SomeObject> {

    protected SomeRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                             RedisTemplate<String, Object> redisTemplate,
                             SelectQueryBuilder selectQueryBuilder,
                             RowMapper<SomeObject> rowMapper,
                             Optional<String> tableName,
                             MultiStateInstanceUtil multiStateInstanceUtil) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder, rowMapper,
                Optional.of("some-table"), multiStateInstanceUtil);
    }
}