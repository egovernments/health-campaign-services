package org.egov.project.validator.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.common.models.product.ProductVariant;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.ProjectRepository;
import org.egov.project.web.models.mdmsv2.Mdms;
import org.egov.project.web.models.mdmsv2.MdmsCriteriaReqV2;
import org.egov.project.web.models.mdmsv2.MdmsCriteriaV2;
import org.egov.project.web.models.mdmsv2.MdmsResponseV2;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
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
import static org.egov.project.config.ServiceConstants.*;

@Component
@Order(value = 8)
@Slf4j
public class PtProductVariantIdValidator implements Validator<TaskBulkRequest, Task> {

    private final RestTemplate restTemplate;

    private final ObjectMapper mapper;

    private final ProjectConfiguration projectConfiguration;

    @Autowired
    public PtProductVariantIdValidator(RestTemplate restTemplate, @Qualifier("objectMapper") ObjectMapper mapper, ProjectRepository projectRepository, ServiceRequestClient serviceRequestClient, ProjectConfiguration projectConfiguration) {
        this.restTemplate = restTemplate;
        this.mapper = mapper;
        this.projectConfiguration = projectConfiguration;
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
        StringBuilder uri = new StringBuilder();
        uri.append(projectConfiguration.getMdmsHost()).append(projectConfiguration.getMdmsEndPoint());
        MdmsCriteriaReqV2 mdmsCriteriaReq = getMdmsRequest(requestInfo, tenantId, productVariantIds, productVariantIds.size());
        Object mdmsResponseMap  = new HashMap<>();
        MdmsResponseV2 mdmsResponse = new MdmsResponseV2();
        try {
            mdmsResponseMap  = restTemplate.postForObject(uri.toString(), mdmsCriteriaReq, Map.class);
            mdmsResponse = mapper.convertValue(mdmsResponseMap , MdmsResponseV2.class);
        } catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_FROM_MDMS, e);
        }
//
        List <Mdms> result = mdmsResponse.getMdms();
        if (result == null || ObjectUtils.isEmpty(result)) {
            log.error(NO_MDMS_DATA_FOUND_FOR_GIVEN_PARAMETERS_MESSAGE + " - " + tenantId);
            throw new CustomException(NO_MDMS_DATA_FOUND_FOR_GIVEN_PARAMETERS, NO_MDMS_DATA_FOUND_FOR_GIVEN_PARAMETERS_MESSAGE);
        }
        log.info(result.toString());

        List<ProductVariant> productVariants = new ArrayList<>();

        result.forEach(data -> {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                // Convert JsonNode to ProductVariant object
                ProductVariant productVariant = objectMapper.treeToValue(data.getData(), ProductVariant.class);
                productVariants.add(productVariant);
            } catch (Exception e) {
                log.error(e.getMessage());
                throw new CustomException(JSONPATH_ERROR_CODE, JSONPATH_ERROR_MESSAGE);
            }
        });

        return productVariants;
    }

    public MdmsCriteriaReqV2 getMdmsRequest(RequestInfo requestInfo, String tenantId, List <String> productVarientIds, Integer limit) {

        MdmsCriteriaV2 mdmsCriteria = getProductVariantsMdmsCriteria(tenantId, productVarientIds, limit);

        return MdmsCriteriaReqV2.builder().mdmsCriteria(mdmsCriteria).requestInfo(requestInfo).build();
    }

    private MdmsCriteriaV2 getProductVariantsMdmsCriteria(String tenantId,List <String> productVarientids, Integer limit) {
        final String schemaCode = MDMS_PRODUCT_VARIANT_MODULE_NAME + DOT_SEPARATOR + MDMS_PRODUCT_VARIANT_MASTER_NAME;

        return MdmsCriteriaV2.builder().tenantId(tenantId).schemaCode(schemaCode).uniqueIdentifiers(productVarientids).limit(limit).build();
    }
}

