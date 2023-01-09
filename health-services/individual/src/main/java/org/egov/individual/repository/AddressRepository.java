package org.egov.individual.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.producer.Producer;
import org.egov.individual.repository.rowmapper.AddressRowMapper;
import org.egov.individual.web.models.Address;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Slf4j
public class AddressRepository extends GenericRepository<Address> {

    protected AddressRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                RedisTemplate<String, Object> redisTemplate,
                                SelectQueryBuilder selectQueryBuilder, AddressRowMapper addressRowMapper,
                                Optional<String> tableName) {
        super(producer, namedParameterJdbcTemplate,
                redisTemplate, selectQueryBuilder, addressRowMapper, Optional.of("address"));
    }
}
