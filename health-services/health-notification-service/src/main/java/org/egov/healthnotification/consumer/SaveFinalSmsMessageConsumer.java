package org.egov.healthnotification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.core.SearchResponse;
import org.egov.healthnotification.repository.ScheduledNotificationRepository;
import org.egov.healthnotification.service.NotificationDispatchService;
import org.egov.healthnotification.web.models.ScheduledNotification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


@Component
@Slf4j
public class SaveFinalSmsMessageConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationDispatchService notificationDispatchService;
    private final ScheduledNotificationRepository notificationRepository;

    @Autowired
    public SaveFinalSmsMessageConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper,
                                       NotificationDispatchService notificationDispatchService,
                                       ScheduledNotificationRepository notificationRepository) {
        this.objectMapper = objectMapper;
        this.notificationDispatchService = notificationDispatchService;
        this.notificationRepository = notificationRepository;
    }

    /**
     * Listens to save-final-sms-message topic with multi-tenant support.
     *
     * The topic pattern (${kafka.tenant.id.pattern}){0,1}${kafka.topics.save.final.sms.message}
     * supports both:
     * - ba-save-final-sms-message (central instance)
     * - save-final-sms-message (non-central instance)
     *
     * @param payload The Kafka ConsumerRecord containing the raw JSON string
     * @param topic The Kafka topic from which the message was received
     */
    @KafkaListener(topicPattern = "(${kafka.tenant.id.pattern}){0,1}${kafka.topics.save.final.sms.message}")
    public void consumeSaveFinalSmsMessage(ConsumerRecord<String, Object> payload,
                                           @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            log.info("Received SMS message from topic: {}", topic);

            // Deserialize the payload to Map
            @SuppressWarnings("unchecked")
            Map<String, Object> smsRequest = objectMapper.readValue(
                    (String) payload.value(),
                    HashMap.class);

            if (smsRequest == null || smsRequest.isEmpty()) {
                log.warn("Received empty SMS message from topic: {}. Skipping.", topic);
                return;
            }

            String notificationId = (String) smsRequest.get("notificationId");
            String tenantId = (String) smsRequest.get("tenantId");
            String mobileNumber = (String) smsRequest.get("mobileNumber");
            String message = (String) smsRequest.get("message");

            if (notificationId == null || notificationId.isBlank()) {
                log.error("Received SMS message with null/blank notificationId from topic: {}. Skipping.", topic);
                return;
            }

            if (tenantId == null || tenantId.isBlank()) {
                log.error("Received SMS message with null/blank tenantId from topic: {}. Skipping.", topic);
                return;
            }

            if (mobileNumber == null || mobileNumber.isBlank()) {
                log.error("Received SMS message with null/blank mobileNumber from topic: {}. Skipping.", topic);
                return;
            }

            if (message == null || message.isBlank()) {
                log.error("Received SMS message with null/blank message from topic: {}. Skipping.", topic);
                return;
            }

            log.info("Processing SMS message from topic: {}, notificationId: {}, tenantId: {}",
                    topic, notificationId, tenantId);

            // Fetch the notification from the repository
            SearchResponse<ScheduledNotification> searchResponse = notificationRepository.findById(
                    tenantId,
                    Collections.singletonList(notificationId),
                    "id",
                    false
            );

            if (searchResponse == null || searchResponse.getResponse() == null || searchResponse.getResponse().isEmpty()) {
                log.error("Notification not found for id: {}, tenantId: {}. Cannot mark as sent.", notificationId, tenantId);
                return;
            }

            ScheduledNotification notification = searchResponse.getResponse().get(0);

            // Mark the notification as SENT (this will push to Kafka topic and update DB)
            notificationDispatchService.markSent(notification);

            log.info("Successfully marked notification as SENT: id={}, tenantId: {}",
                    notificationId, tenantId);

        } catch (Exception e) {
            log.error("Error processing SMS message from topic: {}", topic, e);
        }
    }
}
