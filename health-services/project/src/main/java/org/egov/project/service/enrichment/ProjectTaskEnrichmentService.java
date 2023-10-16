package org.egov.project.service.enrichment;

import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.Address;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.common.models.project.TaskResource;
import org.egov.common.service.IdGenService;
import org.egov.project.config.ProjectConfiguration;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.enrichForUpdate;
import static org.egov.common.utils.CommonUtils.enrichId;
import static org.egov.common.utils.CommonUtils.getAuditDetailsForUpdate;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.uuidSupplier;

@Service
@Slf4j
public class ProjectTaskEnrichmentService {

    private final IdGenService idGenService;

    private final ProjectConfiguration projectConfiguration;

    public ProjectTaskEnrichmentService(IdGenService idGenService, ProjectConfiguration projectConfiguration) {
        this.idGenService = idGenService;
        this.projectConfiguration = projectConfiguration;
    }


    public void create(List<Task> validTasks, TaskBulkRequest request) throws Exception {
        log.info("starting the enrichment for tasks");

        log.info("generating id for tasks");
        List<String> taskIdList = idGenService.getIdList(request.getRequestInfo(),
                getTenantId(request.getTasks()),
                projectConfiguration.getProjectTaskIdFormat(),
                "", request.getTasks().size());
        log.info("enriching tasks");
        enrichForCreate(validTasks, taskIdList, request.getRequestInfo());
        enrichAddressesForCreate(validTasks);
        enrichResourcesForCreate(request, validTasks);
        log.info("enrichment done");
    }

    public void update(List<Task> validTasks, TaskBulkRequest request) throws Exception {
        log.info("generating id for tasks");
        log.info("enriching tasks for update");
        enrichAddressesForUpdate(validTasks);
        enrichResourcesForUpdate(request, validTasks);
        Map<String, Task> iMap = getIdToObjMap(validTasks);
        enrichForUpdate(iMap, request);
        log.info("enrichment done");
    }

    public void delete(List<Task> validTasks, TaskBulkRequest request) throws Exception {
        log.info("enriching tasks for delete");
        for (Task task : validTasks) {
            if (task.getIsDeleted()) {
                log.info("enriching all task resources for delete");
                for (TaskResource resource : task.getResources()) {
                    resource.setIsDeleted(true);
                    updateAuditDetailsForResource(request, resource);
                }
                updateAuditDetailsForTask(request, task);
                task.setRowVersion(task.getRowVersion() + 1);
            } else {
                int previousRowVersion = task.getRowVersion();
                log.info("enriching task resources for delete");
                task.getResources().stream().filter(TaskResource::getIsDeleted).forEach(resource -> {
                    updateAuditDetailsForResource(request, resource);
                    updateAuditDetailsForTask(request, task);
                    task.setRowVersion(previousRowVersion + 1);
                });
            }
        }
        log.info("enrichment done");
    }

    private static void updateAuditDetailsForTask(TaskBulkRequest request, Task task) {
        AuditDetails existingAuditDetails = task.getAuditDetails();
        AuditDetails auditDetails = getAuditDetailsForUpdate(existingAuditDetails,
                request.getRequestInfo().getUserInfo().getUuid());
        task.setAuditDetails(auditDetails);
    }

    private static void updateAuditDetailsForResource(TaskBulkRequest request, TaskResource resource) {
        AuditDetails existingAuditDetails = resource.getAuditDetails();
        AuditDetails auditDetails = getAuditDetailsForUpdate(existingAuditDetails,
                request.getRequestInfo().getUserInfo().getUuid());
        resource.setAuditDetails(auditDetails);
    }

    private static void enrichResourcesForUpdate(TaskBulkRequest request, List<Task> tasks) {
        log.info("enriching resources");
        for (Task task : tasks) {
            List<TaskResource> resourcesToCreate = task.getResources().stream()
                    .filter(r -> r.getId() == null).collect(Collectors.toList());
            List<TaskResource> resourcesToUpdate = task.getResources().stream()
                    .filter(r -> r.getId() != null).collect(Collectors.toList());

            if (!resourcesToCreate.isEmpty()) {
                enrichResourcesForCreate(request, resourcesToCreate, task.getId());
            }
            for (TaskResource resource : resourcesToUpdate) {
                updateAuditDetailsForResource(request, resource);
            }
        }
    }

    private static void enrichAddressesForUpdate(List<Task> validTasks) {
        List<Address> addressesToCreate = validTasks.stream()
                .filter(ad1 -> ad1.getAddress() != null && ad1.getAddress().getId() == null)
                .map(Task::getAddress).collect(Collectors.toList());

        if (!addressesToCreate.isEmpty()) {
            log.info("enriching addresses to create");
            List<String> addressIdList = uuidSupplier().apply(addressesToCreate.size());
            enrichId(addressesToCreate, addressIdList);
        }
    }

    private static void enrichAddressesForCreate(List<Task> validTasks) {
        List<Address> addresses = validTasks.stream().map(Task::getAddress)
                .collect(Collectors.toList());
        if (!addresses.isEmpty()) {
            log.info("enriching addresses");
            List<String> addressIdList = uuidSupplier().apply(addresses.size());
            enrichId(addresses, addressIdList);
        }
    }

    private static void enrichResourcesForCreate(TaskBulkRequest request,
                                                 List<Task> validTasks) {
        for (Task task : validTasks) {
            log.info("enriching resources");
            List<TaskResource> resources = task.getResources();
            if(CollectionUtils.isEmpty(resources))
                continue;
            enrichResourcesForCreate(request, resources, task.getId());
        }
    }

    private static void enrichResourcesForCreate(TaskBulkRequest request,
                                                 List<TaskResource> resources, String taskId) {
        log.info("enriching resources");
        List<String> ids = uuidSupplier().apply(resources.size());
        enrichForCreate(resources, ids, request.getRequestInfo(), false);
        resources.forEach(taskResource -> taskResource.setTaskId(taskId));
    }
}
