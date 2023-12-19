package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.product.*;
import org.egov.tracer.model.CustomException;
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

    public List<String> getProductVariantById(List<String> productVariantIds, String tenantId) {

        List<String> productNames = new ArrayList<>();

        productVariantIds.forEach(productVariantId -> {
            if (productVariantVsNameCache != null && productVariantVsNameCache.containsKey(productVariantId)) {
                log.info("Fetching Product Variant Name for the id: {} from transformer cache", productVariantId);
                productNames.add(productVariantVsNameCache.get(productVariantId));
            } else {
                productNames.add(getProductVariantNameById(productVariantId, tenantId));
            }
        });
        return productNames;
    }

    public String getProductVariantNameById(String productVariantId, String tenantId) {
        if (productVariantVsNameCache != null && productVariantVsNameCache.containsKey(productVariantId)) {
            log.info("Fetching Product Variant Name for the id: {} from transformer cache", productVariantId);
            return productVariantVsNameCache.get(productVariantId);
        }
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
                log.info("No Product variant Found with Id:  {}", productVariantId);
                return productVariantId;
            }
            productVariantVsNameCache.put(productVariantId, response.getProductVariant().get(0).getSku());
            return response.getProductVariant().get(0).getSku();
        } catch (Exception e) {
            log.error("error while fetching product variants in transformer {}", ExceptionUtils.getStackTrace(e));
            return productVariantId;
        }
    }
}
