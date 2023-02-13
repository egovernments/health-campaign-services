package org.egov.stock.repository;

import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.producer.Producer;
import org.egov.stock.repository.rowmapper.StockRowMapper;
import org.egov.stock.web.models.Stock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class StockRepository extends GenericRepository<Stock> {
    @Autowired
    public StockRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                             RedisTemplate<String, Object> redisTemplate,
                             SelectQueryBuilder selectQueryBuilder, StockRowMapper stockRowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder,
                stockRowMapper, Optional.of("stock"));
    }
}
