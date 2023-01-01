package org.egov.product.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.producer.Producer;
import org.egov.product.repository.rowmapper.ProductRowMapper;
import org.egov.product.web.models.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Slf4j
public class ProductRepository extends GenericRepository<Product> {

    @Autowired
    public ProductRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                             RedisTemplate<String, Object> redisTemplate,
                             SelectQueryBuilder selectQueryBuilder, ProductRowMapper productRowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder,
                productRowMapper, Optional.of("product"));
    }

}

