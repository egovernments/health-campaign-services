package org.egov.healthnotification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.common.models.Error;
import org.egov.healthnotification.Constants;
import org.egov.healthnotification.config.HealthNotificationProperties;
import org.egov.healthnotification.producer.HealthNotificationProducer;
import org.egov.healthnotification.service.enrichment.ScheduledNotificationEnrichmentService;
import org.egov.healthnotification.util.HealthNotificationUtils;
import org.egov.healthnotification.web.models.ScheduledNotification;
import org.egov.healthnotification.web.models.enums.NotificationStatus;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;

/**
 * Processes a batch of ScheduledNotifications:
 *   1. Builds the final SMS message from templateCode + contextData (via LocalizationService)
 *   2. Pushes an SMSRequest to the egov.core.notification.sms Kafka topic
 *   3. Updates the notification status to SENT or FAILED
 *
 * The egov-notification-sms service consumes from that topic and handles the actual SMS delivery.
 *
 * SMSRequest format expected by egov-notification-sms:
 *   { "mobileNumber": "...", "message": "..." }
 */
@Service
@Slf4j
public class NotificationDispatchService {

    private final LocalizationService localizationService;
    private final HealthNotificationProducer producer;
    private final ScheduledNotificationEnrichmentService enrichmentService;
    private final HealthNotificationProperties properties;
    private final ObjectMapper objectMapper;
    private final MultiStateInstanceUtil multiStateInstanceUtil;

    @Autowired
    public NotificationDispatchService(LocalizationService localizationService,
                                       HealthNotificationProducer producer,
                                       ScheduledNotificationEnrichmentService enrichmentService,
                                       HealthNotificationProperties properties,
                                       ObjectMapper objectMapper,
                                       MultiStateInstanceUtil multiStateInstanceUtil) {
        this.localizationService = localizationService;
        this.producer = producer;
        this.enrichmentService = enrichmentService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.multiStateInstanceUtil = multiStateInstanceUtil;
    }

    /**
     * Dispatches a batch of notifications: build message, push to SMS topic, update status.
     * Uses populateErrorDetails pattern — one failure does not block others.
     * Returns error map for caller visibility.
     */
    public Map<ScheduledNotification, List<Error>> dispatchBatch(List<ScheduledNotification> batch, String tenantId) {
        Map<ScheduledNotification, List<Error>> errorDetailsMap = new HashMap<>();

        for (ScheduledNotification notification : batch) {
            try {
                processNotification(notification, tenantId);
            } catch (Exception e) {
                log.error("Failed to dispatch notification id={}, entityId={}: {}",
                        notification.getId(), notification.getEntityId(), e.getMessage(), e);

                Error error = Error.builder()
                        .errorCode(e instanceof CustomException ? ((CustomException) e).getCode() : "DISPATCH_FAILED")
                        .errorMessage(e.getMessage())
                        .type(isRecoverable(e) ? Error.ErrorType.RECOVERABLE : Error.ErrorType.NON_RECOVERABLE)
                        .exception(new CustomException("DISPATCH_FAILED", e.getMessage()))
                        .build();
                populateErrorDetails(notification, error, errorDetailsMap);

                markFailed(notification, e.getMessage());
            }
        }

        if (!errorDetailsMap.isEmpty()) {
            log.error("Batch dispatch completed with {} errors out of {} notifications",
                    errorDetailsMap.size(), batch.size());
        }

        return errorDetailsMap;
    }

    /**
     * Determines if an exception is recoverable (transient) or not.
     * Recoverable: timeouts, service unavailable, Kafka send failures
     * Non-recoverable: bad data, missing template, null fields
     */
    private boolean isRecoverable(Exception e) {
        if (e instanceof IllegalStateException) return false;
        if (e instanceof CustomException) {
            String code = ((CustomException) e).getCode();
            return !"LOCALIZATION_MESSAGE_NOT_FOUND".equals(code);
        }
        return true;
    }

