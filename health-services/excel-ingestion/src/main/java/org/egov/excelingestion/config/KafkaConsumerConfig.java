package org.egov.excelingestion.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.egov.excelingestion.web.models.GenerateResourceRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer configuration for the generation init flow.
 *
 * <ul>
 *   <li>max.poll.records = 1 — handle one event at a time per partition.</li>
 *   <li>enable.auto.commit = false + AckMode.MANUAL_IMMEDIATE — the listener
 *       acknowledges only after a successful (or terminally handled) run.</li>
 *   <li>max.poll.interval.ms is configurable high so long-running generations
 *       don't trigger consumer rebalance.</li>
 * </ul>
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${kafka.config.bootstrap_server_config}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:excel-ingestion}")
    private String groupId;

    @Value("${excel.ingestion.consumer.max.poll.records:1}")
    private int maxPollRecords;

    @Value("${excel.ingestion.consumer.max.poll.interval.ms:900000}")
    private int maxPollIntervalMs;

    @Value("${excel.ingestion.consumer.session.timeout.ms:120000}")
    private int sessionTimeoutMs;

    @Bean
    public ConsumerFactory<String, GenerateResourceRequest> generationInitConsumerFactory(ObjectMapper objectMapper) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, maxPollIntervalMs);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, sessionTimeoutMs);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, GenerateResourceRequest.class.getName());

        JsonDeserializer<GenerateResourceRequest> valueDeserializer =
                new JsonDeserializer<>(GenerateResourceRequest.class, objectMapper, false);
        valueDeserializer.addTrustedPackages("*");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }

    @Bean(name = "generationInitListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, GenerateResourceRequest> generationInitListenerContainerFactory(
            ConsumerFactory<String, GenerateResourceRequest> generationInitConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, GenerateResourceRequest> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(generationInitConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(1);
        return factory;
    }
}
