package org.egov.healthnotification.service;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.Task;
import org.egov.healthnotification.Constants;
import org.egov.healthnotification.config.HealthNotificationProperties;
import org.egov.healthnotification.repository.ScheduledNotificationRepository;
import org.egov.healthnotification.service.enrichment.ScheduledNotificationEnrichmentService;
import org.egov.healthnotification.util.HealthNotificationUtils;
import org.egov.healthnotification.validators.ScheduledNotificationValidator;
import org.egov.healthnotification.web.models.MdmsV2Data;
import org.egov.healthnotification.web.models.ScheduledNotification;
import org.egov.healthnotification.web.models.enums.NotificationStatus;
import org.egov.healthnotification.web.models.enums.RecipientType;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.util.ArrayList;
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
    private final LocalizationService localizationService;
    private final ObjectMapper objectMapper;
    private final ScheduledNotificationRepository repository;
    private final ScheduledNotificationEnrichmentService enrichmentService;
    private final ScheduledNotificationValidator validator;
    private final HealthNotificationProperties properties;

    @Autowired
    public PostDistributionService(ProjectService projectService,
                                   MdmsService mdmsService,
                                   HouseholdService householdService,
                                   IndividualService individualService,
                                   LocalizationService localizationService,
                                   ObjectMapper objectMapper,
                                   ScheduledNotificationRepository repository,
                                   ScheduledNotificationEnrichmentService enrichmentService,
                                   ScheduledNotificationValidator validator,
                                   HealthNotificationProperties properties) {
        this.projectService = projectService;
        this.mdmsService = mdmsService;
        this.householdService = householdService;
        this.individualService = individualService;
        this.localizationService = localizationService;
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.enrichmentService = enrichmentService;
        this.validator = validator;
        this.properties = properties;
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
                    throw new CustomException(Constants.ERROR_PROJECT_NOT_FOUND,
                            String.format(Constants.MSG_PROJECT_NOT_FOUND, projectId, tenantId));
                }

                // Fetch distribution date from task's clientAuditDetails.createdTime (epoch format)
                Long createdTime = task.getClientAuditDetails().getCreatedTime();
                String distributionDate = String.valueOf(createdTime);
                log.info("Extracted distributionDate: {} for task: {}", distributionDate, task.getId());

                String campaignName = project.getName();


                String projectType = project.getProjectType();
                // Fetch beneficiaryType from additionalDetails.projectType.beneficiaryType
                String beneficiaryType = null;
                try {
                    if (project.getAdditionalDetails() != null) {
                        JsonNode additionalDetailsNode = objectMapper.valueToTree(project.getAdditionalDetails());
                        JsonNode projectTypeNode = additionalDetailsNode.get(Constants.FIELD_PROJECT_TYPE);
                        if (projectTypeNode != null && !projectTypeNode.isNull()) {
                            JsonNode beneficiaryTypeNode = projectTypeNode.get(Constants.FIELD_BENEFICIARY_TYPE);
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
                boolean smsEnabled = notificationConfig.getData().has(Constants.FIELD_SMS_ENABLED)
                        && notificationConfig.getData().get(Constants.FIELD_SMS_ENABLED).asBoolean();

                if (!smsEnabled) {
                    log.info("SMS notification is not enabled for projectType: {}, task: {}. Skipping notification.",
                            projectType, task.getId());
                    return; // Terminate processing for this task
                }

                log.info("SMS notification is enabled for projectType: {}, task: {}. Continuing with notification flow.",
                        projectType, task.getId());

                // Step 4: Fetch household/beneficiary details based on beneficiaryType
                String projectBeneficiaryClientRefId = task.getProjectBeneficiaryClientReferenceId();
                Map<String, String> householdDetails = HealthNotificationUtils.fetchHouseHoldDetails(
                        beneficiaryType, projectBeneficiaryClientRefId, projectType, tenantId,
                        projectService, householdService, individualService);

                // Add distributionDate to householdDetails for placeholder mapping
                householdDetails.put(Constants.FIELD_DISTRIBUTION_DATE, distributionDate);

                String mobileNumber = householdDetails.get(Constants.FIELD_MOBILE_NUMBER);
                String altContactNumber = householdDetails.get(Constants.FIELD_ALT_CONTACT_NUMBER);
                if ((mobileNumber == null || mobileNumber.isBlank())
                        && (altContactNumber == null || altContactNumber.isBlank())) {
                    log.info("Household/beneficiary does not have any mobileNumber or altContactNumber. " +
                                    "Skipping notification scheduling for task: {}",
                            task.getId());
                    return;
                }

                log.info("Fetched household details for task: {}, beneficiaryType: {}, details: {}",
                        task.getId(), beneficiaryType, householdDetails);

                // Step 5: Fetch event notification configuration for the given event type
                String eventType = projectType + Constants.EVENT_TYPE_SUFFIX_POST_DISTRIBUTION;
                JsonNode eventNotificationConfig = HealthNotificationUtils.getEnabledEventNotificationConfig(
                        notificationConfig, eventType, task.getId(), objectMapper);
                if (eventNotificationConfig == null) {
                    return;
                }

                // Step 6: Build placeholder messages
                List<Map<String, String>> finalMessages = HealthNotificationUtils.buildPlaceholderMessages(
                        householdDetails, eventNotificationConfig, notificationConfig, task, project, tenantId,
                        localizationService, objectMapper);

                if (finalMessages != null && !finalMessages.isEmpty()) {
                    log.info("Successfully built {} finalized notification message(s) for task: {}",
                            finalMessages.size(), task.getId());

                    // Step 7: Convert messages → ScheduledNotification objects → validate → enrich → save
                    String recipientMobileNumber = HealthNotificationUtils.determineRecipientMobileNumber(householdDetails);
                    String recipientId = householdDetails.get(Constants.FIELD_INDIVIDUAL_ID);
                    String recipientTypeStr = eventNotificationConfig.path(Constants.FIELD_RECIPIENT_TYPE).asText(Constants.RECIPIENT_TYPE_HOUSEHOLD_HEAD);

                    List<ScheduledNotification> notificationsToSave = buildScheduledNotifications(
                            finalMessages, task, project, eventNotificationConfig,
                            tenantId, recipientId, recipientMobileNumber, recipientTypeStr);

                    if (!notificationsToSave.isEmpty()) {
                        // Validate — soft-filters out duplicates and missing mobile numbers
                        validator.validateForCreate(notificationsToSave);

                        // Re-check: validator may have filtered out all notifications
                        if (notificationsToSave.isEmpty()) {
                            log.info("All notifications were filtered out during validation " +
                                    "(duplicates or missing mobile numbers) for task: {}", task.getId());
                        } else {
                            // Enrich (generate IDs, auditDetails, set status=PENDING)
                            enrichmentService.enrichForCreate(notificationsToSave, null);

                            // Save → Kafka → persister → DB
                            repository.save(notificationsToSave,
                                    properties.getScheduledNotificationSaveTopic());

                            log.info("Successfully persisted {} scheduled notifications for task: {}",
                                    notificationsToSave.size(), task.getId());
                        }
                    }
                } else {
                    log.info("No notification messages created for task: {}", task.getId());
                }
            } catch (Exception e) {
                log.error("Error processing distribution task: {}", task.getId(), e);
            }
        });

        log.info("Completed processing {} distribution tasks", tasks.size());
    }







    /**
     * Converts finalized message maps into ScheduledNotification domain objects.
     * Each message map (containing templateCode, locale, message) becomes a
     * ScheduledNotification entity with all the context needed for persistence.
     *
     * scheduledAt is calculated from MDMS config:
     *   - If scheduledNode has "scheduledAfterMinutes", adds that delay to current time
     *   - Otherwise defaults to immediate (current time)
     *
     * @param finalMessages       List of maps with templateCode, locale, message
     * @param task                The source Task
     * @param project             The source Project
     * @param eventNotificationConfig The event notification config from MDMS
     * @param tenantId            The tenant ID
     * @param recipientId         The individual ID of the recipient
     * @param recipientMobileNumber The resolved mobile number
     * @param recipientTypeStr    The recipient type string from MDMS config
     * @return List of ScheduledNotification objects ready for validation and enrichment
     */
    private List<ScheduledNotification> buildScheduledNotifications(
            List<Map<String, String>> finalMessages,
            Task task,
            Project project,
            JsonNode eventNotificationConfig,
            String tenantId,
            String recipientId,
            String recipientMobileNumber,
            String recipientTypeStr) {

        List<ScheduledNotification> notifications = new ArrayList<>();
        long currentTimeMillis = System.currentTimeMillis();

        String eventType = eventNotificationConfig.path(Constants.FIELD_EVENT_TYPE).asText();

        // Parse recipientType from MDMS config, default to HOUSEHOLD_HEAD
        RecipientType recipientType;
        try {
            recipientType = RecipientType.fromValue(recipientTypeStr);
            if (recipientType == null) {
                recipientType = RecipientType.HOUSEHOLD_HEAD;
            }
        } catch (Exception e) {
            recipientType = RecipientType.HOUSEHOLD_HEAD;
        }

        // Build a mapping of templateCode → scheduledNode for delay lookup
        Map<String, JsonNode> templateToScheduledNode = new HashMap<>();
        JsonNode scheduledNotificationsArray = eventNotificationConfig.get(Constants.FIELD_SCHEDULED_NOTIFICATIONS);
        if (scheduledNotificationsArray != null && scheduledNotificationsArray.isArray()) {
            for (JsonNode scheduledNode : scheduledNotificationsArray) {
                String tc = scheduledNode.path(Constants.FIELD_TEMPLATE_CODE).asText();
                templateToScheduledNode.put(tc, scheduledNode);
            }
        }

        for (Map<String, String> messageData : finalMessages) {
            String templateCode = messageData.get(Constants.FIELD_TEMPLATE_CODE);
            String locale = messageData.get(Constants.FIELD_LOCALE);
            String message = messageData.get(Constants.FIELD_MESSAGE);

            // Calculate scheduledAt from MDMS config
            long scheduledAt = currentTimeMillis; // default: immediate
            JsonNode scheduledNode = templateToScheduledNode.get(templateCode);
            if (scheduledNode != null && scheduledNode.has(Constants.FIELD_SCHEDULED_AFTER_MINUTES)) {
                int delayMinutes = scheduledNode.path(Constants.FIELD_SCHEDULED_AFTER_MINUTES).asInt(0);
                scheduledAt = currentTimeMillis + ((long) delayMinutes * 60 * 1000);
                log.debug("Scheduling notification {} minutes from now for templateCode: {}",
                        delayMinutes, templateCode);
            }

            // Store message, locale, and other context in contextData (persisted as JSONB)
            Map<String, Object> contextData = new HashMap<>();
            contextData.put(Constants.FIELD_MESSAGE, message);
            contextData.put(Constants.FIELD_LOCALE, locale);
            contextData.put(Constants.FIELD_TASK_ID, task.getId());
            contextData.put(Constants.FIELD_PROJECT_ID, project.getId());
            contextData.put(Constants.FIELD_ENTITY_TYPE, Constants.ENTITY_TYPE_TASK);

            ScheduledNotification notification = ScheduledNotification.builder()
                    .tenantId(tenantId)
                    .entityId(task.getId())
                    .entityType(Constants.ENTITY_TYPE_TASK)
                    .eventType(eventType)
                    .templateCode(templateCode)
                    .recipientId(recipientId)
                    .recipientType(recipientType)
                    .mobileNumber(recipientMobileNumber)
                    .contextData(contextData)
                    .scheduledAt(scheduledAt)
                    .status(NotificationStatus.PENDING)
                    .build();

            notifications.add(notification);

            log.debug("Built ScheduledNotification for task: {}, templateCode: {}, locale: {}, scheduledAt: {}",
                    task.getId(), templateCode, locale, scheduledAt);
        }

        log.info("Built {} ScheduledNotification objects for task: {}", notifications.size(), task.getId());
        return notifications;
    }

}
