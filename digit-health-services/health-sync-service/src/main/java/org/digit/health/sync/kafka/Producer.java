package org.digit.health.sync.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.digit.health.sync.exception.ProducerException;
import org.egov.tracer.kafka.CustomKafkaTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class Producer {
    private CustomKafkaTemplate<String, Object> kafkaTemplate;

    private ObjectMapper objectMapper;

    @Autowired
    public Producer(CustomKafkaTemplate<String, Object> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }


    public void send(String topic, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            log.debug(json);
            kafkaTemplate.send(topic, objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            throw new ProducerException("Topic: " + topic + " " +
                    ex.getMessage(), ex);
        }
    }
}
