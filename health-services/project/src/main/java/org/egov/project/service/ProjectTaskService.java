package org.egov.project.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.project.repository.ProjectRepository;
import org.egov.project.web.models.Task;
import org.egov.project.web.models.TaskRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Slf4j
public class ProjectTaskService {

    private final IdGenService idGenService;

    private final ProjectRepository projectRepository;

    public ProjectTaskService(IdGenService idGenService, ProjectRepository projectRepository) {
        this.idGenService = idGenService;
        this.projectRepository = projectRepository;
    }

    public List<Task> create(TaskRequest request) throws Exception {

        //Check If ProjectId exist
        List<String> projectIds = request.getTask().stream().map(Task::getProjectId)
                .filter(Objects::nonNull).collect(Collectors.toList());
        List<String> validProjectIds = projectRepository.validateIds(projectIds, "id");
        List<String> invalidProjectIds = CommonUtils.getDifference(projectIds, validProjectIds);
        if (!invalidProjectIds.isEmpty()) {
            throw new CustomException("PROJECT_ID_NOT_FOUND",
                    String.format("Following project Ids not found: %s", invalidProjectIds));
        }

        //Check If ProjectBeneficiaryId exist

        //Check If ProjectVariantId exist


        List<String> taskIdList = idGenService.getIdList(request.getRequestInfo(), CommonUtils.getTenantId(request.getTask()),
                "project.task.id", "", request.getTask().size());

        //Enrich Request with Ids
        CommonUtils.enrichForCreate(request.getTask(), taskIdList, request.getRequestInfo());

        //For each task Enrich Resources with Ids
        IntStream.range(0,request.getTask().size()).forEach(i -> {
            Task task = request.getTask().get(i);
            IntStream.range(0, task.getResources().size()).forEach(
                    j -> task.getResources().get(j).setId(UUID.randomUUID().toString())
            );
        });


        return request.getTask();
    }
}
