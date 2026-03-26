package org.egov.healthnotification.service.hfreferral;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.healthnotification.Constants;
import org.egov.healthnotification.config.HealthNotificationProperties;
import org.egov.healthnotification.service.FacilityUserService;
import org.egov.healthnotification.service.MdmsService;
import org.egov.healthnotification.web.models.MdmsV2Data;
import org.egov.healthnotification.web.models.NotificationEvent;
import org.egov.healthnotification.web.models.enums.NotificationChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates HFReferral events into generic NotificationEvent(s).
 *
 * Key design:
 *   - Only referral creation triggers a push notification (not updates)
 *   - projectFacilityId is resolved to facilityId via project facility search API
 *   - Notification recipient is the resolved facilityId
 *     (facility staff / HOIC responsible for receiving referrals)
 *   - Message is a generic static string from localization — no placeholders
 *   - Navigation data carries referralCode, beneficiaryId etc. for app screen redirect
 *   - MDMS campaignType is "PUSH-NOTIFICATION", event type is REFERRAL_CREATED_PUSH_NOTIFICATION
 */
@Service
@Slf4j
public class HFReferralNotificationAdapter {

    private final MdmsService mdmsService;
    private final FacilityUserService facilityUserService;
    private final HealthNotificationProperties properties;

    @Autowired
    public HFReferralNotificationAdapter(MdmsService mdmsService,
                                          FacilityUserService facilityUserService,
                                          HealthNotificationProperties properties) {
        this.mdmsService = mdmsService;
        this.facilityUserService = facilityUserService;
        this.properties = properties;
    }

    /**
     * Builds NotificationEvent(s) from an HFReferral record.
     *
     * @param record The raw JSON record with schema "HFReferral"
     * @param topic  The Kafka topic it came from
     * @return List of NotificationEvents
     */
    public List<NotificationEvent> buildNotificationEvents(JsonNode record, String topic) {
        List<NotificationEvent> events = new ArrayList<>();

        String recordId = record.path("id").asText("unknown");
        String clientReferenceId = record.path("clientReferenceId").asText("unknown");

        log.info("HFReferral notification adapter invoked for record id={}, clientReferenceId={}, topic={}",
                recordId, clientReferenceId, topic);

        // Only send notifications for referral creation, not updates
        if (!isCreateTopic(topic)) {
            log.info("Skipping HFReferral notification for update topic={}. Only create events trigger notifications.", topic);
            return events;
        }

        String tenantId = record.path("tenantId").asText("");
        if (tenantId.isBlank()) {
            log.warn("No tenantId in HFReferral record id={}. Skipping.", recordId);
            return events;
        }

        String eventType = Constants.EVENT_TYPE_REFERRAL_CREATED;

        // Fetch MDMS config using campaignType "PUSH-NOTIFICATION"
        MdmsV2Data notificationConfig = fetchNotificationConfig(tenantId);
        if (notificationConfig == null) {
            log.info("No MDMS notification config found for push notifications. tenantId={}", tenantId);
            return events;
        }

        // Find the matching event config from MDMS
        JsonNode eventConfig = findEventConfig(notificationConfig, eventType);
        if (eventConfig == null) {
            log.info("No enabled event config found for eventType={}", eventType);
            return events;
        }

        // Extract templateCode from scheduledNotifications[0]
        String templateCode = extractTemplateCode(eventConfig);
        if (templateCode == null || templateCode.isBlank()) {
            log.warn("No templateCode found for eventType={}. Skipping.", eventType);
            return events;
        }

        // Extract locale
        List<String> locales = extractLocales(notificationConfig);
        String locale = (locales != null && !locales.isEmpty()) ? locales.get(0) : Constants.DEFAULT_LOCALE;

        // Resolve projectFacilityId → facilityId via project facility search API
        String projectFacilityId = record.path("projectFacilityId").asText("");
        if (projectFacilityId.isBlank()) {
            log.warn("No projectFacilityId in HFReferral record id={}. Skipping notification.", recordId);
            return events;
        }

        String facilityId = facilityUserService.resolveProjectFacilityId(projectFacilityId, tenantId);
        if (facilityId == null || facilityId.isBlank()) {
            log.warn("Could not resolve facilityId from projectFacilityId={}. Skipping notification.", projectFacilityId);
            return events;
        }

        // Build placeholders from record + additionalFields (for future use)
        Map<String, Object> placeholders = buildPlaceholders(record, tenantId);

        // Build navigation data for screen redirect
        Map<String, String> navigationData = buildNavigationData(record);

        String title = Constants.TITLE_REFERRAL_CREATED;
        String entityId = recordId.equals("unknown") ? clientReferenceId : recordId;

        events.add(buildEvent(entityId, eventType, tenantId, templateCode,
                locale, facilityId, placeholders, navigationData, title));

        log.info("Built {} notification event(s) for HFReferral id={}, eventType={}, facilityId={}",
                events.size(), entityId, eventType, facilityId);
        return events;
    }

