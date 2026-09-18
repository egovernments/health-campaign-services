package org.egov.individual.producer;

import org.egov.common.producer.Producer;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.tracer.kafka.CustomKafkaTemplate;
import org.springframework.stereotype.Component;

@Component("individualProducer")
public class IndividualProducer extends Producer {
    /*
     * Constructor to initialize the Kafka template and MultiStateInstanceUtil
     */
    public IndividualProducer(CustomKafkaTemplate<String, Object> kafkaTemplate , MultiStateInstanceUtil multiStateInstanceUtil) {
        super(kafkaTemplate, multiStateInstanceUtil);
    }
}
