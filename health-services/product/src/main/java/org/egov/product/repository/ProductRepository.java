package org.egov.product.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ProductRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    public ProductRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }


    public List<String> validateProductId(List<String> productIds) {
        String query = String.format("SELECT id FROM product WHERE id IN (:productIds) fetch first %s rows only", productIds.size());
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("productIds", productIds);
        return namedParameterJdbcTemplate.queryForObject(query, paramMap, List.class);
    }
}
