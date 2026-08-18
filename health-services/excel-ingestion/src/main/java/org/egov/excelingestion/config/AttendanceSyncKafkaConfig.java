package org.egov.excelingestion.config;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.egov.excelingestion.web.models.AttendanceSyncEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Own listener factory for the attendance sync consumer. The service-wide consumer config pins
 * spring.json.value.default.type to the generation init event, so attendance payloads need their
 * own deserializer; everything else (brokers, group, offset reset) is inherited.
 */
@Configuration
public class AttendanceSyncKafkaConfig {

    private static final String SPRING_JSON_PROPERTY_PREFIX = "spring.json.";

    @Value("${excel.ingestion.attendance.sync.retry.interval.ms:5000}")
    private long retryIntervalMs;

    // Retries after the first attempt, so 2 means three tries in total.
    @Value("${excel.ingestion.attendance.sync.retry.attempts:2}")
    private long retryAttempts;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AttendanceSyncEvent> attendanceSyncListenerContainerFactory(
            KafkaProperties kafkaProperties) {

        // A malformed payload deserializes to null instead of poisoning the partition; the consumer
        // logs and acks it.
        JsonDeserializer<AttendanceSyncEvent> jsonDeserializer =
                new JsonDeserializer<>(AttendanceSyncEvent.class, false);
        jsonDeserializer.ignoreTypeHeaders();

        // The service-wide spring.json.* settings pin the payload type to the generation init event.
        // Drop them: this deserializer is configured in code, and JsonDeserializer rejects both at once.
        Map<String, Object> consumerProperties = new HashMap<>(kafkaProperties.buildConsumerProperties());
        consumerProperties.keySet().removeIf(key -> key.startsWith(SPRING_JSON_PROPERTY_PREFIX));
        ConsumerFactory<String, AttendanceSyncEvent> consumerFactory = new DefaultKafkaConsumerFactory<>(
                consumerProperties,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(jsonDeserializer));

        ConcurrentKafkaListenerContainerFactory<String, AttendanceSyncEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        // A failed expiry is retried with a real gap, so a transient DB/Kafka blip still invalidates
        // the template. After the last attempt the offset is committed (ackAfterHandle), so a
        // permanently failing event is logged and skipped instead of blocking the partition.
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new FixedBackOff(retryIntervalMs, retryAttempts)));
        return factory;
    }
}
