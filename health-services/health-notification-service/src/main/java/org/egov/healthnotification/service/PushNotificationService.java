package org.egov.healthnotification.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.healthnotification.Constants;
import org.egov.healthnotification.config.HealthNotificationProperties;
import org.egov.healthnotification.producer.HealthNotificationProducer;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Pushes notifications to the egov-notification-push service via Kafka.
 *
 * Sends to topic: egov.core.notification.push
 * Format:
 *   { "title": "...", "body": "...", "facilityId": "...", "tenantId": "...", "data": {...} }
 *
 * The notification-push service resolves device tokens from facilityId and sends FCM notifications.
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
     * @param facilityId Recipient facility ID (notification-push resolves device tokens from this)
     * @param tenantId   Tenant ID
     * @param data       Custom metadata (screen navigation, txn ref, etc.)
     */
    public void sendPushNotification(String title, String body,
                                      String facilityId,
                                      String tenantId,
                                      Map<String, String> data) {
        if (!Boolean.TRUE.equals(properties.getPushNotificationEnabled())) {
            log.info("Push notifications are disabled. Skipping push for title: {}", title);
            return;
        }

        if (facilityId == null || facilityId.isBlank()) {
            log.warn("No facilityId provided for push notification. Skipping. title={}", title);
            return;
        }

        try {
            Map<String, Object> pushRequest = new HashMap<>();
            pushRequest.put("title", title);
            pushRequest.put("body", body);
            pushRequest.put("facilityId", facilityId);
            pushRequest.put("tenantId", tenantId);
            if (data != null && !data.isEmpty()) {
                pushRequest.put("data", data);
            }

            log.info("Pushing notification to topic: {}, title={}, facilityId={}, tenantId={}",
                    properties.getPushNotificationTopic(), title, facilityId, tenantId);

            producer.push(properties.getPushNotificationTopic(), pushRequest);

            log.info("Push notification sent to Kafka. title={}, facilityId={}", title, facilityId);

        } catch (Exception e) {
            log.error("Failed to push notification to Kafka. title={}, facilityId={}: {}",
                    title, facilityId, e.getMessage(), e);
            throw new CustomException(Constants.ERROR_PUSH_NOTIFICATION_FAILED,
                    "Failed to send push notification: " + e.getMessage());
        }
    }
}