    // -------------------------------------------------------
    //  Topic Check
    // -------------------------------------------------------

    /**
     * Checks if the topic is a create topic (not update).
     * Supports multi-tenant topic patterns (e.g., "ba-save-hfreferral-topic").
     */
    private boolean isCreateTopic(String topic) {
        String createTopic = properties.getHfReferralCreateTopic();
        return topic != null && (topic.equals(createTopic) || topic.endsWith(createTopic));
    }

    // -------------------------------------------------------
    //  Placeholder Building
    // -------------------------------------------------------

    /**
     * Builds placeholder map from HFReferral record fields and additionalFields.
     * Currently the message is generic with no placeholders, but these are
     * populated for future use if the template is updated.
     */
    private Map<String, Object> buildPlaceholders(JsonNode record, String tenantId) {
        Map<String, Object> placeholders = new HashMap<>();

        placeholders.put(Constants.PLACEHOLDER_REFERRAL_NAME,
                getAdditionalFieldValue(record, Constants.ADDITIONAL_FIELD_NAME_OF_REFERRAL,
                        record.path("name").asText("")));
        placeholders.put(Constants.PLACEHOLDER_REFERRAL_CODE,
                record.path("referralCode").asText(""));
        placeholders.put(Constants.PLACEHOLDER_SYMPTOM,
                record.path("symptom").asText(""));
        placeholders.put(Constants.PLACEHOLDER_GENDER,
                getAdditionalFieldValue(record, Constants.ADDITIONAL_FIELD_GENDER, ""));
        placeholders.put(Constants.PLACEHOLDER_AGE_IN_MONTHS,
                getAdditionalFieldValue(record, Constants.ADDITIONAL_FIELD_AGE_IN_MONTHS, ""));
        placeholders.put(Constants.PLACEHOLDER_REFERRAL_CYCLE,
                getAdditionalFieldValue(record, Constants.ADDITIONAL_FIELD_REFERRAL_CYCLE, ""));
        placeholders.put(Constants.PLACEHOLDER_REFERRED_BY,
                getAdditionalFieldValue(record, Constants.ADDITIONAL_FIELD_REFERRED_BY, ""));
        placeholders.put(Constants.PLACEHOLDER_DATE_OF_EVALUATION,
                getAdditionalFieldValue(record, Constants.ADDITIONAL_FIELD_DATE_OF_EVALUATION, ""));
        placeholders.put(Constants.PLACEHOLDER_ADMINISTRATIVE_AREA,
                getAdditionalFieldValue(record, Constants.ADDITIONAL_FIELD_ADMINISTRATIVE_AREA, ""));

        return placeholders;
    }

    // -------------------------------------------------------
    //  Navigation Data (screen redirect)
    // -------------------------------------------------------

