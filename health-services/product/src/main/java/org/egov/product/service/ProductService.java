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
        productRepository.save(productRequest.getProduct(), "save-product-topic");
        return productRequest.getProduct();
    }

    public List<Product> update(ProductRequest productRequest) throws Exception{
        /*
            Get products from DB or Cache.
            If there is mismatch, return error
            otherwise update
         */
        List<String> productIds = productRequest.getProduct().stream()
                .map(Product::getId).filter(p -> p!=null)
                .collect(Collectors.toList());

        if (productIds.isEmpty() || productIds.size() != productRequest.getProduct().size()) {
           throw new CustomException("PRODUCT_EMPTY", "Product IDs can not be null or empty");
        }

        List<String> validProductsIs = productRepository.validateAllProductId(productIds);
        if (validProductsIs.size() != productIds.size()) {
            throw new CustomException("PRODUCT_DOES_NOT_EXISTS", "Products does not exists");
        }

        productRequest = productEnrichment.enrichUpdateProduct(productRequest);
        productRepository.save(productRequest.getProduct(), "update-product-topic");

        return productRequest.getProduct();
    }
}
