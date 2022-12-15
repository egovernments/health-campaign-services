package org.egov.product.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.producer.Producer;
import org.egov.product.repository.rowmapper.ProductRowMapper;
import org.egov.product.web.models.Product;
import org.egov.product.web.models.ProductSearch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@Slf4j
public class ProductRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final Producer producer;

    //private final RedisTemplate<String, Object> redisTemplate;

    private final SelectQueryBuilder selectQueryBuilder;

    private final String HASH_KEY = "product";

    @Autowired
    public ProductRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate, SelectQueryBuilder selectQueryBuilder) {
        this.producer = producer;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        //this.redisTemplate = redisTemplate;
        this.selectQueryBuilder = selectQueryBuilder;
    }

    public List<String> validateProductId(List<String> ids) {
//        List<String> productIds = ids.stream().filter(id -> redisTemplate.opsForHash()
//                .entries(HASH_KEY).containsKey(id))
//                .collect(Collectors.toList());
//        if (!productIds.isEmpty()) {
//            return productIds;
//        }
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("productIds", ids);
        String query = String.format("SELECT id FROM PRODUCT WHERE id IN (:productIds) AND isDeleted = false fetch first %s rows only", ids.size());
        return namedParameterJdbcTemplate.queryForList(query, paramMap, String.class);
    }

    public List<String> validateAllProductId(List<String> ids){
//        List<String> productIds = ids.stream().filter(id -> redisTemplate.opsForHash()
//                        .entries(HASH_KEY).containsKey(id))
//                .collect(Collectors.toList());
        //Ids to find in DB
        //ids = ids.stream().filter(id -> !productIds.contains(id)).collect(Collectors.toList());
        List<String> productIds = new ArrayList<>();
        if(!ids.isEmpty()){
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("productIds", ids);
            String query = String.format("SELECT id FROM PRODUCT WHERE id IN (:productIds) AND isDeleted = false fetch first %s rows only", ids.size());
            productIds.addAll(namedParameterJdbcTemplate.queryForList(query, paramMap, String.class));
        }
        return productIds;
    }

    public List<Product> save(List<Product> products, String topic) {
        producer.push(topic, products);
        log.info("Pushed to kafka");
        Map<String, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId,
                                product -> product));
        //redisTemplate.opsForHash().putAll(HASH_KEY, productMap);
        return products;
    }

    public List<Product> find(ProductSearch productSearch,
                              Integer limit,
                              Integer offset,
                              String tenantId,
                              Long lastChangedSince,
                              Boolean includeDeleted) throws QueryBuilderException {
        String query = selectQueryBuilder.build(productSearch);
        query += " and tenantId=:tenantId ";
        if (!includeDeleted) {
            query += "and isDeleted=:isDeleted ";
        }
        if(lastChangedSince != null){
            query += "and lastModifiedTime>=:lastModifiedTime ";
        }
        query += "LIMIT :limit OFFSET :offset";
        Map<String, Object> paramsMap = selectQueryBuilder.getParamsMap();
        paramsMap.put("tenantId", tenantId);
        paramsMap.put("isDeleted", includeDeleted);
        paramsMap.put("lastModifiedTime", lastChangedSince);
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);
        List<Product> products = namedParameterJdbcTemplate.query(query, paramsMap, new ProductRowMapper());
        return products;
    }
}

