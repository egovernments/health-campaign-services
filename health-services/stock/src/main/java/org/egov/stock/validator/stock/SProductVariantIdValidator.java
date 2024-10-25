package org.egov.stock.validator.stock;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.common.models.product.ProductVariant;
import org.egov.common.models.product.ProductVariantResponse;
import org.egov.common.models.product.ProductVariantSearch;
import org.egov.common.models.product.ProductVariantSearchRequest;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.stock.config.StockConfiguration;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForEntityWithNetworkError;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentRelatedEntity;
import static org.egov.stock.Constants.*;

@Component
@Slf4j
@Order(5)
public class SProductVariantIdValidator implements Validator<StockBulkRequest, Stock> {

    private final ServiceRequestClient serviceRequestClient;

    private final StockConfiguration stockConfiguration;

    public SProductVariantIdValidator(ServiceRequestClient serviceRequestClient, StockConfiguration stockConfiguration) {
        this.serviceRequestClient = serviceRequestClient;
        this.stockConfiguration = stockConfiguration;
    }

    @Override
    public Map<Stock, List<Error>> validate(StockBulkRequest request) {
        Map<Stock, List<Error>> errorDetailsMap = new HashMap<>();
        log.info("validating stock product variant id");
        List<Stock> entities = request.getStock().stream().filter(notHavingErrors())
                .collect(Collectors.toList());
        if (!entities.isEmpty()) {
            Set<String> productVariantIds = entities.stream().map(Stock::getProductVariantId).collect(Collectors.toSet());
            Map<String, Stock> pvMap = getIdToObjMap(entities, getMethod(GET_PRODUCT_VARIANT_ID, getObjClass(entities)));
            try {
                List<String> validProductVariantsIds = checkIfProductVariantExist(productVariantIds,
                        getTenantId(entities),
                        request.getRequestInfo()).stream().map(ProductVariant::getId).collect(Collectors.toList());
                productVariantIds.forEach(id -> {
                    if (!validProductVariantsIds.contains(id)) {
                        Error error = getErrorForNonExistentRelatedEntity(id);
                        log.info("validation failed for stock product variant id: {} with error {}", entities, error);
                        populateErrorDetails(pvMap.get(id), error, errorDetailsMap);
                    }
                });
            } catch (Exception exception) {
                Error error = getErrorForEntityWithNetworkError();
                entities.forEach(entity -> populateErrorDetails(entity, error, errorDetailsMap));
            }
        }

        log.info("stock product variant id validation completed successfully, total error: "+errorDetailsMap.size());
        return errorDetailsMap;
    }

    private List<ProductVariant> checkIfProductVariantExist(Set<String> pvIds, String tenantId, RequestInfo requestInfo) {

        List<String> productVariantIds = new ArrayList<>(pvIds);
        log.info("validating if stock product variant exist");
        ProductVariantSearch productVariantSearch = ProductVariantSearch.builder()
                .id(productVariantIds).build();
        ProductVariantSearchRequest request = ProductVariantSearchRequest.builder().productVariant(productVariantSearch)
                .requestInfo(requestInfo).build();
        StringBuilder url = new StringBuilder(stockConfiguration.getProductHost()
                + stockConfiguration.getProductVariantSearchUrl()
                + "?limit=" + productVariantIds.size() + "&offset=0&tenantId=" + tenantId);
        ProductVariantResponse response;
        try {
            response = serviceRequestClient.fetchResult(url, request, ProductVariantResponse.class);
        } catch (Exception e) {
            throw new CustomException(S_PRODUCT_VARIANT_ID_VALIDATION_ERROR,
                    String.format("Something went wrong: %s", e.getMessage()));
        }
        log.info("stock product variant exist validation completed successfully");
        return response.getProductVariant();
    }

}
