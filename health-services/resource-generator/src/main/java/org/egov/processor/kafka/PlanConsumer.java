package org.egov.processor.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.egov.processor.service.ResourceEstimationService;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.PlanConfigurationRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

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
            if (planConfigurationRequest.getPlanConfiguration().getStatus().equals(PlanConfiguration.StatusEnum.GENERATED)) {
                resourceEstimationService.estimateResources(planConfigurationRequest);
                log.info("Successfully estimated resources for plan.");
            }
        } catch (Exception exception) {
            log.error("Error processing record from topic "+topic+" with exception :"+exception);
            throw new CustomException(Integer.toString(HttpStatus.INTERNAL_SERVER_ERROR.value()),
					exception.toString());
        }
    }
}
