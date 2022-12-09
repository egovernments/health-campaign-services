package org.egov.product.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.producer.Producer;
import org.egov.product.web.models.Product;
import org.egov.product.web.models.ProductRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@Slf4j
public class ProductRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final Producer producer;

    private final RedisTemplate<String, Object> redisTemplate;

    private final String HASH_KEY = "product";

    @Autowired
    public ProductRepository(Producer producer,  NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                             RedisTemplate<String, Object> redisTemplate) {
        this.producer = producer;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    public List<String> validateProductId(List<String> ids) {
        List<String> productIds = ids.stream().filter(id -> redisTemplate.opsForHash()
                .entries(HASH_KEY).containsKey(id))
                .collect(Collectors.toList());
        if (!productIds.isEmpty()) {
            return productIds;
        }
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("productIds", ids);
        String query = String.format("SELECT id FROM PRODUCT WHERE id IN (:productIds) AND isDeleted = false fetch first %s rows only", ids.size());
        return namedParameterJdbcTemplate.queryForList(query, paramMap, String.class);
    }

    public ProductRequest save(ProductRequest productRequest, String topic) {
        producer.push(topic, productRequest);
        log.info("Pushed to kafka");
        Map<String, Product> productMap = productRequest.getProduct().stream()
                .collect(Collectors.toMap(Product::getId,
                                product -> product));
        redisTemplate.opsForHash().putAll(HASH_KEY, productMap);
        return productRequest;
    }
}

