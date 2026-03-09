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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
                                   ObjectMapper objectMapper,
                                   ScheduledNotificationRepository repository,
                                   ScheduledNotificationEnrichmentService enrichmentService,
                                   ScheduledNotificationValidator validator,
                                   HealthNotificationProperties properties) {
        this.projectService = projectService;
        this.mdmsService = mdmsService;
        this.householdService = householdService;
        this.individualService = individualService;
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.enrichmentService = enrichmentService;
        this.validator = validator;
        this.properties = properties;
    }

    /**
     * Processes distribution tasks to determine if notifications need to be scheduled.
     * Creates one ScheduledNotification row per MDMS scheduledNotification entry per task.
     * Does NOT build messages — stores templateCode + placeholder map. The scheduler builds messages at send time.
     *
     * @param tasks The list of distribution tasks from Kafka topic
     */
    public void processDistributionTasks(List<Task> tasks) {
        log.info("Processing {} distribution tasks for notification scheduling", tasks.size());

        ZoneId timezone = ZoneId.of(properties.getNotificationTimezone());

        tasks.forEach(task -> {
            try {
                log.info("Processing distribution task: {} for project: {}, beneficiary: {}",
                        task.getId(), task.getProjectId(), task.getProjectBeneficiaryId());

                String projectId = task.getProjectId();
                String tenantId = task.getTenantId();

                // Step 1: Fetch project details
                Project project = projectService.searchProjectById(projectId, tenantId);
                if (project == null) {
                    log.error("Project not found for task: {}, projectId: {}", task.getId(), projectId);
                    throw new CustomException(Constants.ERROR_PROJECT_NOT_FOUND,
                            String.format(Constants.MSG_PROJECT_NOT_FOUND, projectId, tenantId));
                }

                // Event timestamp from task's clientAuditDetails
                Long eventTimestamp = task.getClientAuditDetails().getCreatedTime();

                String projectType = project.getProjectType();

                // Extract beneficiaryType
                String beneficiaryType = extractBeneficiaryType(project, task.getId());
                log.info("Task: {} belongs to project type: {}, beneficiaryType: {}",
                        task.getId(), projectType, beneficiaryType);

                // Step 2: Fetch MDMS notification config
                MdmsV2Data notificationConfig = mdmsService.fetchNotificationConfigByProjectType(projectType, tenantId);

                // Step 3: Check if SMS is enabled
                boolean smsEnabled = notificationConfig.getData().has(Constants.FIELD_SMS_ENABLED)
                        && notificationConfig.getData().get(Constants.FIELD_SMS_ENABLED).asBoolean();
                if (!smsEnabled) {
                    log.info("SMS not enabled for projectType: {}, task: {}. Skipping.", projectType, task.getId());
                    return;
                }

                // Step 4: Fetch household/beneficiary details
                String projectBeneficiaryClientRefId = task.getProjectBeneficiaryClientReferenceId();
                Map<String, String> householdDetails = HealthNotificationUtils.fetchHouseHoldDetails(
                        beneficiaryType, projectBeneficiaryClientRefId, projectType, tenantId,
                        projectService, householdService, individualService);

                householdDetails.put(Constants.FIELD_DISTRIBUTION_DATE, String.valueOf(eventTimestamp));

                String mobileNumber = householdDetails.get(Constants.FIELD_MOBILE_NUMBER);
                String altContactNumber = householdDetails.get(Constants.FIELD_ALT_CONTACT_NUMBER);
                if ((mobileNumber == null || mobileNumber.isBlank())
                        && (altContactNumber == null || altContactNumber.isBlank())) {
                    log.info("No mobileNumber or altContactNumber. Skipping task: {}", task.getId());
                    return;
                }

                // Step 5: Get enabled event notification config
                String eventType = projectType + Constants.EVENT_TYPE_SUFFIX_POST_DISTRIBUTION;
                JsonNode eventNotificationConfig = HealthNotificationUtils.getEnabledEventNotificationConfig(
                        notificationConfig, eventType, task.getId(), objectMapper);
                if (eventNotificationConfig == null) {
                    return;
                }

                // Step 6: Extract locale
                List<String> locales = HealthNotificationUtils.extractLocales(notificationConfig, task.getId());
                String locale = (locales != null && !locales.isEmpty()) ? locales.get(0) : "en_NG";

                // Step 7: Build ScheduledNotification objects — one per MDMS scheduledNotification entry
                String recipientMobileNumber = HealthNotificationUtils.determineRecipientMobileNumber(householdDetails);
                String recipientId = householdDetails.get(Constants.FIELD_INDIVIDUAL_ID);
                String recipientTypeStr = eventNotificationConfig.path(Constants.FIELD_RECIPIENT_TYPE)
                        .asText(Constants.RECIPIENT_TYPE_HOUSEHOLD_HEAD);

                List<ScheduledNotification> notificationsToSave = buildScheduledNotifications(
                        householdDetails, eventNotificationConfig, task, project,
                        tenantId, recipientId, recipientMobileNumber, recipientTypeStr,
                        eventTimestamp, locale, timezone);

                if (!notificationsToSave.isEmpty()) {
                    validator.validateForCreate(notificationsToSave);

                    if (!notificationsToSave.isEmpty()) {
                        enrichmentService.enrichForCreate(notificationsToSave, null);
                        repository.save(notificationsToSave, properties.getScheduledNotificationSaveTopic());
                        log.info("Persisted {} scheduled notifications for task: {}",
                                notificationsToSave.size(), task.getId());
                    }
                }
            } catch (Exception e) {
                log.error("Error processing distribution task: {}", task.getId(), e);
            }
        });

        log.info("Completed processing {} distribution tasks", tasks.size());
    }

    /**
     * Builds ScheduledNotification objects from MDMS scheduledNotifications config.
     * One row per MDMS scheduledNotification entry (e.g., SMC Day2 + Day3 = 2 rows).
     *
     * Does NOT build messages. Stores:
     *   - templateCode: the localization template code from MDMS
     *   - contextData: placeholder name → value map (for the scheduler to build messages at send time)
     *   - createdAt: event date (DATE, in configured timezone)
     *   - scheduledAt: event date + delayHours converted to days (DATE, in configured timezone)
     */
    private List<ScheduledNotification> buildScheduledNotifications(
            Map<String, String> householdDetails,
            JsonNode eventNotificationConfig,
            Task task,
            Project project,
            String tenantId,
            String recipientId,
            String recipientMobileNumber,
            String recipientTypeStr,
            Long eventTimestamp,
            String locale,
            ZoneId timezone) {

        List<ScheduledNotification> notifications = new ArrayList<>();

        String eventType = eventNotificationConfig.path(Constants.FIELD_EVENT_TYPE).asText();

        RecipientType recipientType;
        try {
            recipientType = RecipientType.fromValue(recipientTypeStr);
            if (recipientType == null) recipientType = RecipientType.HOUSEHOLD_HEAD;
        } catch (Exception e) {
            recipientType = RecipientType.HOUSEHOLD_HEAD;
        }

        // Event date in configured timezone
        LocalDate eventDate = Instant.ofEpochMilli(eventTimestamp).atZone(timezone).toLocalDate();

        JsonNode scheduledNotificationsArray = eventNotificationConfig.get(Constants.FIELD_SCHEDULED_NOTIFICATIONS);
        if (scheduledNotificationsArray == null || !scheduledNotificationsArray.isArray()) {
            return notifications;
        }

        // Build placeholder map: {HouseholdHeadName} → "John", etc.
        Map<String, String> placeholderMap = HealthNotificationUtils.buildPlaceholderMap(
                householdDetails, eventNotificationConfig, task, project, task.getId());

        // Clean placeholder keys (remove braces) and build contextData
        Map<String, Object> contextData = new HashMap<>();
        for (Map.Entry<String, String> entry : placeholderMap.entrySet()) {
            String key = entry.getKey().replace("{", "").replace("}", "");
            contextData.put(key, entry.getValue());
        }
        contextData.put(Constants.FIELD_LOCALE, locale);

        // Create one ScheduledNotification per MDMS scheduledNotification entry
        for (JsonNode scheduledNode : scheduledNotificationsArray) {
            String templateCode = scheduledNode.path(Constants.FIELD_TEMPLATE_CODE).asText();

            // Calculate scheduled date from delay config
            LocalDate scheduledDate = calculateScheduledDate(scheduledNode, eventDate);

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
                    .scheduledAt(scheduledDate)
                    .createdAt(eventDate)
                    .status(NotificationStatus.PENDING)
                    .build();

            notifications.add(notification);

            log.info("Built ScheduledNotification for task: {}, templateCode: {}, createdAt: {}, scheduledAt: {}",
                    task.getId(), templateCode, eventDate, scheduledDate);
        }

        log.info("Built {} ScheduledNotification objects for task: {}", notifications.size(), task.getId());
        return notifications;
    }

    /**
     * Calculates the scheduled date from MDMS delay config.
     *
     * delayHours is converted to days (delayHours / 24) and added/subtracted from eventDate.
     * e.g., delayHours=24 → +1 day, delayHours=48 → +2 days, delayHours=72 → +3 days
     *
     * @param scheduledNode The MDMS scheduledNotification node
     * @param eventDate     The event date in configured timezone
     * @return The scheduled date
     */
    private LocalDate calculateScheduledDate(JsonNode scheduledNode, LocalDate eventDate) {
        String delayType = scheduledNode.path(Constants.FIELD_DELAY_TYPE).asText(Constants.DELAY_TYPE_AFTER_EVENT);
        int delayHours = scheduledNode.path(Constants.FIELD_DELAY_HOURS).asInt(0);

        long delayDays = delayHours / 24;

        if (Constants.DELAY_TYPE_BEFORE_START.equals(delayType)) {
            return eventDate.minusDays(delayDays);
        } else {
            return eventDate.plusDays(delayDays);
        }
    }

    private String extractBeneficiaryType(Project project, String taskId) {
        try {
            if (project.getAdditionalDetails() != null) {
                JsonNode additionalDetailsNode = objectMapper.valueToTree(project.getAdditionalDetails());
                JsonNode projectTypeNode = additionalDetailsNode.get(Constants.FIELD_PROJECT_TYPE);
                if (projectTypeNode != null && !projectTypeNode.isNull()) {
                    JsonNode beneficiaryTypeNode = projectTypeNode.get(Constants.FIELD_BENEFICIARY_TYPE);
                    if (beneficiaryTypeNode != null && !beneficiaryTypeNode.isNull()) {
                        return beneficiaryTypeNode.asText();
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error extracting beneficiaryType from additionalDetails for task: {}", taskId, e);
        }
        return null;

    }
}