package org.egov.healthnotification.service;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.models.project.Task;
import org.egov.healthnotification.Constants;
import org.egov.healthnotification.web.models.MdmsV2Data;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
@Slf4j
public class PostDistributionService {

    private final ProjectService projectService;
    private final MdmsService mdmsService;
    private final HouseholdService householdService;
    private final IndividualService individualService;
    private final ObjectMapper objectMapper;

    @Autowired
    public PostDistributionService(ProjectService projectService,
                                   MdmsService mdmsService,
                                   HouseholdService householdService,
                                   IndividualService individualService,
                                   ObjectMapper objectMapper) {
        this.projectService = projectService;
        this.mdmsService = mdmsService;
        this.householdService = householdService;
        this.individualService = individualService;
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
                Map<String, String> householdDetails = fetchHouseHoldDetails(beneficiaryType, projectBeneficiaryClientRefId, projectType, tenantId);

                log.info("Fetched household details for task: {}, beneficiaryType: {}", task.getId(), beneficiaryType);

                // Step 5: Process event notification for ITN_POST_DISTRIBUTION
                processEventNotification(notificationConfig, projectType, task.getId(), householdDetails);

                // TODO: Step 6 onwards - Continue with notification processing
            } catch (Exception e) {
                log.error("Error processing distribution task: {}", task.getId(), e);
            }
        });

        log.info("Completed processing {} distribution tasks", tasks.size());
    }

    /**
     * Fetches household or individual details based on the beneficiary type.
     * For HOUSEHOLD type: Retrieves household head's individual details for notification.
     * For INDIVIDUAL type: Implementation pending.
     *
     * @param beneficiaryType The type of beneficiary (HOUSEHOLD/INDIVIDUAL)
     * @param projectBeneficiaryClientRefId The client reference ID of the project beneficiary
     * @param projectType The type of project
     * @param tenantId The tenant ID
     * @return Map containing individual details (givenName, mobileNumber, altContactNumber, emailId, individualId)
     */
    private Map<String, String> fetchHouseHoldDetails(String beneficiaryType, String projectBeneficiaryClientRefId,
                                         String projectType, String tenantId) {
        log.info("Fetching household details for beneficiaryType: {}, tenantId: {}", beneficiaryType, tenantId);

        Map<String, String> househeaddetails = new HashMap<>();

        // Step 1: Fetch project beneficiary details by client reference ID
        log.info("Fetching project beneficiary for clientRefId: {}", projectBeneficiaryClientRefId);
        ProjectBeneficiary projectBeneficiary = projectService.searchProjectBeneficiaryByClientRefId(
                projectBeneficiaryClientRefId, tenantId);

        String beneficiaryClientReferenceId = projectBeneficiary.getBeneficiaryClientReferenceId();
        log.info("Successfully fetched project beneficiary with beneficiaryClientRefId: {}", beneficiaryClientReferenceId);

        if ("HOUSEHOLD".equals(beneficiaryType)) {
            log.info("Beneficiary type is HOUSEHOLD, fetching household head details");

            // Step 2: Fetch household head member by household client reference ID
            HouseholdMember householdHead = householdService.searchHouseholdHeadByClientRefId(
                    beneficiaryClientReferenceId, tenantId);

            String individualId = householdHead.getIndividualId();
            log.info("Successfully fetched household head with individualId: {}", individualId);

            // Step 3: Fetch individual details using individual ID
            Individual individual = individualService.searchIndividualById(individualId, tenantId);

            // Step 4: Extract required fields and populate HashMap
            househeaddetails.put("givenName", individual.getName() != null ? individual.getName().getGivenName() : null);
            househeaddetails.put("mobileNumber", individual.getMobileNumber());
            househeaddetails.put("altContactNumber", individual.getAltContactNumber());
            househeaddetails.put("emailId", individual.getEmail());
            househeaddetails.put("individualId", individualId);

            log.info("Successfully populated household head details for individualId: {}", individualId);
        }

        return househeaddetails;
    }

    /**
     * Processes event notification for POST_DISTRIBUTION.
     * Filters eventNotifications array by eventType and extracts placeholders and scheduledNotifications.
     *
     * @param notificationConfig MDMS notification configuration
     * @param projectType The project type (e.g., ITN, LLIN, SMC)
     * @param taskId The task ID for logging
     * @param householdDetails The household head details
     */
    private void processEventNotification(MdmsV2Data notificationConfig, String projectType,
                                          String taskId, Map<String, String> householdDetails) {
        String eventType = projectType + Constants.EVENT_TYPE_SUFFIX_POST_DISTRIBUTION;
        log.info("Processing event notification for eventType: {}, taskId: {}", eventType, taskId);

        JsonNode eventNotificationsNode = notificationConfig.getData().get("eventNotifications");
        if (eventNotificationsNode == null || !eventNotificationsNode.isArray()) {
            log.error("eventNotifications array not found for projectType: {}", projectType);
            return;
        }

        // Filter: Find event by eventType
        JsonNode eventNode = null;
        for (JsonNode event : eventNotificationsNode) {
            if (eventType.equals(event.path("eventType").asText())) {
                eventNode = event;
                break;
            }
        }

        if (eventNode == null) {
            log.info("Event type {} not found. Skipping notification.", eventType);
            return;
        }

        // Check if event is enabled
        if (!eventNode.path("enabled").asBoolean()) {
            log.info("Event type {} is disabled. Skipping notification.", eventType);
            return;
        }

        log.info("Event type {} is enabled. Processing notification.", eventType);

        // Extract placeholders array
        JsonNode placeholders = eventNode.get("placeholders");

        // Extract scheduledNotifications array
        JsonNode scheduledNotifications = eventNode.get("scheduledNotifications");
        if (scheduledNotifications == null || !scheduledNotifications.isArray()) {
            log.error("scheduledNotifications array not found for eventType: {}", eventType);
            return;
        }

        log.info("Found {} scheduled notifications for eventType: {}", scheduledNotifications.size(), eventType);

        // TODO: Process scheduled notifications
    }
}
