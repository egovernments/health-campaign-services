package org.egov.product.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.product.web.models.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
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

    public boolean checkIfExist(Product product){
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("productId", product.getId());
        String query = "SELECT count(*) FROM PRODUCT WHERE id=:productId";
        return namedParameterJdbcTemplate.queryForObject(query, paramMap, Integer.class) > 0;
    }
}

