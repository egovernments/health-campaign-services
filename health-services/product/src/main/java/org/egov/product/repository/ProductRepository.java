package org.egov.product.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.product.web.models.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CachePut;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@Slf4j
public class ProductRepository {
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Autowired
    public ProductRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }
    public List<String> validateProductId(List<String> ids){
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("productIds", ids);
        String query = String.format("SELECT id FROM PRODUCT WHERE id IN (:productIds) fetch first %s rows only", ids.size());
        return namedParameterJdbcTemplate.queryForList(query, paramMap, String.class);
    }
}

