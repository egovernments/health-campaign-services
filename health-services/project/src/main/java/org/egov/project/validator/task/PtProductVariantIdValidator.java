package org.egov.project.validator.task;

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

@Component
@Order(value = 8)
@Slf4j
public class PtProductVariantIdValidator implements Validator<TaskBulkRequest, Task> {

    private final ProjectRepository projectRepository;

    private final ServiceRequestClient serviceRequestClient;

    private final ProjectConfiguration projectConfiguration;

    @Autowired
    public PtProductVariantIdValidator(ProjectRepository projectRepository, ServiceRequestClient serviceRequestClient, ProjectConfiguration projectConfiguration) {
        this.projectRepository = projectRepository;
        this.serviceRequestClient = serviceRequestClient;
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

