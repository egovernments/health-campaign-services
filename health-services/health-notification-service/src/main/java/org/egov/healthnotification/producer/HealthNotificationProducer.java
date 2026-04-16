package org.egov.healthnotification.producer;

import org.egov.common.producer.Producer;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.tracer.kafka.CustomKafkaTemplate;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Health Notification specific Producer bean.
 * Marked @Primary to resolve ambiguity with the base Producer from health-services-common.
 */
@Component("healthNotificationProducer")
@Primary
public class HealthNotificationProducer extends Producer {
    /*
     * Constructor to initialize the Kafka template and MultiStateInstanceUtil
     */
    public HealthNotificationProducer(CustomKafkaTemplate<String, Object> kafkaTemplate,
                                       MultiStateInstanceUtil multiStateInstanceUtil) {
        super(kafkaTemplate, multiStateInstanceUtil);
    }
}
