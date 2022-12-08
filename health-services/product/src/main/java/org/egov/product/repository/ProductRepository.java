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
    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public ProductRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate, RedisTemplate<String, Object> redisTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

//    private List<String> checkInDb(List<Product> productList){
//        Map<String, Object> paramMap = new HashMap<>();
//        paramMap.put("productIds", productList.stream().map((Product p) -> p.getId()).collect(Collectors.toList()));
//        String query = String.format("SELECT id FROM PRODUCT WHERE id IN (:productIds) fetch first %s rows only", productList.size());
//        return namedParameterJdbcTemplate.queryForList(query, paramMap, String.class);
//    }

    public List<String> validateProductId(List<String> ids){
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("productIds", ids);
        String query = String.format("SELECT id FROM PRODUCT WHERE id IN (:productIds) fetch first %s rows only", ids.size());
        return namedParameterJdbcTemplate.queryForList(query, paramMap, String.class);
    }

//    public List<String> validate(List<Product> productList){
//        List<String> ids = productList.stream().map((Product p) -> p.getId()).collect(Collectors.toList());
//        List<String> foundInCache = checkInCache(ids);
//
//        List<String> toFindInDB = ids.stream().filter(((String id) -> !foundInCache.contains(id))).collect(Collectors.toList());
//        List<String> foundInDB = checkInDbIds(toFindInDB);
//        foundInDB.addAll(foundInCache);
//
//        return foundInDB;
//    }


//    public List<String> checkInCache(List<String> ids){
//        return (List<String>) ids.stream().filter((String id) -> redisTemplate.hasKey(id));
//    }
//
//    public List<String> checkIfExist(List<Product> productList){
//        List<String> ids = new ArrayList<>();
//
//    }
//
//    public void cache(List<Product> productList){
//        for(Product product: productList){
//           redisTemplate.opsForHash().put("PRODUCT", product.getId(), product);
//        }
//    }
}

