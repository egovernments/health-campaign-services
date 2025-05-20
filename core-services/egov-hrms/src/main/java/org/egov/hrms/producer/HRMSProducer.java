package org.egov.hrms.producer;

import lombok.extern.slf4j.Slf4j;
import org.egov.hrms.utils.HRMSUtils;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.tracer.kafka.CustomKafkaTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class HRMSProducer {

    @Autowired
    private CustomKafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private HRMSUtils hrmsUtils;

    @Autowired
    MultiStateInstanceUtil multiStateInstanceUtil;

    public void push(String tenantId, String topic, Object value) {
        String updatedTopic =  multiStateInstanceUtil.getStateSpecificTopicName(tenantId, topic);
        log.info("The Kafka topic for the tenantId : " + tenantId + " is : " + updatedTopic);
        log.info("Topic: "+topic);
        kafkaTemplate.send(updatedTopic, value);
    }

    public void push(String topic, Object value) {
        log.info("Topic: "+topic);
        kafkaTemplate.send(topic, value);
    }
}
