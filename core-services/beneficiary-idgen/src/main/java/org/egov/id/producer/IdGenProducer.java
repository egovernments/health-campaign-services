package org.egov.id.producer;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.tracer.kafka.CustomKafkaTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class IdGenProducer {

    @Autowired
    private CustomKafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private MultiStateInstanceUtil multiStateInstanceUtil;

    public void push(String tenantId, String topic, Object value) {
        String updatedTopic = multiStateInstanceUtil.getStateSpecificTopicName(tenantId, topic);
        kafkaTemplate.send(updatedTopic, value);
        log.debug("Message successfully sent to topic: {} for tenantId: {}", updatedTopic, tenantId);
    }

    public void push(String topic, Object value) {
        kafkaTemplate.send(topic, value);
        log.debug("Message successfully sent to topic: {}", topic);
    }

    public void pushWithKey(String topic, String key, Object value) {
        kafkaTemplate.send(topic, key, value);
        log.debug("Message successfully sent to topic: {} with key: {}", topic, key);
    }

    public void pushWithKey(String tenantId, String topic, String key, Object value) {
        String updatedTopic = multiStateInstanceUtil.getStateSpecificTopicName(tenantId, topic);
        kafkaTemplate.send(updatedTopic, key, value);
        log.debug("Message successfully sent to topic: {} with key: {} for tenantId: {}", updatedTopic, key, tenantId);
    }
}

