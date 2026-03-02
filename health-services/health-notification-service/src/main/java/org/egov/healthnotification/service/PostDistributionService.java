package org.egov.healthnotification.service;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    @Autowired
    public PostDistributionService(ProjectService projectService,
                                   MdmsService mdmsService,
                                   ObjectMapper objectMapper) {
        this.projectService = projectService;
        this.mdmsService = mdmsService;
        this.objectMapper = objectMapper;
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

                // Fetch beneficiaryType from additionalDetails.projectType.beneficiaryType
                String beneficiaryType = null;
                try {
                    if (project.getAdditionalDetails() != null) {
                        JsonNode additionalDetailsNode = objectMapper.valueToTree(project.getAdditionalDetails());
                        JsonNode projectTypeNode = additionalDetailsNode.get("projectType");
                        if (projectTypeNode != null && !projectTypeNode.isNull()) {
                            JsonNode beneficiaryTypeNode = projectTypeNode.get("beneficiaryType");
                            if (beneficiaryTypeNode != null && !beneficiaryTypeNode.isNull()) {
                                beneficiaryType = beneficiaryTypeNode.asText();
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Error extracting beneficiaryType from additionalDetails for task: {}", task.getId(), e);
                }

                log.info("Task: {} belongs to project type: {}, beneficiaryType: {}", task.getId(), projectType, beneficiaryType);

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

                // Step 4: Fetch household/beneficiary details based on beneficiaryType
                String projectBeneficiaryClientRefId = task.getProjectBeneficiaryClientReferenceId();
                Object householdDetails = fetchHouseHoldDetails(beneficiaryType, projectBeneficiaryClientRefId, projectType, tenantId);

                log.info("Fetched household details for task: {}, beneficiaryType: {}", task.getId(), beneficiaryType);

                // TODO: Step 5 onwards - Continue processing SMS notification with household details
            } catch (Exception e) {
                log.error("Error processing distribution task: {}", task.getId(), e);
            }
        });

        log.info("Completed processing {} distribution tasks", tasks.size());
    }

    /**
     * Fetches household or individual details based on the beneficiary type.
     * This method will retrieve the appropriate beneficiary information needed for SMS notification.
     *
     * @param beneficiaryType The type of beneficiary (e.g., "HOUSEHOLD", "INDIVIDUAL")
     * @param projectBeneficiaryClientRefId The client reference ID of the project beneficiary
     * @param projectType The type of project
     * @param tenantId The tenant ID
     * @return Object containing household/individual details
     */
    private Object fetchHouseHoldDetails(String beneficiaryType, String projectBeneficiaryClientRefId,
                                         String projectType, String tenantId) {
        log.info("Fetching household details for beneficiaryType: {}, clientRefId: {}, projectType: {}, tenantId: {}",
                beneficiaryType, projectBeneficiaryClientRefId, projectType, tenantId);

        // TODO: Implement logic to fetch household or individual details


        log.debug("fetchHouseHoldDetails implementation pending");
        return null;
    }
}
