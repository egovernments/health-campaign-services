package org.egov.common.producer;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.config.KafkaEnvironmentConfig;
import org.egov.tracer.kafka.CustomKafkaTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// NOTE: If tracer is disabled change CustomKafkaTemplate to KafkaTemplate in autowiring

@Service
@Slf4j
public class Producer {

    private final CustomKafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaEnvironmentConfig config;


    public Producer(CustomKafkaTemplate<String, Object> kafkaTemplate,
                    KafkaEnvironmentConfig config) {
        this.kafkaTemplate = kafkaTemplate;
        this.config = config;
    }

    public void push(String topic, Object value) {
        kafkaTemplate.send(topic, value);
    }

    public void push(String topic, Object value, String tenantId) {
        String updatedTopic = topic;

        if (config.isCentralInstance()) {

            updatedTopic = tenantId + "-" + topic;
            log.info("Central instance detected. Updated topic: {}", updatedTopic);
        }

        log.info("Producing to Kafka topic: {}", updatedTopic);
        kafkaTemplate.send(updatedTopic, value);
    }
}
