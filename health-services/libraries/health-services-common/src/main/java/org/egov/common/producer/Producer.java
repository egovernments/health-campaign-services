package org.egov.common.producer;

import lombok.extern.slf4j.Slf4j;
import org.egov.tracer.kafka.CustomKafkaTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// NOTE: If tracer is disabled change CustomKafkaTemplate to KafkaTemplate in autowiring

@Service
@Slf4j
public class Producer {
    private static final Logger log = LoggerFactory.getLogger(Producer.class);

//    private final CustomKafkaTemplate<String, Object> kafkaTemplate;
//
//    @Autowired
//    public Producer(CustomKafkaTemplate<String, Object> kafkaTemplate) {
//        this.kafkaTemplate = kafkaTemplate;
//    }

    public void push(String topic, Object value) {
        log.info(topic, value);
    }
}
