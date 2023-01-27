package org.egov.project.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.project.repository.ProjectBeneficiaryRepository;
import org.egov.project.repository.ProjectRepository;
import org.egov.project.repository.ProjectTaskRepository;
import org.egov.project.web.models.Address;
import org.egov.project.web.models.ProductVariant;
import org.egov.project.web.models.ProductVariantResponse;
import org.egov.project.web.models.ProductVariantSearch;
import org.egov.project.web.models.ProductVariantSearchRequest;
import org.egov.project.web.models.Task;
import org.egov.project.web.models.TaskRequest;
import org.egov.project.web.models.TaskResource;
import org.egov.project.web.models.TaskSearch;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.getDifference;
import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdList;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.havingTenantId;
import static org.egov.common.utils.CommonUtils.includeDeleted;
import static org.egov.common.utils.CommonUtils.isSearchByIdOnly;
import static org.egov.common.utils.CommonUtils.lastChangedSince;
import static org.egov.common.utils.CommonUtils.uuidSupplier;

@Service
@Slf4j
public class ProjectTaskService {

    private final IdGenService idGenService;

    private final ProjectRepository projectRepository;

    private final ServiceRequestClient serviceRequestClient;

    private final ProjectTaskRepository projectTaskRepository;

    private final ProjectBeneficiaryRepository projectBeneficiaryRepository;

    @Value("${egov.product.host}")
    private String productHost;

    @Value("${egov.search.product.variant.url}")
    private String productVariantSearchUrl;

    public ProjectTaskService(IdGenService idGenService, ProjectRepository projectRepository,
                              ServiceRequestClient serviceRequestClient,
                              ProjectTaskRepository projectTaskRepository,
                              ProjectBeneficiaryRepository projectBeneficiaryRepository) {
        this.idGenService = idGenService;
        this.projectRepository = projectRepository;
        this.serviceRequestClient = serviceRequestClient;
        this.projectTaskRepository = projectTaskRepository;
        this.projectBeneficiaryRepository = projectBeneficiaryRepository;
    }

    public List<Task> create(TaskRequest request) throws Exception {

        //Check If ProjectId exist
        List<String> projectIds = request.getTask().stream().map(Task::getProjectId)
                .filter(Objects::nonNull).collect(Collectors.toList());
        List<String> validProjectIds = projectRepository.validateIds(projectIds, "id");
        List<String> invalidProjectIds = CommonUtils.getDifference(projectIds, validProjectIds);
        if (!invalidProjectIds.isEmpty()) {
            throw new CustomException("PROJECT_NOT_FOUND",
                    String.format("Following project Ids not found: %s", invalidProjectIds));
        }

        //Check If ProjectBeneficiaryId exist
        validateProjectBeneficiaryIds(request);

        //Enrich Request with Ids
        List<String> taskIdList = idGenService.getIdList(request.getRequestInfo(), CommonUtils.getTenantId(request.getTask()),
                "project.task.id", "", request.getTask().size());
        CommonUtils.enrichForCreate(request.getTask(), taskIdList, request.getRequestInfo());

        //Enrich Request with Address Ids
        List<Address> addresses = request.getTask().stream().map(Task::getAddress)
                .filter(Objects::nonNull).collect(Collectors.toList());
        if (!addresses.isEmpty()) {
            IntStream.range(0, addresses.size()).forEach(i -> addresses.get(i).setId(UUID.randomUUID().toString()));
        }

        //For each task Enrich Resources with Ids
        IntStream.range(0,request.getTask().size()).forEach(i -> {
            Task task = request.getTask().get(i);
            Set<String> pvId = task.getResources().stream()
                    .map(TaskResource::getProductVariantId).collect(Collectors.toSet());
            checkIfProductVariantExist(pvId,getTenantId(task.getResources()), request.getRequestInfo());
            enrichForCreate(task.getResources(), uuidSupplier().apply(task.getResources().size()),
                    request.getRequestInfo());
            task.getResources().forEach(r -> {
                r.setTaskId(task.getId());
                r.setTaskClientReferenceId(task.getClientReferenceId());
            });
        });

        projectTaskRepository.save(request.getTask(), "save-project-task-topic");

        return request.getTask();
    }

    private void validateProjectBeneficiaryIds(TaskRequest request) {
        Method idMethod = getIdMethod(request.getTask(), "projectBeneficiaryId",
                "projectBeneficiaryClientReferenceId");
        List<String> projectBeneficiaryIds = getIdList(request.getTask(), idMethod);
        String columnName = "beneficiaryId";
        if ("getProjectBeneficiaryClientReferenceId".equals(idMethod.getName())) {
            columnName = "beneficiaryClientReferenceId";
        }
        List<String> validProjectBeneficiaryIds = projectBeneficiaryRepository.validateIds(projectBeneficiaryIds,
                columnName);
        List<String> invalidProjectBeneficiaryIds = CommonUtils.getDifference(projectBeneficiaryIds,
                validProjectBeneficiaryIds);
        if (!invalidProjectBeneficiaryIds.isEmpty()) {
            throw new CustomException("PROJECT_BENEFICIARY_NOT_FOUND",
                    String.format("Following project Beneficiary Ids not found: %s", invalidProjectBeneficiaryIds));
        }
    }

    private void checkIfProductVariantExist(Set<String> pvIds, String tenantId, RequestInfo requestInfo) {

        List<String> productVariantIds = new ArrayList<>(pvIds);
        ProductVariantSearch productVariantSearch = ProductVariantSearch.builder()
                .id(productVariantIds).build();
        ProductVariantSearchRequest request = ProductVariantSearchRequest.builder().productVariant(productVariantSearch)
                .requestInfo(requestInfo).build();
        StringBuilder url = new StringBuilder(productHost + productVariantSearchUrl
                + "?limit=1&offset=0&tenantId=" + tenantId);
        ProductVariantResponse response;
        try {
            response = serviceRequestClient.fetchResult(url, request, ProductVariantResponse.class);
        } catch (Exception e) {
            throw new CustomException("PRODUCT_VARIANT",
                    String.format("Something went wrong: %s", e.getMessage()));
        }

        if (response == null || response.getProductVariant().size() != productVariantIds.size()) {
            List<String> validId = response.getProductVariant().stream()
                    .map(ProductVariant::getId).collect(Collectors.toList());
            throw new CustomException("PRODUCT_VARIANT_NOT_FOUND",
                    String.format("Following product variant not found: %s", getDifference(productVariantIds, validId)));
        }

    }

    public List<Task> search(TaskSearch taskSearch, Integer limit, Integer offset, String tenantId,
                             Long lastChangedSince, Boolean includeDeleted) {
        String idFieldName = getIdFieldName(taskSearch);
        if (isSearchByIdOnly(taskSearch, idFieldName)) {
            List<String> ids = new ArrayList<>();
            ids.add((String) ReflectionUtils.invokeMethod(getIdMethod(Collections
                            .singletonList(taskSearch)),
                    taskSearch));
            return projectTaskRepository.findById(ids,
                            idFieldName, includeDeleted).stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
        }

        try {
            return projectTaskRepository.find(taskSearch, limit, offset,
                    tenantId, lastChangedSince, includeDeleted);
        } catch (QueryBuilderException e) {
            throw new CustomException("ERROR_IN_QUERY", e.getMessage());
        }
    }
}
