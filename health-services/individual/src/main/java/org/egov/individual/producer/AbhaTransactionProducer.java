package org.egov.individual.producer;


import org.egov.common.producer.Producer;
import org.springframework.stereotype.Component;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.tracer.kafka.CustomKafkaTemplate;

@Component("abhaTransactionProducer")
public class AbhaTransactionProducer extends Producer {
    public AbhaTransactionProducer(CustomKafkaTemplate<String, Object> kafkaTemplate,
                           MultiStateInstanceUtil multiStateInstanceUtil) {
        super(kafkaTemplate, multiStateInstanceUtil);
    }
}
