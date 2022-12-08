package org.egov.product.repository;

import org.egov.common.producer.Producer;
import org.egov.product.web.models.ProductVariant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class ProductVariantRepository {

    private final Producer producer;

    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public ProductVariantRepository(Producer producer, RedisTemplate<String, Object> redisTemplate) {
        this.producer = producer;
        this.redisTemplate = redisTemplate;
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
}
