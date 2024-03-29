package org.egov.stock.repository;

import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.models.stock.StockReconciliation;
import org.egov.common.producer.Producer;
import org.egov.stock.repository.rowmapper.StockReconciliationRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class StockReconciliationRepository extends GenericRepository<StockReconciliation> {
    @Autowired
    public StockReconciliationRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                         RedisTemplate<String, Object> redisTemplate,
                                         SelectQueryBuilder selectQueryBuilder, StockReconciliationRowMapper stockReconciliationRowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder,
                stockReconciliationRowMapper, Optional.of("stock_reconciliation_log"));
    }
}
