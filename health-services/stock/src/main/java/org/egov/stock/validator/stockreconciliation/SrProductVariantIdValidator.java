package org.egov.stock.validator.stockreconciliation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import digit.models.coremodels.mdms.MasterDetail;
import digit.models.coremodels.mdms.MdmsCriteria;
import digit.models.coremodels.mdms.MdmsCriteriaReq;
import digit.models.coremodels.mdms.MdmsResponse;
import digit.models.coremodels.mdms.ModuleDetail;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.common.models.product.ProductVariant;
import org.egov.common.models.product.ProductVariantSearch;
import org.egov.common.models.product.ProductVariantSearchRequest;
import org.egov.common.models.stock.StockReconciliation;
import org.egov.common.models.stock.StockReconciliationBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.stock.config.StockConfiguration;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.Collections;
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
import static org.egov.stock.Constants.ERROR_WHILE_FETCHING_FROM_MDMS;
import static org.egov.stock.Constants.GET_PRODUCT_VARIANT_ID;
import static org.egov.stock.Constants.MDMS_PRODUCT_VARIANT_MASTER_NAME;
import static org.egov.stock.Constants.MDMS_PRODUCT_VARIANT_MODULE_NAME;
import static org.egov.stock.Constants.NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_CODE;
import static org.egov.stock.Constants.NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_MESSAGE;

@Component
@Slf4j
@Order(5)
public class SrProductVariantIdValidator implements Validator<StockReconciliationBulkRequest, StockReconciliation> {

    private final ServiceRequestClient serviceRequestClient;

    private final StockConfiguration stockConfiguration;

    private final ObjectMapper mapper;

    @Autowired
    public SrProductVariantIdValidator(ServiceRequestClient serviceRequestClient, StockConfiguration stockConfiguration, ObjectMapper mapper) {
        this.serviceRequestClient = serviceRequestClient;
        this.stockConfiguration = stockConfiguration;
        this.mapper = mapper;
    }


    @Override
    public Map<StockReconciliation, List<Error>> validate(StockReconciliationBulkRequest request) {
        Map<StockReconciliation, List<Error>> errorDetailsMap = new HashMap<>();
        log.info("validating stock reconciliation product variant id");
        List<StockReconciliation> entities = request.getStockReconciliation().stream().filter(notHavingErrors())
                .collect(Collectors.toList());
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

        StringBuilder url = new StringBuilder(stockConfiguration.getMdmsHost()
                + stockConfiguration.getMdmsSearchEndpoint());
        MdmsCriteriaReq mdmsCriteriaReq = getMdmsRequest(requestInfo, tenantId, productVariantIds);
        MdmsResponse mdmsResponse = new MdmsResponse();
        Object result;
        try {
            mdmsResponse = serviceRequestClient.fetchResult(url, mdmsCriteriaReq, MdmsResponse.class);
            result = mdmsResponse.getMdmsRes();
            if (result == null || ObjectUtils.isEmpty(result)) {
                log.error(NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_MESSAGE + " - " + tenantId);
                throw new CustomException(NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_CODE, NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_MESSAGE);
            }
        } catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_FROM_MDMS, e);
            throw new CustomException("PRODUCT_VARIANT",
                    String.format(ERROR_WHILE_FETCHING_FROM_MDMS + ": %s", e.getMessage()));
        }
        log.info("stock reconciliation product variant exist validation completed successfully");

        List<ProductVariant> productVariants = Collections.emptyList();
        try {
            log.debug("Parsing product variants from MDMS response");
            Object jsonArray = JsonPath.read(result, "$.HCM-Product.ProductVariants");
            // Convert JSON string to List<Product>
            productVariants = mapper.convertValue(jsonArray, new TypeReference<List<ProductVariant>>() {});
            log.debug("Successfully parsed {} product variants from MDMS response", productVariants.size());
        } catch (Exception e) {
            log.error("Error while converting MDMS response to ProductVariant list", e);
            throw new CustomException("PRODUCT_VARIANT_CONVERSION_ERROR",
                    "Failed to convert MDMS response to ProductVariant list: " + e.getMessage());
        }

        return productVariants;
    }

    private MdmsCriteriaReq getMdmsRequest(RequestInfo requestInfo, String tenantId, List<String> productVariantIds) {
        String filter = "[?(@.id in ['" + String.join("', '", productVariantIds) + "'])]";
        MasterDetail productMaster = MasterDetail.builder().name(MDMS_PRODUCT_VARIANT_MASTER_NAME).filter(filter).build();
        ModuleDetail moduleDetail = ModuleDetail.builder()
                .moduleName(MDMS_PRODUCT_VARIANT_MODULE_NAME)
                .masterDetails(Collections.singletonList(productMaster))
                .build();
        MdmsCriteria mdmsCriteria = MdmsCriteria.builder().moduleDetails(Collections.singletonList(moduleDetail)).tenantId(tenantId).build();

        return MdmsCriteriaReq.builder().mdmsCriteria(mdmsCriteria).requestInfo(requestInfo).build();
    }

}