    /**
     * Processes a single notification:
     *   1. Fetch localized template using templateCode + locale from contextData
     *   2. Replace placeholders using contextData values
     *   3. Push SMSRequest to Kafka topic
     *   4. Update status to SENT
     */
    private void processNotification(ScheduledNotification notification, String tenantId) {
        String templateCode = notification.getTemplateCode();

        // contextData should be Map after decryption, cast it safely
        Object contextDataObj = notification.getContextData();
        if (!(contextDataObj instanceof Map)) {
            throw new IllegalStateException("contextData is not a Map (possibly encrypted) for notification: " + notification.getId());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> contextData = (Map<String, Object>) contextDataObj;

        if (contextData == null || contextData.isEmpty()) {
            throw new IllegalStateException("contextData is null/empty for notification: " + notification.getId());
        }

        // Extract locale from contextData (stored during event processing)
        String locale = (String) contextData.getOrDefault(Constants.FIELD_LOCALE, "en_NG");

        // Use state-level tenantId for localization lookup
        String localizationTenantId = tenantId != null ? tenantId : notification.getTenantId();

        // Step 1: Fetch localized message template (from cache or API)
        String messageTemplate = localizationService.getMessageTemplate(
                templateCode, locale, localizationTenantId);

        // Step 2: Replace placeholders with actual values from contextData
        ZoneId zoneId = ZoneId.of(properties.getNotificationTimezone());
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(Constants.DATE_FORMAT_YYYY_MM_DD);
        String finalMessage = HealthNotificationUtils.replacePlaceholders(messageTemplate, contextData, zoneId, dateFormatter);

        // Step 3: Build and push SMSRequest to Kafka
        String mobileNumber = notification.getMobileNumber();

        if (mobileNumber == null || mobileNumber.isBlank()) {
            throw new IllegalStateException("mobileNumber is null/blank for notification: " + notification.getId());
        }

        pushToSmsTopic(mobileNumber, finalMessage);

        log.info("Dispatched SMS for notification id={}, templateCode={}, mobileNumber={}",
                notification.getId(), templateCode, maskMobileNumber(mobileNumber));

        // Step 4: Update status to SENT
        markSent(notification);
    }

    /**
     * Pushes an SMSRequest to the egov.core.notification.sms Kafka topic.
     * Format: { "mobileNumber": "...", "message": "..." }
     */
    private void pushToSmsTopic(String mobileNumber, String message) {
        Map<String, Object> smsRequest = new HashMap<>();
        smsRequest.put("mobileNumber", mobileNumber);
        smsRequest.put("message", message);

        // TODO: Once partner SMS integration is ready, remove this log and rely on egov-notification-sms
        log.info("Pushing SMSRequest to topic: {}, mobileNumber={}, message={}",
                properties.getSmsNotificationTopic(),
                maskMobileNumber(mobileNumber),
                message);

        producer.push(properties.getSmsNotificationTopic(), smsRequest);
    }

    private void markSent(ScheduledNotification notification) {
        notification.setStatus(NotificationStatus.SENT);
        notification.setAttempts(notification.getAttempts() + 1);
        notification.setLastAttemptAt(System.currentTimeMillis());
        updateNotification(notification);
    }

    private void markFailed(ScheduledNotification notification, String errorMessage) {
        notification.setStatus(NotificationStatus.FAILED);
        notification.setAttempts(notification.getAttempts() + 1);
        notification.setLastAttemptAt(System.currentTimeMillis());
        // Truncate error message to fit DB column
        notification.setErrorMessage(
                errorMessage != null && errorMessage.length() > 500
                        ? errorMessage.substring(0, 500)
                        : errorMessage);
        updateNotification(notification);
    }

    private void updateNotification(ScheduledNotification notification) {
        try {
            enrichmentService.enrichForUpdate(Collections.singletonList(notification), null);

            // Central instance support: Include tenantId in topic name if central instance is enabled
            if (multiStateInstanceUtil.getIsEnvironmentCentralInstance()) {
                log.debug("Central instance enabled. Publishing to state-specific persister topic for tenant: {}",
                        notification.getTenantId());
                producer.push(notification.getTenantId(),
                        properties.getScheduledNotificationUpdateTopic(),
                        Collections.singletonList(notification));
            } else {
                log.debug("Central instance disabled. Publishing to standard persister topic");
                producer.push(properties.getScheduledNotificationUpdateTopic(),
                        Collections.singletonList(notification));
            }
        } catch (Exception e) {
            log.error("Failed to update notification status for id={}: {}",
                    notification.getId(), e.getMessage(), e);
        }
    }

    private String maskMobileNumber(String mobileNumber) {
        if (mobileNumber == null || mobileNumber.length() < 4) return "****";
        return "****" + mobileNumber.substring(mobileNumber.length() - 4);
    }
}