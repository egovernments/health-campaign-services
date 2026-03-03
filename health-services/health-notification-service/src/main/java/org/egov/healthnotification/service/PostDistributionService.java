package org.egov.healthnotification.service;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    @Autowired
    public PostDistributionService(ProjectService projectService,
                                   MdmsService mdmsService,
                                   HouseholdService householdService,
                                   IndividualService individualService,
                                   LocalizationService localizationService,
                                   ObjectMapper objectMapper) {
        this.projectService = projectService;
        this.mdmsService = mdmsService;
        this.householdService = householdService;
        this.individualService = individualService;
        this.localizationService = localizationService;
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
                Map<String, String> householdDetails = fetchHouseHoldDetails(
                        beneficiaryType, projectBeneficiaryClientRefId, projectType, tenantId);

                String mobileNumber = householdDetails.get("mobileNumber");
                String altContactNumber = householdDetails.get("altContactNumber");
                if ((mobileNumber == null || mobileNumber.isBlank())
                        || (altContactNumber == null || altContactNumber.isBlank())) {
                    log.info("Household/beneficiary does not have any mobileNumber or altContactNumber. " +
                                    "Skipping notification scheduling for task: {}",
                            task.getId());
                    return;
                }

                log.info("Fetched household details for task: {}, beneficiaryType: {}", task.getId(), beneficiaryType);

                // Step 5: Fetch event notification configuration for the given event type
                String eventType = projectType + Constants.EVENT_TYPE_SUFFIX_POST_DISTRIBUTION;
                JsonNode eventNotificationConfig = getEnabledEventNotificationConfig(notificationConfig, eventType, task.getId());
                if (eventNotificationConfig == null) {
                    return;
                }

                // Step 6: Build placeholder messages
                List<Map<String, String>> finalMessages = buildPlaceholderMessages(
                        householdDetails, eventNotificationConfig, notificationConfig, task, project, tenantId);

                if (finalMessages != null && !finalMessages.isEmpty()) {
                    log.info("Successfully built {} finalized notification message(s) for task: {}",
                            finalMessages.size(), task.getId());

                    // Log sample message data for debugging
                    log.debug("Sample message data: {}", finalMessages.get(0));

                    // TODO: Step 7 - Implement repository layer to persist notification
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
     * Fetches the event notification configuration for a given event type from the notificationConfig.
     * Only returns the configuration if the eventType is enabled. Also filters scheduledNotifications
     * to return only those entries which are enabled.
     *
     * @param notificationConfig The MDMS notification configuration
     * @param eventType          The event type to look up
     * @param taskId             The id of the task for logging context
     * @return JsonNode representing the matched and enabled eventNotification object, or null if not found / disabled
     */
    private JsonNode getEnabledEventNotificationConfig(
            MdmsV2Data notificationConfig,
            String eventType,
            String taskId) {

        // Step 0: Basic null validation
        if (notificationConfig == null || notificationConfig.getData() == null) {
            log.info("Notification config is null. Skipping notification for task: {}", taskId);
            return null;
        }

        JsonNode eventNotifications = notificationConfig.getData().get("eventNotifications");

        if (eventNotifications == null || !eventNotifications.isArray()) {
            log.info("eventNotifications array not found. Skipping notification for task: {}", taskId);
            return null;
        }

        // Step 1: Find matching eventType
        for (JsonNode eventNode : eventNotifications) {

            JsonNode eventTypeNode = eventNode.get("eventType");

            if (eventTypeNode != null && eventType.equals(eventTypeNode.asText())) {

                // Step 2: Check if event is enabled
                boolean eventEnabled = eventNode.path("enabled").asBoolean(false);

                if (!eventEnabled) {
                    log.info("Event type {} is disabled. Skipping notification for task: {}", eventType, taskId);
                    return null;
                }

                // Step 3: Filter scheduledNotifications
                JsonNode scheduledArray = eventNode.get("scheduledNotifications");

                if (scheduledArray == null || !scheduledArray.isArray()) {
                    log.info("No scheduledNotifications found for eventType: {}. Skipping task: {}", eventType, taskId);
                    return null;
                }

                ArrayNode enabledScheduledNotifications = objectMapper.createArrayNode();

                for (JsonNode scheduledNode : scheduledArray) {
                    if (scheduledNode.path("enabled").asBoolean(false)) {
                        enabledScheduledNotifications.add(scheduledNode);
                    }
                }

                if (enabledScheduledNotifications.isEmpty()) {
                    log.info("No enabled scheduledNotifications for eventType: {}. Skipping task: {}", eventType, taskId);
                    return null;
                }

                // Step 4: Create filtered response object
                ObjectNode resultNode = objectMapper.createObjectNode();
                resultNode.set("eventType", eventNode.get("eventType"));
                resultNode.set("enabled", eventNode.get("enabled"));
                resultNode.set("recipientType", eventNode.get("recipientType"));
                resultNode.set("placeholders", eventNode.get("placeholders"));
                resultNode.set("scheduledNotifications", enabledScheduledNotifications);

                log.info("Returning enabled notification config for eventType: {} with {} scheduled notifications for task: {}",
                        eventType, enabledScheduledNotifications.size(), taskId);

                return resultNode;
            }
        }

        log.info("No matching eventType {} found. Skipping notification for task: {}", eventType, taskId);
        return null;
    }

    /**
     * Builds placeholder messages for scheduled notifications by fetching localization and replacing placeholders.
     * This method:
     * 1. Extracts locale from the notification config
     * 2. Fetches localization messages for all enabled scheduledNotifications
     * 3. Builds a placeholder map from householdDetails, task, and project data
     * 4. Replaces placeholders in the message templates with actual values
     * 5. Returns list of maps containing templateCode, locale, and finalized message
     *
     * @param householdDetails         Map containing household head details (givenName, mobileNumber, altContactNumber, etc.)
     * @param eventNotificationConfig  JsonNode containing the event notification configuration with scheduledNotifications
     * @param notificationConfig       MdmsV2Data containing the full notification config (for locale extraction)
     * @param task                     The Task object containing delivery date, time, and other task details
     * @param project                  The Project object containing campaign name and other project details
     * @param tenantId                 The tenant ID
     * @return List of maps, each containing "templateCode", "locale", and "message"
     */
    private List<Map<String, String>> buildPlaceholderMessages(Map<String, String> householdDetails,
                                                               JsonNode eventNotificationConfig,
                                                               MdmsV2Data notificationConfig,
                                                               Task task,
                                                               Project project,
                                                               String tenantId) {
        String taskId = task.getId();
        log.info("Building placeholder messages for scheduled notifications for task: {}", taskId);

        List<Map<String, String>> finalMessagesList = new ArrayList<>();

        // Step 1: Extract all locales from notificationConfig
        List<String> locales = extractLocales(notificationConfig, taskId);
        if (locales == null || locales.isEmpty()) {
            log.info("No locales found in notification config. Skipping notification for task: {}", taskId);
            return finalMessagesList;
        }
        log.info("Found {} locale(s) for task: {}", locales.size(), taskId);

        // Step 2: Extract scheduledNotifications array from eventNotificationConfig
        JsonNode scheduledNotificationsArray = eventNotificationConfig.get("scheduledNotifications");
        if (scheduledNotificationsArray == null || !scheduledNotificationsArray.isArray() || scheduledNotificationsArray.isEmpty()) {
            log.info("No scheduledNotifications found in eventNotificationConfig. Skipping task: {}", taskId);
            return finalMessagesList;
        }

        // Step 3: Extract all template codes from scheduledNotifications
        List<String> templateCodes = new ArrayList<>();
        for (JsonNode scheduledNode : scheduledNotificationsArray) {
            JsonNode templateCodeNode = scheduledNode.get("templateCode");
            if (templateCodeNode != null && !templateCodeNode.isNull()) {
                templateCodes.add(templateCodeNode.asText());
            }
        }

        if (templateCodes.isEmpty()) {
            log.info("No template codes found in scheduledNotifications. Skipping task: {}", taskId);
            return finalMessagesList;
        }

        log.info("Found {} template codes to fetch localization for task: {}", templateCodes.size(), taskId);

        // Step 4: Fetch localization messages for all template codes and all locales
        // Structure: Map<templateCode, Map<locale, message>>
        Map<String, Map<String, String>> localizationMessages = new HashMap<>();

        for (String locale : locales) {
            try {
                Map<String, String> messagesForLocale = localizationService.fetchLocalizationMessages(
                        templateCodes, locale, tenantId);

                // Organize messages by templateCode -> locale -> message
                for (Map.Entry<String, String> entry : messagesForLocale.entrySet()) {
                    String templateCode = entry.getKey();
                    String message = entry.getValue();

                    localizationMessages.computeIfAbsent(templateCode, k -> new HashMap<>())
                            .put(locale, message);
                }

                log.info("Successfully fetched {} localization messages for locale: {}, task: {}",
                        messagesForLocale.size(), locale, taskId);
            } catch (Exception e) {
                log.error("Error fetching localization messages for locale: {}, task: {}. Continuing with other locales.",
                        locale, taskId, e);
                // Continue with other locales even if one fails
            }
        }

        if (localizationMessages.isEmpty()) {
            log.error("Failed to fetch any localization messages for all locales. Skipping task: {}", taskId);
            return finalMessagesList;
        }

        log.info("Successfully fetched localization messages for {} template codes across {} locales for task: {}",
                localizationMessages.size(), locales.size(), taskId);

        // Step 5: Build placeholder map from householdDetails, task, project, and eventNotificationConfig
        Map<String, String> placeholderMap = buildPlaceholderMap(householdDetails, eventNotificationConfig,
                task, project, taskId);
        log.info("Built placeholder map with {} entries for task: {}", placeholderMap.size(), taskId);

        // Step 6: Process each scheduled notification for each locale and build finalized messages
        for (JsonNode scheduledNode : scheduledNotificationsArray) {
            String templateCode = scheduledNode.path("templateCode").asText();

            // Get all localized message templates for this templateCode
            Map<String, String> messagesForAllLocales = localizationMessages.get(templateCode);
            if (messagesForAllLocales == null || messagesForAllLocales.isEmpty()) {
                log.warn("No localization messages found for templateCode: {}. Skipping this scheduled notification.",
                        templateCode);
                continue;
            }

            // Process notification for each locale
            for (String locale : locales) {
                String messageTemplate = messagesForAllLocales.get(locale);
                if (messageTemplate == null || messageTemplate.isBlank()) {
                    log.warn("No localization message found for templateCode: {}, locale: {}. Skipping this locale.",
                            templateCode, locale);
                    continue;
                }

                // Replace placeholders in the message template
                String finalMessage = replacePlaceholders(messageTemplate, placeholderMap);

                // Create a map to hold the message data
                Map<String, String> messageData = new HashMap<>();
                messageData.put("templateCode", templateCode);
                messageData.put("locale", locale);
                messageData.put("message", finalMessage);

                // Add to the list
                finalMessagesList.add(messageData);

                log.info("Built finalized message for task: {}, templateCode: {}, locale: {}",
                        taskId, templateCode, locale);
            }
        }

        log.info("Completed building {} finalized message(s) for task: {}",
                finalMessagesList.size(), taskId);

        return finalMessagesList;
    }

    /**
     * Extracts all locales from the notification config.
     * The locale is expected to be in the format: ["en_NG", "fr_NG", "ha_NG"] in the data.locale array.
     *
     * @param notificationConfig The MDMS notification configuration
     * @param taskId             The task ID for logging context
     * @return List of locale strings, or empty list if not found
     */
    private List<String> extractLocales(MdmsV2Data notificationConfig, String taskId) {
        List<String> locales = new ArrayList<>();

        try {
            JsonNode dataNode = notificationConfig.getData();
            if (dataNode == null || !dataNode.has("locale")) {
                log.warn("No locale field found in notification config for task: {}", taskId);
                return locales;
            }

            JsonNode localeNode = dataNode.get("locale");
            if (localeNode.isArray() && localeNode.size() > 0) {
                for (JsonNode localeElement : localeNode) {
                    String locale = localeElement.asText();
                    if (locale != null && !locale.isBlank()) {
                        locales.add(locale);
                    }
                }
                log.info("Extracted {} locale(s) from notification config for task: {}", locales.size(), taskId);
                return locales;
            }

            log.warn("Locale array is empty in notification config for task: {}", taskId);
            return locales;
        } catch (Exception e) {
            log.error("Error extracting locales from notification config for task: {}", taskId, e);
            return locales;
        }
    }

    /**
     * Builds a placeholder map from householdDetails, task, project, and eventNotificationConfig.
     * Maps placeholder keys (e.g., {HouseholdHeadName}) to actual values from various sources.
     *
     * @param householdDetails        Map containing household head details
     * @param eventNotificationConfig JsonNode containing placeholders array
     * @param task                    The Task object containing delivery/distribution details
     * @param project                 The Project object containing campaign details
     * @param taskId                  The task ID for logging context
     * @return Map of placeholder to value
     */
    private Map<String, String> buildPlaceholderMap(Map<String, String> householdDetails,
                                                    JsonNode eventNotificationConfig,
                                                    Task task,
                                                    Project project,
                                                    String taskId) {
        Map<String, String> placeholderMap = new HashMap<>();

        // Extract placeholders array from eventNotificationConfig
        JsonNode placeholdersArray = eventNotificationConfig.get("placeholders");
        if (placeholdersArray == null || !placeholdersArray.isArray()) {
            log.warn("No placeholders array found in eventNotificationConfig for task: {}", taskId);
            return placeholderMap;
        }

        // Process each placeholder and map to corresponding value
        for (JsonNode placeholderNode : placeholdersArray) {
            String placeholder = placeholderNode.asText();

            // Map placeholders to actual values from householdDetails, task, or project
            String value = mapPlaceholderToValue(placeholder, householdDetails, task, project);
            if (value != null && !value.isBlank()) {
                placeholderMap.put(placeholder, value);
                log.debug("Mapped placeholder {} to value for task: {}", placeholder, taskId);
            } else {
                log.warn("No value found for placeholder: {} for task: {}", placeholder, taskId);
                placeholderMap.put(placeholder, ""); // Use empty string as fallback
            }
        }

        log.info("Built placeholder map with {} entries for task: {}", placeholderMap.size(), taskId);
        return placeholderMap;
    }

    /**
     * Maps a placeholder key to its corresponding value from householdDetails, task, or project.
     *
     * @param placeholder      The placeholder key (e.g., {HouseholdHeadName})
     * @param householdDetails Map containing household head details
     * @param task             The Task object containing delivery/distribution details
     * @param project          The Project object containing campaign details
     * @return The mapped value, or null if not found
     */
    private String mapPlaceholderToValue(String placeholder, Map<String, String> householdDetails,
                                         Task task, Project project) {
        // Remove curly braces if present
        String key = placeholder.replace("{", "").replace("}", "");

        // Map known placeholders to their actual values
        switch (key) {
            // Household/Individual related placeholders
            case "HouseholdHeadName":
                String givenName = householdDetails.get("givenName");
                return givenName != null ? givenName : "";

            case "MobileNumber":
                String mobileNumber = determineRecipientMobileNumber(householdDetails);
                return mobileNumber != null ? mobileNumber : "";

            case "EmailId":
                String emailId = householdDetails.get("emailId");
                return emailId != null ? emailId : "";

            case "IndividualId":
                String individualId = householdDetails.get("individualId");
                return individualId != null ? individualId : "";

            // Task related placeholders
            case "DistributionDate":
            case "DeliveryDate":
            case "DistributedDate":
                return formatDate(task.getPlannedStartDate());

            case "TaskId":
                String taskId = task.getId();
                return taskId != null ? taskId : "";

            case "TaskStatus":
                return task.getStatus() != null ? task.getStatus().toString() : "";

            // Project related placeholders
            case "ProjectType":
            case "CampaignType":
                String projectType = project.getProjectType();
                return projectType != null ? projectType : "";

            default:
                log.debug("Unknown placeholder: {}. Returning empty string.", placeholder);
                return "";
        }
    }

    /**
     * Formats a timestamp to a readable date string.
     *
     * @param timestamp The timestamp in milliseconds
     * @return Formatted date string (e.g., "12-March-2026")
     */
    private String formatDate(Long timestamp) {
        if (timestamp == null) {
            return "";
        }

        try {
            java.time.Instant instant = java.time.Instant.ofEpochMilli(timestamp);
            java.time.ZoneId zoneId = java.time.ZoneId.systemDefault();
            java.time.LocalDate date = instant.atZone(zoneId).toLocalDate();
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd-MMMM-yyyy");
            return date.format(formatter);
        } catch (Exception e) {
            log.error("Error formatting date from timestamp: {}", timestamp, e);
            return "";
        }
    }

    /**
     * Determines which mobile number to use for notification.
     * First checks mobileNumber, if not present or blank, uses altContactNumber.
     *
     * @param householdDetails Map containing household head details
     * @return The mobile number to use, or null if neither is available
     */
    private String determineRecipientMobileNumber(Map<String, String> householdDetails) {
        String mobileNumber = householdDetails.get("mobileNumber");
        if (mobileNumber != null && !mobileNumber.isBlank()) {
            return mobileNumber;
        }

        String altContactNumber = householdDetails.get("altContactNumber");
        if (altContactNumber != null && !altContactNumber.isBlank()) {
            return altContactNumber;
        }

        return null;
    }

    /**
     * Replaces placeholders in the message template with actual values.
     *
     * @param messageTemplate The message template with placeholders (e.g., "Hello {HouseholdHeadName}")
     * @param placeholderMap  Map of placeholder to value
     * @return The message with placeholders replaced
     */
    private String replacePlaceholders(String messageTemplate, Map<String, String> placeholderMap) {
        String result = messageTemplate;

        for (Map.Entry<String, String> entry : placeholderMap.entrySet()) {
            String placeholder = entry.getKey();
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(placeholder, value);
        }

        return result;
    }
}
