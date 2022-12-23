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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    public List<String> validateProductId(List<String> ids) {
        List<String> productIds = ids.stream().filter(id -> redisTemplate.opsForHash()
                .entries(hashKey).containsKey(id))
                .collect(Collectors.toList());
        if (!productIds.isEmpty()) {
            return productIds;
        }
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("productIds", ids);
        String query = String.format("SELECT id FROM PRODUCT WHERE id IN (:productIds) AND isDeleted = false fetch first %s rows only",
                ids.size());
        return namedParameterJdbcTemplate.queryForList(query, paramMap, String.class);
    }
}

