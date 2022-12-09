package org.egov.product.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.product.enrichment.ProductEnrichment;
import org.egov.product.repository.ProductRepository;
import org.egov.product.web.models.Product;
import org.egov.product.web.models.ProductRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;

    private final ProductEnrichment productEnrichment;

    @Autowired
    public ProductService(ProductRepository productRepository, ProductEnrichment productEnrichment) {
        this.productRepository = productRepository;
        this.productEnrichment = productEnrichment;
    }

    public List<String> validateProductId(List<String> productIds) {
        return productRepository.validateProductId(productIds);
    }

    public List<Product> create(ProductRequest productRequest) throws Exception {
        List<String> productIds = productRequest.getProduct().stream()
                .map(Product::getId)
                .collect(Collectors.toList());
        log.info("Checking if already exists");
        List<String> inValidProductIds = productRepository.validateProductId(productIds);
        if(!inValidProductIds.isEmpty()) {
            log.info("Products {} already present in DB", inValidProductIds);
            throw new CustomException("PRODUCT_ALREADY_EXISTS", inValidProductIds.toString());
        }
        log.info("Enrichment products started");
        productRequest = productEnrichment.enrichProduct(productRequest);
        productRepository.save(productRequest, "health-product-topic");
        return productRequest.getProduct();
    }
}
