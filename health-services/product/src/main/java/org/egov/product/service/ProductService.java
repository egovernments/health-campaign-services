package org.egov.product.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.product.enrichment.ProductEnrichment;
import org.egov.product.repository.ProductRepository;
import org.egov.product.web.models.Product;
import org.egov.product.web.models.ProductRequest;
import org.egov.product.web.models.ProductSearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        if (!inValidProductIds.isEmpty()) {
            log.info("Products {} already present in DB", inValidProductIds);
            throw new CustomException("PRODUCT_ALREADY_EXISTS", inValidProductIds.toString());
        }
        log.info("Enrichment products started");
        productRequest = productEnrichment.enrichProduct(productRequest);
        productRepository.save(productRequest.getProduct(), "save-product-topic");
        return productRequest.getProduct();
    }

    public List<Product> update(ProductRequest productRequest) throws Exception {
        Map<String, Product> productMap =
                productRequest.getProduct().stream().collect(Collectors.toMap(Product::getId, item -> item));
        List<String> productIds = new ArrayList<>(productMap.keySet());

        log.info("Checking if already exists");
        List<Product> validProducts = productRepository.findById(productIds);
        if (validProducts.size() != productIds.size()) {
            List<Product> invalidProducts = new ArrayList<>(productRequest.getProduct());
            invalidProducts.removeAll(validProducts);
            throw new CustomException("INVALID_PRODUCT", invalidProducts.toString());
        }
        for (Product validProduct : validProducts) {
            if (validProduct.getRowVersion() != productMap.get(validProduct.getId()).getRowVersion()) {
                throw new CustomException("ROW_VERSION_MISMATCH", "Row version is not same");
            }
        }

        productRequest = productEnrichment.enrichUpdateProduct(productRequest);
        productRepository.save(productRequest.getProduct(), "update-product-topic");
        return productRequest.getProduct();
    }

    public List<Product> search(ProductSearchRequest productSearchRequest,
                                Integer limit,
                                Integer offset,
                                String tenantId,
                                Long lastChangedSince,
                                Boolean includeDeleted) throws Exception{
        List<Product> products = productRepository.find(productSearchRequest.getProduct(), limit, offset, tenantId, lastChangedSince, includeDeleted);
        if (products.isEmpty()) {
            throw new CustomException("NO_RESULT", "No products found for the given search criteria");
        }
        return products;
    }
}