    /**
     * Builds navigation data for the push notification payload.
     * This data is used by the mobile app to redirect the user
     * to the referral details screen with prefilled beneficiary info.
     */
    private Map<String, String> buildNavigationData(JsonNode record) {
        Map<String, String> data = new HashMap<>();
        data.put("notificationType", Constants.NOTIFICATION_TYPE_REFERRAL);
        data.put("eventType", Constants.EVENT_TYPE_REFERRAL_CREATED);
        data.put("referralCode", record.path("referralCode").asText(""));
        data.put("beneficiaryId", record.path("beneficiaryId").asText(""));
        data.put("projectId", record.path("projectId").asText(""));
        data.put("projectFacilityId", record.path("projectFacilityId").asText(""));
        data.put("screen", Constants.SCREEN_REFERRAL_DETAILS);
        return data;
    }

    // -------------------------------------------------------
    //  MDMS Helpers
    // -------------------------------------------------------

    private MdmsV2Data fetchNotificationConfig(String tenantId) {
        try {
            return mdmsService.fetchNotificationConfigByProjectType(
                    Constants.CAMPAIGN_TYPE_PUSH_NOTIFICATION, tenantId);
        } catch (Exception e) {
            log.error("Failed to fetch push notification config from MDMS: {}", e.getMessage());
            return null;
        }
    }

    private JsonNode findEventConfig(MdmsV2Data config, String eventType) {
        JsonNode eventNotifications = config.getData().get(Constants.FIELD_EVENT_NOTIFICATIONS);
        if (eventNotifications == null || !eventNotifications.isArray()) {
            return null;
        }

        for (JsonNode eventNode : eventNotifications) {
            String type = eventNode.path(Constants.FIELD_EVENT_TYPE).asText();
            boolean enabled = eventNode.path(Constants.FIELD_ENABLED).asBoolean(false);

            if (eventType.equals(type) && enabled) {
                return eventNode;
            }
        }
        return null;
    }

    private String extractTemplateCode(JsonNode eventConfig) {
        JsonNode scheduledNotifications = eventConfig.path(Constants.FIELD_SCHEDULED_NOTIFICATIONS);
        if (scheduledNotifications.isArray() && scheduledNotifications.size() > 0) {
            JsonNode first = scheduledNotifications.get(0);
            if (first.path(Constants.FIELD_ENABLED).asBoolean(false)) {
                return first.path(Constants.FIELD_TEMPLATE_CODE).asText(null);
            }
        }
        return null;
    }

    private List<String> extractLocales(MdmsV2Data config) {
        List<String> locales = new ArrayList<>();
        try {
            JsonNode localeNode = config.getData().path(Constants.FIELD_LOCALE);
            if (localeNode.isArray()) {
                for (JsonNode node : localeNode) {
                    String locale = node.asText();
                    if (locale != null && !locale.isBlank()) {
                        locales.add(locale);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error extracting locales from MDMS config: {}", e.getMessage());
        }
        return locales;
    }

    // -------------------------------------------------------
    //  AdditionalFields Helper
    // -------------------------------------------------------

    /**
     * Extracts a value from additionalFields.fields[] by key.
     * Falls back to defaultValue if not found.
     */
    private String getAdditionalFieldValue(JsonNode record, String key, String defaultValue) {
        JsonNode fields = record.path("additionalFields").path("fields");
        if (fields.isArray()) {
            for (JsonNode field : fields) {
                if (key.equals(field.path("key").asText())) {
                    return field.path("value").asText(defaultValue);
                }
            }
        }
        return defaultValue;
    }

    // -------------------------------------------------------
    //  Event Builder
    // -------------------------------------------------------

    private NotificationEvent buildEvent(String entityId, String eventType, String tenantId,
                                          String templateCode, String locale,
                                          String facilityId,
                                          Map<String, Object> placeholders, Map<String, String> data,
                                          String title) {
        return NotificationEvent.builder()
                .tenantId(tenantId)
                .eventType(eventType)
                .entityType(Constants.ENTITY_TYPE_HF_REFERRAL)
                .entityId(entityId)
                .templateCode(templateCode)
                .title(title)
                .locale(locale)
                .recipientFacilityId(facilityId)
                .placeholders(placeholders)
                .data(data)
                .channel(NotificationChannel.PUSH)
                .build();
    }
}