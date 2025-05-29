package org.egov.id.producer;

import lombok.extern.slf4j.Slf4j;
import org.egov.tracer.kafka.CustomKafkaTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class IdGenProducer {

    @Autowired
    private CustomKafkaTemplate<String, Object> kafkaTemplate;

    public void push(String topic, Object value) {
        log.info("Topic: "+topic);
        try {
            kafkaTemplate.send(topic, value);
            log.debug("Message successfully sent to topic: {}", topic);
        } catch (Exception e) {
            log.error("Failed to send message to topic: {}", topic, e);
        }
    }
}

