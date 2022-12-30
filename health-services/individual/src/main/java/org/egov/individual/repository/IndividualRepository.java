package org.egov.individual.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.producer.Producer;
import org.egov.individual.repository.rowmapper.IndividualRowMapper;
import org.egov.individual.web.models.Individual;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Slf4j
public class IndividualRepository extends GenericRepository<Individual> {

    protected IndividualRepository(Producer producer,
                                   NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                   RedisTemplate<String, Object> redisTemplate,
                                   SelectQueryBuilder selectQueryBuilder,
                                   IndividualRowMapper individualRowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate,
                selectQueryBuilder, individualRowMapper, Optional.of("individual"));
    }
}
