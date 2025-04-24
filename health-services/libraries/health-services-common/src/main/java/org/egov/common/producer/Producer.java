package org.egov.common.producer;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.utils.CommonUtils;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.tracer.kafka.CustomKafkaTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

// NOTE: If tracer is disabled change CustomKafkaTemplate to KafkaTemplate in autowiring

@Service
@Slf4j
public class Producer {

    private final CustomKafkaTemplate<String, Object> kafkaTemplate;
    private final MultiStateInstanceUtil multiStateInstanceUtil;

    @Autowired
    public Producer(CustomKafkaTemplate<String, Object> kafkaTemplate, MultiStateInstanceUtil multiStateInstanceUtil) {
        this.kafkaTemplate = kafkaTemplate;
        this.multiStateInstanceUtil = multiStateInstanceUtil;
    }

    /**
     * push objects to kafka with modified topic for a specified tenant based on
     * central instance environment configuration
     *
     * @param tenantId tenant id to get the topic for.
     * @param topic    topic name to push changes for.
     * @param value    Object which needs to be pushed.
     */
    public void push(String tenantId, String topic, Object value) {
        String updatedTopic = topic;
        String schemaName = CommonUtils.getSchemaName(tenantId, multiStateInstanceUtil);
        if (!ObjectUtils.isEmpty(schemaName)) {
            updatedTopic = schemaName.concat("-").concat(topic);
        }
        log.info("The Kafka topic for the tenantId : {} is : {}", tenantId, updatedTopic);
        kafkaTemplate.send(updatedTopic, value);
    }
}
