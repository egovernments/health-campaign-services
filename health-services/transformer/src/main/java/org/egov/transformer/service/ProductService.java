package org.egov.transformer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.product.Product;
import org.egov.common.models.product.ProductSearch;
import org.egov.common.models.product.ProductSearchRequest;
import org.egov.common.models.transformer.upstream.Boundary;
import org.egov.transformer.boundary.BoundaryTree;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.apache.commons.lang3.exception.ExceptionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
@Service
@Slf4j
public class ProductService {

    private final TransformerProperties properties;

    private final ServiceRequestClient serviceRequestClient;

    private static final Map<String, String> productMap = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public ProductService(TransformerProperties stockConfiguration, ServiceRequestClient serviceRequestClient, ObjectMapper objectMapper) {
        this.properties = stockConfiguration;
        this.serviceRequestClient = serviceRequestClient;
        this.objectMapper = objectMapper;
    }

    public void updateProductsInCache(List<Product> products) {
        products.forEach(product -> productMap.put(product.getId(), product.getName()));
    }

    public String findProductById(String productId, String tenantId) {
        if (productMap.containsKey(productId)) {
            return productMap.get(productId);
        } else {
            ProductSearchRequest productSearchRequest = ProductSearchRequest.builder()
                    .product(ProductSearch.builder().id(Collections.singletonList(productId)).build())
                    .requestInfo(RequestInfo.builder().
                            userInfo(User.builder()
                                    .uuid("transformer-uuid")
                                    .build())
                            .build())
                    .build();

            try {
                JsonNode response = serviceRequestClient.fetchResult(
                        new StringBuilder(properties.getProductHost()
                                + properties.getProductSearchUrl()
                                + "?limit=1"
                                + "&offset=0&tenantId=" + tenantId),
                        productSearchRequest,
                        JsonNode.class);
                List<Product> products = Arrays.asList(objectMapper.convertValue(response.get("Products"), Product[].class));
                updateProductsInCache(products);
                return products.isEmpty() ? null : products.get(0).getName();
            } catch (Exception e) {
                log.error("error while fetching product {}", ExceptionUtils.getStackTrace(e));
                return null;
            }
        }
    }
}
