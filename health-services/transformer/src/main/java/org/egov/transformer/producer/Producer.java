package org.egov.transformer.producer;

import lombok.extern.slf4j.Slf4j;
import org.egov.tracer.kafka.CustomKafkaTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

// NOTE: If tracer is disabled change CustomKafkaTemplate to KafkaTemplate in autowiring

@Service
@Slf4j
public class Producer {

    private final CustomKafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public Producer(CustomKafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void push(String topic, Object value) {
        SendResult<String, Object> result = kafkaTemplate.send(topic, value);
        log.info("pushed to kafka {}", result.getRecordMetadata());
    }
}
