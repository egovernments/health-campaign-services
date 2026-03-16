package org.egov.healthnotification.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.healthnotification.config.HealthNotificationProperties;
import org.egov.healthnotification.web.models.NotificationEvent;
import org.egov.healthnotification.web.models.enums.NotificationChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.egov.healthnotification.Constants;
import org.egov.healthnotification.util.HealthNotificationUtils;

/**
 * Generic notification processor — completely service-agnostic.
 *
 * Receives NotificationEvent objects (built by service-specific adapters),
 * resolves localization templates, replaces placeholders, and sends the
 * notification via the appropriate channel (PUSH or SMS).
 *
 * To add a new service (e.g., ReferralManagement):
 *   1. Create an adapter that builds NotificationEvent
 *   2. Call processAndSend() — no changes needed here.
 */
@Service
@Slf4j
public class NotificationProcessorService {

    private final LocalizationService localizationService;
    private final PushNotificationService pushNotificationService;
    private final HealthNotificationProperties properties;

    @Autowired
    public NotificationProcessorService(LocalizationService localizationService,
                                         PushNotificationService pushNotificationService,
                                         HealthNotificationProperties properties) {
        this.localizationService = localizationService;
        this.pushNotificationService = pushNotificationService;
        this.properties = properties;
    }

    /**
     * Processes a single NotificationEvent: resolves templates, builds message, sends notification.
     *
     * @param event The notification event to process
     */
    public void processAndSend(NotificationEvent event) {
        log.info("Processing notification event: eventType={}, entityType={}, entityId={}, channel={}",
                event.getEventType(), event.getEntityType(), event.getEntityId(), event.getChannel());

        String tenantId = event.getTenantId();
        String locale = event.getLocale() != null ? event.getLocale() : "en_NG";

        // 1. Fetch and build body
        String bodyTemplate = localizationService.getMessageTemplate(
                event.getTemplateCode(), locale, tenantId);

        ZoneId zoneId = ZoneId.of(properties.getNotificationTimezone());
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(Constants.DATE_FORMAT_YYYY_MM_DD);
        String body = HealthNotificationUtils.replacePlaceholders(bodyTemplate, event.getPlaceholders(), zoneId, dateFormatter);

        // 2. Fetch and build title (for push notifications)
        String title = null;
        if (event.getTitleTemplateCode() != null && !event.getTitleTemplateCode().isBlank()) {
            String titleTemplate = localizationService.getMessageTemplate(
                    event.getTitleTemplateCode(), locale, tenantId);
            title = HealthNotificationUtils.replacePlaceholders(titleTemplate, event.getPlaceholders(), zoneId, dateFormatter);
        }

        // 3. Send based on channel
        if (event.getChannel() == NotificationChannel.PUSH) {
            if (title == null || title.isBlank()) {
                title = event.getEventType();
            }
            pushNotificationService.sendPushNotification(
                    title, body, event.getRecipientFacilityId(),
                    tenantId, event.getData());
        } else {
            log.warn("SMS channel not supported in immediate push flow. eventType={}, entityId={}",
                    event.getEventType(), event.getEntityId());
        }

        log.info("Notification processed successfully: eventType={}, entityId={}",
                event.getEventType(), event.getEntityId());
    }

    /**
     * Processes a batch of NotificationEvents.
     * One event failure does not block others.
     *
     * @param events The list of notification events to process
     */
    public void processAndSendBatch(List<NotificationEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        log.info("Processing batch of {} notification events", events.size());
        int successCount = 0;
        int failureCount = 0;

        for (NotificationEvent event : events) {
            try {
                processAndSend(event);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                log.error("Failed to process notification event: eventType={}, entityId={}: {}",
                        event.getEventType(), event.getEntityId(), e.getMessage(), e);
            }
        }

        log.info("Batch processing completed: {} success, {} failures out of {} events",
                successCount, failureCount, events.size());
    }
}
