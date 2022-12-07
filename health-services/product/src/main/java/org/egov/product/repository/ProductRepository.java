package org.egov.product.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@Slf4j
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
        try {
            return namedParameterJdbcTemplate.queryForObject(query, paramMap, (resultSet, rowNumber) -> {
                List<String> validProductIds = new ArrayList<>();
                validProductIds.add(resultSet.getString(1));
                while (resultSet.next()) {
                    validProductIds.add(resultSet.getString(1));
                }
                return validProductIds;
            });
        } catch (EmptyResultDataAccessException e) {
            return Collections.emptyList();
        }
    }
}
