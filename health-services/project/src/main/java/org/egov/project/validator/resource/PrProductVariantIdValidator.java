package org.egov.project.validator.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.Error;
import org.egov.common.models.product.ProductVariant;
import org.egov.common.models.project.ProjectResource;
import org.egov.common.models.project.ProjectResourceBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.web.models.mdmsv2.Mdms;
import org.egov.project.web.models.mdmsv2.MdmsCriteriaReqV2;
import org.egov.project.web.models.mdmsv2.MdmsCriteriaV2;
import org.egov.project.web.models.mdmsv2.MdmsResponseV2;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForEntityWithNetworkError;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentRelatedEntity;
import static org.egov.project.config.ServiceConstants.*;

@Component
@Order(value = 8)
@Slf4j
public class PrProductVariantIdValidator implements Validator<ProjectResourceBulkRequest, ProjectResource> {

    private final RestTemplate restTemplate;

    private final ObjectMapper mapper;

    private final ProjectConfiguration projectConfiguration;

    @Autowired
    public PrProductVariantIdValidator(RestTemplate restTemplate, @Qualifier("objectMapper") ObjectMapper mapper, ProjectConfiguration projectConfiguration) {
        this.restTemplate = restTemplate;
        this.mapper = mapper;
        this.projectConfiguration = projectConfiguration;
    }


    @Override
    public Map<ProjectResource, List<Error>> validate(ProjectResourceBulkRequest request) {
        log.info("validating for product variant id");
        Map<ProjectResource, List<Error>> errorDetailsMap = new HashMap<>();
        List<ProjectResource> entities = request.getProjectResource().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        if (!entities.isEmpty()) {
            Set<String> productVariantIds = entities.stream().map(pr -> pr.getResource().getProductVariantId())
                    .collect(Collectors.toSet());
            Map<String, List<ProjectResource>> prMap = getProductVariantMap(entities);

            try {
                List<String> validProductVariantsIds = checkIfProductVariantExist(productVariantIds,
                        getTenantId(entities),
                        request.getRequestInfo())
                        .stream().map(ProductVariant::getId).collect(Collectors.toList());
                productVariantIds.forEach(id -> {
                    if (!validProductVariantsIds.contains(id)) {
                        Error error = getErrorForNonExistentRelatedEntity(id);
                        log.info("validation failed for product variant id: {} with error {}", entities, error);
                        for(ProjectResource pr : prMap.get(id)) {
                            populateErrorDetails(pr, error, errorDetailsMap);
                        }
                    }
                });
            } catch (Exception exception) {
                Error error = getErrorForEntityWithNetworkError();
                entities.forEach(entity -> populateErrorDetails(entity, error, errorDetailsMap));
            }
        }

        return errorDetailsMap;
    }

    private Map<String, List<ProjectResource>> getProductVariantMap(List<ProjectResource> entities) {
        return entities.stream().collect(
                Collectors.toMap(
                        obj -> obj.getResource().getProductVariantId(),
                        obj -> {
                            List<ProjectResource> projectResources = new ArrayList<>();
                            projectResources.add(obj);
                            return projectResources;
                        },
                        (o, o2) -> {
                            o.add(o2.get(0));
                            return o;
                        }
                ));
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

