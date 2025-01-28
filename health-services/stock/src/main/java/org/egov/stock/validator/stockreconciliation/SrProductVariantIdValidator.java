package org.egov.stock.validator.stockreconciliation;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.common.models.product.ProductVariant;
import org.egov.common.models.product.ProductVariantResponse;
import org.egov.common.models.product.ProductVariantSearch;
import org.egov.common.models.product.ProductVariantSearchRequest;
import org.egov.common.models.stock.StockReconciliation;
import org.egov.common.models.stock.StockReconciliationBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.stock.config.StockReconciliationConfiguration;
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
public class SrProductVariantIdValidator implements Validator<StockReconciliationBulkRequest, StockReconciliation> {

    private final ServiceRequestClient serviceRequestClient;

    private final StockReconciliationConfiguration stockReconciliationConfiguration;

    public SrProductVariantIdValidator(ServiceRequestClient serviceRequestClient, StockReconciliationConfiguration stockReconciliationConfiguration) {
        this.serviceRequestClient = serviceRequestClient;
        this.stockReconciliationConfiguration = stockReconciliationConfiguration;
    }


    @Override
    public Map<StockReconciliation, List<Error>> validate(StockReconciliationBulkRequest request) {
        Map<StockReconciliation, List<Error>> errorDetailsMap = new HashMap<>();
        log.info("validating stock reconciliation product variant id");
        List<StockReconciliation> entities = request.getStockReconciliation();
        if (!entities.isEmpty()) {
            Set<String> productVariantIds = entities.stream().map(StockReconciliation::getProductVariantId).collect(Collectors.toSet());
            Map<String, StockReconciliation> pvMap = getIdToObjMap(entities, getMethod(GET_PRODUCT_VARIANT_ID, getObjClass(entities)));
            try {
                List<String> validProductVariantsIds = checkIfProductVariantExist(productVariantIds,
                        getTenantId(entities),
                        request.getRequestInfo()).stream().map(ProductVariant::getId).collect(Collectors.toList());
                productVariantIds.forEach(id -> {
                    if (!validProductVariantsIds.contains(id)) {
                        Error error = getErrorForNonExistentRelatedEntity(id);
                        log.info("validation failed for stock reconciliation product variant id: {} with error {}", entities, error);
                        populateErrorDetails(pvMap.get(id), error, errorDetailsMap);
                    }
                });
            } catch (Exception exception) {
                Error error = getErrorForEntityWithNetworkError();
                entities.forEach(entity -> populateErrorDetails(entity, error, errorDetailsMap));
            }
        }

        log.info("stock reconciliation product variant id validation completed successfully, total errors " +errorDetailsMap.size());
        return errorDetailsMap;
    }

    private List<ProductVariant> checkIfProductVariantExist(Set<String> pvIds, String tenantId, RequestInfo requestInfo) {

        List<String> productVariantIds = new ArrayList<>(pvIds);
        log.info("validation if stock reconciliation product variant exist");
        ProductVariantSearch productVariantSearch = ProductVariantSearch.builder()
                .id(productVariantIds).build();
        ProductVariantSearchRequest request = ProductVariantSearchRequest.builder().productVariant(productVariantSearch)
                .requestInfo(requestInfo).build();
        StringBuilder url = new StringBuilder(stockReconciliationConfiguration.getProductHost()
                + stockReconciliationConfiguration.getProductVariantSearchUrl()
                + "?limit=" + productVariantIds.size() + "&offset=0&tenantId=" + tenantId);
        ProductVariantResponse response;
        try {
            response = serviceRequestClient.fetchResult(url, request, ProductVariantResponse.class);
        } catch (Exception e) {
            throw new CustomException(SR_PRODUCT_VARIANT_ID_VALIDATION_ERROR,
                    String.format("Something went wrong: %s", e.getMessage()));
        }
        log.info("stock reconciliation product variant exist validation completed successfully");
        return response.getProductVariant();
    }

}
