package org.egov.project.service.enrichment;

import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.service.IdGenService;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.web.models.Address;
import org.egov.project.web.models.Task;
import org.egov.project.web.models.TaskBulkRequest;
import org.egov.project.web.models.TaskResource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.enrichId;
import static org.egov.common.utils.CommonUtils.getAuditDetailsForUpdate;
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
        log.info("generating id for tasks");
        List<String> taskIdList = idGenService.getIdList(request.getRequestInfo(),
                getTenantId(request.getTasks()),
                projectConfiguration.getProjectTaskIdFormat(),
                "", request.getTasks().size());
        log.info("enriching tasks");
        enrichForCreate(validTasks, taskIdList, request.getRequestInfo());
        enrichAddressesForCreate(validTasks);
        enrichResourcesForCreate(request, validTasks);
    }

    public void update(List<Task> validTasks, TaskBulkRequest request) throws Exception {
        log.info("generating id for tasks");
        log.info("enriching tasks");
        enrichAddressesForUpdate(validTasks);
        enrichResourcesForUpdate(request, validTasks);
    }

    private static void enrichResourcesForUpdate(TaskBulkRequest request, List<Task> tasks) {
        for (Task task : tasks) {
            List<TaskResource> resourcesToCreate = task.getResources().stream()
                    .filter(r -> r.getId() == null).collect(Collectors.toList());
            List<TaskResource> resourcesToUpdate = task.getResources().stream()
                    .filter(r -> r.getId() != null).collect(Collectors.toList());

            enrichResourcesForCreate(request, resourcesToCreate, task.getId());
            for (TaskResource resource : resourcesToUpdate) {
                AuditDetails existingAuditDetails = resource.getAuditDetails();
                AuditDetails auditDetails = getAuditDetailsForUpdate(existingAuditDetails,
                        request.getRequestInfo().getUserInfo().getUuid());
                resource.setAuditDetails(auditDetails);
            }
        }
    }

    private static void enrichAddressesForUpdate(List<Task> validTasks) {
        List<Task> addressesToCreate = validTasks.stream()
                .filter(ad1 -> ad1.getAddress() != null && ad1.getAddress().getId() == null)
                .collect(Collectors.toList());
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
            List<TaskResource> resources = task.getResources();
            enrichResourcesForCreate(request, resources, task.getId());
        }
    }

    private static void enrichResourcesForCreate(TaskBulkRequest request,
                                                 List<TaskResource> resources, String taskId) {
        List<String> ids = uuidSupplier().apply(resources.size());
        enrichForCreate(resources, ids, request.getRequestInfo(), false);
        resources.forEach(taskResource -> taskResource.setTaskId(taskId));
    }
}
