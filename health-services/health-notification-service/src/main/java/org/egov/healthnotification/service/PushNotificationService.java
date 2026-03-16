package org.egov.healthnotification.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.healthnotification.Constants;
import org.egov.healthnotification.config.HealthNotificationProperties;
import org.egov.healthnotification.producer.HealthNotificationProducer;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pushes notifications to the egov-notification-push service via Kafka.
 *
 * Sends to topic: egov.core.notification.push
 * Format matches PushNotificationRequest expected by egov-notification-push:
 *   { "title": "...", "body": "...", "deviceTokens": [...], "tenantId": "...", "data": {...} }
 *
 * NOTE: Currently sends userUuids as deviceTokens. The push service resolves
 * actual FCM tokens from these. This may change in future once clarified.
 */
@Service
@Slf4j
public class PushNotificationService {

    private final HealthNotificationProducer producer;
    private final HealthNotificationProperties properties;

    @Autowired
    public PushNotificationService(HealthNotificationProducer producer,
                                    HealthNotificationProperties properties) {
        this.producer = producer;
        this.properties = properties;
    }

    /**
     * Sends a push notification via Kafka to egov-notification-push.
     *
     * @param title      Notification title
     * @param body       Notification body text
     * @param userUuids  User UUIDs (sent as deviceTokens for now)
     * @param tenantId   Tenant ID
     * @param data       Custom metadata (screen navigation, txn ref, etc.)
     */
    public void sendPushNotification(String title, String body,
                                      List<String> userUuids,
                                      String tenantId,
                                      Map<String, String> data) {
        if (!Boolean.TRUE.equals(properties.getPushNotificationEnabled())) {
            log.info("Push notifications are disabled. Skipping push for title: {}", title);
            return;
        }

        if (userUuids == null || userUuids.isEmpty()) {
            log.warn("No userUuids provided for push notification. Skipping. title={}", title);
            return;
        }

        try {
            Map<String, Object> pushRequest = new HashMap<>();
            pushRequest.put("title", title);
            pushRequest.put("body", body);
            pushRequest.put("deviceTokens", userUuids);
            pushRequest.put("tenantId", tenantId);
            if (data != null && !data.isEmpty()) {
                pushRequest.put("data", data);
            }

            log.info("PUSH NOTIFICATION PAYLOAD: {}", pushRequest);

            // TODO: Enable Kafka push once device tokens are resolved
            // producer.push(properties.getPushNotificationTopic(), pushRequest);

        } catch (Exception e) {
            log.error("Failed to push notification to Kafka. title={}, userUuids={}: {}",
                    title, userUuids, e.getMessage(), e);
            throw new CustomException(Constants.ERROR_PUSH_NOTIFICATION_FAILED,
                    "Failed to send push notification: " + e.getMessage());
        }
    }
}
