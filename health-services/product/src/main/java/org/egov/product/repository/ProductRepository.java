package org.egov.product.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.producer.Producer;
import org.egov.product.repository.rowmapper.ProductRowMapper;
import org.egov.product.web.models.Product;
import org.egov.product.web.models.ProductSearch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Repository
@Slf4j
public class ProductRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final Producer producer;

    private final RedisTemplate<String, Object> redisTemplate;

    private final SelectQueryBuilder selectQueryBuilder;

    private final String HASH_KEY = "product";

    @Value("${spring.cache.redis.time-to-live:60}")
    private String timeToLive;

    @Autowired
    public ProductRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                             RedisTemplate<String, Object> redisTemplate, SelectQueryBuilder selectQueryBuilder) {
        this.producer = producer;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.selectQueryBuilder = selectQueryBuilder;
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
        String query = String.format("SELECT id FROM PRODUCT WHERE id IN (:productIds) AND isDeleted = false fetch first %s rows only",
                ids.size());
        return namedParameterJdbcTemplate.queryForList(query, paramMap, String.class);
    }

    public List<Product> findById(List<String> ids) {
        ArrayList<Product> productsFound = new ArrayList<>();
        Collection<Object> collection = new ArrayList<>(ids);
        List<Object> products = redisTemplate.opsForHash()
                .multiGet(HASH_KEY, collection);
        if (!products.isEmpty() && !products.contains(null)) {
            log.info("Cache hit");
            productsFound = (ArrayList<Product>) products.stream().map(Product.class::cast)
                    .collect(Collectors.toList());
            // return only if all the variants are found in cache
            ids.removeAll(productsFound.stream().map(Product::getId).collect(Collectors.toList()));
            if (ids.isEmpty()) {
                return productsFound;
            }
        }

        String query = String.format("SELECT * FROM PRODUCT WHERE id IN (:productIds) AND isDeleted = false fetch first %s rows only",
                ids.size());
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("productIds", ids);

        productsFound.addAll(namedParameterJdbcTemplate.query(query, paramMap, new ProductRowMapper()));
        putInCache(productsFound);
        return productsFound;
    }

    public List<Product> save(List<Product> products, String topic) {
        producer.push(topic, products);
        log.info("Pushed to kafka");
        putInCache(products);
        return products;
    }

    private void putInCache(List<Product> products) {
        Map<String, Product> productMap = products.stream()
                .collect(Collectors
                        .toMap(Product::getId,
                                product -> product));
        redisTemplate.opsForHash().putAll(HASH_KEY, productMap);
        redisTemplate.expire(HASH_KEY, Long.parseLong(timeToLive), TimeUnit.SECONDS);
    }

    public List<Product> find(ProductSearch productSearch,
                              Integer limit,
                              Integer offset,
                              String tenantId,
                              Long lastChangedSince,
                              Boolean includeDeleted) throws QueryBuilderException {
        String hashcode = String.valueOf(productSearch.toString().hashCode());
        if(redisTemplate.opsForHash().get(HASH_KEY, hashcode) != null){
            log.info("cache hit - search");
            return (List<Product>) redisTemplate.opsForHash().get(HASH_KEY, hashcode);
        }
        String query = selectQueryBuilder.build(productSearch);
        query += " and tenantId=:tenantId ";
        if (!includeDeleted) {
            query += "and isDeleted=:isDeleted ";
        }
        if (lastChangedSince != null) {
            query += "and lastModifiedTime>=:lastModifiedTime ";
        }
        query += "ORDER BY id ASC LIMIT :limit OFFSET :offset";
        Map<String, Object> paramsMap = selectQueryBuilder.getParamsMap();
        paramsMap.put("tenantId", tenantId);
        paramsMap.put("isDeleted", includeDeleted);
        paramsMap.put("lastModifiedTime", lastChangedSince);
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);
        List<Product> products = namedParameterJdbcTemplate.query(query, paramsMap, new ProductRowMapper());
        if(productSearch.getId() != null && productSearch.getName() == null
                && productSearch.getManufacturer() == null && productSearch.getType() == null && !products.isEmpty()){
            redisTemplate.opsForHash().put(HASH_KEY, hashcode, products);
        }
        return products;
    }
}

