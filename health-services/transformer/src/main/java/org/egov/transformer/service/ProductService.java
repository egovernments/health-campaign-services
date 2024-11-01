package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.product.*;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;


@Component
@Slf4j
public class ProductService {

    private final TransformerProperties transformerProperties;

    private final ServiceRequestClient serviceRequestClient;

    private static HashMap<String, String> productVariantVsNameCache = new HashMap<>();


    public ProductService(TransformerProperties transformerProperties, ServiceRequestClient serviceRequestClient) {
        this.transformerProperties = transformerProperties;
        this.serviceRequestClient = serviceRequestClient;
    }

    public List<String> getProductVariantNames(List<String> productVariantIds, String tenantId) {

        List<String> productNames = new ArrayList<>();

        productVariantIds.forEach(productVariantId -> {
            if (productVariantVsNameCache != null && productVariantVsNameCache.containsKey(productVariantId)) {
                log.info("Fetching Product Variant Name for the id: {} from transformer cache", productVariantId);
                productNames.add(productVariantVsNameCache.get(productVariantId));
            } else {
                productNames.add(fetchProductVariantNameFromService(productVariantId, tenantId));
            }
        });
        return productNames;
    }

    public String fetchProductVariantNameFromService(String productVariantId, String tenantId) {
        ProductVariantSearchRequest request = ProductVariantSearchRequest.builder()
                .requestInfo(RequestInfo.builder().
                        userInfo(User.builder()
                                .uuid("transformer-uuid")
                                .build())
                        .build())
                .productVariant(ProductVariantSearch.builder().id(Collections.singletonList(productVariantId)).build())
                .build();

        ProductVariantResponse response;
        try {
            StringBuilder uri = new StringBuilder();
            uri.append(transformerProperties.getProductHost())
                    .append(transformerProperties.getProductVariantSearchUrl())
                    .append("?limit=").append(transformerProperties.getSearchApiLimit())
                    .append("&offset=0")
                    .append("&tenantId=").append(tenantId);
            response = serviceRequestClient.fetchResult(uri,
                    request,
                    ProductVariantResponse.class);
            if (response.getProductVariant().isEmpty()) {
                log.info("No Product variant Found with Id: {}, returning variantId", productVariantId);
                return productVariantId;
            }
            String sku = response.getProductVariant().get(0).getSku();
            productVariantVsNameCache.put(productVariantId, sku);
            return sku;
        } catch (Exception e) {
            log.info("PRODUCT_VARIANT_FETCH_ERROR in transformer: {}", ExceptionUtils.getStackTrace(e));
            return productVariantId;
        }
    }
}
