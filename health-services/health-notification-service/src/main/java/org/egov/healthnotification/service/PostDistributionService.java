package org.egov.healthnotification.service;


import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.Task;
import org.egov.healthnotification.web.models.MdmsV2Data;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.util.List;


@Service
@Slf4j
public class PostDistributionService {

    private final ProjectService projectService;
    private final MdmsService mdmsService;

    @Autowired
    public PostDistributionService(ProjectService projectService,
                                   MdmsService mdmsService) {
        this.projectService = projectService;
        this.mdmsService = mdmsService;
    }

    /**
     * Processes distribution tasks to determine if notifications need to be scheduled.
     *
     * @param tasks The list of distribution tasks from Kafka topic
     */
    public void processDistributionTasks(List<Task> tasks) {
        log.info("Processing {} distribution tasks for notification scheduling", tasks.size());

        tasks.forEach(task -> {
            try {
                log.info("Processing distribution task: {} for project: {}, beneficiary: {}",
                        task.getId(),
                        task.getProjectId(),
                        task.getProjectBeneficiaryId());

                // Step 1: Fetch project details to get project type
                String projectId = task.getProjectId();
                String tenantId = task.getTenantId();

                Project project = projectService.searchProjectById(projectId, tenantId);
                if (project == null) {
                    log.error("Project not found for task: {}, projectId: {}", task.getId(), projectId);
                    throw new CustomException("PROJECT_NOT_FOUND",
                            String.format("Project not found for projectId: %s, tenantId: %s", projectId, tenantId));
                }

                String projectType = project.getProjectType();

                log.info("Task: {} belongs to project type: {}", task.getId(), projectType);

                // Step 2: Fetch MDMS notification configuration for this project type
                MdmsV2Data notificationConfig = mdmsService.fetchNotificationConfigByProjectType(projectType, tenantId);
                log.info("Successfully fetched notification config for task: {}, projectType: {}",
                        task.getId(), projectType);

                // Step 3: Check if SMS is enabled in the notification config
                boolean smsEnabled = notificationConfig.getData().has("smsEnabled")
                        && notificationConfig.getData().get("smsEnabled").asBoolean();

                if (!smsEnabled) {
                    log.info("SMS notification is not enabled for projectType: {}, task: {}. Skipping notification.",
                            projectType, task.getId());
                    return; // Terminate processing for this task
                }

                log.info("SMS notification is enabled for projectType: {}, task: {}. Continuing with notification flow.",
                        projectType, task.getId());

                // TODO: Step 4 onwards - Continue processing SMS notification

            } catch (Exception e) {
                log.error("Error processing distribution task: {}", task.getId(), e);
            }
        });

        log.info("Completed processing {} distribution tasks", tasks.size());
    }
}
