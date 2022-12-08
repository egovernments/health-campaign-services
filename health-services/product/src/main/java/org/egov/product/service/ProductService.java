package org.egov.product.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.producer.Producer;
import org.egov.common.service.IdGenService;
import org.egov.product.enrichment.ProductEnrichment;
import org.egov.product.repository.ProductRepository;
import org.egov.product.web.models.Product;
import org.egov.product.web.models.ProductRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Service
@Slf4j
public class ProductService {
    private final Producer producer;

    private ProductRepository productRepository;

    private ProductEnrichment productEnrichment;

    @Autowired
    public ProductService(Producer producer, ProductRepository productRepository, ProductEnrichment productEnrichment) {
        this.producer = producer;
        this.productRepository = productRepository;
        this.productEnrichment = productEnrichment;
    }

    public List<Product> create(ProductRequest productRequest) throws Exception {
        /*
        1. Get all product IDs from the request
        2. Check all the Ids in the DB and return Ids that are found in DB.
        3. If any IDs are returned from DB, that means those products are already in DB.
         */
        List<String> productIds = productRequest.getProduct().stream()
                .map(Product::getId)
                .collect(Collectors.toList());
        List<String> inValidProductIds = productRepository.validateProductId(productIds);
        if(inValidProductIds.size() > 0){
            log.info(String.format("PRODUCT with Ids: %s already present in DB", inValidProductIds.toString()));
            throw new CustomException("PRODUCT_ID_ALREADY_EXISTS", inValidProductIds.toString());
        }
        productEnrichment.enrichProduct(productRequest);
        producer.push("health-product-topic", productRequest);
        return productRequest.getProduct();
    }
}
