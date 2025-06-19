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

    // This is used to get the tenantId from the request
    @Autowired
    private HRMSUtils hrmsUtils;

    @Autowired
    MultiStateInstanceUtil multiStateInstanceUtil;

    /*
     * This method is used to push the data to the kafka topic.
     * The topic name is fetched from the config file and the data is pushed to the topic.
     * The topic name is updated with the tenantId.
     */
    public void push(String tenantId, String topic, Object value) {
        // Updated topic name with tenantId prefixed
        String updatedTopic =  multiStateInstanceUtil.getStateSpecificTopicName(tenantId, topic);
        log.info("The Kafka topic for the tenantId : " + tenantId + " is : " + updatedTopic);
        kafkaTemplate.send(updatedTopic, value);
    }

    public void push(String topic, Object value) {
        log.info("Topic: "+topic);
        kafkaTemplate.send(topic, value);
    }
}
