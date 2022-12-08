package org.egov.product.repository;

import org.egov.common.producer.Producer;
import org.egov.product.web.models.ProductVariant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ProductVariantRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final Producer producer;

    @Autowired
    public ProductVariantRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate, Producer producer) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.producer = producer;
    }

    public List<ProductVariant> save(List<ProductVariant> productVariants) {
        producer.push("save-product-variant-topic", productVariants);
        return productVariants;
    }
}
