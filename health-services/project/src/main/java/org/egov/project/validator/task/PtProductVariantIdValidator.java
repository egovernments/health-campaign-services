package org.egov.project.validator.task;

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
import org.egov.common.models.product.ProductVariantResponse;
import org.egov.common.models.product.ProductVariantSearch;
import org.egov.common.models.product.ProductVariantSearchRequest;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.ProjectRepository;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getDifference;
import static org.egov.common.utils.CommonUtils.getIdList;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForEntityWithNetworkError;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentRelatedEntity;
import static org.egov.project.Constants.ERROR_WHILE_FETCHING_FROM_MDMS;
import static org.egov.project.Constants.MDMS_PRODUCT_VARIANT_MASTER_NAME;
import static org.egov.project.Constants.MDMS_PRODUCT_VARIANT_MODULE_NAME;
import static org.egov.project.Constants.NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_CODE;
import static org.egov.project.Constants.NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_MESSAGE;

@Component
@Order(value = 8)
@Slf4j
public class PtProductVariantIdValidator implements Validator<TaskBulkRequest, Task> {

    private final ProjectRepository projectRepository;

    private final ServiceRequestClient serviceRequestClient;

    private final ProjectConfiguration projectConfiguration;

    private final ObjectMapper mapper;

    @Autowired
    public PtProductVariantIdValidator(ProjectRepository projectRepository, ServiceRequestClient serviceRequestClient, ProjectConfiguration projectConfiguration, ObjectMapper mapper) {
        this.projectRepository = projectRepository;
        this.serviceRequestClient = serviceRequestClient;
        this.projectConfiguration = projectConfiguration;
        this.mapper = mapper;
    }


    @Override
    public Map<Task, List<Error>> validate(TaskBulkRequest request) {
        log.info("validating for product variant id");
        Map<Task, List<Error>> errorDetailsMap = new HashMap<>();
        List<Task> entities = request.getTasks().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        if (!entities.isEmpty()) {
            for (Task task : entities) {
                if(CollectionUtils.isEmpty(task.getResources()))
                    continue;
                Set<String> productVariantIds = new HashSet<>(getIdList(task.getResources(),
                        getIdMethod(task.getResources(), "productVariantId")));
                try {
                    List<ProductVariant> validProductVariants = checkIfProductVariantExist(productVariantIds,
                            getTenantId(task.getResources()), request.getRequestInfo());
                    if (productVariantIds.size() != validProductVariants.size()) {
                        List<String> productVariantInRequest = new ArrayList<>();
                        productVariantInRequest.addAll(productVariantIds);
                        Error error;
                        if (validProductVariants.isEmpty()) {
                            error = getErrorForNonExistentRelatedEntity(productVariantInRequest);
                        } else {
                            error = getErrorForNonExistentRelatedEntity(getDifference(productVariantInRequest,
                                    getIdList(validProductVariants, getIdMethod(validProductVariants))));
                        }
                        populateErrorDetails(task, error, errorDetailsMap);
                    }
                } catch (Exception exception) {
                    Error error = getErrorForEntityWithNetworkError();
                    populateErrorDetails(task, error, errorDetailsMap);
                }
            }
        }

        return errorDetailsMap;
    }

    private List<ProductVariant> checkIfProductVariantExist(Set<String> pvIds, String tenantId, RequestInfo requestInfo) {

        List<String> productVariantIds = new ArrayList<>(pvIds);
        log.info("validation if stock reconciliation product variant exist");

        StringBuilder url = new StringBuilder(projectConfiguration.getMdmsHost()
                + projectConfiguration.getMdmsEndPoint());
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

