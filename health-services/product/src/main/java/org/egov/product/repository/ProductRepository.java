package org.egov.product.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.product.web.models.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Hashtable;

@Repository
@Slf4j
public class ProductRepository {

//    @Autowired
//    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
//
//    public Product getProductById(String id){
//        String query = "SELECT * FROM PRODUCT WHERE id:=id";
//        Hashtable paramsMap = new Hashtable();
//        paramsMap.put("id", id);
//        return namedParameterJdbcTemplate.queryForObject(query, paramsMap, Product.class);
//    }
}
