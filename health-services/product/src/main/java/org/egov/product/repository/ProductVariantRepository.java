package org.egov.product.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.common.producer.Producer;
import org.egov.product.web.models.AdditionalFields;
import org.egov.product.web.models.ProductVariant;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class ProductVariantRepository {

    private final Producer producer;

    private final RedisTemplate<String, Object> redisTemplate;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    public ProductVariantRepository(Producer producer, RedisTemplate<String, Object> redisTemplate,
                                    NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.producer = producer;
        this.redisTemplate = redisTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public List<ProductVariant> save(List<ProductVariant> productVariants, String topic) {
        producer.push(topic, productVariants);
        Map<String, ProductVariant> productVariantMap = productVariants.stream()
                .collect(Collectors
                        .toMap(ProductVariant::getId,
                                productVariant -> productVariant));
        redisTemplate.opsForHash().putAll("product-variant", productVariantMap);
        return productVariants;
    }

    public List<ProductVariant> findById(List<String> ids) {
        String query = "SELECT * FROM product_variant WHERE id IN (:ids) and isDeleted = false";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("ids", ids);
        return namedParameterJdbcTemplate.queryForObject(query, paramMap,
                ((resultSet, i) -> {
                    List<ProductVariant> pvList = new ArrayList<>();
                    try {
                        mapRow(resultSet, pvList);
                        while (resultSet.next()) {
                            mapRow(resultSet, pvList);
                        }
                    } catch (Exception e) {
                        throw new CustomException("ERROR_IN_SELECT", e.getMessage());
                    }
                    return pvList;
                }));
    }

    private void mapRow(ResultSet resultSet, List<ProductVariant> pvList) throws SQLException, JsonProcessingException {
        ProductVariant pv = ProductVariant.builder()
                .id(resultSet.getString("id"))
                .productId(resultSet.getString("productId"))
                .tenantId(resultSet.getString("tenantId"))
                .sku(resultSet.getString("sku"))
                .variation(resultSet.getString("variation"))
                .isDeleted(resultSet.getBoolean("isDeleted"))
                .rowVersion(resultSet.getInt("rowVersion"))
                .additionalFields(new ObjectMapper()
                        .readValue(resultSet.getString("additionalFields"),
                                AdditionalFields.class))
                .auditDetails(AuditDetails.builder()
                        .lastModifiedTime(resultSet.getLong("lastModifiedTime"))
                        .createdTime(resultSet.getLong("createdTime"))
                        .createdBy(resultSet.getString("createdBy"))
                        .lastModifiedBy(resultSet.getString("lastModifiedBy"))
                        .build())
                .build();
        pvList.add(pv);
    }
}
