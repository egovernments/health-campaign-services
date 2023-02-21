package org.egov.project.validator.resource;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.ProjectRepository;
import org.egov.project.web.models.ProductVariant;
import org.egov.project.web.models.ProductVariantResponse;
import org.egov.project.web.models.ProductVariantSearch;
import org.egov.project.web.models.ProductVariantSearchRequest;
import org.egov.project.web.models.ProjectResource;
import org.egov.project.web.models.ProjectResourceBulkRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.egov.project.Constants.GET_PRODUCT_VARIANT_ID;

@Component
@Order(value = 8)
@Slf4j
public class PrProductVariantIdValidator implements Validator<ProjectResourceBulkRequest, ProjectResource> {
    //shiva
    private final ProjectRepository projectRepository;

    private final ServiceRequestClient serviceRequestClient;

    private final ProjectConfiguration projectConfiguration;

    @Autowired
    public PrProductVariantIdValidator(ProjectRepository projectRepository, ServiceRequestClient serviceRequestClient, ProjectConfiguration projectConfiguration) {
        this.projectRepository = projectRepository;
        this.serviceRequestClient = serviceRequestClient;
        this.projectConfiguration = projectConfiguration;
    }


    @Override
    public Map<ProjectResource, List<Error>> validate(ProjectResourceBulkRequest request) {
        log.info("validating for product variant id");
        Map<ProjectResource, List<Error>> errorDetailsMap = new HashMap<>();
        List<ProjectResource> entities = request.getProjectResource().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        if (!entities.isEmpty()) {
            Set<String> productVariantIds = entities.stream().map(ProjectResource::getProductVariantId).collect(Collectors.toSet());
            Map<String, ProjectResource> pvMap = getIdToObjMap(entities, getMethod(GET_PRODUCT_VARIANT_ID, getObjClass(entities)));
            try {
                List<String> validProductVariantsIds = checkIfProductVariantExist(productVariantIds,
                        getTenantId(entities),
                        request.getRequestInfo()).stream().map(ProductVariant::getId).collect(Collectors.toList());
                productVariantIds.forEach(id -> {
                    if (!validProductVariantsIds.contains(id)) {
                        Error error = getErrorForNonExistentRelatedEntity(id);
                        log.info("validation failed for product variant id: {} with error {}", entities, error);
                        populateErrorDetails(pvMap.get(id), error, errorDetailsMap);
                    }
                });
            } catch (Exception exception) {
                Error error = getErrorForEntityWithNetworkError();
                entities.forEach(entity -> populateErrorDetails(entity, error, errorDetailsMap));
            }
        }

        return errorDetailsMap;
    }

    private List<ProductVariant> checkIfProductVariantExist(Set<String> pvIds, String tenantId, RequestInfo requestInfo) {

        List<String> productVariantIds = new ArrayList<>(pvIds);
        ProductVariantSearch productVariantSearch = ProductVariantSearch.builder()
                .id(productVariantIds).build();
        ProductVariantSearchRequest request = ProductVariantSearchRequest.builder().productVariant(productVariantSearch)
                .requestInfo(requestInfo).build();
        StringBuilder url = new StringBuilder(projectConfiguration.getProductHost()
                + projectConfiguration.getProductVariantSearchUrl()
                + "?limit=" + productVariantIds.size() + "&offset=0&tenantId=" + tenantId);
        ProductVariantResponse response;
        try {
            response = serviceRequestClient.fetchResult(url, request, ProductVariantResponse.class);
        } catch (Exception e) {
            throw new CustomException("PRODUCT_VARIANT",
                    String.format("Something went wrong: %s", e.getMessage()));
        }
        return response.getProductVariant();
    }
}

