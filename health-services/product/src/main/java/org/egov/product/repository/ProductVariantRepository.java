package org.egov.product.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.producer.Producer;
import org.egov.product.repository.rowmapper.ProductVariantRowMapper;
import org.egov.product.web.models.ProductVariant;
import org.egov.product.web.models.ProductVariantSearch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@Slf4j
public class ProductVariantRepository {

    private final Producer producer;

    private final RedisTemplate<String, Object> redisTemplate;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final SelectQueryBuilder selectQueryBuilder;

    private static final String HASH_KEY = "product-variant";

    @Autowired
    public ProductVariantRepository(Producer producer, RedisTemplate<String, Object> redisTemplate,
                                    NamedParameterJdbcTemplate namedParameterJdbcTemplate, SelectQueryBuilder selectQueryBuilder) {
        this.producer = producer;
        this.redisTemplate = redisTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.selectQueryBuilder = selectQueryBuilder;
    }

    public List<ProductVariant> save(List<ProductVariant> productVariants, String topic) {
        producer.push(topic, productVariants);
        putInCache(productVariants);
        return productVariants;
    }

    public List<ProductVariant> findById(List<String> ids) {
        Collection<Object> collection = new ArrayList<>(ids);
        ArrayList<ProductVariant> variantsFound = new ArrayList<>();
        List<Object> productVariants = redisTemplate.opsForHash()
                .multiGet(HASH_KEY, collection);
        if (!productVariants.isEmpty() && !productVariants.contains(null)) {
            log.info("Cache hit");
            variantsFound = (ArrayList<ProductVariant>) productVariants.stream().map(ProductVariant.class::cast)
                    .collect(Collectors.toList());
            // return only if all the variants are found in cache
            ids.removeAll(variantsFound.stream().map(ProductVariant::getId).collect(Collectors.toList()));
            if (ids.isEmpty()) {
                return variantsFound;
            }
        }
        String query = "SELECT * FROM product_variant WHERE id IN (:ids) and isDeleted = false";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("ids", ids);

        //Not storing in cache, because we save product variants via save function. so it's duplicate.
        variantsFound.addAll(namedParameterJdbcTemplate.query(query, paramMap, new ProductVariantRowMapper()));
        return variantsFound;
    }

    private void putInCache(List<ProductVariant> productVariants) {
        Map<String, ProductVariant> productVariantMap = productVariants.stream()
                .collect(Collectors
                        .toMap(ProductVariant::getId,
                                productVariant -> productVariant));
        redisTemplate.opsForHash().putAll(HASH_KEY, productVariantMap);
    }

    public List<ProductVariant> find(ProductVariantSearch productVariantSearch,
                                     Integer limit,
                                     Integer offset,
                                     String tenantId,
                                     Long lastChangedSince,
                                     Boolean includeDeleted) throws QueryBuilderException {
        String query = selectQueryBuilder.build(productVariantSearch);
        query += " AND tenantId = :tenantId ";
        if (!includeDeleted) {
            query += " AND isDeleted = :isDeleted ";
        }
        if (lastChangedSince != null) {
            query += " AND lastModifiedTime >= :lastModifiedTime ";
        }
        query += "ORDER BY id ASC LIMIT :limit OFFSET :offset";
        Map<String, Object> paramsMap = selectQueryBuilder.getParamsMap();
        paramsMap.put("tenantId", tenantId);
        paramsMap.put("isDeleted", includeDeleted);
        paramsMap.put("lastModifiedTime", lastChangedSince);
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);
        return namedParameterJdbcTemplate.query(query, paramsMap, new ProductVariantRowMapper());
    }
}
