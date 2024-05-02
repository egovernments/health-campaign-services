package org.egov.processor.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.egov.processor.service.ResourceEstimationService;
import org.egov.processor.web.models.Plan;
import org.egov.processor.web.models.PlanConfigurationRequest;
import org.egov.processor.web.models.PlanRequest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.HashMap;

@Component
@Slf4j
public class PlanConsumer {
    private ObjectMapper objectMapper;

    private ResourceEstimationService resourceEstimationService;

    public PlanConsumer(ObjectMapper objectMapper, ResourceEstimationService resourceEstimationService) {
        this.objectMapper = objectMapper;
        this.resourceEstimationService = resourceEstimationService;
    }

    @KafkaListener(topics = { "${plan.config.consumer.kafka.save.topic}", "${plan.config.consumer.kafka.update.topic}" })
    public void listen(Map<String, Object> consumerRecord, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            PlanConfigurationRequest planConfigurationRequest = objectMapper.convertValue(consumerRecord, PlanConfigurationRequest.class);
            resourceEstimationService.estimateResources(planConfigurationRequest);
        } catch (Exception exception) {
            log.error("Error in Plan consumer", exception);
        }
    }
}
