package org.egov.healthnotification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class HealthNotificationConsumer {

    private final ObjectMapper objectMapper;

    @Autowired
    public HealthNotificationConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${health.notification.consumer.topic}")
    public void listen(Map<String, Object> consumerRecord,
                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            log.info("Received message from topic: {}", topic);
            // Process the message here
            log.info("Message: {}", consumerRecord);
        } catch (Exception exception) {
            log.error("Error in health notification consumer", exception);
        }
    }
}
