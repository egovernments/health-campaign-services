package org.egov.product.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.producer.Producer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@Slf4j
public class ProductRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final Producer producer;

    @Autowired
    public ProductRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate, Producer producer) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.producer = producer;
    }


    public List<String> validateProductId(List<String> productIds) {
        String query = String.format("SELECT id FROM product WHERE id IN (:productIds) AND isDeleted = false fetch first %s rows only", productIds.size());
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("productIds", productIds);
        return namedParameterJdbcTemplate.queryForObject(query, paramMap, (resultSet, rowNumber) -> {
            List<String> validProductIds = new ArrayList<>();
            validProductIds.add(resultSet.getString(1));
            while (resultSet.next()) {
                validProductIds.add(resultSet.getString(1));
            }
            return validProductIds;
        });
    }
}
