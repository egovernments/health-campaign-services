package org.egov.web.notification.push.producer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DeviceTokenProducer {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    public void push(String topic, Object value) {
        log.info("Publishing to topic: {}", topic);
        kafkaTemplate.send(topic, value);
    }

}
