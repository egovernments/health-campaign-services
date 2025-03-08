package org.egov.processor.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.processor.config.Configuration;
import org.egov.processor.service.ResourceEstimationService;
import org.egov.processor.web.models.PlanConfigurationRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Map;

@Component
@Slf4j
public class PlanConsumer {

    private ObjectMapper objectMapper;

    private ResourceEstimationService resourceEstimationService;

    private Configuration config;

    public PlanConsumer(ObjectMapper objectMapper, ResourceEstimationService resourceEstimationService, Configuration config) {
        this.objectMapper = objectMapper;
        this.resourceEstimationService = resourceEstimationService;
        this.config = config;
    }

    @KafkaListener(topics = { "${plan.config.consumer.kafka.save.topic}", "${plan.config.consumer.kafka.update.topic}" })
    public void listen(Map<String, Object> consumerRecord, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            PlanConfigurationRequest planConfigurationRequest = objectMapper.convertValue(consumerRecord, PlanConfigurationRequest.class);
            if (!ObjectUtils.isEmpty(planConfigurationRequest.getPlanConfiguration().getWorkflow()) && (planConfigurationRequest.getPlanConfiguration().getStatus().equals(config.getPlanConfigTriggerPlanEstimatesStatus())
                    || planConfigurationRequest.getPlanConfiguration().getStatus().equals(config.getPlanConfigTriggerCensusRecordsStatus())
                    || planConfigurationRequest.getPlanConfiguration().getStatus().equals(config.getPlanConfigTriggerPlanFacilityMappingsStatus())
                    || planConfigurationRequest.getPlanConfiguration().getStatus().equals(config.getPlanConfigUpdatePlanEstimatesIntoOutputFileStatus()))) {
                resourceEstimationService.estimateResources(planConfigurationRequest);
                log.info("Successfully estimated resources for plan.");
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            log.error("Error processing record from topic "+topic+" with exception :"+exception);

            throw new CustomException(Integer.toString(HttpStatus.INTERNAL_SERVER_ERROR.value()),
					exception.toString());
        }
    }
}
