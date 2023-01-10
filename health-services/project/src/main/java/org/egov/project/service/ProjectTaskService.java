package org.egov.project.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.project.repository.ProjectRepository;
import org.egov.project.repository.ProjectTaskRepository;
import org.egov.project.web.models.Address;
import org.egov.project.web.models.ProductVariantResponse;
import org.egov.project.web.models.ProductVariantSearch;
import org.egov.project.web.models.ProductVariantSearchRequest;
import org.egov.project.web.models.Task;
import org.egov.project.web.models.TaskRequest;
import org.egov.project.web.models.TaskResource;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.uuidSupplier;

@Service
@Slf4j
public class ProjectTaskService {

    private final IdGenService idGenService;

    private final ProjectRepository projectRepository;

    private final ServiceRequestClient serviceRequestClient;

    private final ProjectTaskRepository projectTaskRepository;

    @Value("${egov.product.host}")
    private String productHost;

    @Value("${egov.search.product.variant.url}")
    private String productVariantSearchUrl;

    public ProjectTaskService(IdGenService idGenService, ProjectRepository projectRepository, ServiceRequestClient serviceRequestClient, ProjectTaskRepository projectTaskRepository) {
        this.idGenService = idGenService;
        this.projectRepository = projectRepository;
        this.serviceRequestClient = serviceRequestClient;
        this.projectTaskRepository = projectTaskRepository;
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
            List<String> pvId = task.getResources().stream()
                    .map(TaskResource::getProductVariantId).collect(Collectors.toList());
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

    private void checkIfProductVariantExist(List<String> productVariantIds, String tenantId, RequestInfo requestInfo) {
        for (String productVariantId: productVariantIds) {
            ProductVariantSearch productVariantSearch = ProductVariantSearch.builder()
                    .id(productVariantId).build();
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

            if (response == null || response.getProductVariant().isEmpty()) {
                throw new CustomException("PRODUCT_VARIANT_NOT_FOUND",
                        String.format("Following product variant not found: %s", productVariantId));
            }
        }
    }
}
