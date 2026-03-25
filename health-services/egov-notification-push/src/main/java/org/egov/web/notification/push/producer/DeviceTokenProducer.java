package org.egov.web.notification.push.producer;

import org.egov.web.notification.push.config.PushProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DeviceTokenProducer {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private PushProperties properties;

    public void push(String tenantId, String topic, Object value) {
        String resolvedTopic = topic;
        if (properties.getIsCentralInstance() && tenantId != null && !tenantId.isEmpty()) {
            String stateCode = getStateCode(tenantId);
            resolvedTopic = stateCode + "-" + topic;
        }
        log.info("Publishing to topic: {}", resolvedTopic);
        kafkaTemplate.send(resolvedTopic, value);
    }

    private String getStateCode(String tenantId) {
        if (tenantId.contains(".")) {
            return tenantId.split("\\.")[properties.getSchemaIndexPosition()];
        }
        // tenantId is the state code itself (e.g., "ba")
        return tenantId;
    }

}
