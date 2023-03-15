package org.egov.project.validator.resource;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.common.models.product.ProductVariant;
import org.egov.common.models.product.ProductVariantResponse;
import org.egov.common.models.product.ProductVariantSearch;
import org.egov.common.models.product.ProductVariantSearchRequest;
import org.egov.common.models.project.ProjectResource;
import org.egov.common.models.project.ProjectResourceBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.ProjectRepository;
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

import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForEntityWithNetworkError;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentRelatedEntity;

@Component
@Order(value = 8)
@Slf4j
public class PrProductVariantIdValidator implements Validator<ProjectResourceBulkRequest, ProjectResource> {

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

